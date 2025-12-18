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
                    if (sourceUri == null || destUri == null) {
                        Toast.makeText(
                            this,
                            "Selecciona origen y destino primero",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            this,
                            "Aquí luego haremos el procesamiento",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
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
