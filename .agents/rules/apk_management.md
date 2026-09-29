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

### 4. Build obrigatório após qualquer mudança de código
- **Toda vez que modificar código, GERAR o APK em seguida** para o usuário testar: `assembleDebug` + cópia para `APK/` com o `versionName` atual.
- Se mudar `versionName`/`versionCode` em `app/build.gradle.kts`, o nome do APK deve acompanhar.
- Manter no máximo 2 APKs na pasta (o atual + o anterior); apagar duplicados antigos.

### 5. Histórico obrigatório de modificações
- **Toda modificação DEVE ser registrada** em `docs/HISTORICO_E_STATUS.md` com data, descrição, arquivos afetados e status, no mesmo formato das entradas existentes.
- Relatórios de auditoria/melhoria vão em `docs/reports/` com nome `ASSUNTO_AAAA-MM-DD.md`; manter só os 2 mais recentes + o histórico.
