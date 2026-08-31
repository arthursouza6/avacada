# Proposta de trabalho
- **Integrantes:** Arthur Amaral Públio de Souza
- **Data:** 31/08/2026
- **Domínio escolhido:** Qualidade de pavimento (Análise de vibração e via)

## 1. O problema
Prefeituras e concessionárias de rodovias dependem de inspeções visuais lentas ou veículos perfilômetros caros para mapear buracos e fadiga do asfalto. A solução de mercado envia dados brutos de telemetria para a nuvem. Propõe-se um nó de borda embarcado em veículos comuns (crowdsensing) que adquire a vibração, aprende o padrão da via e alerta anomalias localmente.

## 2. O que é observado
- **Ativo ou processo:** Pavimento e via de rolamento de veículos terrestres.
- **Grandeza física medida:** Vibração vertical (acelerômetro) e Posição/Velocidade.
- **Sensor e origem da incerteza:** Acelerômetro e receptor GNSS do Samsung Galaxy Tab S9. Incerteza derivada da acurácia reportada pela API do Android.
- **Período de amostragem pretendido e prazo associado:** Amostragem periódica a cada 50ms (20Hz), com prazo de execução de 40ms.

## 3. Mapeamento das dez capacidades

| # | Capacidade | O que significa no nosso domínio | Teste de aceitação |
| :--- | :--- | :--- | :--- |
| **1** | Domínio, contratos e estruturas | Modelagem dos tipos imutáveis para Leitura de Vibração e Fixo de GPS. Estrutura de trajeto computada em memória constante. | Validação de invariantes na construção; exceção específica para violação de contratos cinemáticos. |
| **2** | Incerteza e reconciliação | A matriz de covariância usa a acurácia do GPS. A reconciliação amarra a amplitude da aceleração vertical (Z) com a velocidade horizontal, pois o impacto de um solavanco escala com a velocidade. | O resíduo cai em três janelas de trechos reais. Buracos severos atuam como erro grosseiro, detectado pelo resíduo normalizado. |
| **3** | Concorrência e região crítica | Um buffer circular recebe a telemetria da thread de aquisição e a entrega para o agente. | Dez execuções automatizadas do teste de corrida passando sem falha, com o buffer devolvendo cópias defensivas. |
| **4** | Aquisição periódica em tempo real | O serviço em primeiro plano segue coletando a vibração rodoviária ininterruptamente no veículo. | Log comprovando 30 minutos contínuos de aquisição com a tela do Tab S9 bloqueada. |
| **5** | Ensaio reprodutível e injeção de falhas | Os trajetos reais gravados serão reproduzidos. Simularemos falhas injetando picos artificiais (falsos buracos) no log. | Reprodução determinística com semente fixa; geração de tabela de sensibilidade para limites de detecção. |
| **6** | Instrumentação e escalonabilidade | Medição do tempo ($C_i$) gasto na filtragem de cada amostra de 50ms para garantir que a inferência não cause atraso. | Tabela declarando período, prazo, jitter, $C_i$ medido com dispersão e tempo de resposta calculado. |
| **7** | Aprendizado online sob memória constante | Agrupamento incremental das assinaturas de vibração para caracterizar o "asfalto bom" de um trecho percorrido. | O padrão recorrente é aprendido e a predição da condição do asfalto é validada em uma amostra de trajeto retida. |
| **8** | Agente e modelo substituível | O Agente valida a detecção do buraco. Se o `LocalEngine` falhar, ele usa o `RuleEngine` com limite de G-Force estático (degradação graciosa). | Sistema não quebra ao ter o modelo removido em execução, acionando a abstenção segura e operando por regras. |
| **9** | Criptografia e frota multi-nó | Dois nós (tablet e um emulador simulando um segundo veículo) identificam e fundem o alerta para a mesma coordenada esburacada via rede local. | Dado sensível de trajeto (LGPD) cifrado com chave no *Keystore*. Deriva de relógio medida entre os nós na rede. |
| **10** | Validação cega, TRL e Lei de Amdahl | Medição do ganho computacional (*speedup*) ao identificar anomalias viárias, e a justificativa de maturidade da aplicação. | Quatro métricas do ensaio cego reportadas para os dois motores; gráfico de *speedup* com dispersão e níveis TRL. |

## 4. Dados
- **Como serão obtidos:** Tab S9 alocado internamente em um veículo automotor em deslocamento urbano e rodoviário.
- **Quantos dias ou execuções:** 5 gravações de no mínimo 30 minutos em pavimentos com qualidades variadas.
- **Separação:** 3 gravações usadas para ajuste e implementação online; 2 gravações congeladas após a coleta inicial para servirem apenas à validação cega de dezembro (AV2).

## 5. Riscos

| Risco | Probabilidade | Plano B |
| :--- | :--- | :--- |
| **Sistema matar o aplicativo em background (One UI Samsung).** | Alta | Conceder permissão de bateria "Irrestrita" para o aplicativo e implementar notificação persistente. |
| **Acelerômetro captar vibração do próprio motor do carro em vez da via.** | Média | Aplicar um filtro passa-alta simples antes do buffer para focar apenas nos impactos repentinos de suspensão. |
| **Modelo de linguagem sobrecarregar processamento do Tab S9.** | Baixa | Utilizar predominantemente o `RuleEngine` para garantir a análise em tempo real e isolar a inferência pesada. |