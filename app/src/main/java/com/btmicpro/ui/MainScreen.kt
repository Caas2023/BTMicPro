package com.btmicpro.ui

import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import com.btmicpro.R
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsMotorsports
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.text.font.FontStyle
import kotlinx.coroutines.delay
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.btmicpro.core.RecordingItem
import com.btmicpro.core.RecordingState
import com.btmicpro.core.RouterState
import com.btmicpro.ui.theme.AccentRed
import com.btmicpro.ui.theme.PrimaryDark
import com.btmicpro.ui.theme.PrimaryNeon
import com.btmicpro.ui.theme.WarningAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MainScreen(viewModel: MainViewModel) {
    var currentScreen by remember { mutableStateOf("MAIN") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (currentScreen == "MAIN") {
            MotoWhatsAppModeScreen(
                viewModel = viewModel,
                onNavigateToRecorder = { currentScreen = "RECORDER" }
            )
        } else {
            AdvancedRecorderScreen(
                viewModel = viewModel,
                onNavigateBack = { currentScreen = "MAIN" }
            )
        }
    }
}

@Composable
fun MotoWhatsAppModeScreen(viewModel: MainViewModel, onNavigateToRecorder: () -> Unit) {
    val routerState by viewModel.routerState.collectAsState()
    val isRouterEnabled by viewModel.isRouterEnabled.collectAsState()
    val isRawAudioMode by viewModel.isRawAudioMode.collectAsState()
    val showPromoPopup by viewModel.showPromoPopup.collectAsState()
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Title
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = "BT Mic Pro",
                style = MaterialTheme.typography.headlineMedium,
                color = PrimaryNeon,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.Mic, 
                contentDescription = null, 
                tint = PrimaryNeon,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "v1.0.2",
                color = Color.Gray,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Info Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("RIDER 1", color = PrimaryNeon, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("[Intercom 50%]", color = Color.Gray, fontSize = 14.sp)
            }
            Box(modifier = Modifier.width(1.dp).height(40.dp).background(Color.DarkGray))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("PHONE", color = PrimaryNeon, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("[Galaxy S22 Ultra]", color = Color.Gray, fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Massive Central Button
        RouterControlCard(
            isRouterEnabled = isRouterEnabled,
            routerState = routerState,
            onToggleRouter = { viewModel.toggleRouter(it) }
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Raw Audio Mode Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                Text("RAW AUDIO MODE", color = PrimaryNeon, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    "Bypass phone DSP to prevent voice distortion in high winds",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
            Switch(
                checked = isRawAudioMode,
                onCheckedChange = { viewModel.setRawAudioMode(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = PrimaryNeon,
                    uncheckedThumbColor = Color.LightGray,
                    uncheckedTrackColor = Color.DarkGray
                )
            )
        }

        // Dynamic Carousel Promotional Footer
        if (showPromoPopup) {
            val promoList = remember {
                listOf(
                    PromoBannerItem("CAPACETES", "EM PROMOÇÃO", "https://s.shopee.com.br/3g3FumMouO", Icons.Default.SportsMotorsports, Color(0xFFFF3333)),
                    PromoBannerItem("CAPA DE CHUVA", "EM PROMOÇÃO", "https://s.shopee.com.br/2gAij6Mj1r", Icons.Default.Umbrella, Color(0xFF00E5FF)),
                    PromoBannerItem("KIT RELAÇÃO", "EM PROMOÇÃO", "https://s.shopee.com.br/7fZOgLkL36", Icons.Default.Build, Color(0xFFFF9100)),
                    PromoBannerItem("INTERCOMUNICADOR", "EM PROMOÇÃO", "https://s.shopee.com.br/4qFDJF1V58", Icons.Default.HeadsetMic, Color(0xFFD500F9)),
                    PromoBannerItem("PNEUS DE MOTO", "EM PROMOÇÃO", "https://s.shopee.com.br/6fgrTWMGS9", Icons.Default.Speed, Color(0xFF00E676))
                )
            }

            var currentPromoIndex by remember { mutableIntStateOf(0) }

            // Auto-rotate every 4 seconds
            LaunchedEffect(Unit) {
                while (true) {
                    delay(4000)
                    currentPromoIndex = (currentPromoIndex + 1) % promoList.size
                }
            }

            val currentPromo = promoList[currentPromoIndex]

            val infiniteTransition = rememberInfiniteTransition(label = "blink")
            val blinkAlpha by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "blinkAlpha"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .clickable { uriHandler.openUri(currentPromo.link) }
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)),
                    border = androidx.compose.foundation.BorderStroke(2.dp, currentPromo.neonColor.copy(alpha = blinkAlpha))
                ) {
                    Crossfade(targetState = currentPromo, label = "promoCrossfade") { promo ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left: Product Icon with Glow Background
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .background(promo.neonColor.copy(alpha = 0.15f), CircleShape)
                                    .border(1.dp, promo.neonColor.copy(alpha = 0.4f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = promo.icon,
                                    contentDescription = promo.title,
                                    tint = promo.neonColor,
                                    modifier = Modifier.size(34.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Middle: Texts
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = promo.title,
                                    color = promo.neonColor,
                                    fontWeight = FontWeight.Black,
                                    fontStyle = FontStyle.Italic,
                                    fontSize = 15.sp,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = promo.highlight,
                                    color = Color(0xFFFFD700), // Yellow Gold
                                    fontWeight = FontWeight.ExtraBold,
                                    fontStyle = FontStyle.Italic,
                                    fontSize = 17.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                // CTA Mini Button Row
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(promo.neonColor, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "CLIQUE AQUI PARA VER",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 10.sp
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.TouchApp,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Close Button in top right corner
                IconButton(
                    onClick = { viewModel.dismissPromoPopup() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(20.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fechar",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        // Bottom Sliders & Settings Mockup
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp).clickable { onNavigateToRecorder() },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.DarkGray)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("MIC GAIN", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Slider(
                        value = 0.65f, onValueChange = {},
                        colors = SliderDefaults.colors(thumbColor = PrimaryNeon, activeTrackColor = PrimaryNeon, inactiveTrackColor = Color.DarkGray)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("SPEAKER VOL", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Slider(
                        value = 0.8f, onValueChange = {},
                        colors = SliderDefaults.colors(thumbColor = PrimaryNeon, activeTrackColor = PrimaryNeon, inactiveTrackColor = Color.DarkGray)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings", tint = PrimaryNeon)
                    Text("RECORDER", color = PrimaryNeon, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AdvancedRecorderScreen(viewModel: MainViewModel, onNavigateBack: () -> Unit) {
    val recordingState by viewModel.recordingState.collectAsState()
    val denoiseIntensity by viewModel.denoiseIntensity.collectAsState()
    val autoStartOnBoot by viewModel.autoStartOnBoot.collectAsState()
    val recordingsList by viewModel.recordingsList.collectAsState()
    val currentlyPlayingPath by viewModel.currentlyPlayingPath.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = PrimaryNeon)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Advanced Recorder",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        item {
            RecorderCard(
                recordingState = recordingState,
                denoiseIntensity = denoiseIntensity,
                onStartRecording = { viewModel.startRecording() },
                onStopRecording = { viewModel.stopRecording() },
                onDenoiseChange = { viewModel.setDenoiseIntensity(it) }
            )
        }

        item {
            SettingsCard(
                autoStartOnBoot = autoStartOnBoot,
                onToggleAutoStart = { viewModel.setAutoStartOnBoot(it) }
            )
        }

        item {
            RecordingsHeaderSection(totalItems = recordingsList.size)
        }

        if (recordingsList.isEmpty()) {
            item {
                EmptyRecordingsPlaceholder()
            }
        } else {
            items(recordingsList, key = { it.id }) { item ->
                RecordingListItem(
                    item = item,
                    isPlaying = currentlyPlayingPath == item.filePath,
                    onPlayToggle = { viewModel.togglePlayback(item) },
                    onShareWhatsApp = { viewModel.shareAudioToWhatsApp(item.filePath) },
                    onDelete = { viewModel.deleteRecording(item) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun RouterControlCard(
    isRouterEnabled: Boolean,
    routerState: RouterState,
    onToggleRouter: (Boolean) -> Unit
) {
    val statusColor by animateColorAsState(
        targetValue = when {
            !isRouterEnabled -> Color.Gray
            routerState is RouterState.RoutingActive -> PrimaryNeon
            routerState is RouterState.WaitingDevice -> WarningAmber
            else -> AccentRed
        },
        label = "statusColor"
    )

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(if (isRouterEnabled) PrimaryNeon.copy(alpha = 0.15f) else Color.DarkGray.copy(alpha = 0.3f))
                .border(
                    width = if (isRouterEnabled) 6.dp else 2.dp,
                    color = statusColor,
                    shape = CircleShape
                )
                .clickable { onToggleRouter(!isRouterEnabled) },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(40.dp).padding(bottom = 2.dp)
                )
                Text(
                    text = "MOTO",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = statusColor,
                    fontSize = 24.sp
                )
                Text(
                    text = "WHATSAPP",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                    fontSize = 18.sp
                )
                Text(
                    text = "MODE",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = if (routerState is RouterState.RoutingActive) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when {
                    !isRouterEnabled -> "DESATIVADO"
                    routerState is RouterState.RoutingActive -> "CONECTADO: ${(routerState as RouterState.RoutingActive).device.name}"
                    routerState is RouterState.WaitingDevice -> "AGUARDANDO CAPACETE..."
                    routerState is RouterState.Error -> "ERRO: ${(routerState as RouterState.Error).message}"
                    else -> "INATIVO"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = statusColor
            )
        }
    }
}

@Composable
fun RecorderCard(
    recordingState: RecordingState,
    denoiseIntensity: Float,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onDenoiseChange: (Float) -> Unit
) {
    val isRecording = recordingState is RecordingState.Recording

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Visualizer & Recorder Controls (Mockup Style)
            if (isRecording) {
                val state = recordingState as RecordingState.Recording
                val seconds = (state.durationMs / 1000) % 60
                val minutes = (state.durationMs / 1000) / 60
                val timeFormatted = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "AUDIO INPUT",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                    Text(
                        text = timeFormatted,
                        style = MaterialTheme.typography.titleMedium,
                        color = PrimaryNeon,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Faux sound wave
                    LinearProgressIndicator(
                        progress = { state.amplitude },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        color = PrimaryNeon,
                        trackColor = PrimaryDark.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
            
            // Record Button
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(if (isRecording) AccentRed else Color.DarkGray)
                        .border(2.dp, if (isRecording) Color.White else Color.Gray, CircleShape)
                        .clickable { if (isRecording) onStopRecording() else onStartRecording() }
                )
            }
            Text(
                text = if (isRecording) "STOP" else "RECORD",
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                color = if (isRecording) AccentRed else Color.Gray,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Recorder Controls",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Noise Gate Intensity", color = Color.LightGray)
                Text("${(denoiseIntensity * 100).toInt()}%", color = Color.White)
            }
            
            Slider(
                value = denoiseIntensity,
                onValueChange = onDenoiseChange,
                valueRange = 0.1f..1.0f,
                colors = SliderDefaults.colors(
                    thumbColor = PrimaryNeon,
                    activeTrackColor = PrimaryNeon,
                    inactiveTrackColor = Color.DarkGray
                )
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("0", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Text("100%", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Text(
                text = "Suppresses background engine noise when below threshold.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun SettingsCard(
    autoStartOnBoot: Boolean,
    onToggleAutoStart: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Iniciar com o Celular",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Reativa o roteamento de microfone após reiniciar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
            }
            Switch(
                checked = autoStartOnBoot,
                onCheckedChange = onToggleAutoStart,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = PrimaryDark
                )
            )
        }
    }
}

@Composable
fun RecordingsHeaderSection(totalItems: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "SAVED RECORDINGS",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.Gray
        )
    }
}

@Composable
fun EmptyRecordingsPlaceholder() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Nenhuma gravação recente",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = "Grave um áudio tratado acima para ouvir e enviar no WhatsApp",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
fun RecordingListItem(
    item: RecordingItem,
    isPlaying: Boolean,
    onPlayToggle: () -> Unit,
    onShareWhatsApp: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val dateString = dateFormat.format(Date(item.timestamp))
    val seconds = (item.durationMs / 1000) % 60
    val minutes = (item.durationMs / 1000) / 60
    val durationFormatted = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.DarkGray)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Informações do Arquivo
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = "$durationFormatted  4.2 MB  $dateString",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            // Botão Play / Pause
            IconButton(onClick = onPlayToggle) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = PrimaryNeon
                )
            }

            // Ação: Compartilhar no WhatsApp
            IconButton(onClick = onShareWhatsApp) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Enviar no WhatsApp",
                    tint = Color.LightGray
                )
            }

            // Ação: Excluir
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Excluir",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }
}

data class PromoBannerItem(
    val title: String,
    val highlight: String,
    val link: String,
    val icon: ImageVector,
    val neonColor: Color
)
