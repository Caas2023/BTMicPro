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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
                text = "v1.2.0 Pro",
                color = Color.Gray,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Info Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("RIDER 1", color = PrimaryNeon, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("[CleanVoice DSP]", color = Color.Gray, fontSize = 14.sp)
            }
            Box(modifier = Modifier.width(1.dp).height(40.dp).background(Color.DarkGray))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("PHONE", color = PrimaryNeon, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("[KingKong X Pro]", color = Color.Gray, fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Botão Central de Roteamento (MOTO WHATSAPP MODE)
        RouterControlCard(
            isRouterEnabled = isRouterEnabled,
            routerState = routerState,
            onToggleRouter = { viewModel.toggleRouter(it) }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // === NOVO: MONITOR AO VIVO / OUVIDO NO CAPACETE (Estilo Noise Uncanceller) ===
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
                            if (isLiveMonitorEnabled) "Monitorando áudio com CleanVoice DSP (Sem cortes)"
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

        // Toggles lado a lado para economizar espaço
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
            Text("Botão Flutuante", color = if (isFloatingButtonEnabled) PrimaryNeon else Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
}

@Composable
fun RouterControlCard(
    isRouterEnabled: Boolean,
    routerState: RouterState,
    onToggleRouter: (Boolean) -> Unit
) {
    val isConnected = routerState is RouterState.RoutingActive
    val deviceName = if (routerState is RouterState.RoutingActive) (routerState as RouterState.RoutingActive).device.name else ""
    
    val statusColor by animateColorAsState(
        targetValue = when {
            !isRouterEnabled -> Color.Gray
            isConnected -> PrimaryNeon
            routerState is RouterState.WaitingDevice -> WarningAmber
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
                    text = if (isConnected) deviceName else "MOTO",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = statusColor,
                    fontSize = if (isConnected) 16.sp else 21.sp
                )
                Text(
                    text = if (isConnected) "" else "WHATSAPP",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                    fontSize = if (isConnected) 11.sp else 14.sp
                )
                Text(
                    text = if (isConnected) "MODE" else "MODE",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                    fontSize = if (isConnected) 10.sp else 11.sp
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
                imageVector = if (isConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when {
                    !isRouterEnabled -> "DESATIVADO - Toque para ativar"
                    isConnected -> "CONECTADO: $deviceName"
                    routerState is RouterState.WaitingDevice -> "AGUARDANDO CAPACETE..."
                    routerState is RouterState.Error -> "ERRO: ${(routerState as RouterState.Error).message}"
                    else -> "INATIVO"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = statusColor
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isRouterEnabled) "CleanVoice DSP Ativo • Zero delay no WhatsApp • Sem cortes" else "Ative para gravar e falar pelo microfone do capacete",
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
