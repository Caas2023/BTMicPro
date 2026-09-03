# 🛡️ Relatório de Auditoria Completa & Mapa Arquitetural do Sistema
**Projeto:** BT Mic Pro (Intercomunicador Bluetooth para Motociclistas)  
**Data:** 03/09/2026 — Horário de Brasília  
**Ferramenta de Grafo:** Graphify v0.5.x (AST Analyzer + Community Detection)

---

## 🗺️ 1. Mapa Arquitetural do Projeto (Graphify)

O Graphify executou a análise estática avançada (AST) sobre a totalidade do código-fonte, mapeando entidades, fluxos de chamada, interfaces e comunidades funcionais:

- **Total de Nós Mapeados:** 381 entidades
- **Arestas de Conexão:** 661 relacionamentos
- **Comunidades Detectadas:** 24 clusters coesos
- **Ciclos de Importação:** **0 detectados** (Arquitetura limpa e estritamente acíclica)
- **Artefatos Visuais Gerados:**
  - 🌐 [graph.html](file:///d:/aplicativo%20intercominicador/graphify-out/graph.html) (Visualizador 3D/2D Interativo completo, sem necessidade de servidor)
  - 🔄 [callflow.html](file:///d:/aplicativo%20intercominicador/graphify-out/aplicativo-intercominicador-callflow.html) (Diagramas Mermaid interativos de fluxo de rotas)
  - 🌳 [GRAPH_TREE.html](file:///d:/aplicativo%20intercominicador/graphify-out/GRAPH_TREE.html) (Árvore hierárquica D3 v7)
  - 📄 [GRAPH_REPORT.md](file:///d:/aplicativo%20intercominicador/graphify-out/GRAPH_REPORT.md) (Relatório textual com métricas e comunidades)

---

## 👑 2. Mapeamento de "God Nodes" (Nós Centrais do Sistema)

Identificação dos componentes com maior grau de acoplamento e centralidade:

| Rank | Componente / Classe | Arestas | Função Crítica no Sistema |
|:---:|:---|:---:|:---|
| 1 | `MainViewModel` | **51** | Orquestrador principal da UI, estados de serviço e controles. |
| 2 | `RouterState` | **33** | Máquina de estados central de 13 estágios da rota Bluetooth. |
| 3 | `BluetoothRoutingEngine` | **25** | Autoridade de baixo nível com o hardware e `AudioManager`. |
| 4 | `BluetoothAudioRouter` | **17** | Wrapper e ponte de compatibilidade com a engine de roteamento. |
| 5 | `RiderAudioPreset` | **17** | Perfis DSP de pilotagem (Vento Extremo, Rodovia, Cidade, etc.). |
| 6 | `BtMicService` | **17** | Foreground Service com tipos `microphone` e `connectedDevice`. |
| 7 | `MediaBooster` | **16** | Módulo de ganho dinâmico de áudio e inteligibilidade. |
| 8 | `FloatingButtonService` | **15** | Serviço da janela de sobreposição flutuante em tempo real. |
| 9 | `LiveAudioMonitor` | **15** | Pipeline de captura e reprodução de áudio sidetone. |
| 10 | `DualVolumeManager` | **14** | Gerenciador anti-eco de volumes de Mídia e Chamada. |

---

## 🔍 3. Auditoria Técnica Aprofundada

### 3.1. Roteamento Bluetooth & Compatibilidade Android 12 a 15
- **Diagnóstico:** A engine utiliza corretamente a API `setCommunicationDevice` (API 31+) com fallback para `startBluetoothSco` (API 26-30).
- **Status de Conflito com Terceiros:** ✅ **BLINDADO**. O modo de áudio foi mantido estritamente em `AudioManager.MODE_NORMAL`. Isso eliminou por completo o erro fatal do WhatsApp (*"Não é possível gravar áudio durante chamada telefônica"*), permitindo que mensagens de voz e chamadas funcionem simultaneamente sem bloqueio de hardware.
- **Sincronização de Estados:** ✅ **SINCRONIZADO**. A comunicação entre a tela principal, o serviço `BtMicService` e o botão sobreposto `FloatingButtonService` utiliza o `RouterStateHolder` como fonte única da verdade, reagindo de forma instantânea.

### 3.2. Estabilidade dos Controles de Volume
- **Diagnóstico:** O loop de feedback mecânico ("volumes mexendo sozinhos") foi extinto através de:
  - Janela de debounce/anti-eco de 800ms (`lastProgrammaticChangeTime`).
  - Isolamento independente dos canais de Mídia (WhatsApp/GPS/Música) e Chamada (Intercomunicador/Telefone).
  - Estado local reativo no Jetpack Compose com conversão inteira estável.

### 3.3. Motor DSP e Performance de Áudio
- **Diagnóstico:** O app opera em **Zero-GC** (sem alocações contínuas de memória no loop de áudio).
- **Padrão de Fábrica:**
  - Volume de Mídia e Chamada em **100%** (máximo).
  - Preset **Vento Extremo (`EXTREME_WIND`)** ativo por padrão.
  - Redutor de ruído em **100% (1.0)**.
  - Retorno da própria voz mantido em **0% (Mudo)**, prevenindo eco no capacete e liberando os alto-falantes.

### 3.4. Análise de Compilação e Lints
- `gradlew.bat test` e `gradlew.bat lintDebug` concluídos com **`BUILD SUCCESSFUL`**.
- Ausência de leaks em BroadcastReceivers.

---

## 🚀 4. Relatório de Melhorias Recomendadas (Roadmap de Evolução)

Com base na auditoria do Grafo de Dependências e nas boas práticas de engenharia Android, elencamos as 5 principais oportunidades de aprimoramento:

### 💡 1. Refatoração e Decomposição do `MainViewModel` (God Node #1)
- **Problema:** Com 51 arestas conectadas, o `MainViewModel` acumula muitas responsabilidades (diagnósticos, logs do Flight Recorder, controle de volume, sincronização do botão flutuante e configurações DSP).
- **Proposta:** Adotar o padrão de ViewModels especializados ou delegados:
  - `AudioRoutingViewModel` (estado da rota, botão liga/desliga, hardware)
  - `VolumeControlViewModel` (mídia, chamada, debounce)
  - `FlightRecorderViewModel` (logs, compartilhamento, diagnósticos)
- **Benefício:** Redução de complexidade, testes unitários mais simples e menor acoplamento na UI.

### 💡 2. Eliminação do Wrapper `BluetoothAudioRouter` (God Node #4)
- **Problema:** O `BluetoothAudioRouter` atua majoritariamente como repassador de chamadas para `BluetoothRoutingEngine`.
- **Proposta:** Injetar e consumir `BluetoothRoutingEngine` diretamente nos pontos necessários, descontinuando o wrapper intermediário.
- **Benefício:** Redução de 17 arestas e camadas desnecessárias de indireção.

### 💡 3. Otimização Nativa para Dispositivos LE Audio (API 33+ / Android 13-15)
- **Problema:** Intercomunicadores topo de linha (Cardo Packtalk Edge, Sena 50S/60S e novos Wayxin) já começam a adotar Bluetooth Low Energy Audio (LE Audio / LC3).
- **Proposta:** Expandir a detecção em `DeviceCompatibilityManager.kt` para reconhecer `AudioDeviceInfo.TYPE_BLE_HEADSET` e `TYPE_BLE_SPEAKER`, priorizando codecs de altíssima fidelidade e baixíssima latência.
- **Benefício:** Áudio cristalino de 32kHz a 48kHz em intercomunicadores com suporte a LE Audio.

### 💡 4. Ajuste Inteligente Baseado na Velocidade (Speed-Sensitive Boost)
- **Problema:** O piloto na cidade precisa de menos redução de ruído que na rodovia a 120 km/h.
- **Proposta:** Adicionar modo opcional via sensor de velocidade (GPS / FusedLocationProvider):
  - 0 a 40 km/h: Modo Cidade (DSP suave, preserva ambientação).
  - 40 a 80 km/h: Modo Rodovia (Supressão moderada).
  - Acima de 80 km/h: Modo Vento Extremo (Supressão 100% e ganho máximo).
- **Benefício:** Experiência totalmente automática sem o motociclista precisar tirar a mão do guidão.

### 💡 5. Persistência Reativa dos Ajustes via Jetpack DataStore
- **Problema:** O app já memoriza as preferências em `SharedPreferences`, mas a leitura pode ser sincronizada de forma puramente assíncrona.
- **Proposta:** Migrar `DualVolumeManager` e `MainViewModel` para `androidx.datastore.preferences`, expondo Flows nativos.
- **Benefício:** Elimina qualquer chance de I/O em main thread e garante integridade em caso de encerramento súbito do processo.

---

## 📊 5. Conclusão da Auditoria
O sistema BT Mic Pro encontra-se em estado **robusto, estável e pronto para uso operacional**. Os gargalos históricos (erro do WhatsApp, botão flutuante dessincronizado e oscilação de volume) foram completamente superados. A geração do grafo pelo Graphify confirma que o projeto não possui acoplamentos cíclicos, apresentando uma base sólida para as próximas evoluções.
