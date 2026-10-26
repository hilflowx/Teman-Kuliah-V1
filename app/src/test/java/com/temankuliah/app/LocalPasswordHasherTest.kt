package com.temankuliah.app

import com.temankuliah.app.domain.LocalPasswordHasher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPasswordHasherTest {
    @Test
    fun passwordHashCanBeVerifiedAndDoesNotContainPlaintext() {
        val password = "TemanKuliah-Secure-2026"
        val encoded = LocalPasswordHasher.hash(password)
        assertTrue(LocalPasswordHasher.isEncoded(encoded))
        assertTrue(LocalPasswordHasher.verify(password, encoded))
        assertFalse(LocalPasswordHasher.verify("different-password", encoded))
        assertFalse(encoded.contains(password))
    }

    @Test
    fun malformedHashPrefixIsNotTreatedAsAnEncodedPassword() {
        assertFalse(LocalPasswordHasher.isEncoded("pbkdf2-sha256\$broken\$salt\$value"))
        assertFalse(LocalPasswordHasher.verify("password", "pbkdf2-sha256\$broken\$salt\$value"))
    }

    @Test
    fun eachHashUsesItsOwnSalt() {
        val first = LocalPasswordHasher.hash("same-password")
        val second = LocalPasswordHasher.hash("same-password")
        assertFalse(first == second)
        assertTrue(LocalPasswordHasher.verify("same-password", first))
        assertTrue(LocalPasswordHasher.verify("same-password", second))
    }
}
