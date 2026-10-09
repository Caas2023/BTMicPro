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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.btmicpro.BuildConfig
import com.btmicpro.core.AudioDiagnostics
import com.btmicpro.core.AudioModeProfile
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
    val isBarModeEnabled by viewModel.isBarModeEnabled.collectAsState()
    val barBoostLevel by viewModel.barBoostLevel.collectAsState()
    val currentAudioMode by viewModel.audioModeProfile.collectAsState()
    val scrollState = rememberScrollState()
    var showAudioModeDialog by remember { mutableStateOf(false) }

    val isRouteReady = routerState is RouterState.RouteReady || routerState is RouterState.RoutingVerified

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
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
                        isRouteReady -> "🟢 Rota do intercom preparada"
                        routerState is RouterState.RouteDegraded && (routerState as RouterState.RouteDegraded).isMediaPlayback -> "🟡 Escutando mídia: microfone em espera"
                        isRouterEnabled -> "🟡 Aguardando Intercom..."
                        else -> "⚪ Sistema Desconectado"
                    },
                    fontSize = 9.sp,
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

        Spacer(modifier = Modifier.height(8.dp))

        // Hero: Botão Central Principal (RouterControlCard)
        RouterControlCard(
            isRouterEnabled = isRouterEnabled,
            routerState = routerState,
            onToggleRouter = { viewModel.toggleRouter(it) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        HomeBarModeCard(
            enabled = isBarModeEnabled,
            boostLevel = barBoostLevel,
            onToggle = { viewModel.toggleBarMode(it) },
            onBoostChange = { viewModel.setBarBoostLevel(it) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        UnifiedVolumeControlCard(
            mediaVolume = mediaVolume,
            maxMediaVolume = viewModel.maxMediaVolume,
            callVolume = callVolume,
            maxCallVolume = viewModel.maxCallVolume,
            onVolumePercentChange = { viewModel.setUnifiedVolumePercent(it) },
            onStep = { viewModel.stepUnifiedVolume(it) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        AudioModeHomeCard(
            currentAudioMode = currentAudioMode,
            onClick = { showAudioModeDialog = true }
        )

        Spacer(modifier = Modifier.height(10.dp))

        AffiliateBannerCarousel()

        if (showAudioModeDialog) {
            AudioModeSelectorDialog(
                currentAudioMode = currentAudioMode,
                onSelect = { profile ->
                    viewModel.setAudioModeProfile(profile)
                    showAudioModeDialog = false
                },
                onDismiss = { showAudioModeDialog = false }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "BT Mic Pro v${BuildConfig.VERSION_NAME} • Modo Piloto",
            fontSize = 9.sp,
            color = Color.DarkGray
        )
    }
}

/**
 * Tela Secundária de Configurações, Diagnósticos e Flight Recorder.
 */

private enum class SettingsPage(val title: String) {
    MENU("Configurações"),
    LOGS("Logs"),
    DIAGNOSTICS("Diagnóstico"),
    AUDIO_LOCAL("Áudio local"),
    SYSTEM("Sistema")
}

/**
 * Configurações em páginas separadas, sem scroll geral.
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
    val isLiveMonitorEnabled by viewModel.isLiveMonitorEnabled.collectAsState()
    val liveMonitorError by viewModel.liveMonitorError.collectAsState()
    val whatsappStatus by viewModel.whatsappStatus.collectAsState()
    val logsList by viewModel.logsList.collectAsState()
    val diagnostics by viewModel.diagnostics.collectAsState()
    val showDiagnostics by viewModel.showDiagnosticsDialog.collectAsState()
    val returnVolume by viewModel.returnVolume.collectAsState()
    var page by remember { mutableStateOf(SettingsPage.MENU) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { if (page == SettingsPage.MENU) onBack() else page = SettingsPage.MENU },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF222222))
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = PrimaryNeon)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(page.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    text = if (page == SettingsPage.MENU) "Escolha uma área" else "Página única • sem rolagem geral",
                    color = Color.Gray,
                    fontSize = 9.sp
                )
            }
        }

        when (page) {
            SettingsPage.MENU -> SettingsMenuPage(onOpen = { page = it })
            SettingsPage.LOGS -> LogsSettingsPage(context, viewModel, logsList)
            SettingsPage.DIAGNOSTICS -> DiagnosticsSettingsPage(routerState, whatsappStatus) { viewModel.openDiagnostics() }
            SettingsPage.AUDIO_LOCAL -> AudioLocalSettingsPage(
                selectedPreset = selectedPreset,
                returnVolume = returnVolume,
                isLiveMonitorEnabled = isLiveMonitorEnabled,
                liveMonitorError = liveMonitorError,
                onPreset = { viewModel.setRiderPreset(it) },
                onReturnVolume = { viewModel.setReturnVolume(it) },
                onToggleMonitor = { viewModel.toggleLiveMonitor(it) }
            )
            SettingsPage.SYSTEM -> SystemSettingsPage(
                autoStartOnBoot = autoStartOnBoot,
                isFloatingButtonEnabled = isFloatingButtonEnabled,
                isRawAudioMode = isRawAudioMode,
                isBarModeEnabled = isBarModeEnabled,
                barBoostLevel = barBoostLevel,
                onAutoStart = { viewModel.setAutoStartOnBoot(it) },
                onFloating = { viewModel.toggleFloatingButton(it) },
                onRaw = { viewModel.setRawAudioMode(it) },
                onBar = { viewModel.toggleBarMode(it) },
                onBoost = { viewModel.setBarBoostLevel(it) }
            )
        }
    }

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
            onShareLogs = { viewModel.shareLogs(context) },
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

@Composable
private fun SettingsMenuPage(onOpen: (SettingsPage) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SettingsMenuButton("📋 Logs", "Flight Recorder, ZIP e marcação de falha") { onOpen(SettingsPage.LOGS) }
        SettingsMenuButton("🛠️ Diagnóstico", "Hardware, rota e status do WhatsApp") { onOpen(SettingsPage.DIAGNOSTICS) }
        SettingsMenuButton("🏍️ Áudio local", "Retorno local, presets e teste de microfone") { onOpen(SettingsPage.AUDIO_LOCAL) }
        SettingsMenuButton("⚙️ Sistema", "Inicialização, botão flutuante, RAW e Modo Bar") { onOpen(SettingsPage.SYSTEM) }
        Spacer(modifier = Modifier.weight(1f))
        Text("BT Mic Pro v${BuildConfig.VERSION_NAME}", color = Color.DarkGray, fontSize = 9.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun SettingsMenuButton(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().height(86.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E2E2E))
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.Center) {
            Text(title, color = PrimaryNeon, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, color = Color.LightGray, fontSize = 10.sp)
        }
    }
}

@Composable
private fun LogsSettingsPage(context: Context, viewModel: MainViewModel, logsList: List<String>) {
    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E2E2E))
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("FLIGHT RECORDER", color = PrimaryNeon, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    Text("${logsList.size} recentes", color = Color.Gray, fontSize = 9.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(8.dp)).background(Color(0xFF0C0C0C)).padding(6.dp)
                ) {
                    val terminalScroll = rememberScrollState()
                    LaunchedEffect(logsList.size) { if (logsList.isNotEmpty()) terminalScroll.scrollTo(terminalScroll.maxValue) }
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(terminalScroll)) {
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
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = {
                viewModel.copyAllLogs(context)
                Toast.makeText(context, "Logs recentes copiados. Use Exportar ZIP para todos os dias.", Toast.LENGTH_SHORT).show()
            }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF242424)), modifier = Modifier.weight(1f)) { Text("Copiar", fontSize = 9.sp) }
            Button(onClick = { viewModel.shareLogs(context) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF242424)), modifier = Modifier.weight(1.2f)) { Text("ZIP", fontSize = 9.sp) }
            Button(onClick = {
                viewModel.clearLogs()
                Toast.makeText(context, "Logs limpos!", Toast.LENGTH_SHORT).show()
            }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF331A1A)), modifier = Modifier.weight(1f)) { Text("Limpar", fontSize = 9.sp) }
        }
        Text(com.btmicpro.core.AppLogger.storageStatus(), color = Color.Gray, fontSize = 9.sp)
        TextButton(onClick = {
            viewModel.markAudioProblem()
            Toast.makeText(context, "Falha marcada nos logs com horário e modo", Toast.LENGTH_SHORT).show()
        }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Marcar corte/falha agora", color = WarningAmber, fontSize = 11.sp) }
    }
}

@Composable
private fun DiagnosticsSettingsPage(routerState: RouterState, whatsappStatus: WhatsAppRouteStatus, onOpenDiagnostics: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = onOpenDiagnostics,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E281E)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Assessment, contentDescription = null, tint = PrimaryNeon)
            Spacer(modifier = Modifier.width(6.dp))
            Text("ABRIR DIAGNÓSTICO V5", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        StatusTelemetryCard(routerState = routerState, whatsappStatus = whatsappStatus)
    }
}

@Composable
private fun AudioLocalSettingsPage(
    selectedPreset: RiderAudioPreset,
    returnVolume: Float,
    isLiveMonitorEnabled: Boolean,
    liveMonitorError: String?,
    onPreset: (RiderAudioPreset) -> Unit,
    onReturnVolume: (Float) -> Unit,
    onToggleMonitor: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text("PERFIL DE ÁUDIO 🏍️", color = PrimaryNeon, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                Text(selectedPreset.description, color = Color.Gray, fontSize = 9.sp, maxLines = 2)
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    RiderAudioPreset.values().toList().chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { preset ->
                                FilterChip(
                                    selected = selectedPreset == preset,
                                    onClick = { onPreset(preset) },
                                    label = { Text(preset.displayName, fontSize = 8.sp, fontWeight = FontWeight.Bold) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PrimaryNeon, selectedLabelColor = Color.Black, containerColor = Color(0xFF2B2B2B), labelColor = Color.White)
                                )
                            }
                        }
                    }
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Retorno local de teste", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text("0% recomendado para pilotar; afeta só teste local", color = Color.Gray, fontSize = 9.sp)
                    }
                    Text(if (returnVolume <= 0.01f) "0%" else "${(returnVolume * 100).toInt()}%", color = PrimaryNeon, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
                Slider(value = returnVolume, onValueChange = onReturnVolume, valueRange = 0f..1f, colors = SliderDefaults.colors(thumbColor = PrimaryNeon, activeTrackColor = PrimaryNeon, inactiveTrackColor = Color.DarkGray))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Teste local do microfone", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text("Desative antes do WhatsApp", color = Color.Gray, fontSize = 9.sp)
                    }
                    Switch(checked = isLiveMonitorEnabled, onCheckedChange = onToggleMonitor, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PrimaryNeon))
                }
                liveMonitorError?.let { Text(it, color = AccentRed, fontSize = 9.sp) }
            }
        }
    }
}

@Composable
private fun SystemSettingsPage(
    autoStartOnBoot: Boolean,
    isFloatingButtonEnabled: Boolean,
    isRawAudioMode: Boolean,
    isBarModeEnabled: Boolean,
    barBoostLevel: Int,
    onAutoStart: (Boolean) -> Unit,
    onFloating: (Boolean) -> Unit,
    onRaw: (Boolean) -> Unit,
    onBar: (Boolean) -> Unit,
    onBoost: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsToggleRow("Iniciar com o celular", "Liga automaticamente ao reiniciar", autoStartOnBoot, onAutoStart)
        SettingsToggleRow("Botão flutuante", "Controle rápido sobre o WhatsApp", isFloatingButtonEnabled, onFloating)
        SettingsToggleRow("RAW Audio Mode", "Bypass de filtros DSP no teste local", isRawAudioMode, onRaw)
        SettingsToggleRow("Modo Bar 🔊", "Aumenta áudios recebidos; desligue para economizar", isBarModeEnabled, onBar)
        if (isBarModeEnabled) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("Ganho Extra: +${(barBoostLevel * 8 / 100)} dB", color = Color.LightGray, fontSize = 10.sp)
                    Slider(value = barBoostLevel.toFloat(), onValueChange = { onBoost(it.toInt()) }, valueRange = 0f..100f, colors = SliderDefaults.colors(thumbColor = PrimaryNeon, activeTrackColor = PrimaryNeon, inactiveTrackColor = Color.DarkGray))
                }
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Text(subtitle, color = Color.Gray, fontSize = 9.sp)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PrimaryNeon, uncheckedThumbColor = Color.LightGray, uncheckedTrackColor = Color.DarkGray))
        }
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
    val isBtConnected = when (routerState) {
        is RouterState.BluetoothConnected, is RouterState.CommunicationDeviceAvailable,
        is RouterState.CommunicationDeviceSelected, is RouterState.AudioConnecting,
        is RouterState.AudioConnected, is RouterState.InputAvailable, is RouterState.OutputAvailable,
        is RouterState.RouteReady, is RouterState.RouteDegraded, is RouterState.RoutingVerified -> true
        else -> false
    }
    val isRouteReady = routerState is RouterState.RouteReady || routerState is RouterState.RoutingVerified

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isRouteReady) PrimaryNeon else Color.DarkGray)
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            Text(
                "STATUS DA ROTA DE COMUNICAÇÃO V5 📡",
                color = PrimaryNeon,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp
            )
            Spacer(modifier = Modifier.height(2.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Bluetooth:", fontSize = 9.sp, color = Color.Gray)
                Text(if (isBtConnected) "CONECTADO" else "DESCONECTADO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (isBtConnected) PrimaryNeon else Color.Gray)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Intercom:", fontSize = 9.sp, color = Color.Gray)
                Text(if (isBtConnected) "DETECTADO" else "NÃO DETECTADO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (isBtConnected) PrimaryNeon else Color.Gray)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Comunicação:", fontSize = 9.sp, color = Color.Gray)
                Text(if (isRouteReady) "ATIVA" else "INATIVA", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (isRouteReady) PrimaryNeon else Color.Gray)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Entrada (Mic):", fontSize = 9.sp, color = Color.Gray)
                Text(if (isRouteReady) "BT DISPONÍVEL" else "NÃO CONFIRMADA", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (isRouteReady) PrimaryNeon else WarningAmber)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Saída (Fone):", fontSize = 9.sp, color = Color.Gray)
                Text(if (isRouteReady) "BT DISPONÍVEL" else "NÃO CONFIRMADA", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (isRouteReady) PrimaryNeon else WarningAmber)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Rota Central:", fontSize = 9.sp, color = Color.Gray)
                Text(
                    when (routerState) {
                        is RouterState.RouteReady, is RouterState.RoutingVerified -> "ROTA PRONTA"
                        is RouterState.AudioConnected -> "ÁUDIO HFP PRONTO"
                        is RouterState.AudioConnecting -> "NEGOCIANDO HFP"
                        is RouterState.RouteDegraded -> if (routerState.isMediaPlayback) "ESCUTANDO MÍDIA" else "DEGRADADA"
                        is RouterState.Recovering -> "RECUPERANDO"
                        is RouterState.RouteLost, is RouterState.RoutingLost -> "PERDIDA"
                        is RouterState.Disconnected -> "DESCONECTADA"
                        else -> "PREPARANDO"
                    },
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isRouteReady) PrimaryNeon else WarningAmber
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("WhatsApp:", fontSize = 9.sp, color = Color.Gray)
                Text(whatsappStatus.label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (whatsappStatus == WhatsAppRouteStatus.USER_VALIDATED) PrimaryNeon else Color.LightGray)
            }

            if (routerState is RouterState.RouteReady && routerState.routePreparationTimeMs > 0L) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Preparo de Rota:", fontSize = 9.sp, color = Color.Gray)
                    Text("${routerState.routePreparationTimeMs} ms", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = PrimaryNeon)
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
                Text("📱 DISPOSITIVO & HARDWARE", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 9.sp)
                Text("Modelo: ${data.model} (${data.manufacturer})", fontSize = 9.sp, color = Color.LightGray)
                Text("Android: ${data.androidVersion} (SDK ${data.sdk})", fontSize = 9.sp, color = Color.LightGray)
                Text("Perfil: ${data.hardwareProfileName}", fontSize = 9.sp, color = PrimaryNeon)

                Spacer(modifier = Modifier.height(8.dp))
                Text("🎧 BLUETOOTH & INTERCOM", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 9.sp)
                Text("Dispositivo: ${data.bluetoothDevice}", fontSize = 9.sp, color = Color.LightGray)
                Text("Perfil BT: ${data.bluetoothProfile}", fontSize = 9.sp, color = Color.LightGray)
                Text("HFP Audio: ${data.hfpAudioState}", fontSize = 9.sp, color = PrimaryNeon)
                Text("SCO Codec: ${data.scoCodec}", fontSize = 9.sp, color = Color.LightGray)

                Spacer(modifier = Modifier.height(8.dp))
                Text("🔄 ROTA DE COMUNICAÇÃO ANDROID", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 9.sp)
                Text("CommDevice: ${data.communicationDevice}", fontSize = 9.sp, color = Color.LightGray)
                Text("Modo de Áudio: ${data.audioMode}", fontSize = 9.sp, color = Color.LightGray)
                Text("Estado Central: ${data.routeState}", fontSize = 9.sp, color = PrimaryNeon)
                Text("Entrada BT: ${if (data.inputAvailable) "DISPONÍVEL" else "NÃO DISPONÍVEL"}", fontSize = 9.sp, color = if (data.inputAvailable) PrimaryNeon else WarningAmber)
                Text("Saída BT: ${if (data.outputAvailable) "DISPONÍVEL" else "NÃO DISPONÍVEL"}", fontSize = 9.sp, color = if (data.outputAvailable) PrimaryNeon else WarningAmber)
                Text("Rota preparada: ${if (data.isBidirectionalReady) "SIM (instantâneo Android)" else "NÃO"}", fontSize = 9.sp, color = if (data.isBidirectionalReady) PrimaryNeon else WarningAmber)
                Text("Keep-alive: ${data.scoKeepAliveState}", fontSize = 9.sp, color = Color.LightGray)

                Spacer(modifier = Modifier.height(8.dp))
                Text("⏱️ TEMPOS E MÉTRICAS REAIS", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 9.sp)
                Text("Preparo de Rota: ${data.routePreparationTimeMs} ms", fontSize = 9.sp, color = Color.LightGray)
                Text("Buffer Estimado: ${data.audioBufferEstimateMs} ms", fontSize = 9.sp, color = Color.LightGray)
                Text("Latência Fim-a-Fim: ${data.endToEndLatency}", fontSize = 9.sp, color = Color.LightGray)

                Spacer(modifier = Modifier.height(8.dp))
                Text("📊 ESTABILIDADE & CONTADORES DE QUEDAS", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 9.sp)
                Text("Quedas de Rota: ${data.routeLossCount}", fontSize = 9.sp, color = if (data.routeLossCount == 0) PrimaryNeon else WarningAmber)
                Text("Recuperações: ${data.recoveryCount}", fontSize = 9.sp, color = Color.LightGray)
                Text("Desconexões SCO: ${data.scoDisconnectCount}", fontSize = 9.sp, color = Color.LightGray)
                Text("Trocas CommDevice: ${data.communicationDeviceChangeCount}", fontSize = 9.sp, color = Color.LightGray)

                Spacer(modifier = Modifier.height(8.dp))
                Text("💬 WHATSAPP STATUS", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 9.sp)
                Text("Status: ${data.whatsappStatus.label}", fontSize = 9.sp, color = PrimaryNeon)

                Spacer(modifier = Modifier.height(10.dp))
                Text("📋 FLIGHT RECORDER (LOGS EM TEMPO REAL)", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 9.sp)
                Text("Memória recente: ${logs.size} eventos", fontSize = 9.sp, color = PrimaryNeon)
                Text(com.btmicpro.core.AppLogger.storageStatus(), fontSize = 9.sp, color = Color.LightGray)

                Spacer(modifier = Modifier.height(2.dp))
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

                Spacer(modifier = Modifier.height(2.dp))
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
                        Text("Recentes", fontSize = 9.sp, color = Color.White)
                    }
                    Button(
                        onClick = onShareLogs,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B2B2B)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(12.dp), tint = PrimaryNeon)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Exportar ZIP", fontSize = 9.sp, color = Color.White)
                    }
                    Button(
                        onClick = onClearLogs,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF332020)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(0.8f)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(12.dp), tint = AccentRed)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Limpar", fontSize = 9.sp, color = Color.White)
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
                        Text("Copiar TXT", fontSize = 9.sp, color = Color.White)
                    }
                    Button(
                        onClick = onCopyJson,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B2B2B)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = PrimaryNeon)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copiar JSON", fontSize = 9.sp, color = Color.White)
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
                    Text("Marcar: Testado no WhatsApp", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
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
    val isMediaPlayback = routerState is RouterState.RouteDegraded && routerState.isMediaPlayback
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
            isMediaPlayback -> WarningAmber
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

        Spacer(modifier = Modifier.height(2.dp))

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
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = when {
                    !isRouterEnabled -> "DESATIVADO - Toque para ativar"
                    isMediaPlayback -> "ESCUTANDO MÍDIA - Microfone em espera"
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
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = if (isRouterEnabled) "Solicitando rota de comunicação do intercom" else "Ative para preparar a comunicação pelo capacete",
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

private val homeAffiliateBanners = listOf(
    PromoBannerItem(
        title = "Capa de chuva",
        highlight = "Equipamento de moto",
        link = "https://shopee.com.br/search?keyword=capa%20de%20chuva%20moto",
        bannerRes = R.drawable.banner_capa_chuva,
        neonColor = PrimaryNeon
    ),
    PromoBannerItem(
        title = "Capacete",
        highlight = "Segurança na estrada",
        link = "https://shopee.com.br/search?keyword=capacete%20moto",
        bannerRes = R.drawable.banner_capacete,
        neonColor = Color(0xFF4FC3F7)
    ),
    PromoBannerItem(
        title = "Intercomunicador",
        highlight = "Áudio no capacete",
        link = "https://shopee.com.br/search?keyword=intercomunicador%20moto",
        bannerRes = R.drawable.banner_intercom,
        neonColor = PrimaryNeon
    ),
    PromoBannerItem(
        title = "Kit relação",
        highlight = "Manutenção da moto",
        link = "https://shopee.com.br/search?keyword=kit%20rela%C3%A7%C3%A3o%20moto",
        bannerRes = R.drawable.banner_relacao,
        neonColor = WarningAmber
    ),
    PromoBannerItem(
        title = "Capa impermeável",
        highlight = "Chuva forte",
        link = "https://shopee.com.br/search?keyword=capa%20imperme%C3%A1vel%20moto",
        bannerRes = R.drawable.promo_capa_chuva,
        neonColor = PrimaryNeon
    ),
    PromoBannerItem(
        title = "Capacetes",
        highlight = "Promoção",
        link = "https://shopee.com.br/search?keyword=capacete%20fechado%20moto",
        bannerRes = R.drawable.promo_capacete,
        neonColor = Color(0xFF4FC3F7)
    ),
    PromoBannerItem(
        title = "Pneus de moto",
        highlight = "Troca e manutenção",
        link = "https://shopee.com.br/search?keyword=pneu%20moto",
        bannerRes = R.drawable.promo_pneus,
        neonColor = WarningAmber
    ),
    PromoBannerItem(
        title = "Relação",
        highlight = "Kit transmissão",
        link = "https://shopee.com.br/search?keyword=rela%C3%A7%C3%A3o%20moto",
        bannerRes = R.drawable.promo_relacao,
        neonColor = PrimaryNeon
    )
)

@Composable
fun AffiliateBannerCarousel() {
    val uriHandler = LocalUriHandler.current
    var currentIndex by remember { mutableIntStateOf(0) }
    val current = homeAffiliateBanners[currentIndex]

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentIndex = (currentIndex + 1) % homeAffiliateBanners.size
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .clickable { uriHandler.openUri(current.link) },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
        border = androidx.compose.foundation.BorderStroke(1.dp, current.neonColor.copy(alpha = 0.45f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(current.bannerRes),
                contentDescription = "Anúncio: ${current.title}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.30f))
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = current.title.uppercase(),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "${current.highlight} • tocar para abrir",
                    color = current.neonColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "AD",
                color = Color.Black,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(current.neonColor, RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun HomeBarModeCard(
    enabled: Boolean,
    boostLevel: Int,
    onToggle: (Boolean) -> Unit,
    onBoostChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (enabled) WarningAmber.copy(alpha = 0.55f) else Color(0xFF282828)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = if (enabled) WarningAmber else PrimaryNeon,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text("MODO BAR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        Text(
                            text = if (enabled) "Aumentador ligado: +${boostLevel * 8 / 100} dB" else "Aumenta áudios recebidos",
                            color = Color.Gray,
                            fontSize = 9.sp
                        )
                    }
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = WarningAmber,
                        uncheckedThumbColor = Color.LightGray,
                        uncheckedTrackColor = Color.DarkGray
                    ),
                    modifier = Modifier.height(6.dp)
                )
            }
            if (enabled) {
                Slider(
                    value = boostLevel.toFloat(),
                    onValueChange = { onBoostChange(it.toInt()) },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = WarningAmber,
                        activeTrackColor = WarningAmber,
                        inactiveTrackColor = Color.DarkGray
                    )
                )
            }
        }
    }
}


@Composable
fun UnifiedVolumeControlCard(
    mediaVolume: Int,
    maxMediaVolume: Int,
    callVolume: Int,
    maxCallVolume: Int,
    onVolumePercentChange: (Int) -> Unit,
    onStep: (Boolean) -> Unit
) {
    val mediaPercent = ((mediaVolume.toFloat() / maxMediaVolume.coerceAtLeast(1)) * 100).roundToInt()
    val callPercent = ((callVolume.toFloat() / maxCallVolume.coerceAtLeast(1)) * 100).roundToInt()
    val unifiedPercent = ((mediaPercent + callPercent) / 2).coerceIn(0, 100)
    var localValue by remember(unifiedPercent) { mutableFloatStateOf(unifiedPercent.toFloat()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF282828))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = PrimaryNeon, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text("VOLUME", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        Text("Mídia + intercom juntos", color = Color.Gray, fontSize = 9.sp)
                    }
                }
                Text("${localValue.roundToInt()}%", color = PrimaryNeon, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onStep(false) },
                    modifier = Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF242424))
                ) {
                    Icon(Icons.AutoMirrored.Filled.VolumeDown, contentDescription = "Diminuir volume", tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Slider(
                    value = localValue,
                    onValueChange = { value ->
                        localValue = value
                        onVolumePercentChange(value.roundToInt().coerceIn(0, 100))
                    },
                    valueRange = 0f..100f,
                    steps = 19,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(thumbColor = PrimaryNeon, activeTrackColor = PrimaryNeon, inactiveTrackColor = Color(0xFF333333))
                )
                IconButton(
                    onClick = { onStep(true) },
                    modifier = Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF242424))
                ) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Aumentar volume", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun AudioModeHomeCard(currentAudioMode: AudioModeProfile, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E2E2E))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.BluetoothConnected, contentDescription = null, tint = PrimaryNeon, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("MODO DE ÁUDIO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    Text(currentAudioMode.subtitle, color = Color.Gray, fontSize = 9.sp, maxLines = 1)
                }
            }
            Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF252525)) {
                Text(
                    text = currentAudioMode.title,
                    color = PrimaryNeon,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
fun AudioModeSelectorDialog(
    currentAudioMode: AudioModeProfile,
    onSelect: (AudioModeProfile) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Selecionar modo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AudioModeProfile.values().forEach { profile ->
                    val isSelected = currentAudioMode == profile
                    Surface(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onSelect(profile) },
                        color = if (isSelected) Color(0xFF252525) else Color.Transparent,
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, PrimaryNeon.copy(alpha = 0.5f)) else null,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onSelect(profile) },
                                colors = RadioButtonDefaults.colors(selectedColor = PrimaryNeon, unselectedColor = Color.Gray)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                Text(profile.title, color = if (isSelected) PrimaryNeon else Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text(profile.subtitle, color = Color.LightGray, fontSize = 9.sp)
                                Text(profile.details, color = Color.Gray, fontSize = 8.sp, lineHeight = 11.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Color.LightGray, fontSize = 11.sp) } },
        containerColor = Color(0xFF1E1E1E),
        shape = RoundedCornerShape(14.dp)
    )
}
