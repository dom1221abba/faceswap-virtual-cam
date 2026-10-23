package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwitchCamera
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberPurple
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeutralCameraGray
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.VirtualActiveGreen
import com.example.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    previewView: PreviewView? = null,
    onRequireCameraPermission: () -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // Activity Result Launcher for picking visual media (images only)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onImagePicked(uri, context)
        }
    }

    var selectedPreviewTab by remember { mutableIntStateOf(0) }
    var showLogsDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (uiState.isVirtualCamActive) VirtualActiveGreen else NeutralCameraGray)
                        )
                        Text(
                            text = "FaceSwap Virtual Cam",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = TextPrimary
                        )
                    }
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (uiState.isVirtualCamActive) VirtualActiveGreen.copy(alpha = 0.15f)
                                else NeutralCameraGray.copy(alpha = 0.15f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (uiState.isVirtualCamActive) VirtualActiveGreen else NeutralCameraGray,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (uiState.isVirtualCamActive) "VIRTUAL ON" else "PASS-THROUGH",
                            color = if (uiState.isVirtualCamActive) VirtualActiveGreen else NeutralCameraGray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Center constrained for responsive tablet layout
            item {
                Column(
                    modifier = Modifier.widthIn(max = 680.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. PRIMARY CONTROLS CARD (Switch + Gallery Picker)
                    MainControlsCard(
                        isVirtualActive = uiState.isVirtualCamActive,
                        onToggleVirtual = { viewModel.toggleVirtualCam(it) },
                        onPickImageClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )

                    // 2. QUICK AVATAR PRESETS BAR
                    PresetAvatarsBar(
                        activePreset = uiState.activePresetIndex,
                        onSelectPreset = { index ->
                            if (index == 2) {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            } else {
                                viewModel.loadPresetAvatar(index, context)
                            }
                        }
                    )

                    // 3. TELEMETRY & STATUS STRIP
                    TelemetryStrip(
                        fps = uiState.currentFps,
                        latencyMs = uiState.latencyMs,
                        virtualCameraId = uiState.virtualCameraId
                    )

                    // 4. LIVE PREVIEW CONTAINER (Tabbed: Virtual NV21 Feed / Source / CameraX)
                    LivePreviewCard(
                        selectedTab = selectedPreviewTab,
                        onTabSelected = { selectedPreviewTab = it },
                        previewBitmap = uiState.previewBitmap,
                        sourceBitmap = uiState.selectedBitmap,
                        previewView = previewView,
                        isVirtualActive = uiState.isVirtualCamActive,
                        onRequestCamera = onRequireCameraPermission
                    )

                    // 5. BROWSER STREAMING & WEBC.AM INTEGRATION CARD
                    BrowserStreamCard(
                        port = uiState.streamPort,
                        connectedClients = uiState.connectedClients,
                        onOpenBrowser = {
                            val streamUrl = "http://localhost:${uiState.streamPort}"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(streamUrl)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Navegador não encontrado", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onCopyUrl = {
                            val url = "http://localhost:${uiState.streamPort}/stream"
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Virtual Cam Stream", url))
                            Toast.makeText(context, "URL do stream copiada!", Toast.LENGTH_SHORT).show()
                        }
                    )

                    // 6. REAL-TIME LOGCAT & ACTIVITY CARD
                    LogcatViewerCard(
                        logs = uiState.logMessages
                    )
                }
            }
        }
    }
}

@Composable
private fun MainControlsCard(
    isVirtualActive: Boolean,
    onToggleVirtual: (Boolean) -> Unit,
    onPickImageClick: () -> Unit
) {
    val switchBorderColor by animateColorAsState(
        targetValue = if (isVirtualActive) VirtualActiveGreen else DarkSurfaceBorder,
        label = "switchBorder"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("main_controls_card"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(switchBorderColor))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Switch Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isVirtualActive) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            contentDescription = null,
                            tint = if (isVirtualActive) VirtualActiveGreen else NeutralCameraGray,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Modo Câmera Virtual",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = if (isVirtualActive)
                            "Substitui a câmera pela imagem selecionada (640x480 NV21)"
                        else
                            "Pass-through ativado: transmitindo sinal da câmera física",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 32.dp, top = 2.dp)
                    )
                }

                Switch(
                    checked = isVirtualActive,
                    onCheckedChange = onToggleVirtual,
                    modifier = Modifier.testTag("virtual_cam_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF00363D),
                        checkedTrackColor = VirtualActiveGreen,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = DarkSurfaceVariant
                    )
                )
            }

            HorizontalDivider(color = DarkSurfaceBorder)

            // Gallery Picker Button
            Button(
                onClick = onPickImageClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("pick_image_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberCyan,
                    contentColor = Color(0xFF00363D)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = "Selecionar Imagem",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Selecionar Imagem da Galeria",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun PresetAvatarsBar(
    activePreset: Int,
    onSelectPreset: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkSurfaceBorder))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "PRESETS RÁPIDOS PARA TESTE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CyberPurple,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PresetItem(
                    label = "Cyber Face",
                    isSelected = activePreset == 0,
                    iconRes = R.drawable.sample_face_cyber,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectPreset(0) }
                )
                PresetItem(
                    label = "Studio Icon",
                    isSelected = activePreset == 1,
                    iconRes = R.drawable.ic_app_icon_foreground,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectPreset(1) }
                )
                PresetItem(
                    label = "Personalizado",
                    isSelected = activePreset == 2,
                    vectorIcon = Icons.Default.Image,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectPreset(2) }
                )
            }
        }
    }
}

