package io.github.mmasraf.jol.intellij

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "JolMemoryAnalyserSettings", storages = [Storage("jol-memory-analyser.xml")])
class JolMemoryAnalyserSettings : PersistentStateComponent<JolMemoryAnalyserSettings.State> {

    data class State(
        /** Explicit path to `jol-memory-analyser-*-standalone.jar`; optional when auto-detection works. */
        var standaloneJarPath: String = "",
    )

    private var internalState = State()

    override fun getState(): State = internalState

    override fun loadState(state: State) {
        internalState = state
    }

    companion object {
        fun getInstance(): JolMemoryAnalyserSettings =
            ApplicationManager.getApplication().getService(JolMemoryAnalyserSettings::class.java)
    }
}
