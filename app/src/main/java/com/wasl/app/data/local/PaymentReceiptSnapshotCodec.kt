package com.wasl.app.data.local

import com.wasl.app.data.DocumentIdentitySnapshot
import com.wasl.app.data.DocumentTemplateCatalog
import com.wasl.app.data.DocumentTemplateSnapshot
import com.wasl.app.data.DocumentTemplateStyle
import com.wasl.app.data.PaymentReceiptSnapshot
import com.wasl.app.document.DocumentBannerAsset
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtId
import com.wasl.domain.LedgerEntryId
import com.wasl.domain.Money
import com.wasl.domain.PersonId
import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal object PaymentReceiptSnapshotCodec {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
    }

    fun encode(snapshot: PaymentReceiptSnapshot): String =
        json.encodeToString(SnapshotPayload.from(snapshot))

    fun decode(value: String): PaymentReceiptSnapshot =
        json.decodeFromString<SnapshotPayload>(value).toSnapshot()

    private fun bannerAsset(relativePath: String?, sha256: String?): DocumentBannerAsset? {
        if (relativePath == null && sha256 == null) return null
        check(relativePath != null && sha256 != null) {
            "Payment receipt banner metadata is incomplete."
        }
        return DocumentBannerAsset(relativePath = relativePath, sha256 = sha256)
    }

    @Serializable
    private data class SnapshotPayload(
        val version: Int,
        val documentId: String,
        val documentNumber: String,
        val issuedAt: String,
        val issueZoneId: String,
        val debtId: String,
        val paymentId: String,
        val personId: String,
        val personName: String,
        val direction: String,
        val originalAmountMinor: Long,
        val balanceBeforeMinor: Long,
        val paymentAmountMinor: Long,
        val balanceAfterMinor: Long,
        val currencyCode: String,
        val paidAt: String,
        val paymentNote: String?,
        val debtDescription: String?,
        val issuerDisplayName: String,
        val issuerActivityName: String?,
        val issuerPhone: String?,
        val footerText: String?,
        val issuerBannerRelativePath: String? = null,
        val issuerBannerSha256: String? = null,
        val templateId: String = DocumentTemplateCatalog.DEFAULT_TEMPLATE_ID,
        val templateDisplayName: String = "عملي",
        val templateStyle: String = DocumentTemplateStyle.BUSINESS.name,
        val templateShowPhone: Boolean = true,
        val templateShowFooter: Boolean = true,
        val templateShowBalance: Boolean = true,
        val templateShowNotes: Boolean = true,
    ) {
        fun toSnapshot(): PaymentReceiptSnapshot {
            val currency = CurrencyCode.of(currencyCode)
            return PaymentReceiptSnapshot(
                version = version,
                documentId = documentId,
                documentNumber = documentNumber,
                issuedAt = Instant.parse(issuedAt),
                issueZoneId = ZoneId.of(issueZoneId),
                debtId = DebtId(debtId),
                paymentId = LedgerEntryId(paymentId),
                personId = PersonId(personId),
                personName = personName,
                direction = DebtDirection.valueOf(direction),
                originalAmount = Money(originalAmountMinor, currency),
                balanceBefore = Money(balanceBeforeMinor, currency),
                paymentAmount = Money(paymentAmountMinor, currency),
                balanceAfter = Money(balanceAfterMinor, currency),
                paidAt = Instant.parse(paidAt),
                paymentNote = paymentNote,
                debtDescription = debtDescription,
                identity = DocumentIdentitySnapshot(
                    displayName = issuerDisplayName,
                    activityName = issuerActivityName,
                    phone = issuerPhone,
                    footerText = footerText,
                    banner = bannerAsset(issuerBannerRelativePath, issuerBannerSha256),
                ),
                template = DocumentTemplateSnapshot(
                    id = templateId,
                    displayName = templateDisplayName,
                    style = DocumentTemplateStyle.valueOf(templateStyle),
                    showPhone = templateShowPhone,
                    showFooter = templateShowFooter,
                    showBalance = templateShowBalance,
                    showNotes = templateShowNotes,
                ),
            )
        }

        companion object {
            fun from(snapshot: PaymentReceiptSnapshot): SnapshotPayload = SnapshotPayload(
                version = snapshot.version,
                documentId = snapshot.documentId,
                documentNumber = snapshot.documentNumber,
                issuedAt = snapshot.issuedAt.toString(),
                issueZoneId = snapshot.issueZoneId.id,
                debtId = snapshot.debtId.value,
                paymentId = snapshot.paymentId.value,
                personId = snapshot.personId.value,
                personName = snapshot.personName,
                direction = snapshot.direction.name,
                originalAmountMinor = snapshot.originalAmount.minorUnits,
                balanceBeforeMinor = snapshot.balanceBefore.minorUnits,
                paymentAmountMinor = snapshot.paymentAmount.minorUnits,
                balanceAfterMinor = snapshot.balanceAfter.minorUnits,
                currencyCode = snapshot.paymentAmount.currency.value,
                paidAt = snapshot.paidAt.toString(),
                paymentNote = snapshot.paymentNote,
                debtDescription = snapshot.debtDescription,
                issuerDisplayName = snapshot.identity.displayName,
                issuerActivityName = snapshot.identity.activityName,
                issuerPhone = snapshot.identity.phone,
                footerText = snapshot.identity.footerText,
                issuerBannerRelativePath = snapshot.identity.banner?.relativePath,
                issuerBannerSha256 = snapshot.identity.banner?.sha256,
                templateId = snapshot.template.id,
                templateDisplayName = snapshot.template.displayName,
                templateStyle = snapshot.template.style.name,
                templateShowPhone = snapshot.template.showPhone,
                templateShowFooter = snapshot.template.showFooter,
                templateShowBalance = snapshot.template.showBalance,
                templateShowNotes = snapshot.template.showNotes,
            )
        }
    }
}
