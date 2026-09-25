package com.mangareader.app

import java.io.File

internal object StorageMover {
    private val ownedFolders = listOf(
        StorageLocation.DOWNLOADS,
        StorageLocation.CHAPTERS,
        StorageLocation.BACKUPS,
    )

    fun move(from: File, to: File): Result<Int> = runCatching {
        if (!from.exists() || !from.isDirectory) return@runCatching 0
        if (from.absolutePath == to.absolutePath) return@runCatching 0
        if (!StorageLocation.ensureWritable(to)) {
            error("Can't write to the new folder")
        }

        var moved = 0
        from.listFiles()?.forEach { child ->
            val target = File(to, child.name)
            if (child.renameTo(target)) {
                moved++
            } else {
                if (target.exists()) target.deleteRecursively()
                child.copyRecursively(target, overwrite = true)
                child.deleteRecursively()
                moved++
            }
        }

        runCatching { from.delete() }
        moved
    }

    fun hasStore(base: File): Boolean = runCatching {
        ownedFolders.any { name ->
            val dir = File(base, name)
            dir.isDirectory && (dir.listFiles()?.isNotEmpty() == true)
        }
    }.getOrDefault(false)

    fun moveStore(from: File, to: File): Result<Int> = runCatching {
        if (from.absolutePath == to.absolutePath) return@runCatching 0

        var moved = 0
        ownedFolders.forEach { name ->
            val source = File(from, name)
            if (source.isDirectory) {
                moved += move(source, File(to, name)).getOrDefault(0)
            }
        }
        moved
    }
}
