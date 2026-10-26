package com.temankuliah.app

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.temankuliah.app.data.notifications.ReminderScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MainActivityTest {

    @Test
    fun handleOpenIntent_withExtraOpenTab_requestsOpenTabAndRemovesExtra() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            putExtra(ReminderScheduler.EXTRA_OPEN_TAB, 2)
        }

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(2, activity.viewModel.pendingOpenTab.value)
                assertFalse("EXTRA_OPEN_TAB harus dihapus dari Intent setelah diproses", intent.hasExtra(ReminderScheduler.EXTRA_OPEN_TAB))
            }
        }
    }

    @Test
    fun handleOpenIntent_withoutExtra_doesNotChangePendingTab() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                assertNull("Tanpa extra, pendingOpenTab harus bernilai null", activity.viewModel.pendingOpenTab.value)
            }
        }
    }

    @Test
    fun handleOpenIntent_directCall_withExtraAndWithoutExtra() {
        val baseIntent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
        ActivityScenario.launch<MainActivity>(baseIntent).use { scenario ->
            scenario.onActivity { activity ->
                val directIntent = Intent().apply {
                    putExtra(ReminderScheduler.EXTRA_OPEN_TAB, 2)
                }
                activity.handleOpenIntent(directIntent)
                assertEquals(2, activity.viewModel.pendingOpenTab.value)
                assertFalse(directIntent.hasExtra(ReminderScheduler.EXTRA_OPEN_TAB))

                activity.viewModel.consumePendingOpenTab()
                val emptyIntent = Intent()
                activity.handleOpenIntent(emptyIntent)
                assertNull(activity.viewModel.pendingOpenTab.value)

                activity.handleOpenIntent(null)
                assertNull(activity.viewModel.pendingOpenTab.value)
            }
        }
    }

    @Test
    fun manifest_mainActivity_isSingleTop() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val info = app.packageManager.getActivityInfo(ComponentName(app, MainActivity::class.java), 0)

        assertEquals("Ketukan notifikasi/widget tidak boleh menumpuk MainActivity", ActivityInfo.LAUNCH_SINGLE_TOP, info.launchMode)
    }

    @Test
    fun onNewIntent_whileRunning_opensTabFromNewIntentAndRemovesExtra() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val controller = Robolectric.buildActivity(MainActivity::class.java, Intent(app, MainActivity::class.java)).setup()
        val activity = controller.get()
        assertNull(activity.viewModel.pendingOpenTab.value)

        val reminder = Intent(app, MainActivity::class.java).putExtra(ReminderScheduler.EXTRA_OPEN_TAB, 1)
        controller.newIntent(reminder)

        assertEquals(1, activity.viewModel.pendingOpenTab.value)
        assertSame("getIntent() harus diganti dengan Intent terbaru", reminder, activity.intent)
        assertFalse(reminder.hasExtra(ReminderScheduler.EXTRA_OPEN_TAB))
        controller.destroy()
    }

    @Test
    fun onNewIntent_withoutExtra_keepsPendingTabEmpty() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val controller = Robolectric.buildActivity(MainActivity::class.java, Intent(app, MainActivity::class.java)).setup()

        // Ketukan widget: hanya membawa aplikasi ke depan, tanpa tab tujuan.
        controller.newIntent(Intent(app, MainActivity::class.java))

        assertNull(controller.get().viewModel.pendingOpenTab.value)
        controller.destroy()
    }

    @Test
    fun recreate_doesNotReopenTheTabFromTheLaunchIntent() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            putExtra(ReminderScheduler.EXTRA_OPEN_TAB, 2)
        }

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(2, activity.viewModel.pendingOpenTab.value)
                activity.viewModel.consumePendingOpenTab()
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertNull("Setelah dibuat ulang, tab tujuan tidak boleh dibuka lagi", activity.viewModel.pendingOpenTab.value)
            }
        }
    }
}
