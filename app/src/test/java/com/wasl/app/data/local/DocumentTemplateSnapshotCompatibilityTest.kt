package com.wasl.app.data.local

import com.wasl.app.data.DocumentTemplateCatalog
import com.wasl.app.data.DocumentTemplateStyle
import com.wasl.app.data.DocumentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocumentTemplateSnapshotCompatibilityTest {
    @Test
    fun versionOneSnapshotsWithoutTemplateOrBannerFieldsDecodeToDefaults() {
        val payment = PaymentReceiptSnapshotCodec.decode(
            """
            {
              "version": 1,
              "documentId": "legacy-payment-document",
              "documentNumber": "PAY-2026-00001",
              "issuedAt": "2026-08-01T00:10:00Z",
              "issueZoneId": "Asia/Aden",
              "debtId": "legacy-debt",
              "paymentId": "legacy-payment",
              "personId": "legacy-person",
              "personName": "عميل قديم",
              "direction": "RECEIVABLE",
              "originalAmountMinor": 100000,
              "balanceBeforeMinor": 100000,
              "paymentAmountMinor": 25000,
              "balanceAfterMinor": 75000,
              "currencyCode": "YER",
              "paidAt": "2026-08-01T00:05:00Z",
              "paymentNote": null,
              "debtDescription": "دين قديم",
              "issuerDisplayName": "وَصل",
              "issuerActivityName": null,
              "issuerPhone": null,
              "footerText": null
            }
            """.trimIndent(),
        )
        val debt = AccountDocumentSnapshotCodec.decode(
            DocumentType.DEBT_RECEIPT,
            """
            {
              "version": 1,
              "documentId": "legacy-debt-document",
              "documentNumber": "DEBT-2026-00002",
              "issuedAt": "2026-08-01T00:10:00Z",
              "issueZoneId": "Asia/Aden",
              "debtId": "legacy-debt",
              "personId": "legacy-person",
              "personName": "عميل قديم",
              "direction": "RECEIVABLE",
              "originalAmountMinor": 100000,
              "balanceAtIssueMinor": 75000,
              "paidAmountAtIssueMinor": 25000,
              "currencyCode": "YER",
              "openedAt": "2026-08-01T00:00:00Z",
              "dueDate": null,
              "debtDescription": "دين قديم",
              "issuerDisplayName": "وَصل",
              "issuerActivityName": null,
              "issuerPhone": null,
              "footerText": null
            }
            """.trimIndent(),
        )
        val statement = AccountDocumentSnapshotCodec.decode(
            DocumentType.ACCOUNT_STATEMENT,
            """
            {
              "version": 1,
              "documentId": "legacy-statement-document",
              "documentNumber": "STAT-2026-00003",
              "issuedAt": "2026-08-01T00:10:00Z",
              "issueZoneId": "Asia/Aden",
              "debtId": "legacy-debt",
              "personId": "legacy-person",
              "personName": "عميل قديم",
              "direction": "RECEIVABLE",
              "originalAmountMinor": 100000,
              "balanceAtIssueMinor": 75000,
              "paidAmountAtIssueMinor": 25000,
              "currencyCode": "YER",
              "openedAt": "2026-08-01T00:00:00Z",
              "dueDate": null,
              "debtDescription": "دين قديم",
              "entries": [],
              "issuerDisplayName": "وَصل",
              "issuerActivityName": null,
              "issuerPhone": null,
              "footerText": null
            }
            """.trimIndent(),
        )

        listOf(payment, debt, statement).forEach { snapshot ->
            assertEquals(1, snapshot.version)
            assertNull(snapshot.identity.banner)
            assertEquals(DocumentTemplateCatalog.DEFAULT_TEMPLATE_ID, snapshot.template.id)
            assertEquals("عملي", snapshot.template.displayName)
            assertEquals(DocumentTemplateStyle.BUSINESS, snapshot.template.style)
            assertTrue(snapshot.template.showPhone)
            assertTrue(snapshot.template.showFooter)
            assertTrue(snapshot.template.showBalance)
            assertTrue(snapshot.template.showNotes)
        }
    }

    @Test
    fun paymentSnapshotWithBannerKeepsImmutablePathAndDigest() {
        val digest = "a".repeat(64)
        val snapshot = PaymentReceiptSnapshotCodec.decode(
            """
            {
              "version": 2,
              "documentId": "banner-payment-document",
              "documentNumber": "PAY-2026-00010",
              "issuedAt": "2026-08-01T00:10:00Z",
              "issueZoneId": "Asia/Aden",
              "debtId": "banner-debt",
              "paymentId": "banner-payment",
              "personId": "banner-person",
              "personName": "عميل",
              "direction": "RECEIVABLE",
              "originalAmountMinor": 100000,
              "balanceBeforeMinor": 100000,
              "paymentAmountMinor": 25000,
              "balanceAfterMinor": 75000,
              "currencyCode": "YER",
              "paidAt": "2026-08-01T00:05:00Z",
              "paymentNote": null,
              "debtDescription": null,
              "issuerDisplayName": "وَصل",
              "issuerActivityName": null,
              "issuerPhone": null,
              "footerText": null,
              "issuerBannerRelativePath": "document_identity/banners/banner.png",
              "issuerBannerSha256": "$digest"
            }
            """.trimIndent(),
        )

        assertEquals("document_identity/banners/banner.png", snapshot.identity.banner?.relativePath)
        assertEquals(digest, snapshot.identity.banner?.sha256)
        val roundTrip = PaymentReceiptSnapshotCodec.decode(PaymentReceiptSnapshotCodec.encode(snapshot))
        assertEquals(snapshot.identity.banner, roundTrip.identity.banner)
    }

    @Test
    fun incompleteBannerMetadataFailsClosed() {
        val malformed =
            """
            {
              "version": 2,
              "documentId": "invalid-banner-document",
              "documentNumber": "PAY-2026-00011",
              "issuedAt": "2026-08-01T00:10:00Z",
              "issueZoneId": "Asia/Aden",
              "debtId": "banner-debt",
              "paymentId": "banner-payment",
              "personId": "banner-person",
              "personName": "عميل",
              "direction": "RECEIVABLE",
              "originalAmountMinor": 100000,
              "balanceBeforeMinor": 100000,
              "paymentAmountMinor": 25000,
              "balanceAfterMinor": 75000,
              "currencyCode": "YER",
              "paidAt": "2026-08-01T00:05:00Z",
              "paymentNote": null,
              "debtDescription": null,
              "issuerDisplayName": "وَصل",
              "issuerActivityName": null,
              "issuerPhone": null,
              "footerText": null,
              "issuerBannerRelativePath": "document_identity/banners/banner.png"
            }
            """.trimIndent()

        assertFailsWith<IllegalStateException> {
            PaymentReceiptSnapshotCodec.decode(malformed)
        }
    }
}
