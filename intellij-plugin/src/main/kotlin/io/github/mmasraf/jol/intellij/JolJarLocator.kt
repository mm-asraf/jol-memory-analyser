package io.github.mmasraf.jol.intellij

import com.intellij.openapi.project.Project
import java.io.File
import java.util.regex.Pattern

/**
 * Resolves the standalone uber-JAR used to run [com.asraf.jol.ProjectScanner] out-of-process.
 *
 * Resolution order:
 * 1. Environment variable `JOL_MEMORY_ANALYSER_JAR`
 * 2. Path configured in Settings, Tools, JOL Memory Analyser
 * 3. `project/target/jol-memory-analyser-*-standalone.jar` (newest by modification time)
 * 4. Under each Maven local repository root (see [mavenLocalRepositoryRoots]):
 *    `.../io/github/mm-asraf/jol-memory-analyser/<version>/jol-memory-analyser-*-standalone.jar`
 */
object JolJarLocator {

    private val STANDALONE_PATTERN = Pattern.compile("jol-memory-analyser-.+-standalone\\.jar")

    fun resolve(project: Project): File? {
        System.getenv("JOL_MEMORY_ANALYSER_JAR")?.trim()?.takeIf { it.isNotEmpty() }?.let { envPath ->
            File(envPath).takeIf { it.isFile }?.let { return it }
        }

        JolMemoryAnalyserSettings.getInstance().getState().standaloneJarPath.trim().takeIf { it.isNotEmpty() }?.let { configured ->
            File(configured).takeIf { it.isFile }?.let { return it }
        }

        project.basePath?.let { base ->
            findInTargetDirectory(File(base, "target"))?.let { return it }
        }

        for (repoRoot in mavenLocalRepositoryRoots(project)) {
            findStandaloneUnderMavenLocal(repoRoot)?.let { return it }
        }
        return null
    }

    /** IntelliJ’s effective Maven repo + env fallbacks (custom `MAVEN_REPOSITORY`, default `~/.m2/repository`). */
    private fun mavenLocalRepositoryRoots(project: Project): List<File> {
        val roots = LinkedHashSet<File>()
        File(System.getProperty("user.home"), ".m2/repository")
            .takeIf { it.isDirectory }
            ?.let(roots::add)
        System.getenv("MAVEN_REPOSITORY")?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { File(it) }
            ?.takeIf { it.isDirectory }
            ?.let(roots::add)
        mavenRepoFromIntellij(project)?.let(roots::add)
        return roots.toList()
    }

    /**
     * Uses IntelliJ Maven’s effective local repository when the Maven plugin is present (optional).
     */
    private fun mavenRepoFromIntellij(project: Project): File? =
        try {
            val mgrClass = Class.forName("org.jetbrains.idea.maven.project.MavenProjectsManager")
            val mgr = mgrClass.getMethod("getInstance", Project::class.java).invoke(null, project)
            @Suppress("UNCHECKED_CAST")
            (mgrClass.getMethod("getLocalRepository").invoke(mgr) as? File)?.takeIf { it.isDirectory }
        } catch (_: Throwable) {
            null
        }

    private fun findInTargetDirectory(targetDir: File): File? {
        if (!targetDir.isDirectory) return null
        return targetDir.listFiles { file ->
            file.isFile && STANDALONE_PATTERN.matcher(file.name).matches()
        }?.maxByOrNull { it.lastModified() }
    }

    private fun findStandaloneUnderMavenLocal(repositoryRoot: File): File? {
        val artifactRoot = repositoryRoot.resolve("io/github/mm-asraf/jol-memory-analyser")
        if (!artifactRoot.isDirectory) return null

        val versionDirs = artifactRoot.listFiles { f -> f.isDirectory }
            ?.sortedByDescending { it.name }
            .orEmpty()

        for (versionDir in versionDirs) {
            val jar = versionDir.listFiles { file ->
                file.isFile && STANDALONE_PATTERN.matcher(file.name).matches()
            }?.firstOrNull()
            if (jar != null) return jar
        }
        return null
    }

    fun helpText(): String =
        """
        Could not find jol-memory-analyser-*-standalone.jar.

        • Build the library once: mvn package — standalone JAR under target/ (any Maven project).
        • Or resolve the standalone classifier from Maven Central into your local repo, then scan any project.
        • Or set environment variable JOL_MEMORY_ANALYSER_JAR to the full path (IDEs launched from the Dock/Finder often do not inherit terminal env — prefer Settings below).
        • Or set the path in Settings → Tools → JOL Memory Analyser (applies to all projects / windows).
        """.trimIndent()
}
