package io.github.mmasraf.jol.intellij

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.components.JBLabel
import javax.swing.BorderFactory

class JolMemoryAnalyserToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val hint = JBLabel(
            """
            <html>Run <b>Tools → Scan project with JOL Memory Analyser</b> or use the editor right-click action.<br/>
            Full scanner output (stdout/stderr) appears below.</html>
            """.trimIndent(),
        )
        hint.border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
        val content = ContentFactory.getInstance().createContent(hint, "", false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true
}
