package com.btmicpro.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.text.font.FontFamily
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.btmicpro.R
import com.btmicpro.core.AudioDiagnostics
import com.btmicpro.core.RiderAudioPreset
import com.btmicpro.core.RouterState
import com.btmicpro.core.WhatsAppRouteStatus
import com.btmicpro.ui.theme.AccentRed
import com.btmicpro.ui.theme.PrimaryNeon
import com.btmicpro.ui.theme.WarningAmber
import kotlinx.coroutines.delay

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val showSettingsScreen by viewModel.showSettingsScreen.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (showSettingsScreen) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { viewModel.closeSettings() }
            )
        } else {
            CleanHomeScreen(
                viewModel = viewModel,
                onOpenSettings = { viewModel.openSettings() }
            )
        }
    }
}

/**
 * Tela Principal Ultra-Clean e Minimalista para Motociclista.
 * Foco absoluto em:
 * 1. Nome do app e status do intercomunicador
 * 2. Grande botão ergonômico Liga/Desliga da rota
 * 3. Card do Microfone Anti-Queda com slider de volume de retorno (0% = Mudo silencioso recomendado)
 * 4. Botão discreto para Configurações & Flight Recorder
 */
@Composable
fun CleanHomeScreen(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit
) {
    val routerState by viewModel.routerState.collectAsState()
    val isRouterEnabled by viewModel.isRouterEnabled.collectAsState()
    val mediaVolume by viewModel.mediaVolume.collectAsState()
    val callVolume by viewModel.callVolume.collectAsState()
    val isVolumeSyncEnabled by viewModel.isVolumeSyncEnabled.collectAsState()
    val scrollState = rememberScrollState()

    val isRouteReady = routerState is RouterState.RouteReady || routerState is RouterState.RoutingVerified

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "BT Mic Pro",
                        style = MaterialTheme.typography.headlineMedium,
                        color = PrimaryNeon,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = PrimaryNeon,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = when {
                        isRouteReady -> "🟢 Intercom Pronto para WhatsApp"
                        isRouterEnabled -> "🟡 Aguardando Intercom..."
                        else -> "⚪ Sistema Desconectado"
                    },
                    fontSize = 12.sp,
                    color = if (isRouteReady) PrimaryNeon else Color.Gray,
                    fontWeight = FontWeight.Medium
                )
            }

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF222222))
                    .border(1.dp, Color(0xFF444444), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Configurações",
                    tint = PrimaryNeon,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Hero: Botão Central Principal (RouterControlCard)
        RouterControlCard(
            isRouterEnabled = isRouterEnabled,
            routerState = routerState,
            onToggleRouter = { viewModel.toggleRouter(it) }
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Card Informativo: Áudio Bidirecional Automático (Estilo Ligação)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = if (isRouteReady) PrimaryNeon.copy(alpha = 0.4f) else Color(0xFF282828)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (isRouteReady) PrimaryNeon.copy(alpha = 0.15f) else Color(0xFF222222)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isRouteReady) Icons.Default.CheckCircle else Icons.Default.Bluetooth,
                        contentDescription = null,
                        tint = if (isRouteReady) PrimaryNeon else Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isRouteReady) "ÁUDIO BIDIRECIONAL ATIVO" else "SISTEMA EM ESPERA",
                        color = if (isRouteReady) PrimaryNeon else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = if (isRouteReady) {
                            "Tratamento Máximo Extremo: Redutor anti-vento e ganho vocal no máximo. Fones e microfone 100% livres no WhatsApp."
                        } else {
                            "Ao ligar, os volumes vão ao máximo com tratamento extremo de vento e ruído. O microfone fica livre para o WhatsApp."
                        },
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Central de Volume Duplo (Mídia e Chamada)
        DualVolumeControlCard(
            mediaVolume = mediaVolume,
            maxMediaVolume = viewModel.maxMediaVolume,
            callVolume = callVolume,
            maxCallVolume = viewModel.maxCallVolume,
            isSyncEnabled = isVolumeSyncEnabled,
            onMediaVolumeChange = { viewModel.setMediaVolume(it) },
            onCallVolumeChange = { viewModel.setCallVolume(it) },
            onStepMedia = { viewModel.stepMediaVolume(it) },
            onStepCall = { viewModel.stepCallVolume(it) },
            onToggleSync = { viewModel.setVolumeSyncEnabled(it) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Botão de Configurações & Logs
        Button(
            onClick = onOpenSettings,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF202020)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = PrimaryNeon,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Configurações & Flight Recorder",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "BT Mic Pro v1.5.0 • Modo Piloto Ultra-Clean",
            fontSize = 10.sp,
            color = Color.DarkGray
        )
    }
}

