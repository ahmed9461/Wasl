package com.wasl.app.document

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class ReceiptFileIntegrityTest {
    @Test
    fun sha256HexMatchesKnownDigest() {
        val file = File.createTempFile("wasl-receipt-integrity", ".pdf")
        try {
            file.writeText("abc")

            assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                file.sha256Hex(),
            )
        } finally {
            file.delete()
        }
    }
}
