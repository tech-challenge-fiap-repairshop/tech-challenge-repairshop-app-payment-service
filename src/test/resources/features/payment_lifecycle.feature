# language: pt
Funcionalidade: Gestão do Ciclo de Vida de Pagamentos, Integração Mercado Pago e Saga Pattern
  Como microsserviço de pagamentos da oficina mecânica
  Eu quero processar cobranças via Mercado Pago, registrar faturamento no DynamoDB e realizar compensações
  Para assegurar a integridade financeira e a execução resiliente da Saga da Ordem de Serviço

  Contexto:
    Dado que o serviço de pagamentos está operacional e conectado ao DynamoDB e LocalStack SQS
    E o gateway de pagamentos do Mercado Pago está disponível para transações

  Cenário: Fluxo Nominal - Emissão de cobrança PIX no Mercado Pago e faturamento no DynamoDB
    Dado que uma Ordem de Serviço foi concluída com valor de 350.00
    Quando o atendente solicita a geração do pagamento via PIX para a Ordem de Serviço
    Então uma chamada HTTP deve ser realizada com sucesso ao endpoint do Mercado Pago
    E a cobrança deve ser registrada no DynamoDB com status "PENDING"
    E uma fatura deve ser gerada no DynamoDB vinculada à Ordem de Serviço
    E um QR Code Pix válido e link de ticket devem ser retornados para o cliente

  Cenário: Aprovação do Pagamento via Confirmação e notificação SQS
    Dado que existe uma cobrança pendente para uma Ordem de Serviço no valor de 200.00
    Quando a notificação de confirmação de pagamento é recebida pelo sistema
    Então o status do pagamento no DynamoDB deve ser atualizado para "APPROVED"
    E uma mensagem de sucesso deve ser publicada na fila de sucesso de pagamentos
    E uma notificação de pagamento aprovado deve ser enviada para a fila de clientes

  Cenário: Compensação da Saga Pattern - Estorno e Reembolso por inviabilidade técnica
    Dado que existe um pagamento aprovado para a Ordem de Serviço no valor de 450.00
    Quando o sistema aciona o estorno do pagamento com o motivo "Inviabilidade tecnica na execucao da OS"
    Então o endpoint de estorno do Mercado Pago deve ser acionado com sucesso
    E o status do pagamento no DynamoDB deve ser alterado para "REFUNDED"
    E o motivo e a data do estorno devem ser registrados no DynamoDB
    E um evento de estorno deve ser publicado para notificação

  Cenário: Recusa do Pagamento no Mercado Pago e notificação de rejeição
    Dado que existe uma cobrança pendente para uma Ordem de Serviço no valor de 180.00
    Quando o webhook do Mercado Pago notifica que o pagamento foi rejeitado
    Então o status do pagamento no DynamoDB deve ser alterado para "REJECTED"
    E uma notificação de recusa de pagamento deve ser enviada para a fila de clientes

  Cenário: Resiliência e Contingência - Ativação do Circuit Breaker em caso de indisponibilidade do Mercado Pago
    Dado que o gateway do Mercado Pago está instável ou indisponível
    Quando o atendente solicita a geração de pagamento para uma Ordem de Serviço no valor de 500.00
    Então o Circuit Breaker deve interceptar a falha e acionar o fallback de contingência
    E o pagamento deve ser registrado com método "MERCADO_PAGO_CONTINGENCY"
    E o QR Code de contingência deve ser disponibilizado para o cliente

  Cenário: Geração automática de pagamento via evento assíncrono SQS da Ordem de Serviço finalizada
    Dado que uma notificação de Ordem de Serviço finalizada no valor de 280.00 é publicada na fila de OS prontas
    Quando o consumidor de mensageria SQS processa o evento de OS finalizada
    Então a cobrança deve ser registrada no DynamoDB com status "PENDING"
    E uma fatura deve ser gerada no DynamoDB vinculada à Ordem de Serviço
    E uma notificação de cobrança pendente deve ser enviada para a fila de clientes

  Cenário: Compensação da Saga por Ordem de Serviço - Estorno em lote
    Dado que existe um pagamento aprovado para a Ordem de Serviço no valor de 600.00
    Quando a solicitação de estorno é realizada informando o identificador da Ordem de Serviço com o motivo "Cancelamento total da OS pelo cliente"
    Então o estorno de todos os pagamentos daquela Ordem de Serviço deve ser concluído com status "REFUNDED"
    E o motivo e a data do estorno devem ser registrados no DynamoDB
    E um evento de estorno deve ser publicado para notificação

  Cenário: Bloqueio de integridade financeira - Proibição de estorno para pagamento rejeitado
    Dado que existe uma cobrança que foi recusada para uma Ordem de Serviço no valor de 150.00
    Quando o atendente tenta realizar o estorno daquela cobrança recusada
    Então a operação deve ser rejeitada com erro de transição inválida ou estado incompatível
    E o status do pagamento no DynamoDB deve permanecer "REJECTED"
