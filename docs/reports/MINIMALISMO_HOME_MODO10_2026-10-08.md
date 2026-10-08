# Minimalismo da home, páginas de configuração e Modo 10 — v1.5.13

**Data:** 08/10/2026 · **Versão:** 1.5.13, código 31 · **Base preservada:** Modo 8 validado pelo usuário.

## Decisões

- **Modo 8 não foi alterado.** Continua sendo a referência estável: silêncio de voz 16 kHz, buffer 500 ms, retomada após 2,5 s e liberação para mídia sem captura ativa.
- **Modo 9 permanece Eco.** Prioriza bateria com a mesma base de áudio do Modo 8.
- **Modo 10 (Escuta rápida)** foi criado para reduzir o atraso ao começar a ouvir áudio recebido: mantém áudio/buffer do Modo 8, verifica a liberação para mídia a cada 250 ms e retoma o microfone após 1,2 s sem mídia.

## Mudanças de interface

1. Removido da home o card irrelevante “Sistema em espera / Rota preparada”.
2. Volume duplo unificado em **uma barra única sempre visível**; o app ajusta mídia e chamada/intercom proporcionalmente.
3. Seletor dos modos movido para a tela inicial, abaixo do volume.
4. Configurações reorganizadas em páginas separadas, sem scroll geral: Logs, Diagnóstico, Áudio local e Sistema.
5. Notificação do serviço usa ícone pequeno próprio (`ic_notification_btmic`) em vez do launcher quadrado; por regra do Android ele é monocromático na barra superior.
6. Fluxo do botão flutuante não abre mais a tela de sobreposição automaticamente; sem permissão, ele fica desligado e apenas mostra aviso.

## Validação

- `./gradlew testDebugUnitTest lintDebug assembleDebug --no-daemon`: BUILD SUCCESSFUL.
- 44 testes, 0 falhas.
- Lint: 0 erros, 95 avisos existentes.
- APK gerado: `APK/BTMicPro_v1.5.13.apk`, SHA-256 `58474891b09ecbcc072f7bb966405a6a453a20a8a7d7e63ee2e48f3c7ba19cad`.
- Assinatura igual à v1.5.12; atualização preserva dados.

## Protocolo de teste sugerido

1. Manter o Modo 8 como retorno seguro.
2. Testar o Modo 10 com áudio recebido no WhatsApp: tocar áudio curto e longo, alternar gravação/reprodução e marcar qualquer falha.
3. Se o Modo 10 escutar mais rápido sem cortar, ele vira candidato recomendado; se houver instabilidade, voltar ao Modo 8.
