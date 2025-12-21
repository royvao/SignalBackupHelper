package com.lambdar.signalbackuphelper

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.content.edit

class BackupWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val channelId = "backup_channel"
    private val notificationId = 1001

    override suspend fun doWork(): Result {
        android.util.Log.d("BackupWorker", "doWork() arrancado")

        return try {
            createChannelIfNeeded()

            val prefs = applicationContext.getSharedPreferences(
                "signal_backup_helper",
                Context.MODE_PRIVATE
            )
            val sourceStr = prefs.getString("sourceUri", null)
            val destStr = prefs.getString("destUri", null)

            if (sourceStr == null || destStr == null) {
                showFinishedNotification(success = false)
                return Result.failure()
            }

            val src = sourceStr.toUri()
            val dst = destStr.toUri()

            copyLatestBackupWithNotification(src, dst)

            showFinishedNotification(success = true)
            android.util.Log.d("BackupWorker", "doWork() completado OK")
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("BackupWorker", "Error en doWork", e)
            val prefs = applicationContext.getSharedPreferences("signal_backup_helper", Context.MODE_PRIVATE)
            prefs.edit { putBoolean("isProcessing", false) }
            showFinishedNotification(success = false)
            return Result.failure()
        }
    }

    private fun createChannelIfNeeded() {
        val manager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(channelId)
        if (existing == null) {
            val channel = NotificationChannel(
                channelId,
                "Backups de Signal",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progreso de copias de seguridad de Signal"
            }
            manager.createNotificationChannel(channel)
        }
    }

    @SuppressLint("DefaultLocale")
    private fun updateProgressNotification(bytesCopied: Long, totalBytes: Long) {
        // Permiso POST_NOTIFICATIONS en Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val maxGb = totalBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        val copiedGb = bytesCopied.toDouble() / (1024.0 * 1024.0 * 1024.0)

        val percent =
            if (totalBytes > 0L) (bytesCopied * 100 / totalBytes).toInt() else 0

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Copia de seguridad de Signal")
            .setContentText(
                if (maxGb > 0.0)
                    String.format("Copiando backup… %.2f / %.2f GB", copiedGb, maxGb)
                else
                    "Copiando backup…"
            )
            .setSmallIcon(R.drawable.ic_backup)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, percent.coerceIn(0, 100), false)
            .build()

        NotificationManagerCompat.from(applicationContext)
            .notify(notificationId, notification)
    }

    private fun showFinishedNotification(success: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val text = if (success) {
            "Copia de backup completada"
        } else {
            "Error en la copia de backup"
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Signal Backup Helper")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_backup)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext)
            .notify(notificationId + 1, notification)
    }

    private suspend fun copyLatestBackupWithNotification(
        src: Uri,
        dst: Uri
    ) = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences(
            "signal_backup_helper",
            Context.MODE_PRIVATE
        )

        // marcar inicio
        prefs.edit {
            putBoolean("isProcessing", true)
                .putLong("totalBytes", 0L)
                .putLong("copiedBytes", 0L)
        }

        val docTree = DocumentFile.fromTreeUri(applicationContext, src)
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

        val destTree = DocumentFile.fromTreeUri(applicationContext, dst)
            ?: throw IllegalStateException("Destino no es una carpeta válida")

        if (!destTree.isDirectory) {
            throw IllegalStateException("Destino no es una carpeta válida")
        }

        // borrar destino
        destTree.listFiles().forEach { it.delete() }

        val totalSize = latest.length()

        prefs.edit {
            putBoolean("isProcessing", true)
                .putLong("totalBytes", totalSize)
                .putLong("copiedBytes", 0L)
        }

        val input = applicationContext.contentResolver.openInputStream(latest.uri)
            ?: throw IllegalStateException("Error abriendo el backup de origen")

        val newFile = destTree.createFile(
            "application/octet-stream",
            latest.name ?: "signal_latest.backup"
        ) ?: throw IllegalStateException("Error creando archivo en destino")

        val output = applicationContext.contentResolver.openOutputStream(newFile.uri)
            ?: throw IllegalStateException("Error abriendo archivo de destino")

        var copied = 0L

        input.use { inp ->
            output.use { out ->
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val read = inp.read(buffer)
                    if (read == -1) break
                    out.write(buffer, 0, read)
                    copied += read

                    // progreso en prefs
                    prefs.edit {
                        putLong("copiedBytes", copied)
                    }

                    updateProgressNotification(copied, totalSize)
                }
            }
        }

        // marcar fin
        prefs.edit {
            putBoolean("isProcessing", false)
        }
    }

}
