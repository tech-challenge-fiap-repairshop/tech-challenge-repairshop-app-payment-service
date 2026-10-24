# 💳 Tech Challenge RepairShop - Payment Service

> **Microsserviço de Faturamento, Cobranças e Gateway de Pagamentos**  
> **Tech Challenge SOAT (Fase 4)**  
> **Organização GitHub:** `tech-challenge-fiap-repairshop`

---

## 📌 Visão Geral

O **`payment-service`** é o microsserviço responsável pela geração de faturas fiscais (`item_invoice`), transações de pagamento (`item_payment`), cobranças via PIX e integração oficial com a API do **Mercado Pago**, estritamente encapsulada por um **Circuit Breaker** (Resilience4j) para garantir resiliência contra indisponibilidades externas.

---

## 🏛️ Arquitetura e Persistência

- **Padrão de Arquitetura:** Clean Architecture / DDD.
- **Persistência NoSQL:** AWS DynamoDB (`tb-payment`).
  - `item_payment`: Histórico detalhado de transações, status de conciliação e IDs externos do Mercado Pago.
  - `item_invoice`: Faturas fiscais emitidas após quitação de ordens de serviço.
- **Resiliência e Tolerância a Falhas:**
  - Circuit Breaker configurado para chamadas externas ao Mercado Pago.
  - Recepção de Webhooks do Mercado Pago (`POST /webhooks/mercadopago`) com validação de assinatura HMAC.

---

## 🔀 Comunicação por Mensageria (AWS SQS)

1. **Consumo de Ordens Prontas:**
   - Consome da fila `SQS os-ready-for-payment-queue` enviada pelo `service_order_execution-service`.
2. **Notificação de Faturamento:**
   - Publica em `SQS payment-notification-queue` para o `notification-service` alertar o cliente sobre cobrança gerada ou recibo.
3. **Confirmação de Pagamento:**
   - Publica na fila `SQS payment-success-queue` após confirmação do Mercado Pago para autorizar o início dos serviços mecânicos.

---

## 📂 Estrutura de Diretórios

```
tech-challenge-repairshop-payment-service/
├── .github/workflows/         # Pipelines de CI/CD (GitHub Actions)
├── k8s/                       # Manifestos Kubernetes (Deployment, Service, HPA, ConfigMap)
├── src/
│   ├── main/
│   │   ├── kotlin/com/cao/repairshop/payment/
│   │   │   ├── domain/        # Modelos de Pagamento, Fatura e Regras de Negócio
│   │   │   ├── application/   # Casos de Uso (ProcessPayment, GeneratePix, HandleWebhook)
│   │   │   ├── infrastructure/# DynamoDB Enhanced Client, Mercado Pago SDK, Circuit Breaker, SQS Producers/Consumers
│   │   │   └── interfaces/    # Controllers REST e Endpoints de Webhook
│   │   └── resources/         # application.properties
│   └── test/                  # Testes Unitários e Testes BDD (Cucumber)
├── Dockerfile                 # Multi-stage Docker build
└── README.md
```

---

## 🛡️ Governança de Branches
- `main`: Branch de produção (protegida contra push direto).
- `homolog`: Branch de homologação e testes integrados.
- `develop`: Branch de desenvolvimento ativo.
- `feat/*`, `fix/*`, `chore/*`: Branches de trabalho.
