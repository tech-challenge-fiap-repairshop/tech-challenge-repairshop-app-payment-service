package com.cao.repairshop.payment.core.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.sqs.SqsClient
import java.net.URI

@Configuration
class AwsConfig(
    @Value("\${aws.region:\${AWS_REGION:us-east-1}}")
    private val region: String,
    @Value("\${aws.endpoint-url:\${AWS_ENDPOINT_URL:#{null}}}")
    private val endpointUrl: String?
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun dynamoDbClient(): DynamoDbClient =
        DynamoDbClient.builder()
            .region(Region.of(region))
            .credentialsProvider(DefaultCredentialsProvider.builder().build())
            .apply {
                if (!endpointUrl.isNullOrBlank()) {
                    logger.info("Payment Service - Conectando DynamoDB em endpoint customizado: {}", endpointUrl)
                    endpointOverride(URI.create(endpointUrl))
                }
            }
            .build()

    @Bean
    fun dynamoDbEnhancedClient(dynamoDbClient: DynamoDbClient): DynamoDbEnhancedClient =
        DynamoDbEnhancedClient.builder()
            .dynamoDbClient(dynamoDbClient)
            .build()

    @Bean
    fun sqsClient(): SqsClient =
        SqsClient.builder()
            .region(Region.of(region))
            .credentialsProvider(DefaultCredentialsProvider.builder().build())
            .apply {
                if (!endpointUrl.isNullOrBlank()) {
                    logger.info("Payment Service - Conectando SQS em endpoint customizado: {}", endpointUrl)
                    endpointOverride(URI.create(endpointUrl))
                }
            }
            .build()
}

