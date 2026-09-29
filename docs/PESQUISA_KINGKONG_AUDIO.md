# Pesquisa técnica — KingKong 8 vs KingKong X Pro

**Projeto:** BTMicPro
**Baseline:** versão 1.5.3 — não alterar até teste isolado
**Objetivo:** manter retorno Bluetooth no X Pro sem perder envio de áudio no WhatsApp.

## Sintoma confirmado

- KingKong 8: BTMicPro ligado; usuário escuta intercomunicador e envia áudio no WhatsApp.
- KingKong X Pro: WhatsApp grava/envia; retorno desaparece enquanto BTMicPro está ligado. Ao desligar app, retorno volta.
- Interpretação: entrada/captura funciona; saída `AudioTrack`/HFP/SCO é rejeitada, redirecionada ou silenciada no X Pro.

## Hipóteses ordenadas

1. **Mais promissora — tipo de stream do retorno incompatível com HAL do X Pro.**
   Código atual usa `USAGE_NOTIFICATION_RINGTONE` + `CONTENT_TYPE_MUSIC`. Testar variante isolada com `USAGE_VOICE_COMMUNICATION` + `CONTENT_TYPE_SPEECH`, `MODE_IN_COMMUNICATION`, `setCommunicationDevice()` e volume `STREAM_VOICE_CALL` quando aplicável.
2. **Arbitragem de captura Android.**
   `VOICE_COMMUNICATION` do BTMicPro pode disputar a captura do WhatsApp. Android permite objetos simultâneos, mas não garante áudio real para dois apps comuns. Isso explica bloqueio/resultado diferente por HAL.
3. **Diferença de plataforma.**
   KingKong 8: Android 13 / MediaTek MT8788V/WA / Bluetooth 4.2. X Pro: Android 14/15 / Dimensity 8200 / Bluetooth 5.3. Causa provável é política/HAL, não desempenho bruto.
4. **SCO legado.**
   `startBluetoothSco()` funciona em referências antigas, mas é depreciado no API 34. Preferir `setCommunicationDevice()` no X Pro; manter fallback separado.

## Evidências externas

- Android captura simultânea: https://developer.android.com/media/platform/sharing-audio-input
- AOSP política de captura: https://source.android.com/docs/core/audio/implement-policy
- AudioManager/setCommunicationDevice: https://developer.android.com/reference/android/media/AudioManager#setCommunicationDevice(android.media.AudioDeviceInfo)
- Caso SCO sem saída audível: https://stackoverflow.com/questions/32199382/android-bluetooth-sco-audio-is-not-audible-to-bluetooth-headset
- Limitações de microfone Bluetooth em apps Android: https://accessibleandroid.com/why-apps-like-whatsapp-telegram-and-signal-dont-use-wireless-earbud-microphones-for-voice-messages-on-android/
- KingKong 8: https://www.gsmarena.com/cubot_kingkong_8-12640.php
- KingKong X Pro: https://www.gsmarena.com/cubot_kingkong_x_pro-13485.php

## Variante de teste pendente

Não aplicar no baseline. Criar APK experimental separado:

```kotlin
AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
    .build()
```

Comparar no X Pro:

- retorno audível;
- nota de voz WhatsApp imediata;
- cortes;
- estado `communicationDevice`;
- `AudioRecordingConfiguration.isClientSilenced`;
- `AudioTrack.getRoutedDevice()`.

## Testes decisivos

```bash
adb shell dumpsys audio
adb shell dumpsys media.audio_flinger
```

Registrar antes/durante nota WhatsApp:

- `AudioManager.mode`;
- dispositivo de comunicação;
- dispositivo roteado do `AudioTrack`;
- fonte efetiva do `AudioRecord`;
- estado SCO/HFP;
- silêncio aplicado pelo sistema.

## Regra de segurança

Nenhum patch deve substituir baseline ou alterar áudio estável sem APK experimental separado, build bem-sucedido e teste no KingKong X Pro.

## Estado

