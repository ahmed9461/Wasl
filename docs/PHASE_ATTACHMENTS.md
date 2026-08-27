# المرحلة B — المرفقات وخزنة الإثباتات

آخر تحديث: 2026-08-27

الحالة: **منفذة وظيفيًا في الكود وSchema v9؛ تنتظر نجاح بوابة Android instrumentation الكاملة على الرأس الحالي قبل إعلان Complete النهائي.**

المرجع الأعلى: قسم «المرفقات وخزنة الإثباتات» في `WASL_MASTER_PROJECT_PROMPT.md`.

## الهدف

إرفاق صورة أو PDF أو ملف بالدين، وربطه اختياريًا بحركة مالية من نفس الدين، مع تخزين محلي خاص بالتطبيق وMetadata/Binary backup يتيح التحقق والاستعادة بأمان.

## التنفيذ الحالي

تم تنفيذ:

- `AttachmentModels` و`AttachmentStore`.
- `RoomAttachmentStore`.
- `AttachmentDao` و`AttachmentEntity`.
- Migration `8→9` وجدول `attachments`.
- ربط المرفق بالدين وبـ`ledger_entry_id` اختياري.
- خزنة داخل مساحة التطبيق بدل الاعتماد على URI خارجي مؤقت.
- حفظ الاسم، MIME، الحجم، المسار النسبي، SHA-256، الوقت والملاحظة.
- `AttachmentFileAccess` للفتح/المشاركة الآمنة.
- فحص فقد الملف أو اختلاف SHA قبل اعتباره سليمًا.
- دمج ملفات المرفقات وmetadata في Backup/Restore v9.
- فحص path traversal وارتباط الحركة بالدين نفسه أثناء الاستعادة.
- واجهة مرفقات ضمن الحساب واختبارات UI/Store/File access/Backup.

## نموذج البيانات

- `id`
- `debt_id`
- `ledger_entry_id` اختياري
- `display_name`
- `mime_type`
- `size_bytes`
- `relative_path` UNIQUE
- `sha256`
- `created_at`
- `note` اختياري

حالة السلامة مشتقة من وجود الملف وبصمته ولا تستبدل الحقيقة المخزنة.

## ثوابت الأمان

1. الملف المرجعي يحفظ داخل خزنة التطبيق، لا في مسار عام مكشوف.
2. URI مؤقت لا يعد ضمان وصول دائم؛ المحتوى ينسخ إلى الخزنة.
3. اسم الملف المعروض لا يستخدم مباشرة كمسار تخزين.
4. SHA-256 إلزامي لكل ملف محفوظ.
5. الحجم وMIME ووقت الإضافة محفوظة.
6. فتح/مشاركة عبر FileProvider وبفعل صريح فقط.
7. Backup/Restore يرفض path traversal والمسارات الخارجة عن الخزنة.
8. فقد الملف أو اختلاف SHA يظهر كتلف؛ لا يمثل كمرفق سليم.
9. إذا كان `ledger_entry_id` موجودًا فيجب أن تكون الحركة من نفس `debt_id`.
10. المرفق سجل شخصي توثيقي وليس ضمانًا قانونيًا تلقائيًا.

## Room / Backup

- Schema الإضافة: v9.
- Migration `8→9` دون destructive migration.
- `relative_path` عليه Unique index.
- Backup contract v9 يضم جدول `attachments` وملفات الخزنة نفسها.
- Restore يStage الملفات ويفحص المسار والحجم والبصمة والعلاقات قبل اعتماد الحالة.

## Evidence الموجود في المستودع

- `AttachmentStoreInstrumentedTest.kt`
- `AttachmentVaultUiInstrumentedTest.kt`
- `AttachmentFileAccessInstrumentedTest.kt`
- `AttachmentBackupRestoreInstrumentedTest.kt`
- Room Schema v9 verification داخل CI.

## تعريف الاكتمال

التنفيذ الوظيفي موجود، لكن لا توسم المرحلة **Complete/Verified** إلا بعد:

1. نجاح Migration 8→9 وRoom instrumentation على الرأس الحالي.
2. نجاح إضافة/فتح/مشاركة الأنواع المدعومة.
3. نجاح اختبارات missing file / hash mismatch / unsafe path.
4. نجاح Backup/Restore round-trip للمرفقات.
5. نجاح Lint وDebug build وبقية regression suite.
6. تحديث وثائق الحالة وSchema وChangelog.

Android CI #851 أثبت Unit/Lint/Debug build ووجود Schema v9 وجدول `attachments` وفهرس المسار الفريد، لكنه توقف قبل instrumentation بسبب imports قديمة في أربعة Android UI tests. الإصلاح الحالي يعيد تشغيل البوابة على الرأس الجديد.
