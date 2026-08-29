package com.wasl.app.data.local

import com.wasl.app.data.AccountStatementSnapshot
import com.wasl.app.data.DebtReceiptSnapshot
import com.wasl.app.data.DocumentIdentitySnapshot
import com.wasl.app.data.DocumentTemplateCatalog
import com.wasl.app.data.DocumentTemplateSnapshot
import com.wasl.app.data.DocumentTemplateStyle
import com.wasl.app.data.DocumentSnapshot
import com.wasl.app.data.DocumentType
import com.wasl.app.data.StatementEntryType
import com.wasl.app.data.StatementLedgerEntrySnapshot
import com.wasl.app.document.DocumentBannerAsset
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtId
import com.wasl.domain.LedgerEntryId
import com.wasl.domain.Money
import com.wasl.domain.PersonId
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal object AccountDocumentSnapshotCodec {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
    }

    fun encode(snapshot: DocumentSnapshot): String = when (snapshot) {
        is DebtReceiptSnapshot -> json.encodeToString(DebtReceiptPayload.from(snapshot))
        is AccountStatementSnapshot -> json.encodeToString(AccountStatementPayload.from(snapshot))
        else -> error("Unsupported account document snapshot: ${snapshot::class.simpleName}")
    }

    fun decode(type: DocumentType, value: String): DocumentSnapshot = when (type) {
        DocumentType.DEBT_RECEIPT -> json.decodeFromString<DebtReceiptPayload>(value).toSnapshot()
        DocumentType.ACCOUNT_STATEMENT ->
            json.decodeFromString<AccountStatementPayload>(value).toSnapshot()
        DocumentType.PAYMENT_RECEIPT -> error("Payment receipts use PaymentReceiptSnapshotCodec.")
    }

    private fun bannerAsset(relativePath: String?, sha256: String?): DocumentBannerAsset? {
        if (relativePath == null && sha256 == null) return null
        check(relativePath != null && sha256 != null) {
            "Document snapshot banner metadata is incomplete."
        }
        return DocumentBannerAsset(relativePath = relativePath, sha256 = sha256)
    }

    @Serializable
    private data class DebtReceiptPayload(
        val version: Int,
        val documentId: String,
        val documentNumber: String,
        val issuedAt: String,
        val issueZoneId: String,
        val debtId: String,
        val personId: String,
        val personName: String,
        val direction: String,
        val originalAmountMinor: Long,
        val balanceAtIssueMinor: Long,
        val paidAmountAtIssueMinor: Long,
        val currencyCode: String,
        val openedAt: String,
        val dueDate: String?,
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
        fun toSnapshot(): DebtReceiptSnapshot {
            val currency = CurrencyCode.of(currencyCode)
            return DebtReceiptSnapshot(
                version = version,
                documentId = documentId,
                documentNumber = documentNumber,
                issuedAt = Instant.parse(issuedAt),
                issueZoneId = ZoneId.of(issueZoneId),
                debtId = DebtId(debtId),
                personId = PersonId(personId),
                personName = personName,
                direction = DebtDirection.valueOf(direction),
                originalAmount = Money(originalAmountMinor, currency),
                balanceAtIssue = Money(balanceAtIssueMinor, currency),
                paidAmountAtIssue = Money(paidAmountAtIssueMinor, currency),
                openedAt = Instant.parse(openedAt),
                dueDate = dueDate?.let(LocalDate::parse),
                debtDescription = debtDescription,
                identity = identity(),
                template = template(),
            )
        }

        private fun identity() = DocumentIdentitySnapshot(
            displayName = issuerDisplayName,
            activityName = issuerActivityName,
            phone = issuerPhone,
            footerText = footerText,
            banner = bannerAsset(issuerBannerRelativePath, issuerBannerSha256),
        )

        private fun template() = DocumentTemplateSnapshot(
            id = templateId,
            displayName = templateDisplayName,
            style = DocumentTemplateStyle.valueOf(templateStyle),
            showPhone = templateShowPhone,
            showFooter = templateShowFooter,
            showBalance = templateShowBalance,
            showNotes = templateShowNotes,
        )

        companion object {
            fun from(snapshot: DebtReceiptSnapshot) = DebtReceiptPayload(
                version = snapshot.version,
                documentId = snapshot.documentId,
                documentNumber = snapshot.documentNumber,
                issuedAt = snapshot.issuedAt.toString(),
                issueZoneId = snapshot.issueZoneId.id,
                debtId = snapshot.debtId.value,
                personId = snapshot.personId.value,
                personName = snapshot.personName,
                direction = snapshot.direction.name,
                originalAmountMinor = snapshot.originalAmount.minorUnits,
                balanceAtIssueMinor = snapshot.balanceAtIssue.minorUnits,
                paidAmountAtIssueMinor = snapshot.paidAmountAtIssue.minorUnits,
                currencyCode = snapshot.originalAmount.currency.value,
                openedAt = snapshot.openedAt.toString(),
                dueDate = snapshot.dueDate?.toString(),
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

    @Serializable
    private data class AccountStatementPayload(
        val version: Int,
        val documentId: String,
        val documentNumber: String,
        val issuedAt: String,
        val issueZoneId: String,
        val debtId: String,
        val personId: String,
        val personName: String,
        val direction: String,
        val originalAmountMinor: Long,
        val balanceAtIssueMinor: Long,
        val paidAmountAtIssueMinor: Long,
        val currencyCode: String,
        val openedAt: String,
        val dueDate: String?,
        val debtDescription: String?,
        val entries: List<StatementEntryPayload>,
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
        fun toSnapshot(): AccountStatementSnapshot {
            val currency = CurrencyCode.of(currencyCode)
            return AccountStatementSnapshot(
                version = version,
                documentId = documentId,
                documentNumber = documentNumber,
                issuedAt = Instant.parse(issuedAt),
                issueZoneId = ZoneId.of(issueZoneId),
                debtId = DebtId(debtId),
                personId = PersonId(personId),
                personName = personName,
                direction = DebtDirection.valueOf(direction),
                originalAmount = Money(originalAmountMinor, currency),
                balanceAtIssue = Money(balanceAtIssueMinor, currency),
                paidAmountAtIssue = Money(paidAmountAtIssueMinor, currency),
                openedAt = Instant.parse(openedAt),
                dueDate = dueDate?.let(LocalDate::parse),
                debtDescription = debtDescription,
                entries = entries.map { it.toSnapshot(currency) },
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
            fun from(snapshot: AccountStatementSnapshot) = AccountStatementPayload(
                version = snapshot.version,
                documentId = snapshot.documentId,
                documentNumber = snapshot.documentNumber,
                issuedAt = snapshot.issuedAt.toString(),
                issueZoneId = snapshot.issueZoneId.id,
                debtId = snapshot.debtId.value,
                personId = snapshot.personId.value,
                personName = snapshot.personName,
                direction = snapshot.direction.name,
                originalAmountMinor = snapshot.originalAmount.minorUnits,
                balanceAtIssueMinor = snapshot.balanceAtIssue.minorUnits,
                paidAmountAtIssueMinor = snapshot.paidAmountAtIssue.minorUnits,
                currencyCode = snapshot.originalAmount.currency.value,
                openedAt = snapshot.openedAt.toString(),
                dueDate = snapshot.dueDate?.toString(),
                debtDescription = snapshot.debtDescription,
                entries = snapshot.entries.map(StatementEntryPayload::from),
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

    @Serializable
    private data class StatementEntryPayload(
        val id: String,
        val type: String,
        val recordedAt: String,
        val amountMinor: Long?,
        val occurredAt: String?,
        val note: String?,
        val reversesPaymentId: String?,
        val reason: String?,
    ) {
        fun toSnapshot(currency: CurrencyCode) = StatementLedgerEntrySnapshot(
            id = LedgerEntryId(id),
            type = StatementEntryType.valueOf(type),
            recordedAt = Instant.parse(recordedAt),
            amount = amountMinor?.let { Money(it, currency) },
            occurredAt = occurredAt?.let(Instant::parse),
            note = note,
            reversesPaymentId = reversesPaymentId?.let(::LedgerEntryId),
            reason = reason,
        )

        companion object {
            fun from(snapshot: StatementLedgerEntrySnapshot) = StatementEntryPayload(
                id = snapshot.id.value,
                type = snapshot.type.name,
                recordedAt = snapshot.recordedAt.toString(),
                amountMinor = snapshot.amount?.minorUnits,
                occurredAt = snapshot.occurredAt?.toString(),
                note = snapshot.note,
                reversesPaymentId = snapshot.reversesPaymentId?.value,
                reason = snapshot.reason,
            )
        }
    }
}