Pesquisa atualizada sob demanda em 2026-09-27. Não foi configurada uma pesquisa automática nesta sessão. Nenhuma solução foi validada fisicamente no X Pro.

## 2026-09-27 — Redução de vento na gravação direta do WhatsApp

### Objetivo e conclusão

Objetivo do usuário: melhorar a voz do microfone Bluetooth gravada diretamente pelo WhatsApp no KingKong X Pro, mantendo envio e reprodução com BT Mic Pro ligado.

Há caminhos de investigação, mas não foi encontrada uma API pública que permita ao BT Mic Pro inserir seu DSP no microfone do WhatsApp em um Android original. Isso não equivale a impossibilidade absoluta: processamento no acessório, configuração do fabricante e instrumentação com root são camadas diferentes do APK comum.

### Evidências e implicações

1. **Efeitos por sessão, não um filtro global do microfone.** `NoiseSuppressor.create(sessionId)` vincula o efeito a um `AudioRecord`. A documentação de captura concorrente diz que o pré-processamento do cliente ativo de maior prioridade prevalece. Abrir um segundo gravador com NS não constitui uma ponte de áudio tratado para o WhatsApp; pode receber silêncio ou disputar a captura. Fonte: [Android, compartilhamento de entrada](https://developer.android.com/media/platform/sharing-audio-input) e [NoiseSuppressor](https://developer.android.com/reference/kotlin/android/media/audiofx/NoiseSuppressor).

2. **AOSP permite pré-processamento configurado pelo fabricante.** `audio_effects.xml`, bibliotecas de efeitos e regras do HAL determinam processamento por fonte; efeitos por dispositivo também existem. A documentação distingue supressão de vento de câmera de supressão de ruído estacionário de voz. Portanto, NS disponível não significa um filtro de vento eficaz no Bluetooth. Para o X Pro, falta examinar a configuração real do firmware e a cadeia ativa durante a nota de voz. Fontes: [pré-processamento AOSP](https://source.android.com/docs/core/audio/implement-pre-processing), [efeitos por dispositivo](https://source.android.com/docs/core/audio/audio-effects), [captura concorrente no HAL](https://source.android.com/docs/core/audio/concurrent).

3. **O intercomunicador pode influenciar o processamento do telefone.** No HFP, `AT+NREC=0` permite ao acessório solicitar a desativação de NR/EC do gateway para evitar processamento em cascata. Material da ITU documenta implementações divergentes e degradação por processamento duplicado. É uma hipótese relevante, não evidência de que este intercom enviou o comando ou de que o X Pro o aplicou. Os resultados históricos de outros celulares não são transferíveis ao X Pro. Não há controle público genérico no APK para reconfigurar esse comportamento. Fontes: [ITU, nota técnica](https://www.itu.int/en/ITU-T/C-I/Documents/Note%20to%20technical%20editors.pdf), [Silicon Labs AN992](https://www.silabs.com/documents/public/application-notes/AN992.pdf).

4. **Codec melhor pode preservar voz, mas não remove vento.** CVSD/mSBC são caminhos comuns de voz; HFP 1.9 também define LC3-SWB a 32 kHz, portanto a afirmação genérica “todo HFP está limitado a 16 kHz” seria incorreta. LE Audio oferece outra rota bidirecional. Ambos dependem do telefone, firmware e acessório. Bluetooth 5.3 anunciado pela Cubot não comprova LE Audio ou LC3-SWB ativo. A taxa solicitada ao `AudioRecord` não identifica o codec negociado, pois pode haver reamostragem. Mudar codec A2DP de reprodução não é selecionar codec do microfone HFP. Fontes: [HFP 1.9](https://www.bluetooth.org/DocMan/handlers/DownloadDoc.ashx?doc_id=574215), [gravação LE Audio](https://developer.android.com/develop/connectivity/bluetooth/ble-audio/audio-recording), [visão geral LE Audio](https://developer.android.com/develop/connectivity/bluetooth/ble-audio/overview), [Cubot X Pro](https://www.cubot.net/phone/rugged-phone/KingKong-X-Pro).

5. **Com root existe um ponto de intervenção dentro do processo do WhatsApp.** O projeto [WhatsMicFix-LSPosed](https://github.com/D4vRAM369/WhatsMicFix-LSPosed) demonstra a arquitetura: hooks em `AudioRecord.read()` e `startRecording()`, ganho/compressão e efeitos de sessão. O autor relata uso em Pixel, não KingKong X Pro nem vento de intercomunicador. Isso é referência de viabilidade arquitetural, não comprovação de redução de vento. A revisão consultada foi `0715c57b8425045c9dc253fc79d47f120f84f715`: `HookEntry.kt` seleciona `com.whatsapp`/`com.whatsapp.w4b`; `AudioHooks.kt` chama `processWithCompressor` em buffers lidos. Porém, linhas 372–375 de `AudioHooks.kt` exibem blocos `else null` aparentemente incompletos. Não foi compilado/auditado integralmente nem instalado; não recomendar o binário como solução pronta. O README menciona Shizuku como possibilidade de contribuição, não como implementação sem root existente.

6. **Filtro neural é opção de processamento, não acesso ao WhatsApp.** [RNNoise](https://github.com/xiph/rnnoise) implementa supressão de ruído com rede neural e exemplo PCM mono 48 kHz. Pode ser avaliado onde tivermos acesso às amostras: gravador próprio, módulo instrumentado ou implementação de fabricante. Reamostrar SCO para 48 kHz não recupera frequências perdidas. O ganho para vento de capacete precisa ser medido, pois supressão agressiva pode apagar sílabas. Saturação já ocorrida na cápsula/ADC não é recuperada integralmente por filtro posterior.

### Relação com o código atual

- `AudioEffectController.attachToSession` liga NS/AGC/AEC ao gravador local; não há integração com a sessão do WhatsApp.
- A mensagem “Hardware DSP ... ATIVADO” não comprova aceleração física nem eficácia contra vento; implementar um efeito por API pode envolver software e/ou hardware do fabricante.
- Perfis de rota e a liberação de mídia da v1.5.9 tratam conectividade/reprodução, não filtragem da nota de voz enviada.
- Ativar NS, AGC e AEC juntos não deve ser tratado como qualidade máxima automática. É necessário comparar combinações; AGC pode amplificar vento nos intervalos e processamento em cascata pode cortar voz.

### Experimento proposto para o X Pro

**Dados necessários:** modelo exato e firmware do intercomunicador; versão/build do Android; versão do WhatsApp; existência de root; exemplos curtos de voz com e sem vento.

1. Confirmar microfone efetivo com telefone afastado e fala próxima do microfone do capacete, em local parado. Não usar apenas `RouteReady` como prova.
2. Gravar a mesma frase, mesma posição/volume de fala, em silêncio e com vento controlado; guardar amostras do WhatsApp com roteador desligado/ligado quando ambas as rotas realmente usem o mesmo microfone. Não comparar microfone interno com Bluetooth como se fosse só mudança de processamento.
3. Inspecionar via ADB `dumpsys audio`, `dumpsys media.audio_flinger` e `dumpsys media.audio_policy` antes/durante a gravação, conforme informações que o firmware expuser. Coletar configurações `audio_effects*.xml` se forem legíveis. Isso é diagnóstico, não autorização para sobrescrever arquivos do sistema.
4. Se necessário, usar log Bluetooth HCI de uma sessão de teste para procurar negociação de codec e NREC; disponibilidade e detalhamento dependem do firmware. Não deduzir codec a partir do sample rate do cliente.
5. Comparar ajustes de NR do próprio intercomunicador, quando documentados para o modelo. Verificar firmware do fabricante e recursos efetivamente negociados.
6. Em captura local separada, comparar sem efeitos, NS isolado, NS + ganho moderado e filtro anti-vento/neural. Avaliar inteligibilidade, sílabas cortadas, picos saturados e ruído nas pausas, nivelando o volume para não confundir “mais alto” com “melhor”.
7. Para filtragem diretamente no WhatsApp, só iniciar protótipo instrumentado após confirmar ambiente com root compatível. Preservar formato/canais, processar apenas amostras efetivamente lidas, cobrir leituras Java e investigar caminho nativo, sem supor que toda versão do WhatsApp usa o mesmo caminho. Validar notas repetidas e reprodução; nenhuma alteração sistêmica foi executada nesta pesquisa.

**Critério de sucesso:** o destinatário recebe voz mais inteligível com vento, sem perdas de sílabas e sem quebrar envio/reprodução. Logs de efeitos ativados, `RouteReady`, volume maior ou teste unitário aprovado não substituem essa comparação.

**Próximo passo:** identificar intercomunicador e firmware do celular para escolher entre diagnóstico de processamento nativo/acessório e investigação com root. Pesquisa documental concluída; eficácia no aparelho ainda não medida.

### Identificação do acessório — WAYXIN KT-1 (informado pelo usuário)

Consulta em 2026-09-27 por KT-1/KT1, firmware, upgrade, manual e FCC:

- Encontrados anúncios de KT1 com alegações de DSP/CVC, mas não foi localizado firmware oficial, ferramenta de atualização, SDK ou protocolo documentado de ajuste do DSP para esse modelo nas fontes consultadas. Ausência nos resultados não prova que tais recursos não existam.
- As descrições comerciais divergem: [anúncio KT-1 com Bluetooth 5.0](https://www.gotobratislava.sk/sales-352956_New.htm) e [anúncio KT1 com Bluetooth 5.4 e DSP/CVC](https://www.bermuda.ubuy.com/productde/Q7S0TQIYE-motorcycle-headset-kt1-motorcycle-headset-bluetooth-dsp-cvc-double-noise-cancellation-headphones-motorcycle-helmet-perfect-sound). São fontes secundárias, insuficientes para identificar chip, revisão de hardware, codec ou recursos ajustáveis da unidade do usuário. Não adotar percentuais publicitários de cancelamento como medição.
- Manuais encontrados de WAYXIN R9/R15 Pro/X8S não comprovam comandos ou compatibilidade de firmware com KT-1. Não extrapolar combinações de botões desses modelos.
- Próxima evidência necessária: foto da etiqueta e do manual/QR code ou link da compra. Depois, consultar vendedor/fabricante sobre revisão de hardware, firmware oficial e ajuste de redução de ruído/sensibilidade. Conector USB-C, por si só, não comprova atualização por USB.
- Teste comparativo útil: mesma frase e mesmo posicionamento do microfone em chamada e em nota de voz WhatsApp, com confirmação de captura Bluetooth em ambos. Diferença pode indicar caminhos de áudio/processamento distintos; não identifica isoladamente qual efeito ou componente é responsável.
- **Situação:** reprogramação do KT-1 ainda não demonstrada; não foi alterado firmware nem código do aplicativo.

### Inspeção USB do dispositivo confirmado pelo usuário — 2026-09-27

Usuário confirmou que o dispositivo conectado identificado como MK-01 corresponde ao KT-1. Inspeção somente de leitura via sysfs, ALSA e `lsusb`; nenhuma captura de áudio, escrita de controles ou gravação de firmware executada.

- USB VID:PID `4c4a:4155`, fabricante declarado `Jieli Technology`, produto `MK-01`, `bcdDevice=0100`. Esse descritor de revisão não identifica, por si só, versão comercial do firmware ou modelo exato do chip.
- Configuração exposta: duas interfaces USB Audio Class 1.0 (controle e streaming de entrada), driver `snd-usb-audio`; PCM mono, 16 bits, 48000 Hz. ALSA lista captura, sem reprodução para esse dispositivo.
- Feature Unit declara mute, volume e AGC (`bmaControls=0x43`). `amixer -c MK01 contents` informa captura ligada, volume 147/147 (faixa publicada -28,37 a -0,94 dB) e `Auto Gain Control=off`. Valores reportados pelo driver; eficácia acústica ainda não medida.
- Não há controle de supressão de ruído/vento, interface DFU ou interface de fabricante nos descritores dessa configuração. Isso não exclui outro modo de boot/atualização; não há evidência para acioná-lo ou gravar firmware.
- `lsusb` avisou que não conseguiu abrir diretamente o dispositivo; a leitura de descritores via sysfs e dos controles via ALSA funcionou. Não foi executada transferência USB proprietária.
- Ganho/AGC USB não comprovam controle sobre a rota Bluetooth, nem persistência após desligar/desconectar. Ativar AGC pode elevar ruído nas pausas; comparar amostras antes de adotar qualquer ajuste.
- Próxima etapa de medição: coordenar com o usuário uma gravação USB curta, com fala e pausa, para medir clipping, nível e ruído; depois comparar com a nota de voz Bluetooth. O USB a 48 kHz não revela o codec de voz Bluetooth.

### Primeiro teste de captura USB — 2026-09-27

Após solicitação do usuário (“teste”), gravados 10 segundos por `plughw:CARD=MK01,DEV=0`, PCM mono 48 kHz/16 bits, sem alteração de controles. Arquivo temporário: `/tmp/opencode/kt1-teste-usb-01.wav` (não incorporado ao repositório).

- Captura concluída, 480.000 amostras: RMS global -42,6 dBFS; pico -16,4 dBFS; nenhuma amostra com magnitude >=32700.
- Primeiro segundo: RMS -35,2 dBFS. Segundos seguintes: RMS entre -46,0 e -45,3 dBFS.
- A medição confirma entrada USB com sinal e ausência de clipping digital próximo ao limite nessa amostra; não confirma fala inteligível, redução de vento, ausência de distorção anterior ao ADC ou desempenho Bluetooth.
- Falta confirmar se o usuário falou durante a janela de captura. Não calcular SNR de voz nem classificar o restante como ruído de fundo sem confirmar fala/pausa. Nenhum AGC ou filtro foi ativado.

### Investigação de identificação do chip e programação — 2026-09-27

Pedido: identificar concretamente a plataforma e o acesso ao firmware, não confundir captura USB com acesso ao programa.

**Nova inspeção local:** o dispositivo continua `4c4a:4155`, `Jieli Technology / MK-01`, `bcdDevice=0100`. Uma configuração com duas interfaces de áudio (classes/subclasses `01/01` e `01/02`), endpoint de streaming `0x83`. Sem interface HID, armazenamento USB, DFU ou serial exposta nessa configuração. Nenhuma porta ttyUSB/ttyACM presente. O único `sg0` encontrado pertence a disco SATA do PC, não ao KT-1; não foram enviados comandos SCSI a ele. Não há adaptador Bluetooth local exposto para investigar os serviços Bluetooth do acessório. O nó USB permite leitura, mas não abertura para escrita pela identidade atual; não houve tentativa de elevar privilégios.

**Identificação importante:** o código público do SDK [Jieli-Tech/AW30N, descriptor.c](https://github.com/Jieli-Tech/AW30N/blob/main/sdk/apps/app/bsp/common/usb/device/descriptor.c) utiliza os mesmos bytes de VID/PID (`'J','L',0x55,0x41`) e revisão `0x0100` como valores padrão. [Discussão técnica do kernel Linux](https://lists.openwall.net/linux-kernel/2025/09/29/15) confirma reutilização desse identificador em diferentes dispositivos. Logo, esses valores não identificam o chip como AW30N, AC69xx ou qualquer outro modelo específico. “MK-01” é o nome USB apresentado pelo firmware, não um número de chip confirmado.

**Ferramentas e caminhos encontrados, com compatibilidade pendente:**

- [Jieli-Tech/fw-Bootloader](https://github.com/Jieli-Tech/fw-Bootloader): repositório oficial com bibliotecas/projetos específicos por família e suporte a atualização serial/USB HID quando implementado no produto. O KT-1 conectado não expõe HID/serial nessa configuração.
- [jl-uboot-tool](https://github.com/kagaimiq/jl-uboot-tool): ferramenta independente de leitura/gravação para determinadas famílias; a tabela distingue suporte comprovado e incerto. O código `jldevfind.py` procura dispositivos SCSI UBOOT/UDISK/DEVICE, não microfones USB. A interface necessária não está exposta no KT-1 neste momento. Código consultado, não executado.
- [Entrada em UBOOT](https://github.com/kagaimiq/jl-uboot-tool/blob/main/docs/how-to-enter-uboot.md): descreve dongle de atualização Jieli e sinais USB_KEY/ISP_KEY no boot; em algumas implementações há entrada por firmware. Esses sinais não equivalem a uma solicitação USB Audio comum. Não há procedimento de entrada confirmado para a revisão do KT-1 do usuário.
- [Documentação oficial AC79 sobre download USB](https://doc.zh-jieli.com/AC79/zh-cn/master/getting_started/preparation/update.html): confirma existência de modo de download e ferramenta de atualização forçada em produtos Jieli, mas é documentação de outra família, não um procedimento validado para KT-1.

**Resultado:** evidência forte de firmware USB baseado no ecossistema Jieli; modelo exato do silício, revisão da placa, tamanho/proteção da flash e protocolo habilitado continuam não identificados. Não há dump de firmware, firmware de fábrica compatível nem acesso demonstrado à memória do KT-1.

**Dependência para avançar:** obter identificação física (foto legível das inscrições do chip principal e da revisão da placa, se acessíveis) ou documentação específica dessa revisão fornecida pelo fabricante. Com isso, selecionar família/SDK e verificar o modo de leitura/backup compatível antes de qualquer alteração. Nome USB genérico ou anúncio DSP/CVC não substitui essa identificação.

Verificação complementar em 2026-09-27: este PC não expõe adaptador Bluetooth local (`/sys/class/bluetooth` ausente), portanto não foi possível interrogar os serviços Bluetooth do KT-1 por aqui. Os nós `hidraw0-2` pertencem a mouse/teclado, não ao KT-1, que não expõe interface HID nessa configuração USB.

### Segunda rodada de identificação — 2026-09-27 (somente leitura)

- `udevadm` confirma `ID_VENDOR_ID=4c4a`, `ID_MODEL_ID=4155`, `ID_USB_REVISION=0100`, serial `1120021803060616`, interfaces `:010100:010200:` (áudio/controle + streaming). Nenhum identificador novo de chip ou firmware.
- `/dev/sg0` confirmado como disco SATA do próprio PC (`WDC WD10SPZX-24Z`), não é interface do KT-1; nenhum comando foi enviado a ele. O nó USB do KT-1 (`/dev/bus/usb/001/002`) não é legível pela identidade atual; nenhuma tentativa de escrita ou elevação de privilégio foi feita.
- Busca FCC: localizados registros do mesmo fabricante para outras famílias (R9 `2A7YFR9`; R16/R18/R19/X6S/X8S/X9S `2BHEM-R16`), mas nenhum registro FCC localizado para KT-1/KT-1 nas consultas feitas. Ausência nos resultados não prova inexistência de certificação; e fotos internas da família R16 não identificam o KT-1.
- Pista de plataforma com ressalva: o [SDK AW30N](https://github.com/Jieli-Tech/AW30N) é BLE 5.4 de modo único (sem HFP clássico), portanto improvável para um headset com perfil viva-voz clássico; famílias Jieli com áudio clássico e redução de ruído (ex.: linha AC79, com NR convencional e neural) existem, mas vincular o KT-1 a qualquer modelo de chip sem inscrição física ou documento do fabricante seria adivinhação.
- **Resultado mantido:** ecossistema Jieli confirmado via USB; chip exato, revisão da placa, flash e protocolo de gravação continuam desconhecidos. Nenhum acesso ao firmware demonstrado.