/**
 * Tela Secundária de Configurações, Diagnósticos e Flight Recorder.
 */
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val routerState by viewModel.routerState.collectAsState()
    val autoStartOnBoot by viewModel.autoStartOnBoot.collectAsState()
    val isRawAudioMode by viewModel.isRawAudioMode.collectAsState()
    val isBarModeEnabled by viewModel.isBarModeEnabled.collectAsState()
    val barBoostLevel by viewModel.barBoostLevel.collectAsState()
    val isFloatingButtonEnabled by viewModel.isFloatingButtonEnabled.collectAsState()
    val selectedPreset by viewModel.selectedPreset.collectAsState()
    val whatsappStatus by viewModel.whatsappStatus.collectAsState()
    val logsList by viewModel.logsList.collectAsState()
    val diagnostics by viewModel.diagnostics.collectAsState()
    val showDiagnostics by viewModel.showDiagnosticsDialog.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF222222))
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = PrimaryNeon)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Configurações & Diagnósticos",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        // 1. FLIGHT RECORDER (Logs em Tempo Real)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E2E2E))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📋 FLIGHT RECORDER (LOGS)", color = PrimaryNeon, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("${logsList.size} eventos", color = Color.Gray, fontSize = 10.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0C0C0C))
                        .border(1.dp, Color(0xFF262626), RoundedCornerShape(8.dp))
                        .padding(6.dp)
                ) {
                    val terminalScroll = rememberScrollState()
                    LaunchedEffect(logsList.size) {
                        if (logsList.isNotEmpty()) {
                            terminalScroll.scrollTo(terminalScroll.maxValue)
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(terminalScroll)
                    ) {
                        if (logsList.isEmpty()) {
                            Text("Aguardando eventos...", color = Color.Gray, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        } else {
                            logsList.takeLast(120).forEach { line ->
                                val color = when {
                                    line.contains("[ERROR]") -> AccentRed
                                    line.contains("[WARN ]") -> WarningAmber
                                    line.contains("[AUDIO]") -> PrimaryNeon
                                    else -> Color(0xFFCCCCCC)
                                }
                                Text(line, color = color, fontSize = 9.sp, fontFamily = FontFamily.Monospace, lineHeight = 12.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.copyAllLogs(context)
                            Toast.makeText(context, "Todos os logs foram copiados!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF242424)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp), tint = PrimaryNeon)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copiar", fontSize = 10.sp, color = Color.White)
                    }
                    Button(
                        onClick = { viewModel.shareLogs(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF242424)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(12.dp), tint = PrimaryNeon)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("WhatsApp", fontSize = 10.sp, color = Color.White)
                    }
                    Button(
                        onClick = {
                            viewModel.clearLogs()
                            Toast.makeText(context, "Logs limpos!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF331A1A)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(0.9f)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(12.dp), tint = AccentRed)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Limpar", fontSize = 10.sp, color = Color.White)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. DIAGNÓSTICO DO HARDWARE & ROTA V5
        Button(
            onClick = { viewModel.openDiagnostics() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E281E)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Assessment, contentDescription = null, tint = PrimaryNeon)
            Spacer(modifier = Modifier.width(8.dp))
            Text("ABRIR DIAGNÓSTICO DE HARDWARE V5 🛠️", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. STATUS DA ROTA
        StatusTelemetryCard(routerState = routerState, whatsappStatus = whatsappStatus)

        Spacer(modifier = Modifier.height(16.dp))

        // 4. PRESETS DO MOTOCICLISTA
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.DarkGray)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "PERFIL DE TRATAMENTO DE ÁUDIO 🏍️",
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
                    RiderAudioPreset.values().take(3).forEach { preset ->
                        FilterChip(
                            selected = selectedPreset == preset,
                            onClick = { viewModel.setRiderPreset(preset) },
                            label = { Text(preset.displayName, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryNeon,
                                selectedLabelColor = Color.Black,
                                containerColor = Color(0xFF2B2B2B),
                                labelColor = Color.White
                            )
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RiderAudioPreset.values().drop(3).forEach { preset ->
                        FilterChip(
                            selected = selectedPreset == preset,
                            onClick = { viewModel.setRiderPreset(preset) },
                            label = { Text(preset.displayName, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryNeon,
                                selectedLabelColor = Color.Black,
                                containerColor = Color(0xFF2B2B2B),
                                labelColor = Color.White
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4.5 TESTE DE RETORNO DO MICROFONE (SIDETONE OPCIONAL)
        val returnVolume by viewModel.returnVolume.collectAsState()
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E2E2E))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Volume de Retorno (Sidetone de Teste)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("Padrão: 0% (Mudo recomendado para pilotar sem eco)", color = Color.Gray, fontSize = 10.sp)
                    }
                    Text(
                        text = if (returnVolume <= 0.01f) "0% (Mudo)" else "${(returnVolume * 100).toInt()}%",
                        fontSize = 12.sp,
                        color = if (returnVolume <= 0.01f) PrimaryNeon else WarningAmber,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Slider(
                    value = returnVolume,
                    onValueChange = { viewModel.setReturnVolume(it) },
                    valueRange = 0.0f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = PrimaryNeon,
                        activeTrackColor = PrimaryNeon,
                        inactiveTrackColor = Color.DarkGray
                    )
                )

                Text(
                    text = if (returnVolume <= 0.01f) {
                        "🔇 Em 0%: Os alto-falantes do capacete ficam 100% livres para áudios do WhatsApp e chamadas."
                    } else {
                        "🔊 Retorno audível ativo: Use apenas temporariamente para testar o microfone."
                    },
                    fontSize = 10.sp,
                    color = Color.LightGray
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 5. AJUSTES DO SISTEMA
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E2E2E))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("⚙️ AJUSTES DO SISTEMA", color = PrimaryNeon, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Iniciar com o Celular", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("Liga automaticamente ao reiniciar", color = Color.Gray, fontSize = 10.sp)
                    }
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

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Botão Flutuante Sobreposto", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("Controle rápido sobre o WhatsApp", color = Color.Gray, fontSize = 10.sp)
                    }
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

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("RAW Audio Mode", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("Bypass de filtros DSP", color = Color.Gray, fontSize = 10.sp)
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

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Modo Bar 🔊", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("Aumenta o volume dos áudios recebidos", color = Color.Gray, fontSize = 10.sp)
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
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Ganho Extra: +${(barBoostLevel * 8 / 100)} dB", color = Color.LightGray, fontSize = 10.sp)
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
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Modal de Diagnóstico
    if (showDiagnostics && diagnostics != null) {
        AudioDiagnosticsDialogV5(
            data = diagnostics!!,
            logs = logsList,
            onDismiss = { viewModel.closeDiagnostics() },
            onRefresh = { viewModel.refreshDiagnostics() },
            onCopyTxt = {
                val txt = viewModel.exportDiagnosticsText()
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("BT Mic Pro Diagnostics", txt))
                Toast.makeText(context, "Diagnóstico V5 copiado como TXT!", Toast.LENGTH_SHORT).show()
            },
            onCopyJson = {
                val json = viewModel.exportDiagnosticsJson()
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("BT Mic Pro Diagnostics JSON", json))
                Toast.makeText(context, "Diagnóstico V5 copiado como JSON!", Toast.LENGTH_SHORT).show()
            },
            onCopyAllLogs = {
                viewModel.copyAllLogs(context)
                Toast.makeText(context, "Todos os logs foram copiados!", Toast.LENGTH_SHORT).show()
            },
            onShareLogs = {
                viewModel.shareLogs(context)
            },
            onClearLogs = {
                viewModel.clearLogs()
                Toast.makeText(context, "Logs limpos com sucesso!", Toast.LENGTH_SHORT).show()
            },
            onMarkValidated = {
                viewModel.markWhatsAppUserValidated()
                Toast.makeText(context, "Status atualizado: Validado pelo Usuário!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

/**
 * Card de Status de Telemetria da Rota de Comunicação V5 (Itens 47, 48, 49, 58).
 */
@Composable
fun StatusTelemetryCard(
    routerState: RouterState,
    whatsappStatus: WhatsAppRouteStatus
) {
    val isBtConnected = routerState !is RouterState.Disconnected
    val isRouteReady = routerState is RouterState.RouteReady || routerState is RouterState.RoutingVerified

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isRouteReady) PrimaryNeon else Color.DarkGray)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "STATUS DA ROTA DE COMUNICAÇÃO V5 📡",
                color = PrimaryNeon,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Bluetooth:", fontSize = 11.sp, color = Color.Gray)
                Text(if (isBtConnected) "CONECTADO" else "DESCONECTADO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isBtConnected) PrimaryNeon else Color.Gray)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Intercom:", fontSize = 11.sp, color = Color.Gray)
                Text(if (isBtConnected) "DETECTADO" else "NÃO DETECTADO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isBtConnected) PrimaryNeon else Color.Gray)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Comunicação:", fontSize = 11.sp, color = Color.Gray)
                Text(if (isRouteReady) "ATIVA" else "INATIVA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isRouteReady) PrimaryNeon else Color.Gray)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Entrada (Mic):", fontSize = 11.sp, color = Color.Gray)
                Text(if (isRouteReady) "BLUETOOTH" else "NÃO VERIFICÁVEL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isRouteReady) PrimaryNeon else WarningAmber)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Saída (Fone):", fontSize = 11.sp, color = Color.Gray)
                Text(if (isRouteReady) "BLUETOOTH" else "NÃO VERIFICÁVEL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isRouteReady) PrimaryNeon else WarningAmber)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Rota Central:", fontSize = 11.sp, color = Color.Gray)
                Text(
                    when (routerState) {
                        is RouterState.RouteReady, is RouterState.RoutingVerified -> "ROTA PRONTA"
                        is RouterState.AudioConnected -> "ÁUDIO HFP PRONTO"
                        is RouterState.AudioConnecting -> "NEGOCIANDO HFP"
                        is RouterState.RouteDegraded -> "DEGRADADA"
                        is RouterState.Recovering -> "RECUPERANDO"
                        is RouterState.RouteLost, is RouterState.RoutingLost -> "PERDIDA"
                        is RouterState.Disconnected -> "DESCONECTADA"
                        else -> "PREPARANDO"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isRouteReady) PrimaryNeon else WarningAmber
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("WhatsApp:", fontSize = 11.sp, color = Color.Gray)
                Text(whatsappStatus.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (whatsappStatus == WhatsAppRouteStatus.USER_VALIDATED) PrimaryNeon else Color.LightGray)
            }

            if (routerState is RouterState.RouteReady && routerState.routePreparationTimeMs > 0L) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Preparo de Rota:", fontSize = 11.sp, color = Color.Gray)
                    Text("${routerState.routePreparationTimeMs} ms", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryNeon)
                }
            }
        }
    }
}

/**
 * Modal de Diagnóstico Completo V5 (Itens 47, 48, 49, 58, 61, 62 do Prompt Master).
 */
@Composable
fun AudioDiagnosticsDialogV5(
    data: AudioDiagnostics,
    logs: List<String>,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onCopyTxt: () -> Unit,
    onCopyJson: () -> Unit,
    onCopyAllLogs: () -> Unit,
    onShareLogs: () -> Unit,
    onClearLogs: () -> Unit,
    onMarkValidated: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Diagnóstico de Áudio & Rota V5", fontWeight = FontWeight.Bold, color = PrimaryNeon, fontSize = 16.sp)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text("📱 DISPOSITIVO & HARDWARE", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                Text("Modelo: ${data.model} (${data.manufacturer})", fontSize = 11.sp, color = Color.LightGray)
                Text("Android: ${data.androidVersion} (SDK ${data.sdk})", fontSize = 11.sp, color = Color.LightGray)
                Text("Perfil: ${data.hardwareProfileName}", fontSize = 11.sp, color = PrimaryNeon)

                Spacer(modifier = Modifier.height(8.dp))
                Text("🎧 BLUETOOTH & INTERCOM", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                Text("Dispositivo: ${data.bluetoothDevice}", fontSize = 11.sp, color = Color.LightGray)
                Text("Perfil BT: ${data.bluetoothProfile}", fontSize = 11.sp, color = Color.LightGray)
                Text("HFP Audio: ${data.hfpAudioState}", fontSize = 11.sp, color = PrimaryNeon)
                Text("SCO Codec: ${data.scoCodec}", fontSize = 11.sp, color = Color.LightGray)

                Spacer(modifier = Modifier.height(8.dp))
                Text("🔄 ROTA DE COMUNICAÇÃO ANDROID", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                Text("CommDevice: ${data.communicationDevice}", fontSize = 11.sp, color = Color.LightGray)
                Text("Modo de Áudio: ${data.audioMode}", fontSize = 11.sp, color = Color.LightGray)
                Text("Estado Central: ${data.routeState}", fontSize = 11.sp, color = PrimaryNeon)
                Text("Entrada BT: ${if (data.inputAvailable) "DISPONÍVEL" else "NÃO DISPONÍVEL"}", fontSize = 11.sp, color = if (data.inputAvailable) PrimaryNeon else WarningAmber)
                Text("Saída BT: ${if (data.outputAvailable) "DISPONÍVEL" else "NÃO DISPONÍVEL"}", fontSize = 11.sp, color = if (data.outputAvailable) PrimaryNeon else WarningAmber)
                Text("Bidirecional: ${if (data.isBidirectionalReady) "SIM (CONFIRMADO)" else "NÃO"}", fontSize = 11.sp, color = if (data.isBidirectionalReady) PrimaryNeon else WarningAmber)

                Spacer(modifier = Modifier.height(8.dp))
                Text("⏱️ TEMPOS E MÉTRICAS REAIS", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                Text("Preparo de Rota: ${data.routePreparationTimeMs} ms", fontSize = 11.sp, color = Color.LightGray)
                Text("Buffer Estimado: ${data.audioBufferEstimateMs} ms", fontSize = 11.sp, color = Color.LightGray)
                Text("Latência Fim-a-Fim: ${data.endToEndLatency}", fontSize = 11.sp, color = Color.LightGray)

                Spacer(modifier = Modifier.height(8.dp))
                Text("📊 ESTABILIDADE & CONTADORES DE QUEDAS", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                Text("Quedas de Rota: ${data.routeLossCount}", fontSize = 11.sp, color = if (data.routeLossCount == 0) PrimaryNeon else WarningAmber)
                Text("Recuperações: ${data.recoveryCount}", fontSize = 11.sp, color = Color.LightGray)
                Text("Desconexões SCO: ${data.scoDisconnectCount}", fontSize = 11.sp, color = Color.LightGray)
                Text("Trocas CommDevice: ${data.communicationDeviceChangeCount}", fontSize = 11.sp, color = Color.LightGray)

                Spacer(modifier = Modifier.height(8.dp))
                Text("💬 WHATSAPP STATUS", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                Text("Status: ${data.whatsappStatus.label}", fontSize = 11.sp, color = PrimaryNeon)

                Spacer(modifier = Modifier.height(10.dp))
                Text("📋 FLIGHT RECORDER (LOGS EM TEMPO REAL)", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                Text("Total registrado: ${logs.size} eventos", fontSize = 10.sp, color = PrimaryNeon)

                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF101010))
                        .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp))
                        .padding(6.dp)
                ) {
                    val terminalScroll = rememberScrollState()
                    LaunchedEffect(logs.size) {
                        if (logs.isNotEmpty()) {
                            terminalScroll.scrollTo(terminalScroll.maxValue)
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(terminalScroll)
                    ) {
                        if (logs.isEmpty()) {
                            Text("Aguardando eventos...", color = Color.Gray, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        } else {
                            logs.takeLast(120).forEach { line ->
                                val color = when {
                                    line.contains("[ERROR]") -> AccentRed
                                    line.contains("[WARN ]") -> WarningAmber
                                    line.contains("[AUDIO]") -> PrimaryNeon
                                    else -> Color(0xFFCCCCCC)
                                }
                                Text(line, color = color, fontSize = 9.sp, fontFamily = FontFamily.Monospace, lineHeight = 12.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = onCopyAllLogs,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B2B2B)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp), tint = PrimaryNeon)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Copiar Logs", fontSize = 10.sp, color = Color.White)
                    }
                    Button(
                        onClick = onShareLogs,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B2B2B)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(12.dp), tint = PrimaryNeon)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("WhatsApp", fontSize = 10.sp, color = Color.White)
                    }
                    Button(
                        onClick = onClearLogs,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF332020)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(0.8f)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(12.dp), tint = AccentRed)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Limpar", fontSize = 10.sp, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onCopyTxt,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B2B2B)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = PrimaryNeon)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copiar TXT", fontSize = 10.sp, color = Color.White)
                    }
                    Button(
                        onClick = onCopyJson,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B2B2B)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = PrimaryNeon)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copiar JSON", fontSize = 10.sp, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onMarkValidated,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A1E)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp), tint = PrimaryNeon)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Marcar: Testado no WhatsApp", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
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
    val isVerified = routerState is RouterState.RouteReady || routerState is RouterState.RoutingVerified || routerState is RouterState.ScoActive || routerState is RouterState.RoutingActive
    val deviceName = when (routerState) {
        is RouterState.RouteReady -> routerState.device.name
        is RouterState.RoutingVerified -> routerState.device.name
        is RouterState.OutputAvailable -> routerState.device.name
        is RouterState.InputAvailable -> routerState.device.name
        is RouterState.ScoActive -> routerState.device.name
        is RouterState.CommunicationDeviceSelected -> routerState.device.name
        is RouterState.CommunicationDeviceAvailable -> routerState.device.name
        is RouterState.AudioDeviceAvailable -> routerState.device.name
        is RouterState.BluetoothConnected -> routerState.device.name
        is RouterState.RoutingActive -> routerState.device.name
        else -> ""
    }

    val statusColor by animateColorAsState(
        targetValue = when {
            !isRouterEnabled -> Color.Gray
            isVerified -> PrimaryNeon
            routerState is RouterState.WaitingDevice || routerState is RouterState.Recovering || routerState is RouterState.BluetoothConnected || routerState is RouterState.CommunicationDeviceAvailable -> WarningAmber
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

        Spacer(modifier = Modifier.height(18.dp))

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
                    routerState is RouterState.RouteReady -> "ROTA PRONTA: $deviceName"
                    routerState is RouterState.RoutingVerified -> "ROTA PRONTA: $deviceName"
                    routerState is RouterState.OutputAvailable -> "SAÍDA PRONTA: $deviceName"
                    routerState is RouterState.InputAvailable -> "ENTRADA PRONTA: $deviceName"
                    routerState is RouterState.CommunicationDeviceSelected -> "COMUNICAÇÃO: $deviceName"
                    routerState is RouterState.CommunicationDeviceAvailable -> "CANAL DETECTADO: $deviceName"
                    routerState is RouterState.BluetoothConnected -> "BT CONECTADO: $deviceName"
                    routerState is RouterState.Recovering -> "RECUPERANDO ROTA..."
                    routerState is RouterState.RouteLost -> "ROTA PERDIDA"
                    routerState is RouterState.RoutingLost -> "ROTA PERDIDA"
                    routerState is RouterState.WaitingDevice -> "AGUARDANDO CAPACETE..."
                    routerState is RouterState.Error -> "ERRO: ${(routerState as RouterState.Error).message}"
                    else -> "DESCONECTADO"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = statusColor
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (isRouterEnabled) "Rota Bidirecional WhatsApp ↔ Intercom Ativa" else "Ative para manter a comunicação pelo capacete",
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

@Composable
fun DualVolumeControlCard(
    mediaVolume: Int,
    maxMediaVolume: Int,
    callVolume: Int,
    maxCallVolume: Int,
    isSyncEnabled: Boolean,
    onMediaVolumeChange: (Int) -> Unit,
    onCallVolumeChange: (Int) -> Unit,
    onStepMedia: (Boolean) -> Unit,
    onStepCall: (Boolean) -> Unit,
    onToggleSync: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF282828))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Cabeçalho da Central de Volumes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = PrimaryNeon,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "VOLUME DUPLO",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isSyncEnabled) "Sincronizado" else "Separado",
                        color = if (isSyncEnabled) PrimaryNeon else Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Switch(
                        checked = isSyncEnabled,
                        onCheckedChange = onToggleSync,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PrimaryNeon,
                            checkedTrackColor = PrimaryNeon.copy(alpha = 0.3f),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color(0xFF222222)
                        ),
                        modifier = Modifier.height(24.dp)
                    )
                }
            }

            Text(
                text = if (isSyncEnabled) {
                    "As teclas laterais do celular aumentam/diminuem Mídia e Chamada juntas na moto."
                } else {
                    "Ajuste independente para o som de Mídia e voz de Chamada."
                },
                color = Color.Gray,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
            )

            // 🎵 Canal 1: Volume de Mídia (WhatsApp / GPS / Músicas)
            val mediaPercent = ((mediaVolume.toFloat() / maxMediaVolume.coerceAtLeast(1)) * 100).toInt()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color(0xFF4FC3F7),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Mídia (WhatsApp / GPS)",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }
                Text(
                    text = "$mediaPercent%",
                    color = Color(0xFF4FC3F7),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onStepMedia(false) },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF242424))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeDown,
                        contentDescription = "Diminuir Mídia",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                var localMediaValue by remember(mediaVolume) { mutableFloatStateOf(mediaVolume.toFloat()) }

                Slider(
                    value = localMediaValue,
                    onValueChange = { newValue ->
                        localMediaValue = newValue
                        val newInt = newValue.roundToInt()
                        if (newInt != mediaVolume) {
                            onMediaVolumeChange(newInt)
                        }
                    },
                    valueRange = 0f..maxMediaVolume.toFloat(),
                    steps = (maxMediaVolume - 1).coerceAtLeast(0),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF4FC3F7),
                        activeTrackColor = Color(0xFF4FC3F7),
                        inactiveTrackColor = Color(0xFF333333)
                    )
                )

                IconButton(
                    onClick = { onStepMedia(true) },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF242424))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Aumentar Mídia",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 📞 Canal 2: Volume de Chamada / Voz (Capacete)
            val callPercent = ((callVolume.toFloat() / maxCallVolume.coerceAtLeast(1)) * 100).toInt()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = null,
                        tint = PrimaryNeon,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Chamada / Intercom",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }
                Text(
                    text = "$callPercent%",
                    color = PrimaryNeon,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onStepCall(false) },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF242424))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeDown,
                        contentDescription = "Diminuir Chamada",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                var localCallValue by remember(callVolume) { mutableFloatStateOf(callVolume.toFloat()) }

                Slider(
                    value = localCallValue,
                    onValueChange = { newValue ->
                        localCallValue = newValue
                        val newInt = newValue.roundToInt()
                        if (newInt != callVolume) {
                            onCallVolumeChange(newInt)
                        }
                    },
                    valueRange = 0f..maxCallVolume.toFloat(),
                    steps = (maxCallVolume - 1).coerceAtLeast(0),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = PrimaryNeon,
                        activeTrackColor = PrimaryNeon,
                        inactiveTrackColor = Color(0xFF333333)
                    )
                )

                IconButton(
                    onClick = { onStepCall(true) },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF242424))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Aumentar Chamada",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
