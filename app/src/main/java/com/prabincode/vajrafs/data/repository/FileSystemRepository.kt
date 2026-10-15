/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: VajraFS
 * GitHub: https://github.com/PrabinCode/vajrafs-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.vajrafs.data.repository

import android.app.usage.StorageStatsManager
import android.content.ContentUris
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.MediaStore
import com.prabincode.vajrafs.data.model.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class FileSystemRepository(private val context: Context) {

    /**
     * Discovers all available storage volumes (Internal storage, Removable SD cards, and System Root /).
     */
    fun getStorageVolumes(): List<StorageVolumeInfo> {
        val volumes = mutableListOf<StorageVolumeInfo>()
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager

        // 1. Primary Internal Storage (/storage/emulated/0)
        val primaryDir = Environment.getExternalStorageDirectory()
        var totalSpace = primaryDir.totalSpace
        var freeSpace = primaryDir.usableSpace

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
                if (storageStatsManager != null) {
                    val statsTotal = storageStatsManager.getTotalBytes(StorageManager.UUID_DEFAULT)
                    val statsFree = storageStatsManager.getFreeBytes(StorageManager.UUID_DEFAULT)
                    if (statsTotal > 0L) {
                        totalSpace = statsTotal
                        freeSpace = statsFree
                    }
                }
            } catch (_: Exception) {
                // Fallback to primaryDir.usableSpace
            }
        }

        volumes.add(
            StorageVolumeInfo(
                name = "Internal Storage",
                path = primaryDir.absolutePath,
                totalBytes = totalSpace,
                freeBytes = freeSpace,
                isRemovable = false,
                isPrimary = true
            )
        )

        // 2. Removable SD Cards / External media
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                storageManager.storageVolumes.forEach { vol ->
                    if (!vol.isPrimary && vol.state == Environment.MEDIA_MOUNTED) {
                        val file = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            vol.directory
                        } else {
                            val getPathMethod = vol.javaClass.getMethod("getPath")
                            val pathStr = getPathMethod.invoke(vol) as? String
                            if (pathStr != null) File(pathStr) else null
                        }

                        if (file != null && file.exists()) {
                            volumes.add(
                                StorageVolumeInfo(
                                    name = vol.getDescription(context) ?: "SD Card",
                                    path = file.absolutePath,
                                    totalBytes = file.totalSpace,
                                    freeBytes = file.usableSpace,
                                    isRemovable = true,
                                    isPrimary = false
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore volume query errors
            }
        }

        // 3. System Root Filesystem (/)
        val rootDir = File("/")
        if (rootDir.exists() && rootDir.canRead()) {
            volumes.add(
                StorageVolumeInfo(
                    name = "System Root (/)",
                    path = "/",
                    totalBytes = rootDir.totalSpace,
                    freeBytes = rootDir.freeSpace,
                    isRemovable = false,
                    isPrimary = false
                )
            )
        }

        return volumes
    }

    /**
     * Lists directory contents with support for system files, hidden files, and sorting.
     */
    suspend fun getDirectoryContents(
        path: String,
        sortOption: SortOption = SortOption()
    ): List<FileItem> = withContext(Dispatchers.IO) {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) {
            return@withContext emptyList()
        }

        val rawFiles = dir.listFiles() ?: return@withContext emptyList()

        val prefs = context.getSharedPreferences("vajrafs_prefs", Context.MODE_PRIVATE)
        val showNomedia = prefs.getBoolean("show_nomedia_files", true)

        val filtered = rawFiles.filter { file ->
            if (file.name == ".nomedia" && !showNomedia) {
                return@filter false
            }
            if (!sortOption.showHiddenFiles && (file.isHidden || file.name.startsWith("."))) {
                false
            } else {
                true
            }
        }.map { FileItem.fromFile(it) }

        filtered.sortedWith { a, b ->
            if (sortOption.foldersFirst) {
                if (a.isDirectory && !b.isDirectory) return@sortedWith -1
                if (!a.isDirectory && b.isDirectory) return@sortedWith 1
            }

            val comparison = when (sortOption.sortBy) {
                SortBy.NAME -> a.name.compareTo(b.name, ignoreCase = true)
                SortBy.DATE -> a.lastModified.compareTo(b.lastModified)
                SortBy.SIZE -> a.size.compareTo(b.size)
                SortBy.TYPE -> a.extension.compareTo(b.extension, ignoreCase = true)
            }

            if (sortOption.order == SortOrder.DESCENDING) -comparison else comparison
        }
    }

    /**
     * Scans storage for category files using MediaStore for fast retrieval + filesystem fallback.
     */
    suspend fun getCategoryFiles(
        categoryType: FileType,
        rootPath: String = Environment.getExternalStorageDirectory().absolutePath
    ): List<FileItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<FileItem>()

        // 1. MediaStore query based on category
        try {
            val contentResolver = context.contentResolver
            val (uri, projection) = when (categoryType) {
                FileType.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI to arrayOf(MediaStore.Images.Media.DATA)
                FileType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI to arrayOf(MediaStore.Video.Media.DATA)
                FileType.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to arrayOf(MediaStore.Audio.Media.DATA)
                else -> MediaStore.Files.getContentUri("external") to arrayOf(MediaStore.Files.FileColumns.DATA)
            }

            val selection = if (categoryType in setOf(FileType.IMAGE, FileType.VIDEO, FileType.AUDIO)) {
                null
            } else {
                // Build extension filter query
                val extensions = categoryType.extensions
                if (extensions.isNotEmpty()) {
                    val clauses = extensions.map { "${MediaStore.Files.FileColumns.DATA} LIKE '%.${it}'" }
                    clauses.joinToString(" OR ")
                } else null
            }

            contentResolver.query(
                uri,
                projection,
                selection,
                null,
                null
            )?.use { cursor ->
                val dataIndex = cursor.getColumnIndexOrThrow(projection[0])
                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataIndex)
                    if (!path.isNullOrEmpty()) {
                        val file = File(path)
                        if (file.exists() && file.isFile) {
                            val item = FileItem.fromFile(file)
                            if (categoryType in setOf(FileType.IMAGE, FileType.VIDEO, FileType.AUDIO) || item.fileType == categoryType) {
                                results.add(item)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // MediaStore query failed, fallback to direct filesystem scan below
        }

        // 2. Direct filesystem scan fallback if MediaStore returned empty or for code/archives/APKs
        if (results.isEmpty()) {
            val root = File(rootPath)
            if (root.exists()) {
                fun scan(dir: File) {
                    if (dir.name.startsWith(".") || dir.name == "Android") return
                    val list = dir.listFiles() ?: return
                    for (f in list) {
                        if (f.isDirectory) {
                            scan(f)
                        } else {
                            val item = FileItem.fromFile(f)
                            if (item.fileType == categoryType) {
                                results.add(item)
                            }
                        }
                    }
                }
                scan(root)
            }
        }

        results.distinctBy { it.path }.sortedByDescending { it.lastModified }
    }

    /**
     * Search files recursively with coroutine flow cancellation.
     */
    fun searchFiles(
        rootPath: String,
        query: String,
        showHidden: Boolean = false
    ): Flow<List<FileItem>> = flow {
        val root = File(rootPath)
        if (!root.exists()) {
            emit(emptyList())
            return@flow
        }

        val cleanQuery = query.trim().lowercase()
        val results = mutableListOf<FileItem>()

        fun traverse(dir: File) {
            val list = dir.listFiles() ?: return
            for (f in list) {
                if (!showHidden && (f.isHidden || f.name.startsWith("."))) continue

                if (f.name.lowercase().contains(cleanQuery)) {
                    results.add(FileItem.fromFile(f))
                }

                if (f.isDirectory && f.canRead()) {
                    traverse(f)
                }
            }
        }

        traverse(root)
        emit(results)
    }.flowOn(Dispatchers.IO)

    /**
     * Notifies Android media scanner that a file was created or deleted.
     */
    fun notifyMediaScanner(path: String) {
        try {
            MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
        } catch (e: Exception) {
            // Ignore scan notification errors
        }
    }

    suspend fun copyFile(
        source: File,
        destination: File,
        onProgress: (bytesCopied: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (!source.exists()) {
                return@withContext Result.failure(IllegalArgumentException("Source does not exist"))
            }

            if (source.isDirectory) {
                val canonicalSource = source.canonicalPath
                val canonicalDest = destination.canonicalPath
                if (canonicalDest == canonicalSource || canonicalDest.startsWith(canonicalSource + File.separator)) {
                    return@withContext Result.failure(IllegalArgumentException("Cannot copy a directory into itself or its subdirectory"))
                }

                destination.mkdirs()
                source.listFiles()?.forEach { child ->
                    val destChild = File(destination, child.name)
                    copyFile(child, destChild, onProgress)
                }
                return@withContext Result.success(destination)
            }

            destination.parentFile?.mkdirs()
            val totalBytes = source.length()
            var bytesCopied = 0L

            FileInputStream(source).channel.use { inChannel ->
                FileOutputStream(destination).channel.use { outChannel ->
                    val buffer = ByteBuffer.allocateDirect(128 * 1024)
                    while (inChannel.read(buffer) > 0) {
                        buffer.flip()
                        val written = outChannel.write(buffer)
                        bytesCopied += written
                        buffer.compact()
                        onProgress(bytesCopied, totalBytes)
                    }
                }
            }

            notifyMediaScanner(destination.absolutePath)
            Result.success(destination)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun moveFile(source: File, destination: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            val oldPath = source.absolutePath
            if (source.renameTo(destination)) {
                notifyMediaScanner(oldPath)
                notifyMediaScanner(destination.absolutePath)
                return@withContext Result.success(destination)
            }

            val copyResult = copyFile(source, destination)
            if (copyResult.isSuccess) {
                source.deleteRecursively()
                notifyMediaScanner(oldPath)
                notifyMediaScanner(destination.absolutePath)
                Result.success(destination)
            } else {
                Result.failure(copyResult.exceptionOrNull() ?: Exception("Failed to move file"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun batchRename(
        items: List<FileItem>,
        newNames: List<String>
    ): Pair<Int, List<String>> = withContext(Dispatchers.IO) {
        var successCount = 0
        val errors = mutableListOf<String>()
        items.forEachIndexed { index, item ->
            val newName = newNames.getOrNull(index)
            if (!newName.isNullOrEmpty() && newName != item.name) {
                val target = File(item.file.parentFile, newName)
                if (target.exists()) {
                    errors.add("${item.name}: Target already exists")
                } else if (item.file.renameTo(target)) {
                    notifyMediaScanner(item.file.absolutePath)
                    notifyMediaScanner(target.absolutePath)
                    successCount++
                } else {
                    errors.add("${item.name}: Failed to rename")
                }
            }
        }
        Pair(successCount, errors)
    }

    suspend fun zipFiles(
        sourceFiles: List<File>,
        destZipFile: File,
        onProgress: (processedFiles: Int, totalFiles: Int) -> Unit = { _, _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            destZipFile.parentFile?.mkdirs()
            var fileCounter = 0
            val total = sourceFiles.size

            ZipOutputStream(FileOutputStream(destZipFile)).use { zos ->
                for (source in sourceFiles) {
                    fun addEntry(file: File, baseName: String) {
                        if (file.isDirectory) {
                            file.listFiles()?.forEach { child ->
                                addEntry(child, "$baseName/${child.name}")
                            }
                        } else {
                            val entry = ZipEntry(baseName)
                            zos.putNextEntry(entry)
                            FileInputStream(file).use { fis ->
                                fis.copyTo(zos, 64 * 1024)
                            }
                            zos.closeEntry()
                        }
                    }
                    addEntry(source, source.name)
                    fileCounter++
                    onProgress(fileCounter, total)
                }
            }
            notifyMediaScanner(destZipFile.absolutePath)
            Result.success(destZipFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun unzipArchive(
        zipFile: File,
        destDir: File,
        onProgress: (extractedFiles: Int) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            destDir.mkdirs()
            val canonicalDestDir = destDir.canonicalPath
            var extractedCount = 0

            ZipInputStream(FileInputStream(zipFile)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val newFile = File(destDir, entry.name)
                    val canonicalTarget = newFile.canonicalPath
                    // Zip Slip security guard: ensure target file cannot escape destDir
                    if (!canonicalTarget.startsWith(canonicalDestDir + File.separator) && canonicalTarget != canonicalDestDir) {
                        throw SecurityException("Zip entry is outside target directory: ${entry.name}")
                    }

                    if (entry.isDirectory) {
                        newFile.mkdirs()
                    } else {
                        newFile.parentFile?.mkdirs()
                        FileOutputStream(newFile).use { fos ->
                            zis.copyTo(fos, 64 * 1024)
                        }
                        notifyMediaScanner(newFile.absolutePath)
                    }
                    zis.closeEntry()
                    extractedCount++
                    onProgress(extractedCount)
                    entry = zis.nextEntry
                }
            }
            Result.success(destDir)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createFolder(parentPath: String, folderName: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val newFolder = File(parentPath, folderName)
            if (newFolder.exists()) {
                return@withContext Result.failure(FileAlreadyExistsException(newFolder))
            }
            if (newFolder.mkdirs()) {
                notifyMediaScanner(newFolder.absolutePath)
                Result.success(newFolder)
            } else {
                Result.failure(Exception("Could not create directory $folderName"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createFile(parentPath: String, fileName: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val newFile = File(parentPath, fileName)
            if (newFile.exists()) {
                return@withContext Result.failure(FileAlreadyExistsException(newFile))
            }
            if (newFile.createNewFile()) {
                notifyMediaScanner(newFile.absolutePath)
                Result.success(newFile)
            } else {
                Result.failure(Exception("Could not create file $fileName"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
