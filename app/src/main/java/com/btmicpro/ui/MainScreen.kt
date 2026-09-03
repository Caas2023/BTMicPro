package com.btmicpro.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.btmicpro.R
import com.btmicpro.core.AudioDiagnosticsData
import com.btmicpro.core.RiderAudioPreset
import com.btmicpro.core.RouterState
import com.btmicpro.ui.theme.AccentRed
import com.btmicpro.ui.theme.PrimaryNeon
import com.btmicpro.ui.theme.WarningAmber
import kotlinx.coroutines.delay

@Composable
fun MainScreen(viewModel: MainViewModel) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        MotoWhatsAppModeScreen(viewModel = viewModel)
    }
}

@Composable
fun MotoWhatsAppModeScreen(viewModel: MainViewModel) {
    val routerState by viewModel.routerState.collectAsState()
    val isRouterEnabled by viewModel.isRouterEnabled.collectAsState()
    val isRawAudioMode by viewModel.isRawAudioMode.collectAsState()
    val denoiseIntensity by viewModel.denoiseIntensity.collectAsState()
    val isLiveMonitorEnabled by viewModel.isLiveMonitorEnabled.collectAsState()
    val autoStartOnBoot by viewModel.autoStartOnBoot.collectAsState()
    val showPromoPopup by viewModel.showPromoPopup.collectAsState()
    val isBarModeEnabled by viewModel.isBarModeEnabled.collectAsState()
    val isFloatingButtonEnabled by viewModel.isFloatingButtonEnabled.collectAsState()
    val barBoostLevel by viewModel.barBoostLevel.collectAsState()
    val selectedPreset by viewModel.selectedPreset.collectAsState()
    val showDiagnostics by viewModel.showDiagnosticsDialog.collectAsState()
    val diagnosticsData by viewModel.diagnosticsData.collectAsState()

    val uriHandler = LocalUriHandler.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
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
                text = "v1.4.0 V4",
                color = Color.Gray,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Info Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ENGINE DSP", color = PrimaryNeon, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("[V4 Modular]", color = Color.Gray, fontSize = 13.sp)
            }
            Box(modifier = Modifier.width(1.dp).height(36.dp).background(Color.DarkGray))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("DISPOSITIVO", color = PrimaryNeon, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("[KingKong X Pro]", color = Color.Gray, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Botão Central de Roteamento (MOTO WHATSAPP MODE)
        RouterControlCard(
            isRouterEnabled = isRouterEnabled,
            routerState = routerState,
            onToggleRouter = { viewModel.toggleRouter(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // === NOVO NA V4: SELETOR DE PRESETS DO MOTOCICLISTA ===
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.DarkGray)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "PERFIL DE ÁUDIO DO MOTOCICLISTA 🏍️",
                    color = PrimaryNeon,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Text(
                    selectedPreset.description,
                    color = Color.Gray,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RiderAudioPreset.values().forEach { preset ->
                        val isSelected = preset == selectedPreset
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setRiderPreset(preset) },
                            label = { 
                                Text(
                                    preset.displayName, 
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ) 
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryNeon.copy(alpha = 0.25f),
                                selectedLabelColor = PrimaryNeon,
                                containerColor = Color(0xFF252525),
                                labelColor = Color.LightGray
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) PrimaryNeon else Color.DarkGray
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // === MONITOR AO VIVO / OUVIDO NO CAPACETE (Estilo Noise Uncanceller) ===
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp, 
                if (isLiveMonitorEnabled) PrimaryNeon else Color.DarkGray
            )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            "OUVIR CAPACETE AO VIVO 🎧",
                            color = if (isLiveMonitorEnabled) PrimaryNeon else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            if (isLiveMonitorEnabled) "Monitorando áudio com VoiceProcessingEngine V4"
                            else "Escute seu microfone em tempo real para regular antes de rodar",
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                    }
                    Switch(
                        checked = isLiveMonitorEnabled,
                        onCheckedChange = { viewModel.toggleLiveMonitor(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = PrimaryNeon,
                            uncheckedThumbColor = Color.LightGray,
                            uncheckedTrackColor = Color.DarkGray
                        )
                    )
                }

                // Slider de Sensibilidade da Redução de Vento DSP
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Intensidade Anti-Vento (DSP)", color = Color.LightGray, fontSize = 11.sp)
                    Text("${(denoiseIntensity * 100).toInt()}%", color = PrimaryNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = denoiseIntensity,
                    onValueChange = { viewModel.setDenoiseIntensity(it) },
                    valueRange = 0.40f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = PrimaryNeon,
                        activeTrackColor = PrimaryNeon,
                        inactiveTrackColor = Color.DarkGray
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Toggles: RAW Audio e Auto Iniciar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("RAW AUDIO", color = PrimaryNeon, fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text("Bypass DSP", color = Color.Gray, fontSize = 9.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(modifier = Modifier.height(4.dp))
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
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("AUTO INICIAR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text("Liga sozinho", color = Color.Gray, fontSize = 9.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(modifier = Modifier.height(4.dp))
                Switch(
                    checked = autoStartOnBoot,
                    onCheckedChange = { viewModel.setAutoStartOnBoot(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = PrimaryNeon,
                        uncheckedThumbColor = Color.LightGray,
                        uncheckedTrackColor = Color.DarkGray
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // === MODO BAR - Aumentador de volume de mídia ===
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (isBarModeEnabled) PrimaryNeon else Color.DarkGray)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("MODO BAR 🔊", color = if (isBarModeEnabled) PrimaryNeon else Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            "Aumenta volume da mídia até +8dB para bar/ruído",
                            color = Color.Gray, fontSize = 10.sp
                        )
                    }
                    Switch(
                        checked = isBarModeEnabled,
                        onCheckedChange = { viewModel.toggleBarMode(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = PrimaryNeon,
                            uncheckedThumbColor = Color.LightGray,
                            uncheckedTrackColor = Color.DarkGray
                        )
                    )
                }
                if (isBarModeEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Boost", color = Color.Gray, fontSize = 11.sp)
                        Text("${barBoostLevel}%", color = PrimaryNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = barBoostLevel.toFloat(),
                        onValueChange = { viewModel.setBarBoostLevel(it.toInt()) },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = PrimaryNeon,
                            activeTrackColor = PrimaryNeon,
                            inactiveTrackColor = Color.DarkGray
                        )
                    )
                    Text("0% (normal)  •  100% (+8dB max)", color = Color.Gray, fontSize = 9.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Botão Flutuante
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Botão Flutuante Sobreposto", color = if (isFloatingButtonEnabled) PrimaryNeon else Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Switch(
                checked = isFloatingButtonEnabled,
                onCheckedChange = { viewModel.toggleFloatingButton(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = PrimaryNeon,
                    uncheckedThumbColor = Color.LightGray,
                    uncheckedTrackColor = Color.DarkGray
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // === BOTÃO DIAGNÓSTICO DO DESENVOLVEDOR (AUDIO DIAGNOSTICS) ===
        Button(
            onClick = { viewModel.openDiagnostics() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B2B2B)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Assessment, contentDescription = null, tint = PrimaryNeon)
            Spacer(modifier = Modifier.width(8.dp))
            Text("DIAGNÓSTICO DE ÁUDIO & DISPOSITIVO 🛠️", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Dynamic Carousel Promotional Footer (Shopee)
        if (showPromoPopup) {
            val promoList = remember {
                listOf(
                    PromoBannerItem("CAPA DE CHUVA", "EM PROMOÇÃO", "https://s.shopee.com.br/2gAij6Mj1r", bannerRes = R.drawable.banner_capa_chuva, neonColor = Color(0xFFFF2222)),
                    PromoBannerItem("KIT RELAÇÃO", "EM PROMOÇÃO", "https://s.shopee.com.br/7fZOgLkL36", bannerRes = R.drawable.banner_relacao, neonColor = Color(0xFFFF6D00)),
                    PromoBannerItem("CAPACETES", "EM PROMOÇÃO", "https://s.shopee.com.br/3g3FumMouO", bannerRes = R.drawable.banner_capacete, neonColor = Color(0xFFFF2222)),
                    PromoBannerItem("INTERCOMUNICADOR", "EM PROMOÇÃO", "https://s.shopee.com.br/4qFDJF1V58", bannerRes = R.drawable.banner_intercom, neonColor = Color(0xFFD500F9)),
                    PromoBannerItem("PNEUS DE MOTO", "EM PROMOÇÃO", "https://s.shopee.com.br/6fgrTWMGS9", bannerRes = R.drawable.promo_pneus, neonColor = Color(0xFF00E676))
                )
            }

            var currentPromoIndex by remember { mutableStateOf(0) }

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
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    border = androidx.compose.foundation.BorderStroke(2.dp, currentPromo.neonColor.copy(alpha = blinkAlpha))
                ) {
                    Crossfade(targetState = currentPromo, label = "promoBannerCrossfade") { promo ->
                        Image(
                            painter = painterResource(id = promo.bannerRes),
                            contentDescription = promo.title,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                IconButton(
                    onClick = { viewModel.dismissPromoPopup() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fechar",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }

    // Modal de Diagnóstico do Desenvolvedor
    if (showDiagnostics && diagnosticsData != null) {
        AudioDiagnosticsDialog(
            data = diagnosticsData!!,
            onDismiss = { viewModel.closeDiagnostics() },
            onRefresh = { viewModel.refreshDiagnostics() }
        )
    }
}

@Composable
fun AudioDiagnosticsDialog(
    data: AudioDiagnosticsData,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Developer Audio Diagnostics", fontWeight = FontWeight.Bold, color = PrimaryNeon, fontSize = 16.sp)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text("📱 APARELHO & HARDWARE", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                Text("Modelo: ${data.deviceModel} (${data.manufacturer})", fontSize = 11.sp, color = Color.LightGray)
                Text("Android: ${data.androidVersion} (API ${data.sdkVersion})", fontSize = 11.sp, color = Color.LightGray)
                Text("Perfil: ${data.hardwareProfile}", fontSize = 11.sp, color = PrimaryNeon)

                Spacer(modifier = Modifier.height(8.dp))
                Text("🎧 ESTADO DO ROTEAMENTO", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                Text("Status: ${data.routingStateDescription}", fontSize = 11.sp, color = PrimaryNeon)
                Text("Fone BT: ${data.bluetoothDeviceName}", fontSize = 11.sp, color = Color.LightGray)
                Text("Canal SCO: ${if (data.isScoActive) "ATIVO" else "INATIVO"}", fontSize = 11.sp, color = if (data.isScoActive) PrimaryNeon else AccentRed)
                Text("Silent Keeper: ${if (data.isKeeperActive) "RODANDO" else "PARADO"}", fontSize = 11.sp, color = Color.LightGray)
                Text("Latência Estimada: ~${data.estimatedLatencyMs}ms", fontSize = 11.sp, color = Color.LightGray)

                Spacer(modifier = Modifier.height(8.dp))
                Text("🎙️ ENTRADAS DE ÁUDIO DETECTADAS", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                data.inputDevices.forEach { dev ->
                    Text("• $dev", fontSize = 10.sp, color = Color.LightGray)
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("🔊 COMUNICAÇÃO & OUTPUT", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                Text("AudioMode: ${data.audioMode}", fontSize = 11.sp, color = Color.LightGray)
                Text("CommDevice: ${data.communicationDevice}", fontSize = 11.sp, color = Color.LightGray)
            }
        },
        confirmButton = {
            Button(onClick = onRefresh, colors = ButtonDefaults.buttonColors(containerColor = PrimaryNeon)) {
                Text("Atualizar", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar", color = Color.White)
            }
        },
        containerColor = Color(0xFF1E1E1E)
    )
}

@Composable
fun RouterControlCard(
    isRouterEnabled: Boolean,
    routerState: RouterState,
    onToggleRouter: (Boolean) -> Unit
) {
    val isVerified = routerState is RouterState.RoutingVerified || routerState is RouterState.ScoActive || routerState is RouterState.RoutingActive
    val deviceName = when (routerState) {
        is RouterState.RoutingVerified -> routerState.device.name
        is RouterState.ScoActive -> routerState.device.name
        is RouterState.CommunicationDeviceSelected -> routerState.device.name
        is RouterState.AudioDeviceAvailable -> routerState.device.name
        is RouterState.BluetoothConnected -> routerState.device.name
        is RouterState.RoutingActive -> routerState.device.name
        else -> ""
    }

    val statusColor by animateColorAsState(
        targetValue = when {
            !isRouterEnabled -> Color.Gray
            isVerified -> PrimaryNeon
            routerState is RouterState.WaitingDevice || routerState is RouterState.Recovering || routerState is RouterState.BluetoothConnected -> WarningAmber
            else -> AccentRed
        },
        label = "statusColor"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(144.dp)
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
                    modifier = Modifier.size(38.dp).padding(bottom = 4.dp)
                )
                Text(
                    text = if (isVerified && deviceName.isNotEmpty()) deviceName else "MOTO",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = statusColor,
                    fontSize = if (isVerified && deviceName.isNotEmpty()) 15.sp else 21.sp
                )
                Text(
                    text = if (isVerified && deviceName.isNotEmpty()) "" else "WHATSAPP",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                    fontSize = if (isVerified && deviceName.isNotEmpty()) 11.sp else 14.sp
                )
                Text(
                    text = "MODE",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                    fontSize = if (isVerified && deviceName.isNotEmpty()) 10.sp else 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = if (isVerified) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when {
                    !isRouterEnabled -> "DESATIVADO - Toque para ativar"
                    routerState is RouterState.RoutingVerified -> "VERIFICADO: $deviceName"
                    routerState is RouterState.ScoActive -> "SCO ATIVO: $deviceName"
                    routerState is RouterState.CommunicationDeviceSelected -> "COMUNICAÇÃO: $deviceName"
                    routerState is RouterState.AudioDeviceAvailable -> "FONE DETECTADO: $deviceName"
                    routerState is RouterState.BluetoothConnected -> "BT CONECTADO: $deviceName"
                    routerState is RouterState.Recovering -> "RECUPERANDO CONEXÃO..."
                    routerState is RouterState.RoutingLost -> "CONEXÃO PERDIDA"
                    routerState is RouterState.WaitingDevice -> "AGUARDANDO CAPACETE..."
                    routerState is RouterState.Error -> "ERRO: ${(routerState as RouterState.Error).message}"
                    else -> "DESCONECTADO"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = statusColor
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isRouterEnabled) "Engine V4 Ativa • Zero Delay no WhatsApp • Filtro Anti-Vento" else "Ative para manter o microfone do capacete conectado",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            modifier = Modifier.padding(horizontal = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

data class PromoBannerItem(
    val title: String,
    val highlight: String,
    val link: String,
    val bannerRes: Int,
    val neonColor: Color
)
