package com.wasl.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.data.AddAttachmentCommand
import com.wasl.app.data.AttachmentIntegrity
import com.wasl.app.data.CreatePersonWithDebtCommand
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtId
import com.wasl.domain.Money
import com.wasl.domain.PersonId
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AttachmentStoreInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: WaslDatabase
    private lateinit var repository: RoomWaslRepository
    private lateinit var store: RoomAttachmentStore
    private lateinit var debtId: DebtId
    private val createdIds = mutableListOf<String>()

    @BeforeTest
    fun setUp() {
        databaseName = "wasl-attachment-${UUID.randomUUID()}.db"
        database = Room.databaseBuilder(context, WaslDatabase::class.java, databaseName)
            .addMigrations(*WaslDatabase.ALL_MIGRATIONS)
            .build()
        repository = RoomWaslRepository(database)
        store = RoomAttachmentStore(context, database)
        debtId = DebtId("debt-${UUID.randomUUID()}")
        runBlocking {
            repository.createPersonWithDebt(
                CreatePersonWithDebtCommand(
                    personId = PersonId("person-${UUID.randomUUID()}"),
                    debtId = debtId,
                    personName = "اختبار المرفقات",
                    direction = DebtDirection.RECEIVABLE,
                    originalAmount = Money(100_000L, CurrencyCode.YER),
                    openedAt = Instant.parse("2026-08-27T08:00:00Z"),
                    createdAt = Instant.parse("2026-08-27T08:00:00Z"),
                ),
            )
        }
    }

    @AfterTest
    fun tearDown() {
        createdIds.forEach { id -> File(context.filesDir, "attachments/$id.blob").delete() }
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun importedAttachmentIsPrivateHasHashAndDetectsTampering() = runBlocking {
        val id = UUID.randomUUID().toString().also(createdIds::add)
        val bytes = "wasl evidence payload".encodeToByteArray()
        val record = store.importAttachment(
            AddAttachmentCommand(
                id = id,
                debtId = debtId,
                displayName = "evidence.txt",
                mimeType = "text/plain",
                createdAt = Instant.parse("2026-08-27T09:00:00Z"),
            ),
            ByteArrayInputStream(bytes),
        )

        assertEquals(AttachmentIntegrity.OK, record.integrity)
        assertEquals(bytes.size.toLong(), record.sizeBytes)
        assertEquals("attachments/$id.blob", record.relativePath)
        assertEquals(64, record.sha256.length)
        assertEquals(1, store.observeForDebt(debtId).first().size)

        File(context.filesDir, record.relativePath).writeText("changed")
        assertEquals(AttachmentIntegrity.HASH_MISMATCH, store.findById(id)?.integrity)
    }

    @Test
    fun attachmentRejectsMissingDebtAndOversizedContent() = runBlocking {
        val missingId = UUID.randomUUID().toString().also(createdIds::add)
        assertFailsWith<IllegalArgumentException> {
            store.importAttachment(
                AddAttachmentCommand(
                    id = missingId,
                    debtId = DebtId("missing"),
                    displayName = "missing.pdf",
                    mimeType = "application/pdf",
                    createdAt = Instant.now(),
                ),
                ByteArrayInputStream(byteArrayOf(1, 2, 3)),
            )
        }

        val hugeId = UUID.randomUUID().toString().also(createdIds::add)
        val oversized = ByteArray((RoomAttachmentStore.MAX_ATTACHMENT_BYTES + 1).toInt())
        assertFailsWith<IllegalArgumentException> {
            store.importAttachment(
                AddAttachmentCommand(
                    id = hugeId,
                    debtId = debtId,
                    displayName = "huge.bin",
                    mimeType = "application/octet-stream",
                    createdAt = Instant.now(),
                ),
                ByteArrayInputStream(oversized),
            )
        }
    }
}
