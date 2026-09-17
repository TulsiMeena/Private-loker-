package com.example.feature.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.BackupHistoryDao
import com.example.core.database.SecurityAuditDao
import com.example.core.database.VaultDao
import com.example.core.database.VaultDatabase
import com.example.core.database.VaultFolderDao
import com.example.core.database.VaultItemEntity
import com.example.core.database.VaultRepository
import com.example.core.security.AndroidVaultKeyManager
import com.example.core.security.SecureKeyManager
import com.example.core.security.SessionSecurityManager
import com.example.core.security.VaultAccessGate
import com.example.core.storage.VaultCategory
import com.example.core.storage.VaultStorageEngine
import com.example.core.storage.VaultStorageManager
import com.example.core.storage.VaultTempFileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecureBackupManagerTest {

    private lateinit var context: Context
    private lateinit var db: VaultDatabase
    private lateinit var vaultDao: VaultDao
    private lateinit var vaultFolderDao: VaultFolderDao
    private lateinit var securityAuditDao: SecurityAuditDao
    private lateinit var backupHistoryDao: BackupHistoryDao
    private lateinit var storageManager: VaultStorageManager
    private lateinit var sessionManager: SessionSecurityManager
    private lateinit var vaultRepository: VaultRepository
    private lateinit var reminderManager: BackupReminderManager
    private lateinit var backupManager: SecureBackupManager
    private lateinit var keyManager: AndroidVaultKeyManager
    private lateinit var secureKeyManager: SecureKeyManager
    private lateinit var storageEngine: VaultStorageEngine
    private lateinit var accessGate: VaultAccessGate
    private lateinit var tempFileManager: VaultTempFileManager

    private val testScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("vault_security_session_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        keyManager = AndroidVaultKeyManager(keyAliasPrefix = "test_backup_v")
        accessGate = VaultAccessGate(keyManager) { true }
        tempFileManager = VaultTempFileManager(context)
        storageEngine = VaultStorageEngine(context, accessGate, tempFileManager)
        storageManager = VaultStorageManager(context, keyManager, accessGate, storageEngine)

        db = Room.inMemoryDatabaseBuilder(context, VaultDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        vaultDao = db.vaultDao()
        vaultFolderDao = db.vaultFolderDao()
        securityAuditDao = db.securityAuditDao()
        backupHistoryDao = db.backupHistoryDao()

        secureKeyManager = SecureKeyManager()
        sessionManager = SessionSecurityManager(context, secureKeyManager, testScope)
        sessionManager.setupMasterPin("123456")

        vaultRepository = VaultRepository(
            vaultDao = vaultDao,
            vaultFolderDao = vaultFolderDao,
            securityAuditDao = securityAuditDao,
            storageManager = storageManager,
            documentBookmarkDao = db.documentBookmarkDao(),
            backupHistoryDao = backupHistoryDao
        )

        reminderManager = BackupReminderManager(context)

        backupManager = SecureBackupManager(
            context = context,
            vaultRepository = vaultRepository,
            storageManager = storageManager,
            backupHistoryDao = backupHistoryDao,
            securityAuditDao = securityAuditDao,
            vaultDao = vaultDao,
            vaultFolderDao = vaultFolderDao,
            sessionManager = sessionManager,
            reminderManager = reminderManager
        )
    }

    @After
    fun tearDown() {
        db.close()
        storageEngine.purgeAllVaultFiles()
        tempFileManager.cleanupAbandonedTempFiles()
    }

    @Test
    fun testCryptoHelper_deriveKeyAndEncryption() {
        val passphrase = "SecurePassword123!".toCharArray()
        val salt = BackupCryptoHelper.generateSalt()
        val bmk = BackupCryptoHelper.deriveBackupKey(passphrase, salt)

        // Encrypt test payload
        val originalData = "TopSecretPersonalDocument".toByteArray(Charsets.UTF_8)
        val encryptedData = BackupCryptoHelper.encryptBytes(originalData, bmk)
        val decryptedData = BackupCryptoHelper.decryptBytes(encryptedData, bmk)

        assertArrayEquals(originalData, decryptedData)
    }

    @Test
    fun testCryptoHelper_wrongPassphraseThrowsException() {
        val passphrase1 = "CorrectPassword123".toCharArray()
        val passphrase2 = "WrongPassword999".toCharArray()
        val salt = BackupCryptoHelper.generateSalt()

        val bmk1 = BackupCryptoHelper.deriveBackupKey(passphrase1, salt)
        val bmk2 = BackupCryptoHelper.deriveBackupKey(passphrase2, salt)

        val originalData = "ImportantContract".toByteArray(Charsets.UTF_8)
        val encryptedData = BackupCryptoHelper.encryptBytes(originalData, bmk1)

        try {
            BackupCryptoHelper.decryptBytes(encryptedData, bmk2)
            org.junit.Assert.fail("Expected decryption with wrong key to throw exception")
        } catch (_: Exception) {
            // Expected: AEAD tag verification failure
        }
    }

    @Test
    fun testReminderManager_scheduleIntervals() {
        reminderManager.setSchedule(BackupReminderSchedule.WEEKLY)
        assertEquals(BackupReminderSchedule.WEEKLY, reminderManager.schedule.value)

        // Never backed up -> isBackupDue should be true for WEEKLY
        assertTrue(reminderManager.isBackupDue(null))

        // Backed up just now -> not due
        assertFalse(reminderManager.isBackupDue(System.currentTimeMillis()))

        // Backed up 8 days ago -> due for weekly
        val eightDaysAgo = System.currentTimeMillis() - (8L * 24 * 60 * 60 * 1000)
        assertTrue(reminderManager.isBackupDue(eightDaysAgo))

        // Disabled schedule
        reminderManager.setSchedule(BackupReminderSchedule.OFF)
        assertFalse(reminderManager.isBackupDue(eightDaysAgo))
    }

    @Test
    fun testBackupAndRestore_endToEndFlow() {
        runBlocking {
            // 1. Insert initial items into vault
            val itemContent = "Confidential financial statement 2026"
            val importResult = storageManager.importAndEncrypt(
                inputStream = ByteArrayInputStream(itemContent.toByteArray(Charsets.UTF_8)),
                category = VaultCategory.DOCUMENT,
                expectedSizeBytes = itemContent.length.toLong()
            )

            val itemId = vaultDao.insertItem(
                VaultItemEntity(
                    title = "Financials.pdf",
                    category = VaultCategory.DOCUMENT.name,
                    encryptedPath = importResult.relativePath,
                    mimeType = "application/pdf",
                    sizeBytes = itemContent.length.toLong(),
                    createdAt = System.currentTimeMillis(),
                    modifiedAt = System.currentTimeMillis(),
                    isFavorite = true,
                    checksumSha256 = importResult.checksumSha256
                )
            )

            assertTrue(itemId > 0)

            // 2. Prepare backup
            val prepareResult = backupManager.prepareBackup()
            assertTrue(prepareResult.isSuccess)
            val (itemCount, sizeBytes) = prepareResult.getOrThrow()
            assertEquals(1, itemCount)
            assertTrue(sizeBytes > 0)

            // 3. Create Backup File
            val backupFile = File(context.cacheDir, "test_backup_${System.currentTimeMillis()}.pvault")
            val backupUri = Uri.fromFile(backupFile)
            val passphrase = "BackupSecretPassword123!".toCharArray()

            val createResult = backupManager.createBackupSaf(
                destinationUri = backupUri,
                protectionType = BackupProtectionType.PASSPHRASE,
                secretChars = passphrase,
                destinationLabel = "Local Test Storage"
            )

            assertTrue("Backup creation should succeed", createResult.isSuccess)
            assertTrue("Backup file must exist on disk", backupFile.exists())
            assertTrue("Backup file size must be > 0", backupFile.length() > 0)

            // 4. Verify Backup Integrity
            val report = backupManager.verifyBackupSaf(
                backupUri = backupUri,
                secretChars = passphrase
            )
            assertEquals(BackupVerificationStatus.VERIFIED, report.status)
            assertEquals(1, report.objectCount)

            // 5. Inspect Backup for Restore Preview
            val inspectResult = backupManager.inspectBackupForRestore(backupUri, passphrase)
            assertTrue(inspectResult.isSuccess)
            val preview = inspectResult.getOrThrow()
            assertEquals(1, preview.objectCount)
            assertEquals(BackupProtectionType.PASSPHRASE, preview.protectionType)

            // 6. Test Restore in MERGE mode
            val restoreResult = backupManager.restoreBackupSaf(
                backupUri = backupUri,
                secretChars = passphrase,
                restoreMode = RestoreMode.MERGE_VAULT,
                conflictResolution = RestoreConflictResolution.KEEP_BOTH
            )

            assertTrue("Restore operation should succeed", restoreResult.isSuccess)
            val restoreStats = restoreResult.getOrThrow()
            assertTrue(restoreStats.restoredCount >= 1)

            // Vault now has items
            val allItems = vaultDao.getAllItemsList()
            assertTrue("Vault items must be present after restore", allItems.isNotEmpty())

            // 7. Test Verification of Corrupted File (truncated file)
            val corruptedFile = File(context.cacheDir, "corrupted_${System.currentTimeMillis()}.pvault").apply {
                writeBytes(backupFile.readBytes().copyOf(50)) // truncated
            }
            val corruptedReport = backupManager.verifyBackupSaf(
                backupUri = Uri.fromFile(corruptedFile),
                secretChars = passphrase
            )
            assertEquals(BackupVerificationStatus.CORRUPTED, corruptedReport.status)

            // Clean up backup files
            backupFile.delete()
            corruptedFile.delete()
        }
    }
}
