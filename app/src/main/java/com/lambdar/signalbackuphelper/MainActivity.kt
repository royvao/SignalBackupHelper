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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.content.edit
import androidx.core.net.toUri

class MainActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences

    private var sourceUri: Uri? = null
    private var destUri: Uri? = null

    private enum class PickType { SOURCE, DEST }
    private var currentPickType: PickType? = null

    private enum class Screen { HOME, HISTORY, SETTINGS, HELP, ABOUT }

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

    @OptIn(ExperimentalMaterial3Api::class)
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

            var currentScreen by rememberSaveable { mutableStateOf(Screen.HOME) }
            val drawerState = androidx.compose.material3.rememberDrawerState(initialValue = DrawerValue.Closed)
            val scope = rememberCoroutineScope()

            MaterialTheme {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            Text(
                                text = "Signal Backup Helper",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(16.dp)
                            )

                            NavigationDrawerItem(
                                label = { Text("Inicio") },
                                selected = currentScreen == Screen.HOME,
                                onClick = {
                                    currentScreen = Screen.HOME
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                            NavigationDrawerItem(
                                label = { Text("Historial") },
                                selected = currentScreen == Screen.HISTORY,
                                onClick = {
                                    currentScreen = Screen.HISTORY
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                            NavigationDrawerItem(
                                label = { Text("Configuración") },
                                selected = currentScreen == Screen.SETTINGS,
                                onClick = {
                                    currentScreen = Screen.SETTINGS
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                            NavigationDrawerItem(
                                label = { Text("Ayuda") },
                                selected = currentScreen == Screen.HELP,
                                onClick = {
                                    currentScreen = Screen.HELP
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                            NavigationDrawerItem(
                                label = { Text("Acerca de") },
                                selected = currentScreen == Screen.ABOUT,
                                onClick = {
                                    currentScreen = Screen.ABOUT
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                        }
                    }
                ) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = {
                                    Text(
                                        when (currentScreen) {
                                            Screen.HOME -> "Signal Backup Helper"
                                            Screen.HISTORY -> "Historial"
                                            Screen.SETTINGS -> "Configuración"
                                            Screen.HELP -> "Ayuda"
                                            Screen.ABOUT -> "Acerca de"
                                        }
                                    )
                                },
                                navigationIcon = {
                                    IconButton(onClick = {
                                        scope.launch { drawerState.open() }
                                    }) {
                                        Icon(
                                            imageVector = Icons.Filled.Menu,
                                            contentDescription = "Menú"
                                        )
                                    }
                                }
                            )
                        }
                    ) { paddingValues ->
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                        ) {
                            when (currentScreen) {
                                Screen.HOME -> SignalBackupScreen(
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
                                    onProcess = { processLatestBackup() }
                                )
                                Screen.HISTORY -> HistoryScreen()
                                Screen.SETTINGS -> SettingsScreen()
                                Screen.HELP -> HelpScreen()
                                Screen.ABOUT -> AboutScreen()
                            }
                        }
                    }
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
fun SignalBackupScreen(
    sourceUri: Uri?,
    destUri: Uri?,
    isProcessing: Boolean,
    totalBytes: Long,
    copiedBytes: Long,
    onPickSource: () -> Unit,
    onPickDest: () -> Unit,
    onProcess: () -> Unit
) {
    val sourceText = sourceUri?.toString() ?: "Origen aún no seleccionado"
    val destText = destUri?.toString() ?: "Destino aún no seleccionado"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Signal Backup Helper",
                style = MaterialTheme.typography.headlineMedium
            )

            Text(
                text = "Selecciona la carpeta donde Signal guarda las copias\n" +
                        "y la carpeta donde quieres mantener solo la última.",
                style = MaterialTheme.typography.bodyMedium
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Carpeta de backups de Signal",
                    style = MaterialTheme.typography.titleMedium
                )
                Button(
                    onClick = onPickSource,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Elegir carpeta de origen")
                }
                Text(
                    text = sourceText,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Carpeta destino (último backup)",
                    style = MaterialTheme.typography.titleMedium
                )
                Button(
                    onClick = onPickDest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Elegir carpeta de destino")
                }
                Text(
                    text = destText,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onProcess,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isProcessing && sourceUri != null && destUri != null
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
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            progress = progress.coerceIn(0f, 1f),
                            modifier = Modifier
                                .padding(bottom = 8.dp)
                                .size(48.dp)
                        )
                        Text(
                            text = String.format(
                                "Restante: %.2f GB",
                                if (gbRemaining < 0) 0.0 else gbRemaining
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                },
                confirmButton = {}
            )
        }
    }
}

@Composable
fun HistoryScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Aquí podrás ver el historial de copias (placeholder).")
    }
}

@Composable
fun SettingsScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Aquí irán opciones de configuración (placeholder).")
    }
}

@Composable
fun HelpScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Ayuda rápida: selecciona la carpeta de backups y destino, luego pulsa Procesar.")
    }
}

@Composable
fun AboutScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Signal Backup Helper\nDesarrollado por ti.\nVersión 1.0.0")
    }
}
