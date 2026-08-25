package com.wasl.app.backup

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BackupEnvelopeTest {
    @Test
    fun roundTripPreservesArabicAndMetadata() {
        val payload = BackupPayload(
            schemaVersion = 6,
            tables = listOf(
                TableDump(
                    name = "persons",
                    columns = listOf("id", "display_name"),
                    rows = listOf(
                        listOf(
                            BackupCell(BackupCellType.TEXT, "person-1"),
                            BackupCell(BackupCellType.TEXT, "أحمد اليماني"),
                        ),
                    ),
                ),
            ),
            documentFiles = emptyList(),
        )
        val createdAt = Instant.parse("2026-08-25T12:34:56Z")
        val password = "correct horse battery staple".toCharArray()

        val sealed = BackupEnvelope.seal(payload, createdAt, password)
        val opened = BackupEnvelope.open(sealed, password)

        assertEquals(createdAt, opened.createdAt)
        assertEquals(payload, opened.payload)
    }

    @Test
    fun wrongPasswordCannotDecryptBackup() {
        val payload = samplePayload()
        val sealed = BackupEnvelope.seal(
            payload,
            Instant.parse("2026-08-25T12:34:56Z"),
            "correct-password".toCharArray(),
        )

        assertFailsWith<SecurityException> {
            BackupEnvelope.open(sealed, "wrong-password".toCharArray())
        }
    }

    @Test
    fun tamperingIsDetectedBeforePayloadIsAccepted() {
        val password = "correct-password".toCharArray()
        val sealed = BackupEnvelope.seal(
            samplePayload(),
            Instant.parse("2026-08-25T12:34:56Z"),
            password,
        )
        sealed[sealed.lastIndex] = (sealed.last().toInt() xor 0x01).toByte()

        assertFailsWith<SecurityException> {
            BackupEnvelope.open(sealed, password)
        }
    }

    private fun samplePayload() = BackupPayload(
        schemaVersion = 6,
        tables = listOf(
            TableDump(
                name = "persons",
                columns = listOf("id"),
                rows = listOf(listOf(BackupCell(BackupCellType.TEXT, "person-1"))),
            ),
        ),
        documentFiles = emptyList(),
    )
}
