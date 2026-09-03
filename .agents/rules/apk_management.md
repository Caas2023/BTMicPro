# 📦 Regra Obrigatória de Armazenamento e Nomenclatura de APKs

## 🎯 Objetivo
Padronizar e automatizar a organização de todos os executáveis Android (.apk) gerados durante o ciclo de desenvolvimento e build do projeto.

---

## 📜 Regras de Operação

### 1. Verificação e Auto-Criação da Pasta `APK`
- **Checagem Inicial:** Antes de salvar qualquer build de APK, verificar se o diretório `APK/` existe na raiz do workspace.
- **Auto-Criação Obrigatória:** Se a pasta `APK/` NÃO existir, ela **DEVE SER CRIADA AUTOMATICAMENTE**:
  ```powershell
  New-Item -ItemType Directory -Force -Path 'APK'
  ```
- **Se já existir:** Basta salvar o novo APK diretamente dentro da pasta `APK/`.

### 2. Padrão Estrito de Nomenclatura dos APKs
- **Proibido salvar APKs soltos na raiz** do projeto.
- Todo APK gerado deve seguir rigorosamente a convenção:
  ```
  APK/[NomeDoApp]_v[NumeroDaVersao].apk
  ```
  *(Opcionalmente, pode ser mantida uma cópia `APK/[NomeDoApp]_latest.apk` para links rápidos).*
- **Extração da Versão:** O número da versão deve ser consultado em `app/build.gradle.kts` (ou `build.gradle`), capturando o campo `versionName` (ex: `1.5.0` -> `BTMicPro_v1.5.0.apk`).

### 3. Exemplo Prático de Fluxo de Build e Cópia:
```powershell
# 1. Compilar
.\gradlew.bat assembleDebug

# 2. Garantir pasta APK
New-Item -ItemType Directory -Force -Path 'APK' | Out-Null

# 3. Copiar com versão
Copy-Item 'app\build\outputs\apk\debug\app-debug.apk' -Destination 'APK\BTMicPro_v1.5.0.apk' -Force
```
