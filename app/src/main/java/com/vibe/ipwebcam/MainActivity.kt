package com.vibe.ipwebcam

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private var service: StreamService? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as StreamService.LocalBinder).getService()
            bound = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            bound = false
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startStream()
        } else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme
            ) {
                IPWebcamScreen(
                    serviceGetter = { service },
                    onStartStream = { checkPermissionAndStart() },
                    onStopStream = { stopStream() }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, StreamService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        if (bound) {
            unbindService(connection)
            bound = false
            service = null
        }
    }

    private fun checkPermissionAndStart() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED -> startStream()
            else -> cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startStream() {
        val prefs = Prefs(this)
        startForegroundService(
            StreamService.startIntent(this, prefs.port, prefs.width, prefs.height, prefs.fps)
        )
    }

    private fun stopStream() {
        startService(StreamService.stopIntent(this))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IPWebcamScreen(
    serviceGetter: () -> StreamService?,
    onStartStream: () -> Unit,
    onStopStream: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val ipAddress = remember { MjpegServer.getDeviceIpAddress() }

    var isStreaming by remember { mutableStateOf(false) }
    var clientCount by remember { mutableIntStateOf(0) }
    var selectedWidth by remember { mutableIntStateOf(prefs.width) }
    var selectedHeight by remember { mutableIntStateOf(prefs.height) }
    var selectedFps by remember { mutableIntStateOf(prefs.fps) }
    var portText by remember { mutableStateOf(prefs.port.toString()) }

    // Poll streaming state
    LaunchedEffect(Unit) {
        while (true) {
            val svc = serviceGetter()
            isStreaming = svc?.isStreaming() == true
            clientCount = svc?.getClientCount() ?: 0
            kotlinx.coroutines.delay(500)
        }
    }

    val resolutions = listOf(
        "640x480 (VGA)" to Pair(640, 480),
        "1280x720 (HD)" to Pair(1280, 720),
        "1920x1080 (FHD)" to Pair(1920, 1080)
    )
    val fpsOptions = listOf(10, 15, 25, 30)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("IP Webcam") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (isStreaming) Color(0xFF00E676) else Color(0xFF546E7A))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isStreaming) "Streaming" else "Stopped",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "IP: $ipAddress",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Port: ${portText}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    )
                    if (isStreaming) {
                        Text(
                            text = "Clients: $clientCount",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Resolution: ${selectedWidth}x${selectedHeight} @ ${selectedFps}fps",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Start/Stop button
            Button(
                onClick = {
                    if (isStreaming) {
                        onStopStream()
                    } else {
                        val port = portText.toIntOrNull() ?: 8080
                        prefs.port = port
                        prefs.width = selectedWidth
                        prefs.height = selectedHeight
                        prefs.fps = selectedFps
                        onStartStream()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isStreaming) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = if (isStreaming) "Stop Streaming" else "Start Streaming",
                    fontSize = 16.sp
                )
            }

            // Settings
            Text("Settings", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            // Port
            OutlinedTextField(
                value = portText,
                onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 5) portText = it },
                label = { Text("HTTP Port") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isStreaming
            )

            // Resolution dropdown
            var resExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = resExpanded,
                onExpandedChange = { if (!isStreaming) resExpanded = it }
            ) {
                OutlinedTextField(
                    value = resolutions.firstOrNull { it.second == Pair(selectedWidth, selectedHeight) }?.first ?: "${selectedWidth}x${selectedHeight}",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Resolution") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = resExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    enabled = !isStreaming
                )
                ExposedDropdownMenu(
                    expanded = resExpanded,
                    onDismissRequest = { resExpanded = false }
                ) {
                    resolutions.forEach { (label, size) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                selectedWidth = size.first
                                selectedHeight = size.second
                                resExpanded = false
                            }
                        )
                    }
                }
            }

            // FPS dropdown
            var fpsExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = fpsExpanded,
                onExpandedChange = { if (!isStreaming) fpsExpanded = it }
            ) {
                OutlinedTextField(
                    value = "${selectedFps} fps",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Frame Rate") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fpsExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    enabled = !isStreaming
                )
                ExposedDropdownMenu(
                    expanded = fpsExpanded,
                    onDismissRequest = { fpsExpanded = false }
                ) {
                    fpsOptions.forEach { fps ->
                        DropdownMenuItem(
                            text = { Text("$fps fps") },
                            onClick = {
                                selectedFps = fps
                                fpsExpanded = false
                            }
                        )
                    }
                }
            }

            // Linux command hint
            if (isStreaming) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 1.dp,
                    shape = MaterialTheme.shapes.small
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Linux command:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "ffmpeg -i http://$ipAddress:$portText/video -vf format=yuv420p -f v4l2 /dev/video1",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
