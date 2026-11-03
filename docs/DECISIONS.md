# Registro de Decisões de Arquitetura e Engenharia (docs/DECISIONS.md)
**Disciplina:** GAT108 — Automação Avançada (2026/2)  
**Projeto:** Fragmented Learning — Qualidade de Pavimento em Nó Ciberfísico de Borda  
**Discente:** Arthur Amaral Públio de Souza (`arthur.souza6@estudante.ufla.br`)  

Conforme a subseção **6.10** das diretrizes práticas da disciplina, este arquivo documenta de forma estruturada as decisões de arquitetura e engenharia tomadas ao longo do projeto.

---

## D-01 — Modelo de Domínio e Invariantes Imediatas (Fail-Fast)
- **Data:** 08/09/2026
- **Decisão:** Tipos imutáveis (`KinematicFix`, `VibrationSample`) implementados como `data class` em Kotlin com validações contratuais invariantes diretamente no bloco `init`.
- **Alternativas consideradas:** Classes mutáveis com *setters*; validações externas em serviços de aplicação; deixar validação a cargo dos consumidores.
- **Motivo:** Impede que amostras com física corrompida (ex.: velocidade negativa $v < 0$, acurácias $\le 0$ ou coordenadas fora de $[-90, 90]^\circ$) transitem pelo sistema. O erro é interceptado na fronteira de entrada.
- **Consequências:** Qualquer leitura espúria do hardware lança `IllegalArgumentException` imediatamente, simplificando os testes unitários (`ModelsTest.kt`) e garantindo que o buffer e o reconciliador só operem sobre dados válidos.
- **Decidido por:** Arthur Amaral Públio de Souza, em 08/09/2026.

---

## D-02 — Modelo de Acoplamento Físico e Reconciliação por MQP
- **Data:** 15/09/2026
- **Decisão:** Adoção da restrição física linear de suspensão $f(y) = a_z - k \cdot v = 0$ com $k = 0{,}5\text{ s}^{-1}$, propagação dinâmica de covariâncias ($V_r = \sigma^2_{az} + k^2\sigma^2_v$) e teste de hipótese Qui-Quadrado ($\gamma = r^2/V_r > 3{,}84$ para 95% de confiança) como detector de erro grosseiro.
- **Alternativas consideradas:** Reconciliação MQP cega sem detector de resíduo; filtro passa-baixas fixo; limiar estático em aceleração pura ($|a_z| > \text{limiar}$).
- **Motivo:** O MQP puro atuaria como um otimizador cego diante de um buraco real (+29,76 m/s² a 72 km/h), forçando o fechamento da equação física, atenuando a aceleração vertical e inflando artificialmente a velocidade (+24%), o que destruiria a evidência da anomalia. O teste Qui-Quadrado preserva o buraco intacto e ativa o alarme de erro grosseiro.
- **Consequências:** O sistema equilibra com perfeição a filtragem de ruído de sensor em asfalto normal e o alerta robusto em buracos reais, comprovado em `ReconcilerTest.kt`.
- **Decidido por:** Arthur Amaral Públio de Souza, em 15/09/2026.

---

## D-03 — Concorrência na Região Crítica e Buffer Circular
- **Data:** 22/09/2026
- **Decisão:** Buffer circular thread-safe baseado em `ArrayDeque<VibrationSample>` de capacidade fixa $N = 500$ amostras, protegido por exclusão mútua (`@Synchronized`), entregando cópias defensivas (`.toList()`).
- **Alternativas consideradas:** `ArrayList` dinâmico; `ConcurrentLinkedQueue`; canais sem buffer (*rendezvous channels*); `Mutex` de corrotinas.
- **Motivo:** Em operação a 20 Hz (72.000 amostras/hora), uma lista dinâmica geraria alocações e realocações contínuas, disparando coletas de lixo (*GC pauses*) e risco de `OutOfMemoryError`. O `ArrayDeque` garante complexidade de espaço $O(1)$ e tempo de inserção $O(1)$. As cópias defensivas blindam a thread de renderização da UI contra `ConcurrentModificationException`.
- **Consequências:** Estabilidade total comprovada em 10 rodadas consecutivas de estresse multithread com 10.000 inserções cada (`TrackRecordConcurrencyTest.kt`), com zero condições de corrida.
- **Decidido por:** Arthur Amaral Públio de Souza, em 22/09/2026.

---

## D-04 — Aquisição Periódica em Tempo Real e Mitigação do Doze Mode
- **Data:** 06/10/2026
- **Decisão:** Implementação de um `Foreground Service` com notificação persistente (`FOREGROUND_SERVICE_LOCATION`), vinculado a um `PowerManager.PARTIAL_WAKE_LOCK` e deadband de velocidade para semáforos ($v < 0{,}5\text{ km/h}$).
- **Alternativas consideradas:** `WorkManager` periódico; *Background Service* convencional; amostragem apenas enquanto a Activity estiver visível.
- **Motivo:** O Android desativa sensores e suspende a CPU minutos após o bloqueio da tela (*Doze Mode*). Apenas o *Foreground Service* com *Partial WakeLock* assegura que a amostragem a 20 Hz e a captura GNSS permaneçam ativas ininterruptamente em longas viagens rodoviárias.
- **Consequências:** Comprovada a coleta contínua de 26 minutos e 55 segundos (58.673 amostras consecutivas) com a tela do Samsung Galaxy Tab S9 desligada.
- **Decidido por:** Arthur Amaral Públio de Souza, em 06/10/2026.

---

## D-05 — Ensaio Reprodutível e Injeção Determinística de Falhas
- **Data:** 27/10/2026
- **Decisão:** Criação dos componentes `FaultInjector.kt` com semente pseudo-aleatória fixa (`seed = 42`) e `TelemetryReplay.kt` para reproduzir ensaios de campo com injeção controlada de perturbações (+0,5G, +1,0G, +2,0G).
- **Alternativas consideradas:** Testes apenas com dados sintéticos aleatórios; testes manuais sem semente; injeção de hardware.
- **Motivo:** Atende estritamente à Capacidade 5 da rubrica do GAT108, provando a curva de sensibilidade do reconciliador e garantindo que qualquer membro da banca consiga reproduzir bit a bit os resultados de detecção.
- **Consequências:** O teste automatizado `FaultInjectionTest.kt` valida que perturbações $\ge 0{,}5\text{G}$ violam $\chi^2 > 3{,}84$ com 100% de reproducibilidade em qualquer máquina.
- **Decidido por:** Arthur Amaral Públio de Souza, em 27/10/2026.

---

## D-06 — Soberania do Nó de Borda e Persistência Local via MediaStore
- **Data:** 28/10/2026
- **Decisão:** Gravação direta de telemetria em arquivos CSV estruturados na pasta pública `Downloads` do dispositivo através da API `MediaStore`, com histórico indexado no `SharedPreferences` e zero tráfego de rede para servidores em nuvem.
- **Alternativas consideradas:** Envio via MQTT/HTTP para servidor remoto; banco de dados SQLite/Room local; arquivos em diretório interno inacessível via USB.
- **Motivo:** Garante a topologia de nó de borda soberano e privacidade de dados (*edge crowdsensing* sem dependência de sinal 4G/5G). A gravação via `MediaStore` permite que o operador conecte o tablet via cabo USB ou abra o gerenciador de arquivos e copie diretamente os logs CSV para análise.
- **Consequências:** Os 11 ensaios de campo (359.084 amostras) foram persistidos e analisados de forma 100% autônoma no dispositivo.
- **Decidido por:** Arthur Amaral Públio de Souza, em 28/10/2026.
