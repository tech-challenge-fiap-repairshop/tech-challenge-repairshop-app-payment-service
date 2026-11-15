# 💳💰 Repair Shop — Payment Service (`app-payment-service`)

[![CI/CD Pipeline](https://github.com/tech-challenge-fiap-repairshop/tech-challenge-repairshop-app-payment-service/actions/workflows/ci.yml/badge.svg)](https://github.com/tech-challenge-fiap-repairshop/tech-challenge-repairshop-app-payment-service/actions/workflows/ci.yml)
[![Java 24](https://img.shields.io/badge/Java-24-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Kotlin 2.2](https://img.shields.io/badge/Kotlin-2.2.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Spring Boot 4](https://img.shields.io/badge/Spring%20Boot-4.0.7-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)

Microsserviço de **Faturamento e Pagamentos** da plataforma **Repair Shop (Tech Challenge FIAP — 15SOAT)**. Implementado em **Kotlin + Clean Architecture** (pacote `com.cao.repairshop.payment`), gerencia cobranças, faturas/notas fiscais (`Invoice`), processamento de webhooks com idempotência, estornos e integração resiliente com o **Mercado Pago** protegida por **Resilience4j Circuit Breaker**, utilizando **Amazon DynamoDB** e **Amazon SQS**.

---

## 📋 Índice

- [Endpoints, Mercado Pago e Resiliência](#endpoints-mercado-pago-e-resiliência)
- [Arquitetura Atual — Motivação e Benefícios dos Componentes](#arquitetura-atual--motivação-e-benefícios-dos-componentes)
- [Por que DynamoDB em vez de Banco Relacional para Pagamentos?](#por-que-dynamodb-em-vez-de-banco-relacional-para-pagamentos)
- [Fluxo SQS (Saga Coreografada) e Idempotência](#fluxo-sqs-saga-coreografada-e-idempotência)
- [Como Executar Localmente](#como-executar-localmente)
- [Como Executar em Produção (Esteira CI/CD GitHub Actions)](#como-executar-em-produção-esteira-cicd-github-actions)
- [Documentação Técnica e Collection do Postman](#documentação-técnica-e-collection-do-postman)

---

## Endpoints, Mercado Pago e Resiliência

O serviço escuta internamente na porta **`8080`** (exposto na porta **`8082`** no Docker Compose local) e disponibiliza os seguintes endpoints no `PaymentController`:

| Método | Rota | Descrição |
| :--- | :--- | :--- |
| `POST` | `/payment/{serviceOrderId}` e `/payments` | Inicia o pagamento e gera fatura/QR ou dados do gateway para uma Ordem de Serviço. |
| `GET` | `/payment/{serviceOrderId}` e `/payments/{id}` | Consulta detalhes de pagamento por ID da OS ou ID do pagamento. |
| `POST` | `/payment/{id}/confirm` e `/payments/{id}/confirm` | Confirmação manual de pagamento. |
| `POST` | `/payment/{id}/refund` e `/payments/{id}/refund` | Solicita estorno por ID do pagamento (`RefundPaymentUseCase`). |
| `POST` | `/payment/order/{serviceOrderId}/refund` | Solicita estorno vinculado à Ordem de Serviço. |
| `POST` | `/payment/webhook` e `/payments/webhook` | Recebe notificações assíncronas do Mercado Pago (`ProcessWebhookUseCaseImpl`) com **verificação de idempotência** (ignora eventos duplicados caso o status já corresponda ao final). *(Nota: a validação de assinatura HMAC do payload de webhook ainda não é aplicada no código atual).* |

### Integração Mercado Pago com Resilience4j Circuit Breaker
- O `MercadoPagoClient` integra com a API externa (`/v1/payments` e `/v1/payments/{id}/refunds`) protegido por **`@CircuitBreaker(name = "mercadoPagoCircuitBreaker")`** tanto na criação quanto no estorno.
- Em caso de indisponibilidade do provedor externo, o circuito entra em fallback controlado (`PENDING_CONTINGENCY`) evitando falha em cascata sobre as demais filas e pods.
- **Health Probes (Actuator)**: `/actuator/health/liveness` (`livenessState,sqsListener`) monitorado por `SqsListenerHealthIndicator`, e `/actuator/health/readiness`.

---

## Arquitetura Atual — Motivação e Benefícios dos Componentes

| Pilar / Tecnologia | Papel no Ecossistema | Motivação Técnica e Pontos Positivos |
| :--- | :--- | :--- |
| **RDS (PostgreSQL 16)** | Base cadastral no [`app-register-service`](https://github.com/tech-challenge-fiap-repairshop/tech-challenge-repairshop-app-register-service) via [`infra-db-rds`](https://github.com/tech-challenge-fiap-repairshop/tech-challenge-repairshop-infra-db-rds) | Reserva a consistência relacional ACID para Clientes, Veículos e Usuários, sem sobrecarregar o banco transacional com eventos financeiros de pagamento e retentativas de webhook. |
| **DynamoDB (NoSQL)** | **Banco principal deste serviço** (`tb-payment-{env}` via [`infra-db-dynamo`](https://github.com/tech-challenge-fiap-repairshop/tech-challenge-repairshop-infra-db-dynamo)) | Armazena pagamentos e faturas sob chave de partição `PK=BILLING#<serviceOrderId>` (`SK=PAY#...|INV#...`) e GSI `dispatch-notification-index`. Entrega alta disponibilidade, baixa latência, `PAY_PER_REQUEST` e PITR nativo para auditoria financeira. |
| **ElastiCache (Redis 7.0)** | Camada em memória provisionada via [`infra-redis`](https://github.com/tech-challenge-fiap-repairshop/tech-challenge-repairshop-infra-redis) | Prepara a camada de entrada (`register-service`) para cache sub-milissegundo de sessão/autenticação em picos de tráfego, isolando a borda de acessos dos contextos financeiros. |
| **Apps (EKS + Lambda Auth)** | Container no **EKS (`1.31`)** ([`infra-eks`](https://github.com/tech-challenge-fiap-repairshop/tech-challenge-repairshop-infra-eks)) | Arquitetura Clean em Kotlin com HPA no Kubernetes, combinada ao [`lambda-auth`](https://github.com/tech-challenge-fiap-repairshop/tech-challenge-repairshop-lambda-auth) serverless na borda para escalar autenticação independentemente das transações financeiras. |
| **Amazon SQS (Mensageria + DLQ)** | **Comunicação assíncrona deste serviço** ([`infra-sqs`](https://github.com/tech-challenge-fiap-repairshop/tech-challenge-repairshop-infra-sqs)) | Consome OS finalizadas e publica notificações de cobrança e confirmação de quitação usando filas dedicadas pareadas a DLQs (14 dias de retenção). Absorve picos de webhooks/processamentos sem bloquear a execução das oficinas. |
| **API Gateway (HTTP API v2)** | Ponto de entrada único ([`infra-apigateway`](https://github.com/tech-challenge-fiap-repairshop/tech-challenge-repairshop-infra-apigateway)) | Expõe `ANY /payment/{proxy+}`, `ANY /payments` e `ANY /payments/{proxy+}` encaminhando diretamente via `HTTP_PROXY` ao NLB interno do `payment-service`, garantindo roteamento uniforme e suporte a `traceparent` no CORS. |
| **Observabilidade (Grafana)** | Dashboards `payment-service-observability` e `payment-service-logs-traces` | Rastreamento distribuído OpenTelemetry + Jaeger propagando `traceparent` nas mensagens SQS, métricas JVM/HTTP/Circuit Breaker no Prometheus e logs no Loki consolidados no Grafana. |

---

## Por que DynamoDB em vez de Banco Relacional para Pagamentos?

Optar por **DynamoDB (`tb-payment-{env}`)** em vez de um banco relacional (RDS) no `payment-service` traz ganhos objetivos de escalabilidade, flexibilidade e resiliência:

1. **Schema evolutivo para integrações de gateway externo**: Gateways como o Mercado Pago adicionam frequentemente metadados de QR Code, IDs de transação, códigos de contingência (`PENDING_CONTINGENCY`) e motivos de recusa. Um banco relacional exige migrações DDL (`ALTER TABLE`) potencialmente bloqueantes a cada novo campo; o DynamoDB acomoda esses atributos de forma flexível mantendo o registro `PAY#*` e `INV#*` sob a mesma partição `BILLING#<serviceOrderId>`.
2. **Absorção de rajadas de webhooks e polling SQS sem gargalo de conexões**: Sob alta concorrência de callbacks do Mercado Pago e escalonamento horizontal de pods no EKS (HPA), pools de conexões SQL podem esgotar rapidamente. Como o DynamoDB opera via HTTPS *stateless* no modo `PAY_PER_REQUEST`, não há disputa por conexões ou locks prolongados de tabela.
3. **Modelagem orientada à consulta do fluxo de despacho (`dispatch-notification-index`)**: O GSI permite consultar apenas cobranças pendentes de notificação (`dispatch_notificate_status` + `payment_date`), evitando varreduras completas (`SCAN`) que degradariam a performance de tabelas relacionais de histórico financeiro em crescimento contínuo.

---

## Fluxo SQS (Saga Coreografada) e Idempotência

| Direção | Fila SQS (`-{env}`) | Evento / Responsabilidade |
| :--- | :--- | :--- |
| **Consumer (`PaymentSqsConsumer`)** | `os-ready-for-payment-queue-dev` | Recebe OS finalizada vinda do `service-order-execution-service` e gera a cobrança correspondente. |
| **Producer (`PaymentSqsProducer`)** | `payment-notification-queue-dev` | Dispara solicitação de notificação (`payment_approved` / `payment_refused` / link ou aviso) para o `notification-service`. |
| **Producer (`PaymentSqsProducer`)** | `payment-success-queue-dev` | Notifica o `service-order-execution-service` quando o pagamento é aprovado para que a OS avance ao status **`PAID`**. |

> **Nota sobre build Maven**: Este repositório **não inclui Maven Wrapper** (`mvnw`). Utilize `mvn` instalado localmente com **JDK 24**.

---

## Como Executar Localmente

### Pré-requisitos
- **Docker** e **Docker Compose**
- **JDK 24** e **Apache Maven 3.9+** (`mvn`)

### Passo a Passo com Docker Compose + LocalStack (Recomendado)

O arquivo [`docker-compose.yml`](docker-compose.yml) sobe o **LocalStack** (`SERVICES=dynamodb,sqs`, porta `4566`) e o container `payment-service` mapeado na porta **`8082`** (`8082:8080`):

```bash
# Subir LocalStack (DynamoDB + SQS) e Payment Service
docker compose up -d --build

# Verificar logs
docker compose logs -f payment-service
```

- **Swagger UI / OpenAPI**: `http://localhost:8082/swagger-ui.html`
- **Healthcheck**: `http://localhost:8082/actuator/health`

Principais variáveis ([`application.properties`](src/main/resources/application.properties)):
- `DYNAMODB_TABLE_PAYMENT` (`tb-payment-dev`)
- `AWS_SQS_QUEUE_OS_READY` (`os-ready-for-payment-queue-dev`)
- `AWS_SQS_QUEUE_PAYMENT_NOTIFICATION` (`payment-notification-queue-dev`)
- `AWS_SQS_QUEUE_PAYMENT_SUCCESS` (`payment-success-queue-dev`)
- `MERCADOPAGO_ACCESS_TOKEN`, `MERCADOPAGO_BASE_URL`

Para rodar testes automatizados e cobertura via Maven:
```bash
mvn clean verify
```

Para encerrar o ambiente local:
```bash
docker compose down -v
```

---

## Como Executar em Produção (Esteira CI/CD GitHub Actions)

O deploy automatizado está definido em [`.github/workflows/ci.yml`](.github/workflows/ci.yml), com gatilho em `main`, `homolog`, `develop` e `workflow_dispatch`.

### Etapas da Pipeline (`ci.yml`)
1. **`build`**: Compila com JDK 24 (`mvn clean package -DskipTests`) e executa smoke test local no runner validando `/actuator/health`.
2. **`test`**: Roda `mvn clean verify` gerando relatórios **JaCoCo** e **Cucumber**.
3. **`quality`**: Executa scan **Trivy FS** no `pom.xml` e análise de qualidade no **SonarCloud**.
4. **`docker-build-push`**: Publica a imagem no **Amazon ECR** (`payment-service-{env}`), aplica scan de container com **Trivy** e realiza smoke test da imagem.
5. **`deploy`**:
   - Verifica/ajusta nos nós EC2 do EKS o parâmetro **IMDSv2 `http-put-response-hop-limit 2`** para assegurar que os pods tenham acesso transparente às credenciais IAM (DynamoDB e SQS).
   - Aplica os manifestos Kubernetes em [`k8s/`](k8s/) (`namespace.yaml`, `secret.yaml`, `deployment.yaml`, `service.yaml`, `hpa.yaml`) e valida o `rollout status`.

---

## Documentação Técnica e Collection do Postman

- **Postman Collections & Environments (Organização)**:
  - Collection Principal: [`Repair Shop (Tech Challenge FIAP)`](https://github.com/tech-challenge-fiap-repairshop/tech-challenge-repairshop-app-service-order-execution-service/blob/main/docs/postman/Repair%20Shop%20%28Tech%20Challenge%20FIAP%29.postman_collection.json)
  - Collection Completa: [`Tech Challenge FIAP - Completo`](https://github.com/tech-challenge-fiap-repairshop/tech-challenge-repairshop-app-service-order-execution-service/blob/main/docs/postman/Tech_Challenge_Fiap_-_Completo.postman_collection.json)
  - Environment Local: [`Local.postman_environment.json`](https://github.com/tech-challenge-fiap-repairshop/tech-challenge-repairshop-app-service-order-execution-service/blob/main/docs/postman/Local.postman_environment.json)
  - Guia do Postman: [`docs/postman/README.md`](https://github.com/tech-challenge-fiap-repairshop/tech-challenge-repairshop-app-service-order-execution-service/blob/main/docs/postman/README.md)
- **Dashboards Grafana deste Serviço**:
  - Observabilidade Geral: [`repairshop-payment-service-observability.json`](https://github.com/tech-challenge-fiap-repairshop/tech-challenge-repairshop-app-service-order-execution-service/blob/main/local/observability/grafana/dashboards/repairshop-payment-service-observability.json)
  - Logs e Traces: [`repairshop-payment-service-logs-traces.json`](https://github.com/tech-challenge-fiap-repairshop/tech-challenge-repairshop-app-service-order-execution-service/blob/main/local/observability/grafana/dashboards/repairshop-payment-service-logs-traces.json)
- **ADRs, RFCs e Diagramas**:
  - [ADRs (`ADR-001` a `ADR-013`)](https://github.com/tech-challenge-fiap-repairshop/tech-challenge-repairshop-app-service-order-execution-service/blob/main/ADRs/README.md)
  - [RFCs (`RFC-001` a `RFC-014`)](https://github.com/tech-challenge-fiap-repairshop/tech-challenge-repairshop-app-service-order-execution-service/blob/main/RFCs/README.md)
  - [`Diagrama de Arquitetura`](https://github.com/tech-challenge-fiap-repairshop/tech-challenge-repairshop-app-service-order-execution-service/blob/main/docs/delivery/clean_arquiteture/diagrama_arquitetura.png)
- **Repositórios relacionados**: [`infra-db-dynamo`](https://github.com/tech-challenge-fiap-repairshop/tech-challenge-repairshop-infra-db-dynamo) | [`infra-sqs`](https://github.com/tech-challenge-fiap-repairshop/tech-challenge-repairshop-infra-sqs) | [`infra-apigateway`](https://github.com/tech-challenge-fiap-repairshop/tech-challenge-repairshop-infra-apigateway)
