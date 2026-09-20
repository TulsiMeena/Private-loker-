package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.DuplicateCheckResult
import com.example.core.database.DuplicateResolution
import com.example.core.database.VaultDatabase
import com.example.core.database.VaultFolderEntity
import com.example.core.database.VaultRepository
import com.example.core.security.AndroidVaultKeyManager
import com.example.core.security.VaultAccessGate
import com.example.core.security.VaultIntegrityException
import com.example.core.security.VaultLockedException
import com.example.core.storage.VaultCategory
import com.example.core.storage.VaultObjectHeader
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
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VaultStorageEngineTest {

    private lateinit var context: Context
    private lateinit var keyManager: AndroidVaultKeyManager
    private var isUnlocked = true
    private lateinit var accessGate: VaultAccessGate
    private lateinit var tempFileManager: VaultTempFileManager
    private lateinit var storageEngine: VaultStorageEngine
    private lateinit var storageManager: VaultStorageManager
    private lateinit var database: VaultDatabase
    private lateinit var repository: VaultRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        keyManager = AndroidVaultKeyManager(keyAliasPrefix = "test_key_v")
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
            storageManager = storageManager
        )
    }

    @After
    fun tearDown() {
        database.close()
        storageEngine.purgeAllVaultFiles()
        tempFileManager.cleanupAbandonedTempFiles()
    }

    @Test
    fun `1 encryption and decryption preserves exact payload bytes`() {
        val originalText = "Top-secret vault financial record: 4239-0129-8831-2940. Confidential!"
        val originalBytes = originalText.toByteArray(Charsets.UTF_8)

        val importResult = storageEngine.importStream(
            inputStream = ByteArrayInputStream(originalBytes),
            expectedTotalBytes = originalBytes.size.toLong()
        )

        assertTrue("Encrypted file must exist", File(storageEngine.encryptedDir, importResult.relativePath).exists())
        assertEquals(originalBytes.size.toLong(), importResult.originalSizeBytes)

        val decryptedBytes = storageEngine.decryptToMemory(importResult.relativePath)
        val decryptedText = String(decryptedBytes, Charsets.UTF_8)
        assertEquals(originalText, decryptedText)
    }

    @Test
    fun `2 corrupted encrypted data fails integrity verification and emits no data`() {
        val sampleData = "Sensitive biometric and private health data payload".toByteArray()
        val importResult = storageEngine.importStream(ByteArrayInputStream(sampleData))

        val vaultFile = File(storageEngine.encryptedDir, importResult.relativePath)
        assertTrue(vaultFile.exists())

        // Tamper with a byte inside the ciphertext payload (past the 64-byte header)
        val raf = RandomAccessFile(vaultFile, "rw")
        val tamperOffset = 64L + 2L
        raf.seek(tamperOffset)
        val originalByte = raf.readByte()
        raf.seek(tamperOffset)
        raf.writeByte(originalByte.toInt() xor 0xFF)
        raf.close()

        try {
            storageEngine.decryptToMemory(importResult.relativePath)
            fail("Decryption should have thrown VaultIntegrityException due to GCM/checksum mismatch")
        } catch (e: Exception) {
            assertTrue("Expected integrity exception but got ${e::class.java.simpleName}", e is VaultIntegrityException || e is SecurityException)
        }
    }

    @Test
    fun `3 locked state access restrictions prevent cryptographic operations`() {
        val sampleData = "Protected documents".toByteArray()
        val importResult = storageEngine.importStream(ByteArrayInputStream(sampleData))

        // Lock the vault
        isUnlocked = false

        try {
            storageEngine.decryptToMemory(importResult.relativePath)
            fail("Accessing locked vault should throw VaultLockedException")
        } catch (e: VaultLockedException) {
            // Expected
            assertTrue(e.message?.contains("Vault is locked") == true)
        }
    }

    @Test
    fun `4 physical vault files are encrypted and contain no readable plaintext`() {
        val secretPhrase = "SUPER_SECRET_UNENCRYPTED_PHRASE_12345"
        val sampleData = secretPhrase.toByteArray()
        val importResult = storageEngine.importStream(ByteArrayInputStream(sampleData))

        val vaultFile = File(storageEngine.encryptedDir, importResult.relativePath)
        val fileBytes = vaultFile.readBytes()

        // Verify magic bytes header
        val magicString = String(fileBytes.take(6).toByteArray())
        assertEquals("PVAULT", magicString)

        // Verify secret phrase does NOT appear in raw disk bytes
        val diskContent = String(fileBytes)
        assertFalse("Raw disk file must not contain plaintext string", diskContent.contains(secretPhrase))
    }

    @Test
    fun `5 import cancellation shreds temporary container and leaves no orphaned files`() {
        val sampleData = ByteArray(1024 * 64) { it.toByte() }

        var cancelled = false
        try {
            storageEngine.importStream(
                inputStream = ByteArrayInputStream(sampleData),
                isCancelled = {
                    cancelled = true
                    true
                }
            )
            fail("Expected cancellation exception")
        } catch (_: Exception) {
            // Expected
        }

        // Verify no leftover .tmp_enc or .pvault files
        val files = storageEngine.encryptedDir.listFiles() ?: emptyArray()
        assertEquals("No incomplete vault artifacts should remain after cancellation", 0, files.size)
    }

    @Test
    fun `6 duplicate detection identifies existing checksum and name conflicts`() = runBlocking {
        val originalData = "Unique quarterly report contents".toByteArray()
        val importResult = repository.importItem(
            title = "Quarterly_Report.pdf",
            category = VaultCategory.DOCUMENT,
            mimeType = "application/pdf",
            inputStream = ByteArrayInputStream(originalData)
        )
        assertTrue(importResult.isSuccess)
        val item = importResult.getOrThrow()

        // Check duplicate with same checksum
        val checksumCheck = repository.checkForDuplicate(
            checksumSha256 = item.checksumSha256,
            title = "Different_Name.pdf"
        )
        assertTrue(checksumCheck is DuplicateCheckResult.ContentDuplicate)

        // Check duplicate with same filename
        val nameCheck = repository.checkForDuplicate(
            checksumSha256 = "different_checksum_hash_here",
            title = "Quarterly_Report.pdf"
        )
        assertTrue(nameCheck is DuplicateCheckResult.NameConflict)

        // Check no duplicate
        val noneCheck = repository.checkForDuplicate(
            checksumSha256 = "non_existent_hash",
            title = "Brand_New_File.txt"
        )
        assertTrue(noneCheck is DuplicateCheckResult.None)
    }

    @Test
    fun `7 large file streaming simulation without memory loading`() {
        val largeSize = 2 * 1024 * 1024 // 2 MB
        val streamData = object : java.io.InputStream() {
            private var bytesRead = 0
            override fun read(): Int {
                return if (bytesRead++ < largeSize) (bytesRead % 255) else -1
            }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (bytesRead >= largeSize) return -1
                val toRead = minOf(len, largeSize - bytesRead)
                for (i in 0 until toRead) {
                    b[off + i] = ((bytesRead + i) % 255).toByte()
                }
                bytesRead += toRead
                return toRead
            }
        }

        var progressCalls = 0
        val importResult = storageEngine.importStream(
            inputStream = streamData,
            expectedTotalBytes = largeSize.toLong(),
            onProgress = { _, _, _ -> progressCalls++ }
        )

        assertEquals(largeSize.toLong(), importResult.originalSizeBytes)
        assertTrue("Progress callbacks should fire during chunk streaming", progressCalls > 0)

        // Stream decrypt to verify
        val outSink = ByteArrayOutputStream()
        val decryptedCount = storageEngine.decryptToStream(importResult.relativePath, outSink)
        assertEquals(largeSize.toLong(), decryptedCount)
        assertEquals(largeSize, outSink.size())
    }

    @Test
    fun `8 metadata persistence in Room DB`() = runBlocking {
        val data = "Metadata test note".toByteArray()
        val res = repository.importItem(
            title = "secret_note.txt",
            category = VaultCategory.TEXT,
            mimeType = "text/plain",
            inputStream = ByteArrayInputStream(data)
        )
        assertTrue(res.isSuccess)
        val entity = res.getOrThrow()

        val allItems = repository.allItems.first()
        assertEquals(1, allItems.size)
        assertEquals("secret_note.txt", allItems[0].title)
        assertEquals(VaultCategory.TEXT.name, allItems[0].category)
        assertTrue(allItems[0].encryptedPath.endsWith(".pvault"))
    }

    @Test
    fun `9 folder operations move and query items correctly`() = runBlocking {
        val folderRes = repository.createFolder("Finances")
        assertTrue(folderRes.isSuccess)
        val folder = folderRes.getOrThrow()

        val data = "Invoice 2026".toByteArray()
        val itemRes = repository.importItem(
            title = "Invoice.pdf",
            category = VaultCategory.DOCUMENT,
            mimeType = "application/pdf",
            inputStream = ByteArrayInputStream(data)
        )
        val item = itemRes.getOrThrow()

        // Move to folder
        repository.moveItemToFolder(item.id, folder.id)

        val folderItems = repository.getItemsInFolder(folder.id).first()
        assertEquals(1, folderItems.size)
        assertEquals(item.id, folderItems[0].id)
    }

    @Test
    fun `10 trash and restore keeps encrypted asset and metadata`() = runBlocking {
        val data = "Tax statement".toByteArray()
        val itemRes = repository.importItem(
            title = "Tax.pdf",
            category = VaultCategory.DOCUMENT,
            mimeType = "application/pdf",
            inputStream = ByteArrayInputStream(data)
        )
        val item = itemRes.getOrThrow()

        // Move to trash
        repository.moveToTrash(item)

        val activeItems = repository.allItems.first()
        assertEquals("Item should be hidden from active vault view", 0, activeItems.size)

        val trashItems = repository.trashItems.first()
        assertEquals("Item must appear in trash", 1, trashItems.size)
        assertEquals(item.id, trashItems[0].id)

        // Restore
        repository.restoreFromTrash(item)
        val restoredActive = repository.allItems.first()
        assertEquals("Item must return to active vault view", 1, restoredActive.size)
    }

    @Test
    fun `11 permanent deletion shreds physical encrypted file`() = runBlocking {
        val data = "Shred this immediately".toByteArray()
        val itemRes = repository.importItem(
            title = "BurnAfterReading.txt",
            category = VaultCategory.TEXT,
            mimeType = "text/plain",
            inputStream = ByteArrayInputStream(data)
        )
        val item = itemRes.getOrThrow()
        val physicalFile = File(storageEngine.encryptedDir, item.encryptedPath)
        assertTrue(physicalFile.exists())

        repository.permanentDeleteItem(item)

        assertFalse("Physical encrypted container must be wiped and shredded", physicalFile.exists())
        val allItems = repository.allItems.first()
        assertEquals(0, allItems.size)
    }

    @Test
    fun `12 temporary file manager cleans abandoned working files`() {
        val temp1 = tempFileManager.createTransientFile("pdf")
        val temp2 = tempFileManager.createTransientFile("png")
        temp1.writeText("transient data 1")
        temp2.writeText("transient data 2")

        assertTrue(temp1.exists())
        assertTrue(temp2.exists())

        tempFileManager.cleanupAbandonedTempFiles()

        assertFalse("Transient file 1 should be shredded", temp1.exists())
        assertFalse("Transient file 2 should be shredded", temp2.exists())
    }

    @Test
    fun `13 updateFileContent re-encrypts payload and replaces original container safely`() = runBlocking {
        val originalContent = "Original source code"
        val item = repository.createNewFile(
            title = "test_script.py",
            content = originalContent,
            category = VaultCategory.CODE
        ).getOrThrow()

        val originalPath = item.encryptedPath

        val updatedContent = "Updated source code with patch applied"
        val updatedItem = repository.updateFileContent(item, updatedContent).getOrThrow()

        val decryptedBytes = repository.decryptItemBytes(updatedItem).getOrThrow()
        val decryptedText = String(decryptedBytes, Charsets.UTF_8)
        assertEquals(updatedContent, decryptedText)
    }

    @Test
    fun `14 consistency check reports accurate health status`() = runBlocking {
        val item1 = repository.createNewFile("note1.txt", "Content 1", VaultCategory.TEXT).getOrThrow()
        val item2 = repository.createNewFile("note2.txt", "Content 2", VaultCategory.TEXT).getOrThrow()

        val reportBefore = repository.performConsistencyCheck()
        assertTrue("Vault must be consistent", reportBefore.isConsistent)
        assertEquals(2, reportBefore.healthyCount)
        assertEquals(0, reportBefore.missingPhysicalFiles.size)
        assertEquals(0, reportBefore.orphanedPhysicalFiles.size)
    }

    @Test
    fun `15 verify PDF and image uploads encrypt store and decrypt successfully`() = runBlocking {
        // Test PDF Document Upload & Retrieval
        val samplePdfContent = "%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n%%EOF".toByteArray(Charsets.UTF_8)
        val pdfResult = repository.importFileFromStream(
            stream = ByteArrayInputStream(samplePdfContent),
            fileName = "Official_Statement.pdf",
            mimeType = "application/pdf"
        )
        assertTrue("PDF upload must succeed", pdfResult.isSuccess)
        val pdfItem = pdfResult.getOrThrow()
        assertEquals("Official_Statement.pdf", pdfItem.title)
        assertEquals("DOCUMENT", pdfItem.category)
        assertEquals(samplePdfContent.size.toLong(), pdfItem.sizeBytes)

        val decryptedPdf = repository.decryptItemBytes(pdfItem).getOrThrow()
        assertEquals(String(samplePdfContent), String(decryptedPdf))

        // Test Image Upload & Retrieval
        val sampleImageBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D)
        val imageResult = repository.importFileFromStream(
            stream = ByteArrayInputStream(sampleImageBytes),
            fileName = "Confidential_Photo.png",
            mimeType = "image/png"
        )
        assertTrue("Image upload must succeed", imageResult.isSuccess)
        val imageItem = imageResult.getOrThrow()
        assertEquals("Confidential_Photo.png", imageItem.title)
        assertEquals("IMAGE", imageItem.category)
        assertEquals(sampleImageBytes.size.toLong(), imageItem.sizeBytes)

        val decryptedImage = repository.decryptItemBytes(imageItem).getOrThrow()
        assertTrue("Decrypted image bytes must match sample", sampleImageBytes.contentEquals(decryptedImage))

        // Verify both appear in allDocuments / allMedia flows
        val allDocs = repository.allDocuments.first()
        assertTrue("allDocuments must contain uploaded PDF", allDocs.any { it.id == pdfItem.id })

        val allMedia = repository.allMedia.first()
        assertTrue("allMedia must contain uploaded image", allMedia.any { it.id == imageItem.id })
    }
}
