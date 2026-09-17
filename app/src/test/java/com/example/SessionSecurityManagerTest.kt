package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.security.AutoLockTimeout
import com.example.core.security.LockState
import com.example.core.security.SecureKeyManager
import com.example.core.security.SessionSecurityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SessionSecurityManagerTest {

    private lateinit var context: Context
    private lateinit var secureKeyManager: SecureKeyManager
    private lateinit var sessionManager: SessionSecurityManager
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        secureKeyManager = SecureKeyManager()
        // Clear prefs before test
        context.getSharedPreferences("vault_security_session_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        sessionManager = SessionSecurityManager(context, secureKeyManager, scope)
    }

    @Test
    fun `initial state is SetupRequired when no PIN exists`() {
        assertTrue(
            "State should be SetupRequired when first launched",
            sessionManager.lockState.value is LockState.SetupRequired
        )
    }

    @Test
    fun `setting master pin transitions to Unlocked and allows future auth`() {
        val testPin = "987654"
        val setupResult = sessionManager.setupMasterPin(testPin)
        assertTrue("Setup PIN must succeed", setupResult)
        assertTrue("Session must be unlocked immediately after setup", sessionManager.lockState.value is LockState.Unlocked)

        // Lock manually
        sessionManager.lockVault()
        assertTrue("State must be Locked after manual lock", sessionManager.lockState.value is LockState.Locked)

        // Wrong PIN fails
        val wrongResult = sessionManager.authenticatePin("000000")
        assertFalse("Wrong PIN must fail", wrongResult)
        assertTrue("State must remain Locked", sessionManager.lockState.value is LockState.Locked)
        assertEquals(1, sessionManager.failedAttempts.value)

        // Correct PIN unlocks
        val correctResult = sessionManager.authenticatePin(testPin)
        assertTrue("Correct PIN must authenticate", correctResult)
        assertTrue("State must become Unlocked", sessionManager.lockState.value is LockState.Unlocked)
        assertEquals("Failed attempts must reset on success", 0, sessionManager.failedAttempts.value)
    }

    @Test
    fun `auto lock timeout preferences persist properly`() {
        sessionManager.setAutoLockTimeout(AutoLockTimeout.ONE_MINUTE)
        assertEquals(AutoLockTimeout.ONE_MINUTE, sessionManager.getAutoLockTimeout())

        sessionManager.setAutoLockTimeout(AutoLockTimeout.FIVE_MINUTES)
        assertEquals(AutoLockTimeout.FIVE_MINUTES, sessionManager.getAutoLockTimeout())
    }

    @Test
    fun `screen protection toggle persists`() {
        sessionManager.setScreenProtectionEnabled(true)
        assertTrue(sessionManager.isScreenProtectionEnabled())

        sessionManager.setScreenProtectionEnabled(false)
        assertFalse(sessionManager.isScreenProtectionEnabled())
    }

    @Test
    fun `emergency wipe resets to SetupRequired and clears session`() {
        sessionManager.setupMasterPin("123456")
        assertTrue(sessionManager.lockState.value is LockState.Unlocked)

        sessionManager.emergencyWipeAllSecuritySettings()
        assertTrue(
            "Wipe must reset state to SetupRequired",
            sessionManager.lockState.value is LockState.SetupRequired
        )
    }
}
