package com.openctv.browser.features.adblock.data

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Clock
import java.time.Instant

/** Copie locale de chaque liste, rechargée au démarrage avant tout accès réseau. */
interface FilterListCache {
    fun lastUpdated(list: FilterListId): Instant?

    /** Lit le cache de [list] en flux ; `null` s'il n'y en a pas. */
    fun <T> readLines(list: FilterListId, block: (Sequence<String>) -> T): T?

    /** Copie [input] dans un fichier temporaire sans toucher au cache : il ne le remplace qu'à [StagedList.commit]. */
    fun stage(list: FilterListId, input: InputStream): StagedList

    interface StagedList {
        fun <T> readLines(block: (Sequence<String>) -> T): T
        fun commit()
        fun discard()
    }
}

class FileFilterListCache(
    private val directory: File,
    private val clock: Clock,
    private val maxBytes: Long = DefaultMaxBytes,
) : FilterListCache {
    private fun cacheFileOf(list: FilterListId) = File(directory, list.cacheFileName)
    private fun stagedFileOf(list: FilterListId) = File(directory, list.cacheFileName + ".tmp")

    override fun lastUpdated(list: FilterListId): Instant? =
        cacheFileOf(list).takeIf { it.isFile }?.let { Instant.ofEpochMilli(it.lastModified()) }

    override fun <T> readLines(list: FilterListId, block: (Sequence<String>) -> T): T? =
        cacheFileOf(list).takeIf { it.isFile }?.let { it.readLinesOf(block) }

    override fun stage(list: FilterListId, input: InputStream): FilterListCache.StagedList {
        val stagedFile = stagedFileOf(list)
        try {
            input.use { source ->
                stagedFile.outputStream().use { target ->
                    var copied = 0L
                    val buffer = ByteArray(BufferBytes)
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        copied += read
                        if (copied > maxBytes) throw IOException("Liste trop volumineuse")
                        target.write(buffer, 0, read)
                    }
                }
            }
        } catch (e: Throwable) {
            // Téléchargement interrompu, trop gros… : pas de fichier partiel laissé sur la TV.
            stagedFile.delete()
            throw e
        }
        return Staged(stagedFile, cacheFileOf(list))
    }

    private inner class Staged(private val stagedFile: File, private val cacheFile: File) : FilterListCache.StagedList {
        override fun <T> readLines(block: (Sequence<String>) -> T): T = stagedFile.readLinesOf(block)

        override fun commit() {
            stagedFile.setLastModified(clock.millis())
            if (!stagedFile.renameTo(cacheFile)) {
                stagedFile.copyTo(cacheFile, overwrite = true)
                stagedFile.delete()
            }
        }

        override fun discard() {
            stagedFile.delete()
        }
    }

    private fun <T> File.readLinesOf(block: (Sequence<String>) -> T): T =
        bufferedReader().use { block(it.lineSequence()) }

    private companion object {
        const val DefaultMaxBytes = 32L * 1024 * 1024
        const val BufferBytes = 16 * 1024
    }
}
