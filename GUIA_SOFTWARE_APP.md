# 📘 Guia Completo de Software e Arquitetura do Aplicativo
**Projeto:** Qualidade de Pavimento (Pavement Quality)  
**Disciplina:** GAT108 — Automação Avançada (2026/2) — UFLA  
**Discente:** Arthur Amaral Públio de Souza  
**Hardware Alvo:** Samsung Galaxy Tab S9 (Nó de Borda 100% Offline)  

---

## 📑 Sumário Executivo
Este guia documenta detalhadamente a arquitetura de software, o papel de cada arquivo do projeto, os conceitos teóricos essenciais de Sistemas Embarcados e Concorrência, e um roteiro prático para a arguição com a banca avaliadora da **Avaliação 1 (AV1)**.

---

## 1. Visão Geral da Arquitetura e Fluxo de Dados

O aplicativo adota princípios de **Clean Architecture** e **Design Orientado a Contratos**, rodando **100% localmente no dispositivo (Edge Computing)**, sem qualquer chamada de rede externa:

```
[ Acelerômetro (20 Hz) ]       [ GPS Puro / Satélites (1 Hz) ]
          │                                  │
          ▼                                  ▼
 ┌──────────────────────────────────────────────────────────────┐
 │                HardwareSensorManager.kt                      │
 │ - Registra SensorEventListener e LocationListener            │
 │ - Filtro de deadband (ignora jitter com carro parado)        │
 │ - Monitoramento de satélites com GnssStatus.Callback         │
 └──────────────┬────────────────────────────┬──────────────────┘
                │ StateFlow<VibrationSample> │ StateFlow<KinematicFix>
                ▼                            ▼
 ┌──────────────────────────────────────────────────────────────┐
 │                          Engine.kt                           │
 │ - Orquestra a coleta assíncrona (Dispatchers.Default)        │
 │ - Valida frescor temporal do GPS (< 5 segundos)              │
 └──────┬──────────────────────┬────────────────────────┬───────┘
        │                      │                        │
        ▼                      ▼                        ▼
┌──────────────┐     ┌───────────────────┐    ┌──────────────────┐
│TrackRecord.kt│     │   Reconciler.kt   │    │ LogRecorder.kt   │
│Buffer        │     │ Mínimos Quadrados │    │ Gravação em CSV  │
│Circular      │     │ Ponderados (MQP)  │    │ para ensaios     │
│Memória O(1)  │     │ Teste Chi-Quadrado│    │ reprodutíveis    │
└──────────────┘     └─────────┬─────────┘    └──────────────────┘
                               │ StateFlow<ReconciledSample>
                               ▼
            ┌─────────────────────────────────────────┐
            │             MainActivity.kt             │
            │ DashboardScreen reativa Jetpack Compose │
            └─────────────────────────────────────────┘
```

---

## 2. Dicionário Completo de Arquivos (Arquivo por Arquivo)

### 📂 Camada de Domínio (`app/src/main/java/com/example/pavementquality/domain/`)

#### 📄 `Models.kt`
* **O que faz:** Define os modelos fundamentais de dados do sistema (`KinematicFix` e `VibrationSample`).
* **Propriedades-Chave:**
  * `KinematicFix`: `latitude`, `longitude`, `speedMetersPerSecond`, `speedAccuracyMetersPerSecond`, `timestampMs`.
  * `VibrationSample`: `zAcceleration`, `accuracy`, `timestampMs`.
* **Conceito de Software:** **Imutabilidade e Design por Contrato**. No bloco `init`, qualquer valor impossível (como velocidade negativa, acurácia menor ou igual a zero, ou coordenadas fora dos limites geográficos) lança uma `DomainConstraintException` imediatamente (*fail-fast*).
* **Por que é assim:** Impede que dados corruptos ou corrompidos por falhas de hardware se propaguem pelo restante do código.

