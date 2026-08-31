# Proposta de trabalho
- **Integrantes:** Arthur Amaral Públio de Souza
- **Data:** 31/08/2026
- **Domínio escolhido:** Qualidade de pavimento (Análise de vibração e via)

## 1. O problema
Prefeituras e concessionárias de rodovias dependem de inspeções visuais lentas ou veículos perfilômetros caros para mapear buracos e fadiga do asfalto. A solução de mercado envia dados brutos de telemetria para a nuvem. Propõe-se um nó de borda embarcado em veículos comuns que adquire a vibração, aprende o padrão da via e alerta anomalias localmente.

## 2. O que é observado
- **Ativo ou processo:** Pavimento e via de rolamento de veículos terrestres.
- **Grandeza física medida:** Vibração vertical (acelerômetro) e Posição/Velocidade.
- **Sensor e origem da incerteza:** Acelerômetro e receptor GNSS do Samsung Galaxy Tab S9. Incerteza derivada da acurácia reportada pela API do Android.
- **Período de amostragem pretendido e prazo associado:** Amostragem periódica a cada 50ms (20Hz), com prazo de execução de 40ms.

## 3. Mapeamento das dez capacidades

| # | Capacidade | O que significa no nosso domínio | Teste de aceitação |
| :--- | :--- | :--- | :--- |
| **1** | Domínio, contratos e estruturas | Uso de tipos imutáveis para Ponto GPS e Vibração, impedindo variáveis soltas no código. | O construtor lança um erro imediato se receber dados impossíveis (ex: velocidade negativa). |
| **2** | Incerteza e reconciliação | Amarrar a vibração com a velocidade do GPS. Um solavanco em alta velocidade é buraco; em baixa, é ruído. | Cálculo do resíduo matemático: um buraco real faz o resíduo explodir (erro grosseiro detectado). |
| **3** | Concorrência e região crítica | Uso de um buffer circular para o sensor ler os dados contínuos sem atropelar a análise do Agente. | Rodar 10 vezes automatizadas para provar que o buffer entrega cópias seguras e não trava a memória. |
| **4** | Aquisição periódica em tempo real | O Android não pode suspender a leitura contínua a cada 50ms para economizar bateria. | Log comprovando 30 minutos de gravação ininterrupta com a tela do tablet bloqueada. |
| **5** | Ensaio reprodutível e injeção de falhas | Tocar um trajeto real gravado como se estivesse acontecendo ao vivo. | Injetar buracos artificiais nesse log gravado para ver se o detector acerta com precisão. |
| **6** | Instrumentação e escalonabilidade | Cronometrar o código para garantir que a análise não estoure o limite de 40ms. | Tabela exibindo o tempo de processamento ($C_i$) de cada amostra lida. |
| **7** | Aprendizado online sob memória constante | Aprender o padrão de "asfalto bom" do trajeto sem lotar a memória RAM do tablet ao longo da viagem. | Treinar o sistema na primeira metade do log e prever com sucesso a qualidade da segunda metade. |
| **8** | Agente e modelo substituível | Se a Inteligência Artificial travar o tablet, o sistema passa a usar regras matemáticas estáticas. | Desligar a IA de propósito na execução; o aplicativo deve continuar detectando os buracos pela regra. |
| **9** | Criptografia e frota multi-nó | Criptografar o trajeto do GPS e simular dois veículos (tablet e emulador) achando o mesmo buraco. | Fundir os alertas na mesma rede Wi-Fi e validar que a chave do GPS está protegida no *Keystore*. |
| **10** | Validação cega, TRL e Lei de Amdahl | Provar matematicamente se usar Inteligência Artificial foi melhor do que as regras estáticas simples. | Avaliar os dois motores em 2 logs escondidos (ensaio cego) e exibir a tabela comparativa de desempenho. |

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