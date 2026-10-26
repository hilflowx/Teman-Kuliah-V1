package com.temankuliah.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.temankuliah.app.data.backup.LocalDataArchive
import com.temankuliah.app.data.local.AUTH_GUEST
import com.temankuliah.app.data.local.AUTH_LOCAL
import com.temankuliah.app.data.local.OneTimeTaskGate
import com.temankuliah.app.data.local.TemanKuliahDatabase
import com.temankuliah.app.data.local.UserEntity
import com.temankuliah.app.data.repository.PasswordSetupRequiredException
import com.temankuliah.app.data.repository.UserRepository
import com.temankuliah.app.domain.LocalPasswordHasher
import com.temankuliah.app.domain.LoginThrottle
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthSecurityRepositoryTest {
    private lateinit var db: TemanKuliahDatabase
    private var now = 0L
    private lateinit var repo: UserRepository

    private val email = "budi@example.com"
    private val password = "rahasia-123"
    private val legacyEmail = "lama@example.com"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TemanKuliahDatabase::class.java).allowMainThreadQueries().build()
        repo = UserRepository(db.userDao(), LoginThrottle(maxFailures = 5, baseDelayMs = 30_000L) { now })
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun registerPasswordAccount() = runBlocking {
        repo.registerUser("Budi", "", "", "", "", 1, email, password).getOrThrow()
        repo.logout()
    }

    /** Akun tanpa sandi (password kosong), mis. hasil restore cadangan. */
    private fun insertPasswordlessAccount(
        authType: String = AUTH_LOCAL,
        accountEmail: String = legacyEmail,
        nim: String = ""
    ): Long = runBlocking {
        db.userDao().insertUser(
            UserEntity(
                fullName = "Lama", nim = nim, studyProgram = "", faculty = "", university = "",
                password = "", email = accountEmail, authType = authType
            )
        )
    }

    private fun storedUser(id: Long): UserEntity = runBlocking { db.userDao().getAllUsersOnce().single { it.id == id } }

    @Test
    fun `password login still works for the password account`() = runBlocking {
        registerPasswordAccount()
        assertTrue(repo.loginWithEmailAndPassword(email, password).isSuccess)
    }

    @Test
    fun `five wrong passwords lock the email even for the correct password`() = runBlocking {
        registerPasswordAccount()
        repeat(4) { assertTrue(repo.loginWithEmailAndPassword(email, "salah").isFailure) }
        val fifth = repo.loginWithEmailAndPassword(email, "salah").exceptionOrNull()!!.message!!
        assertTrue(fifth, fifth.contains("Coba lagi dalam 30 detik"))

        val locked = repo.loginWithEmailAndPassword(email, password)
        assertTrue(locked.isFailure)
        assertFalse(db.userDao().getActiveUser() != null)
    }

    @Test
    fun `correct password works again after the lock expires and failures reset on success`() = runBlocking {
        registerPasswordAccount()
        repeat(5) { repo.loginWithEmailAndPassword(email, "salah") }
        now += 30_001L
        assertTrue(repo.loginWithEmailAndPassword(email, password).isSuccess)

        repo.logout()
        repeat(4) { assertTrue(repo.loginWithEmailAndPassword(email, "salah").isFailure) }
        assertTrue(repo.loginWithEmailAndPassword(email, password).isSuccess)
    }

    // ---------------------------------------------------------------------------------------------
    // Akun tanpa sandi: login tidak boleh menerima sandi apa pun; harus lewat alur "Buat sandi".
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `login to a passwordless account is refused and never stores the typed password`() = runBlocking {
        val id = insertPasswordlessAccount()

        val error = repo.loginWithEmailAndPassword(legacyEmail, "sandi-apa-saja-1").exceptionOrNull()

        assertTrue(error is PasswordSetupRequiredException)
        assertEquals(legacyEmail, (error as PasswordSetupRequiredException).identifier)
        assertEquals("Sandi yang diketik saat login tidak boleh tersimpan", "", storedUser(id).password)
        assertNull("Login tidak boleh mengaktifkan akun tanpa sandi", db.userDao().getActiveUser())
    }

    @Test
    fun `repeated logins never set a password and never lock a passwordless account`() = runBlocking {
        val id = insertPasswordlessAccount()

        repeat(10) {
            val error = repo.loginWithEmailAndPassword(legacyEmail, "tebakan-$it-abc").exceptionOrNull()
            assertTrue(error is PasswordSetupRequiredException)
        }

        assertEquals("", storedUser(id).password)
        assertTrue(
            "Akun tanpa sandi tidak boleh terkunci pembatas percobaan; pemiliknya harus bisa membuat sandi",
            repo.createPasswordForAccount(legacyEmail, "sandi-baru-123", "sandi-baru-123").isSuccess
        )
    }

    @Test
    fun `create password stores a hash, signs in, and later logins need that password`() = runBlocking {
        val id = insertPasswordlessAccount()

        val created = repo.createPasswordForAccount(legacyEmail, "sandi-baru-123", "sandi-baru-123").getOrThrow()

        assertTrue(created.isLoggedIn)
        val stored = storedUser(id)
        assertTrue(LocalPasswordHasher.isEncoded(stored.password))
        assertTrue(LocalPasswordHasher.verify("sandi-baru-123", stored.password))
        assertEquals(AUTH_LOCAL, stored.authType)
        assertEquals(id, db.userDao().getActiveUser()?.id)

        repo.logout()
        assertTrue(repo.loginWithEmailAndPassword(legacyEmail, "sandi-lain-456").isFailure)
        assertTrue(repo.loginWithEmailAndPassword(legacyEmail, "sandi-baru-123").isSuccess)
    }

    @Test
    fun `create password validates length and confirmation and leaves the account passwordless`() = runBlocking {
        val id = insertPasswordlessAccount()

        assertTrue(repo.createPasswordForAccount(legacyEmail, "pendek", "pendek").isFailure)
        assertTrue(repo.createPasswordForAccount(legacyEmail, "sandi-baru-123", "sandi-baru-124").isFailure)
        assertTrue(repo.createPasswordForAccount(legacyEmail, "", "").isFailure)

        assertEquals("", storedUser(id).password)
        assertNull(db.userDao().getActiveUser())
    }

    @Test
    fun `create password cannot be used to take over an account that already has a password`() = runBlocking {
        registerPasswordAccount()

        val result = repo.createPasswordForAccount(email, "penyerang-123", "penyerang-123")

        assertTrue(result.isFailure)
        assertNull(db.userDao().getActiveUser())
        assertTrue(repo.loginWithEmailAndPassword(email, "penyerang-123").isFailure)
        assertTrue("Sandi asli harus tetap berlaku", repo.loginWithEmailAndPassword(email, password).isSuccess)
    }

    @Test
    fun `a second create password cannot overwrite the first one`() = runBlocking {
        insertPasswordlessAccount()
        assertTrue(repo.createPasswordForAccount(legacyEmail, "sandi-pertama-1", "sandi-pertama-1").isSuccess)
        repo.logout()

        assertTrue(repo.createPasswordForAccount(legacyEmail, "sandi-kedua-22", "sandi-kedua-22").isFailure)

        assertTrue(repo.loginWithEmailAndPassword(legacyEmail, "sandi-kedua-22").isFailure)
        assertTrue(repo.loginWithEmailAndPassword(legacyEmail, "sandi-pertama-1").isSuccess)
    }

    @Test
    fun `the conditional update refuses to touch an account that already has a password`() = runBlocking {
        registerPasswordAccount()
        val existing = db.userDao().getUserByEmail(email)!!

        assertEquals(0, db.userDao().setInitialPassword(existing.id, LocalPasswordHasher.hash("penyerang-123")))

        assertTrue(LocalPasswordHasher.verify(password, db.userDao().getUserByEmail(email)!!.password))
    }

    @Test
    fun `guest accounts are never given a password through login or create password`() = runBlocking {
        // Tamu normalnya tak punya email sehingga tak terjangkau lewat identitas; data rusak/restore bisa saja
        // membuatnya terjangkau, jadi penolakannya harus berlaku di kedua jalur.
        val id = insertPasswordlessAccount(authType = AUTH_GUEST, accountEmail = "tamu-rusak@example.com")

        val login = repo.loginWithEmailAndPassword("tamu-rusak@example.com", "sandi-apa-saja-1").exceptionOrNull()
        assertTrue(login != null && login !is PasswordSetupRequiredException)
        assertTrue(repo.createPasswordForAccount("tamu-rusak@example.com", "sandi-baru-123", "sandi-baru-123").isFailure)

        val stored = storedUser(id)
        assertEquals("", stored.password)
        assertEquals(AUTH_GUEST, stored.authType)
        assertNull(db.userDao().getActiveUser())
    }

    @Test
    fun `create password for an unknown identity fails and creates nothing`() = runBlocking {
        assertTrue(repo.createPasswordForAccount("tidak-ada@example.com", "sandi-baru-123", "sandi-baru-123").isFailure)

        assertTrue(db.userDao().getAllUsersOnce().isEmpty())
    }

    @Test
    fun `a passwordless account can be reached by NIM for both login and create password`() = runBlocking {
        val id = insertPasswordlessAccount(authType = AUTH_LOCAL, accountEmail = "", nim = "TI-009")

        val error = repo.loginWithEmailAndPassword("  ti-009 ", "sandi-apa-saja-1").exceptionOrNull()
        assertTrue(error is PasswordSetupRequiredException)
        assertEquals("", storedUser(id).password)

        assertTrue(repo.createPasswordForAccount("  TI-009 ", "sandi-baru-123", "sandi-baru-123").isSuccess)
        repo.logout()
        assertTrue(repo.loginWithEmailAndPassword("ti-009", "sandi-baru-123").isSuccess)
    }

    @Test
    fun `accounts restored from a backup without a password must go through create password`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Akun lokal tanpa sandi pulih dengan password kosong, apa pun asalnya.
        val firstId = insertPasswordlessAccount(authType = AUTH_LOCAL, accountEmail = "pertama@example.com")
        val localId = insertPasswordlessAccount(authType = AUTH_LOCAL, accountEmail = "lokal@example.com")
        val output = ByteArrayOutputStream()
        LocalDataArchive.export(context, db, output)
        LocalDataArchive.restore(context, db, ByteArrayInputStream(output.toByteArray()))
        assertNull("Restore tanpa sesi aktif harus keluar dari semua akun", db.userDao().getActiveUser())
        assertEquals("", storedUser(firstId).password)
        assertEquals("", storedUser(localId).password)

        for ((id, restoredEmail) in listOf(firstId to "pertama@example.com", localId to "lokal@example.com")) {
            val error = repo.loginWithEmailAndPassword(restoredEmail, "sandi-apa-saja-1").exceptionOrNull()
            assertTrue("$restoredEmail harus diminta membuat sandi", error is PasswordSetupRequiredException)
            assertEquals("", storedUser(id).password)
            assertNull(db.userDao().getActiveUser())

            assertTrue(repo.createPasswordForAccount(restoredEmail, "sandi-baru-123", "sandi-baru-123").isSuccess)
            assertEquals(AUTH_LOCAL, storedUser(id).authType)
            assertTrue(LocalPasswordHasher.verify("sandi-baru-123", storedUser(id).password))
            assertNotNull(db.userDao().getActiveUser())
            repo.logout()
            assertTrue(repo.loginWithEmailAndPassword(restoredEmail, "sandi-baru-123").isSuccess)
            repo.logout()
        }
    }

    @Test
    fun `one time gate runs a task once and keeps keys independent`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("gate-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val gate = OneTimeTaskGate(prefs)

        assertFalse(gate.isDone("cleanup_user_1"))
        gate.markDone("cleanup_user_1")
        assertTrue(gate.isDone("cleanup_user_1"))
        assertFalse(gate.isDone("cleanup_user_2"))
    }
}
