package com.wasl.app

import kotlin.test.Test
import kotlin.test.assertEquals

class AttachmentImportFailureMessageTest {
    @Test
    fun securityFailureUsesHumanReadableArabicMessage() {
        assertEquals(
            "لا يمكن قراءة هذا الملف. اختر ملفًا آخر أو امنح الإذن المطلوب.",
            attachmentImportFailureMessage(SecurityException("provider denied access")),
        )
    }

    @Test
    fun oversizedAttachmentDoesNotExposeInternalExceptionText() {
        assertEquals(
            "حجم المرفق أكبر من الحد المسموح (25 ميجابايت).",
            attachmentImportFailureMessage(IllegalArgumentException("Attachment is larger than 25 MB")),
        )
    }

    @Test
    fun emptyAttachmentUsesSpecificMessage() {
        assertEquals(
            "الملف المختار فارغ ولا يمكن إضافته.",
            attachmentImportFailureMessage(IllegalArgumentException("Attachment is empty")),
        )
    }

    @Test
    fun unknownProviderFailureDoesNotLeakTechnicalDetails() {
        assertEquals(
            "تعذر حفظ المرفق. جرّب ملفًا آخر.",
            attachmentImportFailureMessage(IllegalStateException("content provider transaction failed at android.os.Binder")),
        )
    }
}
