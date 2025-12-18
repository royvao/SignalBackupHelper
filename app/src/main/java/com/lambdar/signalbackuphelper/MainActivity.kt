package com.lambdar.signalbackuphelper

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile


class MainActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences

    private var sourceUri: Uri? = null
    private var destUri: Uri? = null

    private enum class PickType { SOURCE, DEST }
    private var currentPickType: PickType? = null

    private val folderPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null && currentPickType != null) {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, flags)

                when (currentPickType) {
                    PickType.SOURCE -> {
                        sourceUri = uri
                        prefs.edit().putString("sourceUri", uri.toString()).apply()
                    }
                    PickType.DEST -> {
                        destUri = uri
                        prefs.edit().putString("destUri", uri.toString()).apply()
                    }
                    null -> {
                        // No debería pasar porque ya comprobamos antes,
                        // pero lo dejamos vacío para satisfacer al compilador
                    }
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
        sourceUri = sourceStr?.let { Uri.parse(it) }
        destUri = destStr?.let { Uri.parse(it) }

        setContent {
            SignalBackupApp(
                sourceUri = sourceUri,
                destUri = destUri,
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

    private fun processLatestBackup() {
        val src = sourceUri
        val dst = destUri
        if (src == null || dst == null) {
            Toast.makeText(this, "Selecciona origen y destino primero", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // 1) Obtener lista de backups en origen
            val docTree = DocumentFile.fromTreeUri(this, src)
            if (docTree == null || !docTree.isDirectory) {
                Toast.makeText(this, "Origen no es una carpeta válida", Toast.LENGTH_SHORT).show()
                return
            }

            val backupFiles = docTree.listFiles()
                .filter { it.isFile && it.name?.startsWith("signal.") == true && it.name?.endsWith(".backup") == true }

            if (backupFiles.isEmpty()) {
                Toast.makeText(this, "No se encontraron backups de Signal en el origen", Toast.LENGTH_SHORT).show()
                return
            }

            // 2) Seleccionar el más reciente por fecha de modificación
            val latest = backupFiles.maxByOrNull { it.lastModified() }

            if (latest == null) {
                Toast.makeText(this, "No se pudo determinar el último backup", Toast.LENGTH_SHORT).show()
                return
            }

            // 3) Limpiar carpeta destino
            val destTree = DocumentFile.fromTreeUri(this, dst)
            if (destTree == null || !destTree.isDirectory) {
                Toast.makeText(this, "Destino no es una carpeta válida", Toast.LENGTH_SHORT).show()
                return
            }

            destTree.listFiles().forEach { it.delete() }

            // 4) Copiar el último backup al destino
            val input = contentResolver.openInputStream(latest.uri)
            if (input == null) {
                Toast.makeText(this, "Error abriendo el backup de origen", Toast.LENGTH_SHORT).show()
                return
            }

            val newFile = destTree.createFile("application/octet-stream", latest.name ?: "signal_latest.backup")
            if (newFile == null) {
                Toast.makeText(this, "Error creando archivo en destino", Toast.LENGTH_SHORT).show()
                input.close()
                return
            }

            val output = contentResolver.openOutputStream(newFile.uri)
            if (output == null) {
                Toast.makeText(this, "Error abriendo archivo de destino", Toast.LENGTH_SHORT).show()
                input.close()
                return
            }

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

            Toast.makeText(this, "Último backup copiado correctamente", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

}


@Composable
fun SignalBackupApp(
    sourceUri: Uri?,
    destUri: Uri?,
    onPickSource: () -> Unit,
    onPickDest: () -> Unit,
    onProcess: () -> Unit
) {
    val sourceText = sourceUri?.toString() ?: "Origen: no seleccionado"
    val destText = destUri?.toString() ?: "Destino: no seleccionado"

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
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Procesar ahora")
        }
    }
}
