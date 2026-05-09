package io.github.mmasraf.jol.intellij

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import java.io.File
import java.nio.charset.StandardCharsets

object JolScannerRunner {

    private const val TOOL_WINDOW_ID = "JolMemoryAnalyser"

    fun scanProject(project: Project) {
        run(project, fileArgument = null)
    }

    fun scanCurrentJavaFile(project: Project, absolutePath: String) {
        run(project, fileArgument = absolutePath)
    }

    private fun run(project: Project, fileArgument: String?) {
        ApplicationManager.getApplication().invokeLater {
            val console = TextConsoleBuilderFactory.getInstance()
                .createBuilder(project)
                .console

            console.clear()

            val basePath = project.basePath
            if (basePath == null) {
                console.print(
                    "Project has no base path on disk.\n",
                    ConsoleViewContentType.ERROR_OUTPUT,
                )
                if (!showConsoleInToolWindow(project, console)) {
                    Messages.showErrorDialog(project, "Project has no base path.", "JOL Memory Analyser")
                }
                return@invokeLater
            }

            val jar = JolJarLocator.resolve(project)
            if (jar == null) {
                console.print(JolJarLocator.helpText() + "\n\n", ConsoleViewContentType.ERROR_OUTPUT)
                console.print(
                    "Tip: Configure the standalone JAR once under Settings → Tools → JOL Memory Analyser; " +
                        "that path is shared across all open projects and IDE windows.\n",
                    ConsoleViewContentType.NORMAL_OUTPUT,
                )
                if (!showConsoleInToolWindow(project, console)) {
                    Messages.showErrorDialog(project, JolJarLocator.helpText(), "JOL Memory Analyser")
                }
                return@invokeLater
            }

            console.print("Working directory: $basePath\n", ConsoleViewContentType.NORMAL_OUTPUT)
            console.print("Command: ", ConsoleViewContentType.NORMAL_OUTPUT)
            console.print(javaExecutable(), ConsoleViewContentType.USER_INPUT)
            console.print(" -jar ", ConsoleViewContentType.NORMAL_OUTPUT)
            console.print(jar.absolutePath, ConsoleViewContentType.USER_INPUT)
            if (fileArgument != null) {
                console.print(" --file ", ConsoleViewContentType.NORMAL_OUTPUT)
                console.print(fileArgument, ConsoleViewContentType.USER_INPUT)
            }
            console.print("\n\n", ConsoleViewContentType.NORMAL_OUTPUT)

            val cmdLine = GeneralCommandLine()
                .withExePath(javaExecutable())
                .withParameters("-jar", jar.absolutePath)
                .withWorkDirectory(File(basePath))
                .withCharset(StandardCharsets.UTF_8)
            if (fileArgument != null) {
                cmdLine.addParameters("--file", fileArgument)
            }
            cmdLine.withRedirectErrorStream(true)

            if (!showConsoleInToolWindow(project, console)) {
                Messages.showErrorDialog(
                    project,
                    "Could not open the JOL Memory Analyser tool window.",
                    "JOL Memory Analyser",
                )
                return@invokeLater
            }

            try {
                val handler = OSProcessHandler(cmdLine)
                handler.addProcessListener(object : ProcessAdapter() {
                    override fun processTerminated(event: ProcessEvent) {
                        ApplicationManager.getApplication().invokeLater {
                            val code = event.exitCode
                            if (code == 0) {
                                console.print(
                                    "\n---\nScan finished (exit 0).\n" +
                                        "Excel workbooks: memory-report.xlsx, memory-report-developer.xlsx\n" +
                                        "Location: $basePath\n",
                                    ConsoleViewContentType.LOG_INFO_OUTPUT,
                                )
                            } else {
                                console.print(
                                    "\n---\nProcess exited with code $code\n",
                                    ConsoleViewContentType.ERROR_OUTPUT,
                                )
                            }
                        }
                    }
                })
                console.attachToProcess(handler)
                handler.startNotify()
            } catch (e: Exception) {
                console.print(
                    "Failed to start process: ${e.message}\n",
                    ConsoleViewContentType.ERROR_OUTPUT,
                )
            }
        }
    }

    private fun showConsoleInToolWindow(project: Project, console: ConsoleView): Boolean {
        val tw = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return false
        tw.contentManager.removeAllContents(true)
        val content = ContentFactory.getInstance().createContent(console.component, "", false)
        tw.contentManager.addContent(content)
        tw.activate(null)
        return true
    }

    private fun javaExecutable(): String {
        val home = System.getProperty("java.home") ?: return "java"
        val win = File(home, "bin/java.exe")
        if (win.isFile) return win.absolutePath
        val unix = File(home, "bin/java")
        if (unix.isFile) return unix.absolutePath
        return "java"
    }
}
