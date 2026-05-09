package io.github.mmasraf.jol.intellij

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

class ScanCurrentFileAction : AnAction() {

    override fun update(e: AnActionEvent) {
        val vf = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val visible = vf != null && !vf.isDirectory && "java".equals(vf.extension, ignoreCase = true)
        e.presentation.isEnabledAndVisible = visible
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        JolScannerRunner.scanCurrentJavaFile(project, file.path)
    }
}
