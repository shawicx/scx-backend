package com.scx.backend.common.constants

import kotlin.test.Test
import kotlin.test.assertEquals

class TtlConstantsTest {

    @Test
    fun `access token ttl is 2 hours`() {
        assertEquals(2 * 60 * 60 * 1000L, TtlConstants.ACCESS_TOKEN_TTL_MS)
    }

    @Test
    fun `refresh token ttl is 7 days`() {
        assertEquals(7 * 24 * 60 * 60 * 1000L, TtlConstants.REFRESH_TOKEN_TTL_MS)
    }

    @Test
    fun `encryption key ttl is 5 minutes`() {
        assertEquals(5 * 60 * 1000L, TtlConstants.ENCRYPTION_KEY_TTL_MS)
    }

    @Test
    fun `email verification ttl is 10 minutes`() {
        assertEquals(10 * 60 * 1000L, TtlConstants.EMAIL_VERIFICATION_TTL_MS)
    }

    @Test
    fun `login verification ttl is 10 minutes`() {
        assertEquals(10 * 60 * 1000L, TtlConstants.LOGIN_VERIFICATION_TTL_MS)
    }
}
