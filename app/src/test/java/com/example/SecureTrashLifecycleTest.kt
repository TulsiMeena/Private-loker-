package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.VaultDatabase
import com.example.core.database.VaultFolderEntity
import com.example.core.database.VaultItemEntity
import com.example.core.database.VaultRepository
import com.example.core.lifecycle.AutoCleanRetention
import com.example.core.lifecycle.DataLifecycleManager
import com.example.core.lifecycle.RestoreConflictResolution
import com.example.core.lifecycle.RestoreOutcome
import com.example.core.security.AndroidVaultKeyManager
import com.example.core.security.VaultAccessGate
import com.example.core.storage.VaultCategory
import com.example.core.storage.VaultStorageEngine
import com.example.core.storage.VaultStorageManager
import com.example.core.storage.VaultTempFileManager
import kotlinx.coroutines.flow.first
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
import java.io.ByteArrayInputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecureTrashLifecycleTest {

    private lateinit var context: Context
    private lateinit var keyManager: AndroidVaultKeyManager
    private var isUnlocked = true
    private lateinit var accessGate: VaultAccessGate
    private lateinit var tempFileManager: VaultTempFileManager
    private lateinit var storageEngine: VaultStorageEngine
    private lateinit var storageManager: VaultStorageManager
    private lateinit var database: VaultDatabase
    private lateinit var repository: VaultRepository
    private lateinit var lifecycleManager: DataLifecycleManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        keyManager = AndroidVaultKeyManager(keyAliasPrefix = "test_lifecycle_key")
        isUnlocked = true
        accessGate = VaultAccessGate(keyManager) { isUnlocked }
        tempFileManager = VaultTempFileManager(context)
        storageEngine = VaultStorageEngine(context, accessGate, tempFileManager)
        storageManager = VaultStorageManager(context, keyManager, accessGate, storageEngine)

        database = Room.inMemoryDatabaseBuilder(context, VaultDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        repository = VaultRepository(
            vaultDao = database.vaultDao(),
            vaultFolderDao = database.vaultFolderDao(),
            securityAuditDao = database.securityAuditDao(),
            storageManager = storageManager,
            documentBookmarkDao = database.documentBookmarkDao(),
            backupHistoryDao = database.backupHistoryDao()
        )

        lifecycleManager = DataLifecycleManager(
            context = context,
            vaultDao = database.vaultDao(),
            vaultFolderDao = database.vaultFolderDao(),
            securityAuditDao = database.securityAuditDao(),
            storageManager = storageManager
        )
    }

    @After
    fun tearDown() {
        database.close()
        storageManager.encryptedDir.deleteRecursively()
    }

    private fun importTestFile(title: String, content: String, folderId: Long? = null): VaultItemEntity = runBlocking {
        val bytes = content.toByteArray(Charsets.UTF_8)
        val importRes = repository.importItem(
            title = title,
            category = VaultCategory.DOCUMENT,
            mimeType = "text/plain",
            inputStream = ByteArrayInputStream(bytes),
            folderId = folderId,
            expectedSizeBytes = bytes.size.toLong()
        ).getOrThrow()
        importRes
    }

    @Test
    fun testMoveToTrashPreservesPhysicalFileAndSetsMetadata() = runBlocking {
        val item = importTestFile("secret.txt", "Top Secret Content")
        val physicalFile = File(storageManager.encryptedDir, item.encryptedPath)
        assertTrue("Physical encrypted file must exist", physicalFile.exists())

        // Move to trash
        val result = repository.moveToTrash(item)
        assertTrue("Move to trash should succeed", result.isSuccess)

        // Item should disappear from active items and appear in trashItems
        val activeItems = repository.allItems.first()
        val trashItems = repository.trashItems.first()

        assertFalse("Item should no longer be in active vault items", activeItems.any { it.id == item.id })
        assertTrue("Item must be present in trashItems", trashItems.any { it.id == item.id })

        val trashedItem = trashItems.first { it.id == item.id }
        assertTrue("isTrash must be true", trashedItem.isTrash)
        assertNotNull("deletedAt must be set", trashedItem.deletedAt)
        assertTrue("Physical encrypted container remains in vault during trash stage", physicalFile.exists())
    }

    @Test
    fun testRestoreWithoutConflictRestoresToOriginalFolder() = runBlocking {
        val folder = repository.createFolder("Finances").getOrThrow()
        val item = importTestFile("budget.pdf", "Financial Data", folderId = folder.id)

        repository.moveToTrash(item)
        val trashedItem = repository.trashItems.first().first { it.id == item.id }
        assertEquals(folder.id, trashedItem.originalFolderId)

        // Restore
        val outcome = repository.restoreFromTrash(trashedItem)
        assertTrue("Restore should succeed", outcome is RestoreOutcome.Success)
        val restored = (outcome as RestoreOutcome.Success).item

        assertEquals(folder.id, restored.folderId)
        assertFalse("isTrash must be false", restored.isTrash)
        assertNull("deletedAt must be null", restored.deletedAt)

        val activeInFolder = repository.getItemsInFolder(folder.id).first()
        assertTrue("Restored item must appear in target folder", activeInFolder.any { it.id == item.id })
    }

    @Test
    fun testRestoreWhenOriginalFolderDeletedRestoresToRoot() = runBlocking {
        val folder = repository.createFolder("TemporaryFolder").getOrThrow()
        val item = importTestFile("notes.txt", "Important Notes", folderId = folder.id)

        repository.moveToTrash(item)
        // Delete original folder while item is in trash
        repository.deleteFolder(folder)

        val trashedItem = repository.trashItems.first().first { it.id == item.id }
        val outcome = repository.restoreFromTrash(trashedItem)

        assertTrue("Restore should succeed", outcome is RestoreOutcome.Success)
        val success = outcome as RestoreOutcome.Success
        assertTrue("Must indicate restored to root because original folder is gone", success.restoredToRoot)
        assertNull("folderId must be null (root)", success.item.folderId)
    }

    @Test
    fun testRestoreConflictDetectionAndResolutionKeepBoth() = runBlocking {
        // Create initial item and move to trash
        val item1 = importTestFile("report.docx", "Version 1 Content")
        repository.moveToTrash(item1)

        // Create new item in active vault with the SAME name
        val item2 = importTestFile("report.docx", "Version 2 Content")

        val trashedItem = repository.trashItems.first().first { it.id == item1.id }

        // Attempt restore without resolution specified -> must return Conflict
        val conflictOutcome = repository.restoreFromTrash(trashedItem, resolution = null)
        assertTrue("Must detect filename collision", conflictOutcome is RestoreOutcome.Conflict)
        val conflict = conflictOutcome as RestoreOutcome.Conflict
        assertEquals("report.docx", conflict.itemToRestore.title)
        assertEquals(item2.id, conflict.existingConflictItem.id)

        // Resolve with KEEP_BOTH -> must rename restored item
        val resolvedOutcome = repository.restoreFromTrash(trashedItem, resolution = RestoreConflictResolution.KEEP_BOTH)
        assertTrue("Restore with KEEP_BOTH must succeed", resolvedOutcome is RestoreOutcome.Success)
        val success = resolvedOutcome as RestoreOutcome.Success
        assertTrue("Must be marked as renamed", success.renamed)
        assertEquals("report (restored).docx", success.item.title)

        // Verify both items are now active in the root vault
        val activeItems = repository.allItems.first()
        assertTrue(activeItems.any { it.title == "report.docx" && it.id == item2.id })
        assertTrue(activeItems.any { it.title == "report (restored).docx" && it.id == item1.id })
    }

    @Test
    fun testRestoreConflictResolutionReplace() = runBlocking {
        val item1 = importTestFile("plan.txt", "Old Plan")
        repository.moveToTrash(item1)

        val item2 = importTestFile("plan.txt", "New Plan")
        val trashedItem = repository.trashItems.first().first { it.id == item1.id }

        // Resolve with REPLACE -> old active file (item2) moved to trash, item1 restored
        val outcome = repository.restoreFromTrash(trashedItem, resolution = RestoreConflictResolution.REPLACE)
        assertTrue("Restore with REPLACE must succeed", outcome is RestoreOutcome.Success)

        val activeItems = repository.allItems.first()
        val trashItems = repository.trashItems.first()

        assertTrue("item1 must be active with original title", activeItems.any { it.id == item1.id && it.title == "plan.txt" })
        assertTrue("item2 must be moved to trash", trashItems.any { it.id == item2.id })
    }

    @Test
    fun testPermanentDeleteShredsPhysicalEncryptedFileAndPurgesDatabase() = runBlocking {
        val item = importTestFile("to_shred.bin", "Sensitive Data to Shred")
        val physicalFile = File(storageManager.encryptedDir, item.encryptedPath)
        assertTrue(physicalFile.exists())

        repository.moveToTrash(item)
        val trashedItem = repository.trashItems.first().first { it.id == item.id }

        val deleteResult = repository.permanentDeleteItem(trashedItem)
        assertTrue("Permanent delete should succeed", deleteResult.isSuccess)

        // Physical encrypted container must be deleted
        assertFalse("Physical encrypted container must be deleted from storage", physicalFile.exists())

        // Database record must be purged
        assertNull("Database record must be deleted", database.vaultDao().getItemById(item.id))
        val trashItems = repository.trashItems.first()
        assertFalse("Item must not be in trash", trashItems.any { it.id == item.id })
    }

    @Test
    fun testEmptyTrashShredsAllItemsAndReturnsReport() = runBlocking {
        val item1 = importTestFile("file1.txt", "Content 1")
        val item2 = importTestFile("file2.txt", "Content 2")
        val item3 = importTestFile("file3.txt", "Content 3")

        repository.moveToTrash(item1)
        repository.moveToTrash(item2)
        repository.moveToTrash(item3)

        val initialTrash = repository.trashItems.first()
        assertEquals(3, initialTrash.size)

        val reportResult = repository.emptyTrash()
        assertTrue(reportResult.isSuccess)
        val report = reportResult.getOrThrow()

        assertEquals(3, report.deletedCount)
        assertEquals(0, report.failedCount)
        assertTrue(report.freedBytes > 0)
        assertTrue(report.isFullSuccess)

        val remainingTrash = repository.trashItems.first()
        assertTrue("Trash must be empty after emptyTrash()", remainingTrash.isEmpty())

        assertFalse(File(storageManager.encryptedDir, item1.encryptedPath).exists())
        assertFalse(File(storageManager.encryptedDir, item2.encryptedPath).exists())
        assertFalse(File(storageManager.encryptedDir, item3.encryptedPath).exists())
    }

    @Test
    fun testAutoCleanRetentionPolicyPurgesOnlyExpiredItems() = runBlocking {
        val oldItem = importTestFile("old_trash.txt", "Old Content")
        val newItem = importTestFile("new_trash.txt", "Recent Content")

        repository.moveToTrash(oldItem)
        repository.moveToTrash(newItem)

        // Simulate oldItem deleted 40 days ago
        val fortyDaysAgo = System.currentTimeMillis() - (40L * 24 * 60 * 60 * 1000)
        database.vaultDao().moveToTrash(oldItem.id, fortyDaysAgo)

        // Configure policy to 30 days
        lifecycleManager.setAutoCleanPolicy(AutoCleanRetention.DAYS_30)
        assertEquals(AutoCleanRetention.DAYS_30, lifecycleManager.getAutoCleanPolicy())

        val cleanedResult = lifecycleManager.executeAutoClean()
        assertTrue(cleanedResult.isSuccess)
        assertEquals(1, cleanedResult.getOrThrow())

        val trashRemaining = repository.trashItems.first()
        assertEquals("Only non-expired item should remain in trash", 1, trashRemaining.size)
        assertEquals(newItem.id, trashRemaining.first().id)
    }

    @Test
    fun testOrphanScanAndRecovery() = runBlocking {
        // Create an unlinked .pvault file directly in storage
        val fakeOrphan = File(storageManager.encryptedDir, "orphan_vault_123.pvault").apply {
            writeBytes("raw simulated encrypted bytes".toByteArray())
        }
        // Create an incomplete stream file (.tmp_enc)
        val incompleteFile = File(storageManager.encryptedDir, "incomplete_stream.tmp_enc").apply {
            writeBytes("partial".toByteArray())
        }

        val scan = lifecycleManager.scanForOrphansAndInconsistencies()
        assertTrue("Must detect unlinked .pvault file", scan.unlinkedContainers.any { it.name == fakeOrphan.name })
        assertTrue("Must detect incomplete .tmp_enc file", scan.incompleteOperations.any { it.name == incompleteFile.name })
        assertTrue(scan.hasIssues)

        // Recover unlinked container
        val recoverResult = lifecycleManager.recoverOrphanedContainers(scan.unlinkedContainers)
        assertTrue(recoverResult.isSuccess)
        assertEquals(1, recoverResult.getOrThrow())

        // Purge incomplete operations
        val purgeResult = lifecycleManager.purgeIncompleteOperations(scan.incompleteOperations)
        assertTrue(purgeResult.isSuccess)
        assertEquals(1, purgeResult.getOrThrow())
        assertFalse(incompleteFile.exists())

        // Verify recovered item is now present in root vault
        val activeItems = repository.allItems.first()
        assertTrue(activeItems.any { it.title.startsWith("Recovered_orphan_vault_123") })
    }

    @Test
    fun testStorageSummaryReportsRealValues() = runBlocking {
        val active = importTestFile("active.txt", "Active Data")
        val trashed = importTestFile("trashed.txt", "Trashed Data")
        repository.moveToTrash(trashed)

        val summary = lifecycleManager.getStorageSummary()
        assertEquals(1, summary.activeItemCount)
        assertEquals(1, summary.trashItemCount)
        assertEquals(active.sizeBytes, summary.vaultBytes)
        assertEquals(trashed.sizeBytes, summary.trashBytes)
        assertTrue("Device available storage must be positive", summary.availableDeviceBytes > 0)
    }
}
