import re

main_screen_path = 'app/src/main/java/com/btmicpro/ui/MainScreen.kt'
strings_xml_path = 'app/src/main/res/values/strings.xml'

with open(main_screen_path, 'r', encoding='utf-8') as f:
    content = f.read()

replacements = [
    ('\"v1.1.4\"', 'stringResource(R.string.app_version_label)'),
    ('\"RAW AUDIO MODE\"', 'stringResource(R.string.mode_raw_audio_title)'),
    ('\"Bypass DSP para não cortar voz no vento forte\"', 'stringResource(R.string.mode_raw_audio_desc)'),
    ('\"Iniciar com o Celular\"', 'stringResource(R.string.mode_boot_title)'),
    ('\"Liga sozinho quando o celular reinicia\"', 'stringResource(R.string.mode_boot_desc)'),
    ('\"MODO BAR 🔊\"', 'stringResource(R.string.mode_bar_title)'),
    ('\"Aumenta volume da mídia até +8dB para bar/ruído\"', 'stringResource(R.string.mode_bar_desc)'),
    ('\"Botão Flutuante Sobreposto\"', 'stringResource(R.string.mode_float_title)'),
    ('\"Fica por cima do WhatsApp para ligar/desligar sem abrir o app\"', 'stringResource(R.string.mode_float_desc)'),
    ('\"CAPA DE CHUVA\"', 'stringResource(R.string.promo_capa_title)'),
    ('\"KIT RELAÇÃO\"', 'stringResource(R.string.promo_kit_title)'),
    ('\"CAPACETES\"', 'stringResource(R.string.promo_helmet_title)'),
    ('\"INTERCOMUNICADOR\"', 'stringResource(R.string.promo_intercom_title)'),
    ('\"PNEUS DE MOTO\"', 'stringResource(R.string.promo_tires_title)'),
    ('\"EM PROMOÇÃO\"', 'stringResource(R.string.promo_badge)'),
    ('contentDescription = \"Fechar\"', 'contentDescription = stringResource(R.string.promo_close)'),
    ('text = \"MOTO WHATSAPP MODE\"', 'text = stringResource(R.string.router_title_idle)'),
    ('text = if (isConnected) deviceName else \"MOTO\"', 'text = if (isConnected) deviceName else stringResource(R.string.router_title_moto)'),
    ('text = if (isConnected) \"\" else \"WHATSAPP\"', 'text = if (isConnected) \"\" else stringResource(R.string.router_title_whatsapp)'),
    ('text = if (isConnected) \"MODE\" else \"MODE\"', 'text = stringResource(R.string.router_title_mode)'),
    ('!isRouterEnabled -> \"DESATIVADO - Toque para ativar\"', '!isRouterEnabled -> stringResource(R.string.router_status_disabled)'),
    ('isConnected -> \"CONECTADO: \\"', 'isConnected -> stringResource(R.string.router_status_connected, deviceName)'),
    ('routerState is RouterState.WaitingDevice -> \"AGUARDANDO CAPACETE...\"', 'routerState is RouterState.WaitingDevice -> stringResource(R.string.router_status_waiting)'),
    ('routerState is RouterState.Error -> \"ERRO: \\"', 'routerState is RouterState.Error -> stringResource(R.string.router_status_error, (routerState as RouterState.Error).message)'),
    ('else -> \"INATIVO\"', 'else -> stringResource(R.string.router_status_inactive)'),
    ('text = if (isRouterEnabled) \"Minimize e use qualquer app - áudio vai pelo capacete\" else \"Ative e o capacete fica sempre em chamada\"', 'text = if (isRouterEnabled) stringResource(R.string.router_desc_active) else stringResource(R.string.router_desc_inactive)')
]

for old, new in replacements:
    content = content.replace(old, new)

if 'import androidx.compose.ui.res.stringResource' not in content:
    content = content.replace('import androidx.compose.ui.res.painterResource', 'import androidx.compose.ui.res.painterResource\nimport androidx.compose.ui.res.stringResource\nimport com.btmicpro.R')

with open(main_screen_path, 'w', encoding='utf-8') as f:
    f.write(content)

with open(strings_xml_path, 'r', encoding='utf-8') as f:
    strings_content = f.read()

new_strings = \"\"\"
    <!-- Labels UI -->
    <string name=\"app_version_label\">v1.1.4</string>
    <string name=\"mode_raw_audio_title\">RAW AUDIO MODE</string>
    <string name=\"mode_raw_audio_desc\">Bypass DSP para não cortar voz no vento forte</string>
    <string name=\"mode_boot_title\">Iniciar com o Celular</string>
    <string name=\"mode_boot_desc\">Liga sozinho quando o celular reinicia</string>
    <string name=\"mode_bar_title\">MODO BAR 🔊</string>
    <string name=\"mode_bar_desc\">Aumenta volume da mídia até +8dB para bar/ruído</string>
    <string name=\"mode_float_title\">Botão Flutuante Sobreposto</string>
    <string name=\"mode_float_desc\">Fica por cima do WhatsApp para ligar/desligar sem abrir o app</string>
    <string name=\"promo_capa_title\">CAPA DE CHUVA</string>
    <string name=\"promo_kit_title\">KIT RELAÇÃO</string>
    <string name=\"promo_helmet_title\">CAPACETES</string>
    <string name=\"promo_intercom_title\">INTERCOMUNICADOR</string>
    <string name=\"promo_tires_title\">PNEUS DE MOTO</string>
    <string name=\"promo_badge\">EM PROMOÇÃO</string>
    <string name=\"promo_close\">Fechar</string>
    <string name=\"router_title_idle\">MOTO WHATSAPP MODE</string>
    <string name=\"router_title_moto\">MOTO</string>
    <string name=\"router_title_whatsapp\">WHATSAPP</string>
    <string name=\"router_title_mode\">MODE</string>
    <string name=\"router_status_disabled\">DESATIVADO - Toque para ativar</string>
    <string name=\"router_status_connected\">CONECTADO: %1\</string>
    <string name=\"router_status_waiting\">AGUARDANDO CAPACETE...</string>
    <string name=\"router_status_error\">ERRO: %1\</string>
    <string name=\"router_status_inactive\">INATIVO</string>
    <string name=\"router_desc_active\">Minimize e use qualquer app - áudio vai pelo capacete</string>
    <string name=\"router_desc_inactive\">Ative e o capacete fica sempre em chamada</string>
\"\"\"

if 'mode_raw_audio_title' not in strings_content:
    strings_content = strings_content.replace('</resources>', new_strings + '\n</resources>')
    with open(strings_xml_path, 'w', encoding='utf-8') as f:
        f.write(strings_content)

print('Done')
