package com.lambdar.signalbackuphelper

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.net.toUri

class BackupWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val prefs = appContext.getSharedPreferences("signal_backup_helper", Context.MODE_PRIVATE)
            val sourceStr = prefs.getString("sourceUri", null)
            val destStr = prefs.getString("destUri", null)

            if (sourceStr == null || destStr == null) {
                return Result.failure()
            }

            val src = sourceStr.toUri()
            val dst = destStr.toUri()

            copyLatestBackupInWorker(appContext, src, dst)

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private suspend fun copyLatestBackupInWorker(
        context: Context,
        src: Uri,
        dst: Uri
    ) = withContext(Dispatchers.IO) {
        val docTree = DocumentFile.fromTreeUri(context, src)
            ?: throw IllegalStateException("Origen no es una carpeta válida")

        if (!docTree.isDirectory) {
            throw IllegalStateException("Origen no es una carpeta válida")
        }

        val allFiles = docTree.listFiles()

        val backupFiles = allFiles.filter { file ->
            file.isFile && (file.name?.contains(".backup") == true)
        }

        if (backupFiles.isEmpty()) {
            throw IllegalStateException("No se encontraron backups de Signal en el origen")
        }

        val latest = backupFiles.maxByOrNull { it.lastModified() }
            ?: throw IllegalStateException("No se pudo determinar el último backup")

        val destTree = DocumentFile.fromTreeUri(context, dst)
            ?: throw IllegalStateException("Destino no es una carpeta válida")

        if (!destTree.isDirectory) {
            throw IllegalStateException("Destino no es una carpeta válida")
        }

        destTree.listFiles().forEach { it.delete() }

        val input = context.contentResolver.openInputStream(latest.uri)
            ?: throw IllegalStateException("Error abriendo el backup de origen")

        val newFile = destTree.createFile(
            "application/octet-stream",
            latest.name ?: "signal_latest.backup"
        ) ?: throw IllegalStateException("Error creando archivo en destino")

        val output = context.contentResolver.openOutputStream(newFile.uri)
            ?: throw IllegalStateException("Error abriendo archivo de destino")

        input.use { inp ->
            output.use { out ->
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val read = inp.read(buffer)
                    if (read == -1) break
                    out.write(buffer, 0, read)
                }
            }
        }
    }
}
