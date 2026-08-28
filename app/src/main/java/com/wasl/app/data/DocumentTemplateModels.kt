package com.wasl.app.data

import java.time.Instant

enum class DocumentTemplateStyle {
    MINIMAL,
    BUSINESS,
    CLASSIC,
    COMPACT,
    MODERN,
}

data class DocumentTemplateRecord(
    val id: String,
    val displayName: String,
    val style: DocumentTemplateStyle,
    val showPhone: Boolean,
    val showFooter: Boolean,
    val showBalance: Boolean,
    val showNotes: Boolean,
    val isDefault: Boolean,
    val isBuiltIn: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(id.isNotBlank()) { "Document template ID cannot be blank." }
        require(displayName.isNotBlank()) { "Document template name cannot be blank." }
    }

    fun toSnapshot(): DocumentTemplateSnapshot = DocumentTemplateSnapshot(
        id = id,
        displayName = displayName,
        style = style,
        showPhone = showPhone,
        showFooter = showFooter,
        showBalance = showBalance,
        showNotes = showNotes,
    )
}

data class DocumentTemplateSnapshot(
    val id: String,
    val displayName: String,
    val style: DocumentTemplateStyle,
    val showPhone: Boolean,
    val showFooter: Boolean,
    val showBalance: Boolean,
    val showNotes: Boolean,
) {
    init {
        require(id.isNotBlank()) { "Document template snapshot ID cannot be blank." }
        require(displayName.isNotBlank()) { "Document template snapshot name cannot be blank." }
    }
}

object DocumentTemplateCatalog {
    const val MINIMAL_ID = "builtin-minimal"
    const val DEFAULT_TEMPLATE_ID = "builtin-business"
    const val CLASSIC_ID = "builtin-classic"
    const val COMPACT_ID = "builtin-compact"
    const val MODERN_ID = "builtin-modern"

    val builtIns: List<DocumentTemplateRecord> = listOf(
        builtIn(MINIMAL_ID, "بسيط", DocumentTemplateStyle.MINIMAL, false, false, true, false, false),
        builtIn(DEFAULT_TEMPLATE_ID, "عملي", DocumentTemplateStyle.BUSINESS, true, true, true, true, true),
        builtIn(CLASSIC_ID, "كلاسيكي", DocumentTemplateStyle.CLASSIC, true, true, true, true, false),
        builtIn(COMPACT_ID, "مضغوط", DocumentTemplateStyle.COMPACT, false, false, true, false, false),
        builtIn(MODERN_ID, "حديث", DocumentTemplateStyle.MODERN, true, true, true, true, false),
    )

    val defaultSnapshot: DocumentTemplateSnapshot =
        builtIns.single { it.id == DEFAULT_TEMPLATE_ID }.toSnapshot()

    private fun builtIn(
        id: String,
        displayName: String,
        style: DocumentTemplateStyle,
        showPhone: Boolean,
        showFooter: Boolean,
        showBalance: Boolean,
        showNotes: Boolean,
        isDefault: Boolean,
    ) = DocumentTemplateRecord(
        id = id,
        displayName = displayName,
        style = style,
        showPhone = showPhone,
        showFooter = showFooter,
        showBalance = showBalance,
        showNotes = showNotes,
        isDefault = isDefault,
        isBuiltIn = true,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