#### 📄 `Exceptions.kt`
* **O que faz:** Define a exceção de domínio personalizada `DomainConstraintException`.
* **Conceito de Software:** Segregação de erros de domínio. Permite distinguir falhas de regras de negócio (dados inválidos) de exceções de infraestrutura (falta de permissão, disco cheio).

#### 📄 `Reconciler.kt`
* **O que faz:** É o coração matemático da **Capacidade 2**. Executa a Reconciliação de Dados por **Mínimos Quadrados Ponderados (MQP)** e o **Teste de Erro Grosseiro**.
* **Modelo Físico:** *f*(*y*) = *a<sub>z</sub>* − *k* · *v* = 0 (o impacto vertical é proporcional à velocidade horizontal, com *k* = 0,5).
* **Passo a passo matemático:**
  1. Mapeia a acurácia dos sensores em variância (*σ*²<sub>*az*</sub> e *σ*²<sub>*v*</sub>).
  2. Calcula o resíduo: *r* = *y<sub>az</sub>* − (*k* · *y<sub>v</sub>*).
  3. Calcula a variância do resíduo: *V<sub>r</sub>* = *σ*²<sub>*az*</sub> + *k*² · *σ*²<sub>*v*</sub>.
  4. Calcula o resíduo normalizado global: *γ* = *r*² / *V<sub>r</sub>*.
  5. Compara com o limiar crítico da distribuição Qui-Quadrado (*χ*² = 3,84 para 95% de confiança).
  6. Se *γ* > 3,84, acusa **Erro Grosseiro** (Buraco ou Anomalia).
* **Conceito de Software:** Métodos determinísticos e puros (`@Synchronized`), garantindo reproducibilidade matemática sem efeitos colaterais.

#### 📄 `TrackRecord.kt`
* **O que faz:** Implementa o **Buffer Circular de Memória Constante** da **Capacidade 3**.
* **Como funciona:**
  * Usa um `ArrayDeque<VibrationSample>(capacity)`.
  * Quando a capacidade é atingida (ex.: 500 amostras), a amostra mais antiga é removida (`removeFirst()`) antes de adicionar a nova (`addLast()`). Isso garante complexidade de espaço *O*(1) e tempo *O*(1).
* **Conceito Crítico de Concorrência:**
  * **Exclusão Mútua:** Todos os métodos são `@Synchronized` no monitor da instância.
  * **Cópias Defensivas:** Os métodos `getAllSamplesDefensiveCopy()` e `getSamplesInWindow()` nunca retornam a referência interna da lista. Eles usam `.toList()` e `.filter()`, que alocam uma nova coleção imutável. Assim, a thread leitora pode iterar tranquilamente sem perigo de sofrer `ConcurrentModificationException` enquanto as threads produtoras continuam inserindo dados a 20 Hz.

#### 📄 `Engine.kt`
* **O que faz:** Orquestra o ciclo de vida e a integração entre a captura de hardware, a concorrência e a matemática.
* **Comportamento:**
  * Inicia a coleta no `Dispatchers.Default` (pool de threads de background para processamento intensivo de CPU).
  * Sempre adiciona a vibração ao `TrackRecord`.
  * Verifica se o fix do GPS é recente (&lt; 5 segundos). Se o sinal foi perdido em um túnel, o sistema interrompe temporariamente a reconciliação para evitar usar uma velocidade congelada antiga.
  * Expõe o estado via `StateFlow` (`latestReconciliation`, `isRecording`, `lastLogMessage`).

---

### 📂 Camada de Dados e Infraestrutura (`app/src/main/java/com/example/pavementquality/data/`)

