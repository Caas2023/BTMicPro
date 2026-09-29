# AGENTS.md — BT Mic Pro

Instruções obrigatórias para qualquer LLM/agente que modificar este projeto.

## 1. APK de teste é entrega obrigatória
- Após **qualquer mudança de código**, gerar o APK: `.\gradlew.bat assembleDebug` e copiar `app\build\outputs\apk\debug\app-debug.apk` para `APK\BTMicPro_v<VERSION>.apk` (versão = `versionName` em `app/build.gradle.kts`).
- Regras completas: `.agents/rules/apk_management.md`. Nunca deixar APK solto na raiz. Manter no máximo 2 APKs (atual + anterior).

## 2. Histórico de modificações é obrigatório
- Registrar **toda modificação** em `docs/HISTORICO_E_STATUS.md` (data + descrição + arquivos + status).
- Relatórios novos vão em `docs/reports/ASSUNTO_AAAA-MM-DD.md`; manter só os 2 mais recentes.

## 3. Contexto do projeto
- App Android nativo (Kotlin + Compose): roteia o microfone do intercomunicador Bluetooth para o WhatsApp via `setCommunicationDevice()` (API 31+) / SCO legado.
- Guia de áudio: `.agents/skills/android-audio-sound-expert/SKILL.md`.
- Restrição crítica: o app NÃO processa o áudio do WhatsApp (só prepara a rota); `VoiceProcessingEngine` afeta apenas o teste local.
- Pastas de toolchain local (`android_sdk/`, `jdk-17.0.2/`, `gradle-8.9/`, `.gradle/`) NÃO devem ser apagadas — o build offline depende delas.
