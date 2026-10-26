package com.temankuliah.app

import com.temankuliah.app.domain.LoginThrottle
import org.junit.Assert.assertEquals
import org.junit.Test

class LoginThrottleTest {
    private var now = 0L
    private val throttle = LoginThrottle(maxFailures = 5, baseDelayMs = 30_000L, maxDelayMs = 240_000L) { now }

    @Test
    fun `first four failures do not lock`() {
        repeat(4) { assertEquals(0L, throttle.recordFailure("a@example.com")) }
        assertEquals(0L, throttle.remainingLockMs("a@example.com"))
    }

    @Test
    fun `fifth failure locks for base delay and delay doubles afterwards`() {
        repeat(4) { throttle.recordFailure("a@example.com") }
        assertEquals(30_000L, throttle.recordFailure("a@example.com"))
        now += 30_000L
        assertEquals(0L, throttle.remainingLockMs("a@example.com"))
        assertEquals(60_000L, throttle.recordFailure("a@example.com"))
        assertEquals(60_000L, throttle.remainingLockMs("a@example.com"))
    }

    @Test
    fun `delay is capped at the maximum`() {
        repeat(4) { throttle.recordFailure("a@example.com") }
        repeat(10) { throttle.recordFailure("a@example.com") }
        assertEquals(240_000L, throttle.remainingLockMs("a@example.com"))
    }

    @Test
    fun `success clears the failure count`() {
        repeat(4) { throttle.recordFailure("a@example.com") }
        throttle.recordSuccess("a@example.com")
        repeat(4) { assertEquals(0L, throttle.recordFailure("a@example.com")) }
    }

    @Test
    fun `email is matched case-insensitively and each email is independent`() {
        repeat(5) { throttle.recordFailure("Dina@Example.com") }
        assertEquals(30_000L, throttle.remainingLockMs("dina@example.com"))
        assertEquals(0L, throttle.remainingLockMs("lain@example.com"))
    }
}