#### 📄 `HardwareSensorManager.kt`
* **O que faz:** Gerencia o acesso ao acelerômetro e ao receptor GNSS nativo do tablet, otimizado para operação **100% offline**.
* **Destaques de Implementação:**
  * **Isolamento de Provedores:** Usa exclusivamente o `LocationManager.GPS_PROVIDER` (satélites diretos). Evita o `NETWORK_PROVIDER` que, sem internet, gera saltos de localização e velocidades espúrias de 1800 km/h.
  * **Preservação de Velocidade Doppler:** Dá prioridade máxima a `location.speed` (velocidade nativa do chip GPS por desvio Doppler). Só recorre ao cálculo manual de Δ*d* / Δ*t* se `!location.hasSpeed()`.
  * **Filtro de Zona Morta (Deadband):** Deslocamentos menores que a incerteza do GPS enquanto parado são zerados (0 km/h), eliminando o jitter fantasma de semáforo.
  * **Tempo Monotônico:** Usa `location.elapsedRealtimeNanos` em vez de `location.time` para deltas de tempo imunes a correções do relógio UTC.
  * **GnssStatus.Callback:** Escuta o status dos satélites no Android (visíveis vs. usados na fixação 3D) e expõe para a interface.

#### 📄 `LogRecorder.kt`
* **O que faz:** Persiste as leituras em arquivos CSV na pasta pública `Downloads` do dispositivo (via `MediaStore.Downloads`), permitindo acesso direto pelo app "Meus Arquivos".
* **Formato do CSV:** `timestamp, z_accel, accuracy, lat, lon, speed, speed_acc`
* **Importância:** Fornece os datasets para o **Ensaio Reprodutível (Capacidade 5)** e a **Validação Cega (Capacidade 10)**.

---

### 📂 Camada de Serviço e UI (`app/src/main/java/com/example/pavementquality/`)

#### 📄 `AcquisitionService.kt` (`service/`)
* **O que faz:** Implementa o `Foreground Service` do Android com notificação persistente e `PowerManager.PARTIAL_WAKE_LOCK`.
* **Por que existe:** No Android moderno (e especialmente na personalização One UI da Samsung do Tab S9), o sistema operacional encerra qualquer processo que consome sensores em background após poucos segundos. O Foreground Service associado ao WakeLock garante amostragem contínua por 30+ minutos com a tela bloqueada.

