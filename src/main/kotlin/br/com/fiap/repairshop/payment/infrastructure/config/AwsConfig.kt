package br.com.fiap.repairshop.payment.infrastructure.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.sqs.SqsClient
import java.net.URI

@Configuration
class AwsConfig(
    @Value("\${aws.region:us-east-1}")
    private val region: String,
    @Value("\${aws.endpoint-url:#{null}}")
    private val endpointUrl: String?,
    @Value("\${aws.access-key-id:#{null}}")
    private val accessKeyId: String?,
    @Value("\${aws.secret-access-key:#{null}}")
    private val secretAccessKey: String?
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private fun credentialsProvider() = if (!accessKeyId.isNullOrBlank() && !secretAccessKey.isNullOrBlank()) {
        StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey))
    } else {
        DefaultCredentialsProvider.create()
    }

    @Bean
    fun dynamoDbClient(): DynamoDbClient {
        val builder = DynamoDbClient.builder()
            .region(Region.of(region))
            .credentialsProvider(credentialsProvider())

        if (!endpointUrl.isNullOrBlank()) {
            logger.info("Payment Service - Conectando DynamoDB em: {}", endpointUrl)
            builder.endpointOverride(URI.create(endpointUrl))
        }

        return builder.build()
    }

    @Bean
    fun sqsClient(): SqsClient {
        val builder = SqsClient.builder()
            .region(Region.of(region))
            .credentialsProvider(credentialsProvider())

        if (!endpointUrl.isNullOrBlank()) {
            logger.info("Payment Service - Conectando SQS em: {}", endpointUrl)
            builder.endpointOverride(URI.create(endpointUrl))
        }

        return builder.build()
    }
}
