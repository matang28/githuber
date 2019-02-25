package matang28.githuber

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.EmptyProgressMonitor
import org.kohsuke.github.GitHub
import java.io.File
import java.nio.file.Paths


data class GitRepository(
    val name: String,
    val language: String,
    val cloneUrl: String
)

suspend fun main(args: Array<String>) {

    val oAuthToken = args[0]
    val userName = args[1]
    val pathToClone = args[2]

    val github = GitHub.connect(userName, oAuthToken)

    github
        .getUser(userName)
        .listRepositories(20)

        .sortedBy { it.size }

        .map {
            GitRepository(it.name, it.language ?: "unknown", it.gitTransportUrl)
        }

        .groupBy { it.language }

        .map { entry ->
            val basePath = Paths.get(pathToClone, sanitizeLanguage(entry.key)).toFile()
            basePath.mkdirs()
            cloneByLanguage(entry, basePath)
        }

        .awaitAll()

    println("Done cloning public repositories of $userName, navigate to: $pathToClone to whiteness this magic")
}

private fun cloneByLanguage(entry: Map.Entry<String, List<GitRepository>>, basePath: File): Deferred<String> =
    GlobalScope.async {
        entry.value.forEach { repository ->
            val clonePath = Paths.get(basePath.absolutePath, repository.name).toFile()

            println("Cloning ${repository.cloneUrl} to: $clonePath")

            Git.cloneRepository()
                .setURI(repository.cloneUrl)
                .setDirectory(clonePath)
                .setProgressMonitor(ImNotStuckProgressMonitor(repository.name))
                .call()

            println("Done Cloning ${repository.cloneUrl} to: $clonePath")
        }

        return@async entry.key
    }

private fun sanitizeLanguage(language: String) =
    language
        .toLowerCase()
        .replace(" ", "-")


class ImNotStuckProgressMonitor(
    private val name: String,
    private val thresholdInMillis: Long = 1000 * 10
) : EmptyProgressMonitor() {

    var startTime: Long = 0
    var lastCheck: Long = 0

    override fun start(totalTasks: Int) {
        startTime = System.currentTimeMillis()
        lastCheck = startTime
    }

    override fun update(completed: Int) {
        val now = System.currentTimeMillis()
        if (now - lastCheck >= thresholdInMillis) {
            println("Still cloning $name... Dont give up on me :)")
            lastCheck = now
        }
    }
}