#### 📄 `MainActivity.kt`
* **O que faz:** Ponto de entrada da aplicação.
* **Componentes:**
  * Gerencia a solicitação dinâmica de permissões em tempo de execução (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` e `POST_NOTIFICATIONS` para Android 13+).
  * Renderiza o `DashboardScreen` em Jetpack Compose:
    * Botões de LIGAR/PARAR o serviço e GRAVAR TRAJETO.
    * Card do Acelerômetro com valor em tempo real e qualidade do sensor.
    * Card de Velocidade GPS com velocidade em km/h, coordenadas e diagnóstico em tempo real de satélites GNSS.
    * Card do "Status da Via" com alerta visual (Verde para Asfalto Normal, Vermelho para Buraco Detectado) e valor do $\chi^2$.

#### 📄 `PavementApp.kt`
* **O que faz:** Subclasse de `Application` que inicializa o grafo de dependências na inicialização do app (Singletons de `HardwareSensorManager`, `TrackRecord`, `Reconciler`, `LogRecorder` e `Engine`) e cria o canal de notificação (`NotificationChannel`).

#### 📄 `AndroidManifest.xml`
* **O que faz:** Declara as permissões críticas exigidas pelo Android para o projeto:
  * `ACCESS_FINE_LOCATION` e `ACCESS_COARSE_LOCATION`: Localização precisa via chip GNSS.
  * `FOREGROUND_SERVICE` e `FOREGROUND_SERVICE_LOCATION`: Permissão do Android 14+ para rodar serviços de localização em background.
  * `WAKE_LOCK`: Permissão para manter a CPU rodando com tela apagada.
  * `POST_NOTIFICATIONS`: Exibição da notificação persistente obrigatória para Foreground Services no Android 13+.

---

### 📂 Suíte de Testes Automatizados (`app/src/test/`)

#### 📄 `ModelsTest.kt`
* Valida as restrições e contratos do domínio: velocidade negativa é rejeitada, precisão nula/negativa é rejeitada, buffer circular descarta os mais antigos mantendo tamanho fixo.

#### 📄 `ReconcilerTest.kt`
* Valida a matemática do MQP:
  * Asfalto normal satisfaz a equação $a_z - k \cdot v = 0$ após reconciliação com tolerância de 0.001.
  * Injeção de impacto forte em alta velocidade eleva o resíduo normalizado acima de 3.84, disparando erro grosseiro.
  * Ruído em velocidade zero viola a física do modelo e também acusa violação.

#### 📄 `TrackRecordConcurrencyTest.kt`
* Ensaio de estresse concorrente rodando **10 repetições**:
  * 10 threads escritoras inserindo 10.000 amostras simultaneamente.
  * 2 threads leitoras requisitando e iterando cópias defensivas em paralelo.
  * Comprova 0 exceções de concorrência e respeito estrito ao limite de memória.

---

## 3. Conceitos de Software Cruciais para Estar por Dentro

Se a banca fizer perguntas teóricas de software ou sistemas embarcados, aqui estão os conceitos e termos exatos que você deve dominar:

### A. Concorrência e Threads
1. **Região Crítica (*Critical Section*):** Trecho de código que acessa um recurso compartilhado mutável (no nosso caso, o `ArrayDeque` do `TrackRecord`). Se duas threads entrarem ao mesmo tempo, os ponteiros internos da lista quebram.
2. **Exclusão Mútua (*Mutual Exclusion / Mutex*):** A garantia de que apenas uma thread por vez executa a região crítica. No Kotlin/Java, a anotação `@Synchronized` usa o monitor intrínseco do objeto para travar a entrada de outras threads até a conclusão.
3. **Cópia Defensiva (*Defensive Copy*):** Prática de segurança onde um método retorna uma nova cópia dos dados em vez da referência interna. Se retornássemos a lista original, outra thread poderia modificar os itens enquanto o consumidor estivesse lendo, disparando `ConcurrentModificationException`.
4. **Deadlock (Impasse):** Situação em que duas threads ficam travadas esperando que a outra libere um recurso. Evitamos deadlocks mantendo os métodos sincronizados curtos e sem chamadas aninhadas a outros locks.

### B. Sistemas Operacionais Móveis e Background
1. **Doze Mode (Modo de Suspensão Profunda):** Mecanismo de economia de bateria introduzido no Android que corta acesso a sensores e rede quando a tela é desligada.
2. **Foreground Service:** Tipo especial de serviço no Android com prioridade máxima na tabela do *OOM Killer* (Out-Of-Memory Killer), acompanhado obrigatoriamente de uma notificação visível na barra de status. O sistema não mata esse serviço para economizar memória.
3. **Partial WakeLock:** Trava de hardware que avisa o kernel do Linux para manter o processador (CPU) acordado mesmo com a tela e a GPU desligadas.

### C. Sensores e GNSS Offline
1. **A-GPS (*Assisted GPS*) vs. GNSS Puro:** Com internet, o tablet baixa em 2 segundos a posição dos satélites (efemérides). Em modo offline, o tablet precisa demodular as efemérides diretamente do sinal de rádio dos satélites a 50 bps, levando entre 30 e 90 segundos em céu aberto.
2. **Velocidade por Efeito Doppler:** O chip de satélite mede a velocidade analisando o desvio na frequência da onda de rádio emitida pelo satélite em movimento. É ordens de grandeza mais precisa e estável do que calcular a distância entre duas posições (Δ*d* / Δ*t*).
3. **Tempo Monotônico (`elapsedRealtimeNanos`):** Um contador de nanossegundos que só avança a partir do boot da CPU. Diferente do `System.currentTimeMillis()` (relógio de parede), ele não sofre atrasos, adiantamentos ou correções de fuso horário.

### D. Reconciliação de Dados e MQP
1. **Por que reconciliar dados?** Sensores reais têm ruído e incerteza. A reconciliação usa leis físicas conhecidas do sistema para ajustar as medições de forma ótima, minimizando a soma ponderada dos quadrados dos erros.
2. **Ponderação por Variância:** Leituras de sensores mais precisos (menor *σ*²) sofrem menos correção; sensores ruidosos sofrem mais correção.
3. **Detecção de Erro Grosseiro via Chi-Quadrado:** O resíduo normalizado *γ* = *r*² / *V<sub>r</sub>* segue uma distribuição *χ*² com 1 grau de liberdade. Se *γ* &gt; 3,84, a probabilidade daquilo ser apenas ruído normal do sensor é menor que 5%, provando estatisticamente que houve um evento anômalo (buraco na pista).

---

## 4. Guia de Arguição da Banca (Possíveis Perguntas e Respostas)

| Pergunta da Banca | Resposta Técnica Recomendada |
| :--- | :--- |
| **"Como você garante que o app não trava a memória com o passar das horas?"** | *"O `TrackRecord` utiliza um buffer circular com capacidade estrita de 500 amostras (*O*(1) de memória). Quando o limite é atingido, a amostra mais antiga é descartada com `removeFirst()`. O garbage collector não sofre pressão contínua e a memória é finita e constante."* |
| **"Por que não usar um ArrayList simples para guardar as medições?"** | *"O `ArrayList` não é thread-safe. Se o sensor registrar uma leitura a 20 Hz enquanto a Engine ou a UI estiver iterando a lista, o Android lança `ConcurrentModificationException`. Além disso, o ArrayList cresceria infinitamente, causando OutOfMemory."* |
| **"Por que o MQP usa a acurácia reportada pelo próprio Android?"** | *"Para respeitar a matriz de covariância dinâmica. O desvio padrão da velocidade do GPS (*σ<sub>v</sub>*) varia conforme a quantidade de satélites, e o acelerômetro reporta estados HIGH, MEDIUM ou LOW. O MQP pondera o resíduo com base na incerteza real de cada instante."* |
| **"O que acontece se o carro passar por um túnel e perder o GPS?"** | *"Na `Engine.kt`, validamos o frescor temporal do fix (&lt; 5 segundos). Se o sinal for perdido, a reconciliação é pausada para não comparar vibrações novas com uma velocidade congelada antiga, evitando falsos positivos."* |
| **"Por que o app funciona 100% offline se o GPS precisa de internet?"** | *"O receptor GNSS do Tab S9 funciona por rádio com a constelação de satélites (GPS, Galileo, GLONASS). A internet só serve para acelerar o fix inicial (A-GPS). Sem internet, ele faz o download das efemérides diretamente pelo sinal de rádio em céu aberto."* |
| **"Como você provou que não há condições de corrida?"** | *"Implementamos o `TrackRecordConcurrencyTest.kt`, que roda 10 repetições automáticas submetendo o buffer a 10 threads de escrita e 2 de leitura concorrentes, validando zero falhas e cópias defensivas íntegras."* |

---

## 5. Como Abrir as Apresentações no Navegador

Na pasta `apresentacao/` do projeto, você tem dois arquivos HTML prontos para abrir em qualquer navegador (Google Chrome, Edge, Firefox):

1. **`apresentacao/apresentacao_av1.html`** *(Recomendado para a banca da AV1)*:
   * 10 slides completos com navegação por setas (<kbd>←</kbd> <kbd>→</kbd> ou <kbd>Espaço</kbd>), design profissional, formulação matemática do MQP, gráficos em texto, diagramas de arquitetura e tabela de rastreabilidade das 5 capacidades.
2. **`apresentacao/apresentacao_proposta.html`**:
   * A apresentação da proposta inicial de agosto (5 slides).
