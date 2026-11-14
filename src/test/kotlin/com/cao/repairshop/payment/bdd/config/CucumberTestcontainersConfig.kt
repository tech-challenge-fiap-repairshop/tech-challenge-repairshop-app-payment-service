package com.cao.repairshop.payment.bdd.config

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import io.cucumber.spring.CucumberContextConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CucumberTestcontainersConfig {

    companion object {
        init {
            System.setProperty("api.version", "1.44")
            System.setProperty("DOCKER_API_VERSION", "1.44")
        }

        const val TABLE_NAME = "tb-payment-test"
        const val QUEUE_OS_READY = "os-ready-for-payment-queue-test"
        const val QUEUE_NOTIFICATION = "payment-notification-queue-test"
        const val QUEUE_SUCCESS = "payment-success-queue-test"

        val localstack: LocalStackContainer = LocalStackContainer(DockerImageName.parse("localstack/localstack:3.4.0"))
            .withServices(LocalStackContainer.Service.DYNAMODB, LocalStackContainer.Service.SQS)
            .apply {
                start()
                createDynamoTable(this)
                createSqsQueues(this)
            }

        val wireMockServer: WireMockServer = WireMockServer(wireMockConfig().dynamicPort()).apply {
            start()
            setupDefaultStubs(this)
        }

        fun setupDefaultStubs(server: WireMockServer = wireMockServer) {
            server.stubFor(
                post(urlEqualTo("/v1/payments"))
                    .willReturn(
                        aResponse()
                            .withStatus(201)
                            .withHeader("Content-Type", "application/json")
                            .withBody(
                                """
                                {
                                    "id": "1234567890",
                                    "status": "pending",
                                    "point_of_interaction": {
                                        "transaction_data": {
                                            "qr_code": "00020126580014BR.GOV.BCB.PIX0136test-pix-qr-code520400005303986540350.005802BR5925OFICINA REPAIRSHOP6009SAO PAULO",
                                            "ticket_url": "https://www.mercadopago.com.br/payments/1234567890/ticket"
                                        }
                                    }
                                }
                                """.trimIndent()
                            )
                    )
            )

            server.stubFor(
                post(urlMatching("/v1/payments/.*/refunds"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(
                                """
                                {
                                    "id": "REF-MP-12345678",
                                    "status": "approved",
                                    "amount": 450.0
                                }
                                """.trimIndent()
                            )
                    )
            )
        }

        fun resetWireMock() {
            wireMockServer.resetAll()
            setupDefaultStubs(wireMockServer)
        }

        fun simulateMercadoPagoFailure() {
            wireMockServer.resetAll()
            wireMockServer.stubFor(
                post(urlEqualTo("/v1/payments"))
                    .willReturn(aResponse().withStatus(500).withBody("Internal Server Error"))
            )
            wireMockServer.stubFor(
                post(urlMatching("/v1/payments/.*/refunds"))
                    .willReturn(aResponse().withStatus(500).withBody("Internal Server Error"))
            )
        }

        @JvmStatic
        @DynamicPropertySource
        fun overrideProperties(registry: DynamicPropertyRegistry) {
            System.setProperty("aws.accessKeyId", localstack.accessKey)
            System.setProperty("aws.secretAccessKey", localstack.secretKey)
            System.clearProperty("aws.sessionToken")

            registry.add("aws.endpoint-url") { localstack.endpoint.toString() }
            registry.add("aws.region") { localstack.region }

            registry.add("dynamodb.table.payment") { TABLE_NAME }
            registry.add("sqs.queue.os-ready-for-payment") { QUEUE_OS_READY }
            registry.add("sqs.queue.payment-notification") { QUEUE_NOTIFICATION }
            registry.add("sqs.queue.payment-success") { QUEUE_SUCCESS }

            registry.add("mercadopago.base-url") { "http://localhost:${wireMockServer.port()}" }
            registry.add("mercadopago.access-token") { "TEST-MP-ACCESS-TOKEN" }
        }

        private fun createDynamoTable(container: LocalStackContainer) {
            val client = DynamoDbClient.builder()
                .endpointOverride(container.getEndpointOverride(LocalStackContainer.Service.DYNAMODB))
                .region(Region.of(container.region))
                .credentialsProvider(
                    StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(container.accessKey, container.secretKey)
                    )
                )
                .build()

            val request = CreateTableRequest.builder()
                .tableName(TABLE_NAME)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(
                    AttributeDefinition.builder().attributeName("PK").attributeType(ScalarAttributeType.S).build(),
                    AttributeDefinition.builder().attributeName("SK").attributeType(ScalarAttributeType.S).build(),
                    AttributeDefinition.builder().attributeName("dispatch_notificate_status").attributeType(ScalarAttributeType.S).build(),
                    AttributeDefinition.builder().attributeName("payment_date").attributeType(ScalarAttributeType.S).build()
                )
                .keySchema(
                    KeySchemaElement.builder().attributeName("PK").keyType(KeyType.HASH).build(),
                    KeySchemaElement.builder().attributeName("SK").keyType(KeyType.RANGE).build()
                )
                .globalSecondaryIndexes(
                    GlobalSecondaryIndex.builder()
                        .indexName("dispatch-notification-index")
                        .keySchema(
                            KeySchemaElement.builder().attributeName("dispatch_notificate_status").keyType(KeyType.HASH).build(),
                            KeySchemaElement.builder().attributeName("payment_date").keyType(KeyType.RANGE).build()
                        )
                        .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                        .build()
                )
                .build()

            client.createTable(request)
        }

        private fun createSqsQueues(container: LocalStackContainer) {
            val client = SqsClient.builder()
                .endpointOverride(container.getEndpointOverride(LocalStackContainer.Service.SQS))
                .region(Region.of(container.region))
                .credentialsProvider(
                    StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(container.accessKey, container.secretKey)
                    )
                )
                .build()

            listOf(QUEUE_OS_READY, QUEUE_NOTIFICATION, QUEUE_SUCCESS).forEach { queueName ->
                client.createQueue(CreateQueueRequest.builder().queueName(queueName).build())
            }
        }
    }
}

