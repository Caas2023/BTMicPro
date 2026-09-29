# Revisão de áudio, modos comparativos e logs — 1.5.11

## Evidência que orientou a correção

Na v1.5.9 restaurada, o KingKong X Pro abriu SCO às 01:56:31 e o encerrou às 01:56:37. O dump registrou `updateCommunicationRouteClientState`, cliente do BT Mic Pro com `mPlaybackActive=false/mRecordingActive=false` e `stopScoUsingVirtualVoiceCall` solicitado pelo sistema. Não havia uma nova nota WhatsApp ativa nessa janela.

O [AudioDeviceBroker do AOSP Android 15](https://github.com/aosp-mirror/platform_frameworks_base/blob/android15-release/services/core/java/com/android/server/audio/AudioDeviceBroker.java) usa `CHECK_CLIENT_STATE_DELAY_MS = 6000`: uma solicitação sem atividade de áudio ganha uma tolerância inicial e depois perde prioridade. Ocorreu correlação temporal com esse mecanismo no aparelho. Alertas `SCO Choppy` também existem, mas não explicam sozinhos os fechamentos comandados pelo Android nem demonstram a causa de todos os cortes de gravação.

## Alterações da revisão

- A engine controla a sustentação de áudio durante a solicitação, preparação e recuperação; não depende mais de notificação `RouteReady` para iniciar um AudioTrack.
- Escrita bloqueante de silêncio sem `sleep(30)`, buffer pré-preenchido, resultados de escrita verificados, isolamento entre sessões e diagnóstico de underruns.
- Liberação do AudioTrack e da solicitação própria durante mídia/chamada conforme estratégia. Captura ativa visível impede liberação por mídia. Troca de perfil aguarda fim da captura.
- Modos VoIP fazem uma solicitação por ativação; callbacks e watchdog não a reafirmam quando Android retorna a normal. Chamada celular tem prioridade. A API pública não revela o proprietário de um modo VoIP igual ao solicitado pelo app; esse caso precisa de validação física nos modos 2/5.
- Rechecagem de oscilação em 300 ms, em vez de depender de três watchdogs de 15 s; UI passa a informar a incerteza imediatamente. Recuperação com orçamento limitado, sem reset apenas por reaparecer endpoint SCO.
- Correspondência de entrada exige endereço exato ou um único par sem endereço identificável. Ambiguidade não produz sucesso nem reseleção repetida enquanto seleção/canal estiverem ativos.
- Registro próprio de solicitação mesmo quando o dispositivo global já estiver selecionado; encerramento libera somente a solicitação do app.
- Notificação de desconexão atualizada e indicadores de entrada/saída descritos como disponíveis, sem inferir o microfone usado pelo WhatsApp.

## Matriz de experimentos

| Código | Variável em teste |
|---|---|
| `x_pro_test` | MODE_NORMAL, silêncio VOICE_COMMUNICATION 16 kHz, buffer 200 ms, pausa para mídia e retomada em 1,5 s |
| `standard` | Referência acima sem pausa para mídia |
| `mode_2` | MODE_IN_COMMUNICATION solicitado uma vez, com silêncio |
| `mode_3` | MODE_NORMAL, sem AudioTrack; controle da expiração por inatividade |
| `mode_4` | Silêncio com atributos de sonificação e saída Bluetooth preferida |
| `mode_5` | MODE_IN_COMMUNICATION solicitado uma vez, sem AudioTrack; comparação com estratégia antiga |
| `mode_6` | Silêncio em buffer estático de 1 s com loop; elimina thread produtora contínua |
| `mode_7` | PCM do AudioTrack em 8 kHz, sem afirmar mudança de codec Bluetooth |
| `mode_8` | Buffer de 500 ms e retomada após 2,5 s sem mídia |

São hipóteses comparáveis, não nove soluções comprovadas. Nenhum desses perfis mantém AudioRecord de fundo. O DSP pertence ao monitor local; não processa PCM da nota WhatsApp.

Os buffers de fluxo são mínimos solicitados: o tamanho efetivo é o maior entre esse valor e duas vezes `AudioTrack.getMinBufferSize`. O log `bufferBytes` informa o tamanho usado (no X Pro observado, 13.944 bytes a 16 kHz, cerca de 436 ms).

## Logs de vários dias

`AppLogger` grava em `cache/flight_recorder/`, até 7 dias ou 32 MiB. Segmentos diários de até 2 MiB, escrita assíncrona serializada, memória de 1.500 eventos e restauração de até 400 eventos ao reabrir. Crash handler grava diretamente no armazenamento; fila cheia gera contador explícito. Limpeza e exportação passam pela mesma fila de escrita.

Eventos possuem data/fuso, sessão, sequência, PID, tempo monotônico e perfil **aplicado**. Eventos úteis:

- `SESSION_START`, `PROFILE_SELECTED`, `PROFILE_PENDING`, `PROFILE_APPLIED`;
- `MODE_REQUEST`, `MODE_RELEASE`, `MODE_CHANGED`;
- SCO/HFP conectado/conectando/desconectado e dispositivos adicionados/removidos;
- `CAPTURE_STATE`: quantidade, entrada/tipo quando acessível, fonte, taxa, silenciamento e duração observada;
- `PLAYBACK_STATE`, `MEDIA_YIELD`, `CALL_YIELD`, `ROUTE_RESUME`;
- `SELECT`, `SELECT_TIMEOUT`, `CLEAR`, `ROUTE_RETRY`, alterações de estado;
- `ROUTE_HEARTBEAT` a cada 30 s: canal, keeper/underruns, capturas, volumes, bateria, tela, idle e economia;
- `USER_REPORT` e snapshot quando o usuário marca falha; `USER_VALIDATION` ao aprovar teste;
- exceções e erros de armazenamento/exportação.

**Exportar ZIP** inclui todos os segmentos retidos e o diagnóstico corrente; compartilha arquivo via URI temporária do FileProvider, sem enviar automaticamente. **Copiar** leva apenas eventos recentes. O Android pode limpar o cache, e o app não lê todo o logcat do sistema: clientes externos podem estar anonimizados. Um `inputType` observado não identifica automaticamente WhatsApp. Não há gravação de conteúdo de voz neste logger.

## Protocolo para alguns dias

1. Escolher um perfil e mantê-lo por um período; os logs registram início e fim da estratégia.
2. Gravar notas de 30–60 s, ouvir a reprodução e comparar origem do microfone com o telefone afastado do intercom.
3. Ao ouvir corte ou perceber microfone errado, abrir o BT Mic Pro e tocar **Marcar corte/falha agora**; a marca tem o horário da ação, que pode ser posterior ao defeito.
4. Comparar X Pro com modos 6/8, depois as demais hipóteses conforme resultado. Incluir tela apagada e reconexão Bluetooth.
5. Exportar ZIP ao final do período. Analisar capturas sobrepostas às quedas, frequência por modo/tempo de uso, gaps entre sessões, underruns e marcas manuais. Um período sem captura não serve como prova de microfone funcionando.

## Verificação e pendências

- `testDebugUnitTest lintDebug assembleDebug`: aprovado; **42 testes sem falhas**, **Lint 0 erros/93 avisos**. APK instalado por atualização preservando dados e perfil; resultados de observação no aparelho registrados no histórico.
- KingKong X Pro, perfil experimental, tela apagada: dumps em t=0/12/40 s com `mPlaybackActive=true` e SCO KT-1 preferido. Heartbeat persistido com zero underruns/quedas/desconexões, MODE_NORMAL e sem captura ativa. Confirma sustentação nessa janela; não comprova a entrada de uma nota WhatsApp.
- Testes novos: persistência/exportação além do limite da memória e entre dias/reabertura, rotação por tamanho/idade, recriação do cache, escrita concorrente de crash/worker, precedência de chamada e entrada ambígua.
- Permanecem necessárias amostras reais e validação prolongada de captura/reprodução de cada perfil, chamadas, bateria, Android legado e LE Audio.
- Achados antigos de retorno/DSP, efeitos globais, sobreposição e acessibilidade não equivalem à causa do corte WhatsApp. Controles de retorno/DSP ainda têm fluxo de UI incompleto; MediaBooster depende de efeitos na sessão global; execução prolongada do overlay precisa de teste. As auditorias de 06/09 foram consolidadas aqui para manter os dois relatórios mais recentes; suas descrições históricas continuam no histórico do projeto.

## Repositório e ferramentas

MCP oficial GitHub v1.12.2 instalado e conectado no OpenCode V2 deste ambiente. Remoto `https://github.com/Caas2023/BTMicPro.git`; credencial armazenada fora do projeto. Artefatos de depuração, backups ADB e logs brutos ficam fora do histórico Git. Entrega local em `APK/BTMicPro_v1.5.11.apk`, preservando a 1.5.9 para retorno.

Código v1.5.11 publicado na branch `main`, commit `7723cef`; push confirmado em 29/09/2026.
