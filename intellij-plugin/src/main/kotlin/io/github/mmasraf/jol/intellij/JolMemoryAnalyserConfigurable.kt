package io.github.mmasraf.jol.intellij

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import java.io.File
import javax.swing.JComponent

class JolMemoryAnalyserConfigurable : Configurable {

    private val jarField = JBTextField()

    override fun getDisplayName(): String = "JOL Memory Analyser"

    override fun createComponent(): JComponent {
        jarField.toolTipText =
            "Optional. Leave empty to auto-detect jol-memory-analyser-*-standalone.jar."
        jarField.text = JolMemoryAnalyserSettings.getInstance().getState().standaloneJarPath

        return FormBuilder.createFormBuilder()
            .addLabeledComponent(
                JBLabel("Standalone JAR path (jol-memory-analyser-*-standalone.jar)"),
                jarField,
                true,
            )
            .panel
    }

    override fun isModified(): Boolean {
        return jarField.text.trim() != JolMemoryAnalyserSettings.getInstance().getState().standaloneJarPath.trim()
    }

    @Throws(ConfigurationException::class)
    override fun apply() {
        val path = jarField.text.trim()
        if (path.isNotEmpty() && !File(path).isFile) {
            throw ConfigurationException("File does not exist: $path")
        }
        JolMemoryAnalyserSettings.getInstance()
            .loadState(JolMemoryAnalyserSettings.State(standaloneJarPath = path))
    }

    override fun reset() {
        jarField.text = JolMemoryAnalyserSettings.getInstance().getState().standaloneJarPath
    }
}
