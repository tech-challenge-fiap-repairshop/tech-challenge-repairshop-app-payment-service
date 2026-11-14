package com.cao.repairshop.payment.core.exception

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    @Test
    fun `should handle PaymentNotFoundException with 404`() {
        val ex = PaymentNotFoundException("Pagamento não encontrado: 123")
        val response = handler.handleNotFound(ex)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertNotNull(response.body)
        assertEquals(404, response.body?.status)
        assertEquals("Not Found", response.body?.error)
        assertEquals("Pagamento não encontrado: 123", response.body?.message)
    }

    @Test
    fun `should handle PaymentBusinessException with 400`() {
        val ex = PaymentBusinessException("Regra de negócio violada")
        val response = handler.handleBusinessException(ex)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertNotNull(response.body)
        assertEquals(400, response.body?.status)
        assertEquals("Bad Request", response.body?.error)
        assertEquals("Regra de negócio violada", response.body?.message)
    }

    @Test
    fun `should handle generic Exception with 500`() {
        val ex = RuntimeException("Erro inesperado de infraestrutura")
        val response = handler.handleGeneric(ex)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertNotNull(response.body)
        assertEquals(500, response.body?.status)
        assertEquals("Internal Server Error", response.body?.error)
        assertEquals("Ocorreu um erro interno ao processar a operação", response.body?.message)
    }
}