@Composable
private fun PresetItem(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    iconRes: Int? = null,
    vectorIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) CyberCyan else DarkSurfaceBorder
    val bg = if (isSelected) CyberCyan.copy(alpha = 0.12f) else DarkSurfaceVariant

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.5.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (iconRes != null) {
                Image(
                    painter = painterResource(id = iconRes),
                    contentDescription = label,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else if (vectorIcon != null) {
                Icon(
                    imageVector = vectorIcon,
                    contentDescription = label,
                    tint = if (isSelected) CyberCyan else TextSecondary,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) CyberCyan else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TelemetryStrip(
    fps: Float,
    latencyMs: Long,
    virtualCameraId: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // FPS Badge
        MetricCard(
            label = "TAXA DE QUADROS",
            value = String.format(java.util.Locale.US, "%.1f FPS", fps),
            icon = Icons.Default.Speed,
            accentColor = if (fps >= 25f) VirtualActiveGreen else CyberCyan,
            modifier = Modifier.weight(1f)
        )

        // Latency Badge
        MetricCard(
            label = "CONVERSÃO YUV",
            value = "${latencyMs}ms",
            icon = Icons.Default.Refresh,
            accentColor = CyberPurple,
            modifier = Modifier.weight(1f)
        )

        // Resolution Badge
        MetricCard(
            label = "SAÍDA VIRTUAL",
            value = "640x480",
            icon = Icons.Default.CameraAlt,
            accentColor = CyberCyan,
            modifier = Modifier.weight(1.1f)
        )
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkSurfaceBorder))
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = value,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        }
    }
}

@Composable
private fun LivePreviewCard(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    previewBitmap: android.graphics.Bitmap?,
    sourceBitmap: android.graphics.Bitmap?,
    previewView: PreviewView?,
    isVirtualActive: Boolean,
    onRequestCamera: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("preview_card"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkSurfaceBorder))
    ) {
        Column {
            // Tabs: 0: Virtual Output (NV21), 1: Source Image, 2: CameraX Physical
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = DarkSurfaceVariant,
                contentColor = CyberCyan,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = CyberCyan
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { onTabSelected(0) },
                    text = {
                        Text(
                            text = "Saída Virtual (NV21)",
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { onTabSelected(1) },
                    text = {
                        Text(
                            text = "Imagem Fonte",
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { onTabSelected(2) },
                    text = {
                        Text(
                            text = "Câmera Física",
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }

            // Preview Viewport (Aspect ratio 4:3 matches 640x480 standard camera resolution)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                when (selectedTab) {
                    0 -> {
                        // Real-time Virtual NV21 Frame Preview
                        if (previewBitmap != null) {
                            Image(
                                bitmap = previewBitmap.asImageBitmap(),
                                contentDescription = "Preview da Câmera Virtual (Buffer NV21)",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("virtual_preview_image"),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Text(
                                text = "Aguardando frames da câmera virtual...",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }

                        // Watermark / Indicator badge
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(alpha = 0.7f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "● LIVE • 640x480 YUV_420_888",
                                color = if (isVirtualActive) VirtualActiveGreen else NeutralCameraGray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    1 -> {
                        // Source Photo Preview
                        if (sourceBitmap != null) {
                            Image(
                                bitmap = sourceBitmap.asImageBitmap(),
                                contentDescription = "Imagem selecionada pelo usuário",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("source_preview_image"),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Text(
                                text = "Nenhuma imagem selecionada",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }

                    2 -> {
                        // CameraX Hardware Preview
                        if (previewView != null) {
                            AndroidView(
                                factory = { previewView },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = "Preview da Câmera Física",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Toque abaixo para conceder permissão de câmera",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(onClick = onRequestCamera) {
                                    Text("Solicitar Acesso à Câmera")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowserStreamCard(
    port: Int,
    connectedClients: List<String>,
    onOpenBrowser: () -> Unit,
    onCopyUrl: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkSurfaceBorder))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.OpenInBrowser,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Integração Web & Chrome",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextPrimary
                    )
                }

                if (connectedClients.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(VirtualActiveGreen.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${connectedClients.size} conectado(s)",
                            color = VirtualActiveGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Text(
                text = "Abra o Google Chrome no dispositivo e acesse a página de streaming ou use o link de teste para validar com sites como webc.am:",
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 16.sp
            )

            // URL Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurfaceVariant)
                    .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "http://localhost:$port/stream",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = CyberCyan,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onCopyUrl,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copiar URL",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onOpenBrowser,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("open_browser_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberPurple,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInBrowser,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Abrir no Navegador",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            // WebUSB / Test Steps explanation
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF10141D))
                    .padding(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Dica de Teste (webc.am / Chrome):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = CyberCyan
                    )
                    Text(
                        text = "1. Toque em 'Abrir no Navegador' ou digite http://localhost:8080 no Chrome.\n" +
                               "2. Para conectar com webc.am no PC via WebUSB ou ADB: execute `adb reverse tcp:8080 tcp:8080` no terminal.\n" +
                               "3. A Virtual Camera injeta o frame NV21 instantaneamente a 30 FPS.",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun LogcatViewerCard(
    logs: List<String>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkSurfaceBorder))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "LOGCAT & DIAGNÓSTICO [VirtualCam]",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = CyberPurple,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "${logs.size} eventos",
                    fontSize = 11.sp,
                    color = TextTertiary
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF090D12))
                    .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(logs) { log ->
                        Text(
                            text = log,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = when {
                                log.contains("CONSUMIDOR", ignoreCase = true) || log.contains("conectado", ignoreCase = true) -> VirtualActiveGreen
                                log.contains("Erro", ignoreCase = true) || log.contains("Falha", ignoreCase = true) -> Color(0xFFFF5252)
                                else -> TextSecondary
                            },
                            lineHeight = 13.sp
                        )
                    }
                }
            }
        }
    }
}
