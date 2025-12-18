package com.lambdar.signalbackuphelper

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.content.edit
import androidx.core.net.toUri

class MainActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences

    private var sourceUri: Uri? = null
    private var destUri: Uri? = null

    private enum class PickType { SOURCE, DEST }
    private var currentPickType: PickType? = null

    // estado para diálogo y progreso
    private var isProcessing = mutableStateOf(false)
    private var bytesTotal = mutableLongStateOf(0L)
    private var bytesCopied = mutableLongStateOf(0L)

    private val folderPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null && currentPickType != null) {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                try {
                    contentResolver.takePersistableUriPermission(uri, flags)
                } catch (_: SecurityException) {
                    // por si ya teníamos el permiso
                }

                when (currentPickType) {
                    PickType.SOURCE -> {
                        sourceUri = uri
                        prefs.edit { putString("sourceUri", uri.toString()) }
                    }
                    PickType.DEST -> {
                        destUri = uri
                        prefs.edit { putString("destUri", uri.toString()) }
                    }
                    else -> { /* no-op */ }
                }
            } else {
                Toast.makeText(this, "Selección cancelada", Toast.LENGTH_SHORT).show()
            }
            currentPickType = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("signal_backup_helper", MODE_PRIVATE)

        // Cargar URIs guardadas
        val sourceStr = prefs.getString("sourceUri", null)
        val destStr = prefs.getString("destUri", null)
        sourceUri = sourceStr?.toUri()
        destUri = destStr?.toUri()

        setContent {
            val processing by isProcessing
            val total by bytesTotal
            val copied by bytesCopied

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SignalBackupApp(
                        sourceUri = sourceUri,
                        destUri = destUri,
                        isProcessing = processing,
                        totalBytes = total,
                        copiedBytes = copied,
                        onPickSource = {
                            currentPickType = PickType.SOURCE
                            folderPickerLauncher.launch(null)
                        },
                        onPickDest = {
                            currentPickType = PickType.DEST
                            folderPickerLauncher.launch(null)
                        },
                        onProcess = {
                            processLatestBackup()
                        }
                    )
                }
            }
        }
    }

    private fun processLatestBackup() {
        val src = sourceUri
        val dst = destUri
        if (src == null || dst == null) {
            Toast.makeText(this, "Selecciona origen y destino primero", Toast.LENGTH_SHORT).show()
            return
        }

        isProcessing.value = true
        bytesTotal.longValue = 0L
        bytesCopied.longValue = 0L

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val docTree = DocumentFile.fromTreeUri(this@MainActivity, src)
                        ?: throw IllegalStateException("Origen no es una carpeta válida")

                    if (!docTree.isDirectory) {
                        throw IllegalStateException("Origen no es una carpeta válida")
                    }

                    val allFiles = docTree.listFiles()

                    // Filtramos solo archivos que tengan ".backup" en el nombre
                    val backupFiles = allFiles.filter { file ->
                        file.isFile && (file.name?.contains(".backup") == true)
                    }

                    if (backupFiles.isEmpty()) {
                        throw IllegalStateException("No se encontraron backups de Signal en el origen")
                    }

                    val latest = backupFiles.maxByOrNull { it.lastModified() }
                        ?: throw IllegalStateException("No se pudo determinar el último backup")

                    val destTree = DocumentFile.fromTreeUri(this@MainActivity, dst)
                        ?: throw IllegalStateException("Destino no es una carpeta válida")

                    if (!destTree.isDirectory) {
                        throw IllegalStateException("Destino no es una carpeta válida")
                    }

                    // borrar destino
                    destTree.listFiles().forEach { it.delete() }

                    val totalSize = latest.length()
                    bytesTotal.longValue = totalSize
                    bytesCopied.longValue = 0L

                    val input = contentResolver.openInputStream(latest.uri)
                        ?: throw IllegalStateException("Error abriendo el backup de origen")

                    val newFile = destTree.createFile(
                        "application/octet-stream",
                        latest.name ?: "signal_latest.backup"
                    ) ?: throw IllegalStateException("Error creando archivo en destino")

                    val output = contentResolver.openOutputStream(newFile.uri)
                        ?: throw IllegalStateException("Error abriendo archivo de destino")

                    input.use { inp ->
                        output.use { out ->
                            val buffer = ByteArray(8 * 1024)
                            while (true) {
                                val read = inp.read(buffer)
                                if (read == -1) break
                                out.write(buffer, 0, read)

                                // actualizar progreso
                                val current = bytesCopied.longValue + read
                                bytesCopied.longValue = current
                            }
                        }
                    }
                }

                Toast.makeText(
                    this@MainActivity,
                    "Último backup copiado correctamente",
                    Toast.LENGTH_SHORT
                ).show()

            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                isProcessing.value = false
            }
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun SignalBackupApp(
    sourceUri: Uri?,
    destUri: Uri?,
    isProcessing: Boolean,
    totalBytes: Long,
    copiedBytes: Long,
    onPickSource: () -> Unit,
    onPickDest: () -> Unit,
    onProcess: () -> Unit
) {
    val sourceText = sourceUri?.toString() ?: "Origen: no seleccionado"
    val destText = destUri?.toString() ?: "Destino: no seleccionado"

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Button(
                onClick = onPickSource,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Seleccionar carpeta de backups (Signal)")
            }

            Text(
                text = sourceText,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onPickDest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Seleccionar carpeta destino (último backup)")
            }

            Text(
                text = destText,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onProcess,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isProcessing
            ) {
                Text("Procesar ahora")
            }
        }

        if (isProcessing) {
            val progress =
                if (totalBytes > 0L) copiedBytes.toFloat() / totalBytes.toFloat()
                else 0f

            val gbRemaining = if (totalBytes > 0L) {
                val remaining = totalBytes - copiedBytes
                remaining.toDouble() / (1024.0 * 1024.0 * 1024.0)
            } else 0.0

            AlertDialog(
                onDismissRequest = { /* no permitir cerrar mientras procesa */ },
                title = { Text("Procesando") },
                text = {
                    Column {
                        CircularProgressIndicator(
                            progress = progress.coerceIn(0f, 1f),
                            modifier = Modifier
                                .padding(bottom = 16.dp)
                                .size(48.dp)
                        )
                        Text(
                            text = String.format(
                                "Restante: %.2f GB",
                                if (gbRemaining < 0) 0.0 else gbRemaining
                            )
                        )
                    }
                },
                confirmButton = {}
            )
        }
    }
}
