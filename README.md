# Payment Service

Microsserviço de pagamentos do Tech Challenge RepairShop (Fase 4), desenvolvido em Kotlin com Spring Boot. O serviço registra pagamentos no DynamoDB, recebe ordens e envia eventos por filas SQS.

## O que está implementado

- **API REST de pagamentos:** cria registros, consulta por ID, permite confirmação manual e recebe notificações pelo webhook.
- **Persistência DynamoDB:** tabela configurável (padrão `tb-payment-dev`), com chaves `PK` e `SK`.
- **Mensageria SQS:** consulta periodicamente `create-payment-order-queue-dev` e, após aprovação, publica eventos em `callback-payment-order-queue-dev` e `payment-notification-queue-dev`. Os nomes podem ser configurados por ambiente.
- **Operação e observabilidade:** Spring Boot Actuator expõe health checks e métricas Prometheus; há monitoramento de atividade do listener SQS e probes de readiness/liveness nos manifestos Kubernetes.
- **Empacotamento e deploy:** Dockerfile com imagem de runtime não-root e agente OpenTelemetry; o repositório também contém manifestos Kubernetes e um workflow de CI/CD.

O código atual **não emite faturas fiscais** e não contém uma integração ativa com a API do Mercado Pago. O valor padrão `MERCADO_PAGO_QR` e a rota chamada webhook não significam que exista comunicação com o provedor: o endpoint `/payments/webhook` recebe uma notificação, mas não valida assinatura HMAC. Não há Circuit Breaker configurado.

## API

O serviço escuta na porta `8082`.

| Método | Rota | Função |
|---|---|---|
| `POST` | `/payments` | Cria e persiste um pagamento; retorna `201`. |
| `GET` | `/payments/{id}` | Consulta um pagamento; retorna `404` quando não encontrado. |
| `POST` | `/payments/{id}/confirm` | Confirma o pagamento e envia os eventos de sucesso às filas configuradas. |
| `POST` | `/payments/webhook` | Processa a notificação recebida e atualiza o status quando o pagamento existe. |

A interface OpenAPI/Swagger UI é disponibilizada pelo Springdoc em `/swagger-ui/index.html` enquanto a aplicação está em execução.

## Executar localmente

O Docker Compose inicia o LocalStack (DynamoDB e SQS) e pode executar a aplicação. **Ele não cria automaticamente a tabela nem as filas**, portanto faça essa preparação uma vez após iniciar o LocalStack.

Pré-requisitos: Docker com Compose, AWS CLI e Java 24/Maven apenas se preferir executar a aplicação fora do container.

1. Inicie o LocalStack:

   ```powershell
   docker compose up -d localstack
   ```

2. No PowerShell, configure credenciais locais de teste e crie a tabela DynamoDB. Ela usa chave de partição String `PK` e chave de ordenação String `SK`:

   ```powershell
   $env:AWS_ACCESS_KEY_ID = "test"
   $env:AWS_SECRET_ACCESS_KEY = "test"
   $env:AWS_DEFAULT_REGION = "us-east-1"
   aws --endpoint-url http://localhost:4566 --region us-east-1 dynamodb create-table --table-name tb-payment-dev --attribute-definitions AttributeName=PK,AttributeType=S AttributeName=SK,AttributeType=S --key-schema AttributeName=PK,KeyType=HASH AttributeName=SK,KeyType=RANGE --billing-mode PAY_PER_REQUEST
   ```

3. Crie as três filas usadas pelo serviço:

   ```powershell
   aws --endpoint-url http://localhost:4566 --region us-east-1 sqs create-queue --queue-name create-payment-order-queue-dev
   aws --endpoint-url http://localhost:4566 --region us-east-1 sqs create-queue --queue-name callback-payment-order-queue-dev
   aws --endpoint-url http://localhost:4566 --region us-east-1 sqs create-queue --queue-name payment-notification-queue-dev
   ```

4. Inicie a aplicação no container:

   ```powershell
   docker compose up --build -d payment-service
   ```

5. Verifique a inicialização e consulte a saúde da aplicação:

   ```powershell
   docker compose logs --tail 100 payment-service
   Invoke-RestMethod http://localhost:8082/actuator/health
   ```

Para encerrar os containers, execute `docker compose down`. O Compose não configura volume persistente para o LocalStack; os dados locais podem ser perdidos ao remover o container.

## Estrutura do repositório

- `src/main/kotlin/br/com/fiap/repairshop/payment/infrastructure/web`: controller e modelos das requisições HTTP.
- `infrastructure/persistence`: acesso ao DynamoDB.
- `infrastructure/messaging`: polling e publicação de mensagens SQS.
- `infrastructure/config` e `infrastructure/health`: configuração AWS e health check do listener.
- `src/test/kotlin`: testes para controller, repositório DynamoDB, mensageria SQS e health check.
- `docker-compose.yml` e `Dockerfile`: ambiente local e imagem da aplicação.
- `k8s/` e `.github/workflows/ci.yml`: manifestos Kubernetes e pipeline de CI/CD.

O pipeline configura etapas de compilação, testes, análise SonarCloud (quando há token configurado), build/scan de imagem e deploy, condicionadas aos eventos e ambientes definidos no workflow. A configuração, por si só, não representa um resultado de execução ou uma cobertura de testes alcançada.
