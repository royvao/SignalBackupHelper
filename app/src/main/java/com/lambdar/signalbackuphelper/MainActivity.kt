package com.lambdar.signalbackuphelper

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat


class MainActivity : ComponentActivity() {

    lateinit var prefs: SharedPreferences

    private var sourceUri: Uri? = null
    private var destUri: Uri? = null

    private enum class PickType { SOURCE, DEST }
    private var currentPickType: PickType? = null

    private enum class Screen { HOME, HISTORY, SETTINGS, HELP, ABOUT }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("signal_backup_helper", MODE_PRIVATE)

        val sourceStr = prefs.getString("sourceUri", null)
        val destStr = prefs.getString("destUri", null)
        sourceUri = sourceStr?.toUri()
        destUri = destStr?.toUri()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }


        setContent {
            var currentScreen by rememberSaveable { mutableStateOf(Screen.HOME) }
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val scope = rememberCoroutineScope()

            var sourceUriState by remember { mutableStateOf(sourceUri) }
            var destUriState by remember { mutableStateOf(destUri) }

            // estados de progreso
            var isProcessing by remember { mutableStateOf(false) }
            var totalBytes by remember { mutableLongStateOf(0L) }
            var copiedBytes by remember { mutableLongStateOf(0L) }

            // polling de SharedPreferences para progreso
            LaunchedEffect(Unit) {
                while (true) {
                    isProcessing = prefs.getBoolean("isProcessing", false)
                    totalBytes = prefs.getLong("totalBytes", 0L)
                    copiedBytes = prefs.getLong("copiedBytes", 0L)
                    delay(500)
                }
            }

            val folderPickerLauncher =
                rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
                    if (uri != null && currentPickType != null) {
                        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        try {
                            contentResolver.takePersistableUriPermission(uri, flags)
                        } catch (_: SecurityException) { }

                        when (currentPickType) {
                            PickType.SOURCE -> {
                                sourceUri = uri
                                sourceUriState = uri
                                prefs.edit { putString("sourceUri", uri.toString()) }
                            }
                            PickType.DEST -> {
                                destUri = uri
                                destUriState = uri
                                prefs.edit { putString("destUri", uri.toString()) }
                            }
                            else -> {}
                        }
                    } else {
                        Toast.makeText(this, "Selección cancelada", Toast.LENGTH_SHORT).show()
                    }
                    currentPickType = null
                }

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
                                    Screen.ABOUT
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
                                    sourceUri = sourceUriState,
                                    destUri = destUriState,
                                    isProcessing = isProcessing,
                                    totalBytes = totalBytes,
                                    copiedBytes = copiedBytes,
                                    onPickSource = {
                                        currentPickType = PickType.SOURCE
                                        folderPickerLauncher.launch(null)
                                    },
                                    onPickDest = {
                                        currentPickType = PickType.DEST
                                        folderPickerLauncher.launch(null)
                                    },
                                    onProcess = { enqueueManualBackup() }
                                )
                                Screen.HISTORY -> HistoryScreen()
                                Screen.SETTINGS -> SettingsScreen(
                                    prefs = prefs,
                                    onScheduleChange = { hour, minute, enabled ->
                                        scheduleOrCancelBackup(hour, minute, enabled)
                                    }
                                )
                                Screen.HELP -> HelpScreen()
                                Screen.ABOUT -> AboutScreen()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun enqueueManualBackup() {
        val src = sourceUri
        val dst = destUri
        if (src == null || dst == null) {
            Toast.makeText(this, "Selecciona origen y destino primero", Toast.LENGTH_SHORT).show()
            return
        }

        val wm = WorkManager.getInstance(applicationContext)
        val request = OneTimeWorkRequestBuilder<BackupWorker>().build()
        wm.enqueue(request)

        Toast.makeText(
            this,
            "Backup iniciado (revisa progreso en pantalla y notificación)",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun scheduleOrCancelBackup(hour: Int, minute: Int, enabled: Boolean) {
        val workManager = WorkManager.getInstance(applicationContext)
        val uniqueName = "daily_signal_backup"

        if (!enabled) {
            workManager.cancelUniqueWork(uniqueName)
            return
        }

        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val delayMillis = target.timeInMillis - now.timeInMillis

        val request = PeriodicWorkRequestBuilder<BackupWorker>(
            24, TimeUnit.HOURS
        )
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            uniqueName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
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

    var showInlineProgress by remember { mutableStateOf(true) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Snackbar con mensaje de progreso
    LaunchedEffect(isProcessing) {
        if (isProcessing) {
            snackbarHostState.showSnackbar(
                message = "Procesando copia de seguridad…",
                withDismissAction = false
            )
        } else {
            snackbarHostState.currentSnackbarData?.dismiss()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
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

            // Panel flotante de progreso, cerrable
            if (isProcessing && showInlineProgress) {
                val progress =
                    if (totalBytes > 0L) copiedBytes.toFloat() / totalBytes.toFloat()
                    else 0f

                val gbRemaining = if (totalBytes > 0L) {
                    val remaining = totalBytes - copiedBytes
                    remaining.toDouble() / (1024.0 * 1024.0 * 1024.0)
                } else 0.0

                Surface(
                    tonalElevation = 4.dp,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 80.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = progress.coerceIn(0f, 1f),
                            modifier = Modifier.size(32.dp)
                        )
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Procesando copia de seguridad")
                            Text(
                                text = String.format(
                                    "Restante: %.2f GB",
                                    if (gbRemaining < 0) 0.0 else gbRemaining
                                ),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton(onClick = { showInlineProgress = false }) {
                            Text("Ocultar")
                        }
                    }
                }
            }
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

@SuppressLint("DefaultLocale")
@Composable
fun SettingsScreen(
    prefs: SharedPreferences,
    onScheduleChange: (Int, Int, Boolean) -> Unit
) {
    val savedHour = prefs.getInt("schedule_hour", 3)
    val savedMinute = prefs.getInt("schedule_minute", 0)
    val savedEnabled = prefs.getBoolean("schedule_enabled", false)

    var hour by rememberSaveable { mutableIntStateOf(savedHour) }
    var minute by rememberSaveable { mutableIntStateOf(savedMinute) }
    var enabled by rememberSaveable { mutableStateOf(savedEnabled) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Configuración de backup automático",
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            text = "Hora diaria para ejecutar el backup:",
            style = MaterialTheme.typography.bodyMedium
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { hour = (hour + 23) % 24 }
            ) {
                Text("-")
            }
            Text(
                text = String.format("%02d", hour),
                style = MaterialTheme.typography.titleMedium
            )
            Button(
                onClick = { hour = (hour + 1) % 24 }
            ) {
                Text("+")
            }

            Spacer(modifier = Modifier.width(16.dp))

            Button(
                onClick = { minute = (minute + 55) % 60 }
            ) {
                Text("-")
            }
            Text(
                text = String.format("%02d", minute),
                style = MaterialTheme.typography.titleMedium
            )
            Button(
                onClick = { minute = (minute + 5) % 60 }
            ) {
                Text("+")
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Backup diario activado",
                style = MaterialTheme.typography.bodyMedium
            )
            Switch(
                checked = enabled,
                onCheckedChange = { enabled = it }
            )
        }

        Button(
            onClick = {
                prefs.edit {
                    putInt("schedule_hour", hour)
                    putInt("schedule_minute", minute)
                    putBoolean("schedule_enabled", enabled)
                }
                onScheduleChange(hour, minute, enabled)
            }
        ) {
            Text("Guardar configuración")
        }
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
