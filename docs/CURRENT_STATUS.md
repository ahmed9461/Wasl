# وَصل — الحالة الحالية وخطة الإكمال

آخر مراجعة: 2026-08-27

هذا الملف هو نقطة الرجوع السريعة للحالة الفعلية. عند التعارض بين توثيق قديم والكود، يكون الترتيب: الكود الحالي + Room schema + CI evidence، ثم هذا الملف، ثم بقية وثائق المراحل.

## الفرع ومسار التسليم

- المستودع: `ahmed9461/Wasl`
- الفرع النشط: `agent/bootstrap-wasl-foundation`
- Pull Request: `#1` إلى `main` وما زال Draft.
- `main` لا يدمج قبل اكتمال بوابة التحقق والمراجعة.
- Room Schema الحالي: **v9** مع migrations متسلسلة دون destructive migration.

## المرحلة الحالية

**Post-MVP feature completion + stabilization.**

الـMVP المالي الأساسي مكتمل من ناحية التنفيذ: الأشخاص والحسابات، Ledger، الدفعات والعكس، الاستحقاقات، Today، التذكيرات، البحث، PDF، Backup/Restore، App Lock، الوعود والأقساط.

بعده دخلت ميزات إضافية تم تنفيذها فعليًا في الكود: Claims، Attachments، Person Timeline، Payment Messages، Statistics، Natural Text Entry وAdaptive Search. هذه الميزات لا تعتبر مقفلة نهائيًا إلا بعد نجاح بوابة Android instrumentation على الرأس الحالي.

## ما يعمل الآن

### الحسابات والدفتر المالي

- أشخاص وحسابات متعددة للشخص نفسه.
- `RECEIVABLE` / `PAYABLE`.
- YER / SAR / USD مع Minor Units صحيحة.
- Ledger append-only.
- دفعات جزئية ونهائية.
- Payment reversal موثق بدل حذف التاريخ.
- Idempotency للعمليات الحساسة.
- الرصيد وحالة الإغلاق مشتقان من Replay.

### الاستحقاق والمتابعة

- تاريخ استحقاق اختياري، تعديل وإلغاء مع Audit.
- Today للاستحقاقات الحالية والمتأخرة.
- WorkManager + Recovery.
- Exact Alarm اختياري للمنبه القوي.
- General Reminder مستقل عن `due_date`: مرة واحدة / يومي / أسبوعي / شهري.
- إجراءات إشعار: دفع جزء / تم السداد / ذكرني لاحقًا / فتح الحساب.
- لا Ledger write من Notification callback؛ الدفع يمر عبر شاشة المراجعة والتأكيد.

### الوعود والأقساط

- Payment Promises مستقلة عن Ledger.
- Installment Plans مع `ACTIVE / SUPERSEDED` وRevision history.
- تقدم الأقساط مشتق من الدفعات الحقيقية.
- Today يجمع الاستحقاقات والوعود والأقساط دون خلط مصادر الحقيقة.

### البحث

- بحث Reactive أساسي.
- Advanced Search في الأشخاص والحسابات والعمليات والمستندات والمبالغ والتواريخ.
- Adaptive Search واختبارات UI موجودة.

### المستندات

- `PAYMENT_RECEIPT`.
- `DEBT_RECEIPT`.
- `ACCOUNT_STATEMENT` متعدد الصفحات.
- Immutable snapshots، ترقيم، metadata، SHA-256، page count.
- فتح/مشاركة بعد فحص السلامة.

### النسخ الاحتياطي والأمان

- Backup/Restore تطبيقي مشفر بكلمة مرور.
- Backup schema الحالي v9 ويشمل 12 جدولًا وملفات PDF والمرفقات.
- Restore مرحلي مع فحص Schema والمسارات والبصمات وForeign Keys والثوابت المالية وRollback.
- App Lock عبر BiometricPrompt / Device Credential.
- `FLAG_SECURE` وإخفاء تفاصيل الإشعارات الحساسة.

## الميزات المنفذة بعد قاعدة MVP

### A — «طالبني» / Payment Claims

**الحالة: منفذة في الكود، تنتظر إغلاق CI النهائي للرأس الحالي.**

- جدول `payment_claims` في Schema v8 وما بعدها.
- Claim يسمح فقط لحساب `PAYABLE` المفتوح.
- `TODAY / TOMORROW / SALARY / CUSTOM`.
- `ACTIVE / RESOLVED / CANCELLED`.
- Store/DAO/ViewModel/UI.
- ظهور في Account وToday.
- Backup/Restore validation.
- Migration 7→8 واختبارات مخصصة.
- Claim لا يغير Ledger أو الرصيد أو `due_date`.

### B — المرفقات وخزنة الإثباتات

**الحالة: منفذة في الكود، تنتظر إغلاق CI النهائي للرأس الحالي.**

- جدول `attachments` في Schema v9.
- ربط بالدين وبـLedger entry اختياري.
- تخزين داخلي خاص بالتطبيق.
- `display_name`, MIME, size, relative path, SHA-256, note, created_at.
- فتح/مشاركة آمنة عبر FileProvider.
- فحص فقد الملف/اختلاف SHA.
- Backup/Restore للملفات والmetadata.
- Migration 8→9 واختبارات Store/UI/File access/Backup.

### C — صفحة الشخص

**الحالة: منفذة.**

- `PersonTimelineActivity/Screen/ViewModel`.
- تجميع حسابات الشخص مع إبقاء العملات منفصلة.
- Timeline موحد وفتح الحسابات.
- اختبارات Unit وUI، بما فيها font scale.

### D — رسائل السداد

**الحالة: منفذة.**

- قوالب رسائل متعددة.
- نسخ/مشاركة بفعل صريح.
- لا إرسال تلقائي.
- Unit/UI tests موجودة.

### E — الوصول والتكيف

**الحالة: بدأت ولم تغلق بعد.**

- يوجد Adaptive Layout/Search واختبارات Font Scale في عدة شاشات.
- المتبقي: مراجعة شاملة Compact/Medium/Expanded، TalkBack semantics، focus order، touch targets، RTL/LTR للأرقام والتواريخ والأموال.

### F — الإحصاءات الموضوعية

**الحالة: منفذة وتحتاج تثبيت نهائي عبر البوابة الكاملة.**

- عدد الحسابات المفتوحة/المسددة.
- متوسطات زمنية موضوعية.
- مؤشرات الوعود.
- لا تقييم أشخاص من نوع موثوق/سيئ.

### G1 — الإدخال الطبيعي للنص

**الحالة: منفذ وتحتاج تثبيت نهائي عبر البوابة الكاملة.**

- `NaturalEntryActivity`.
- `NaturalEntryParser`.
- Draft منفصل.
- Confirmation service.
- Parse → Preview → Confirm → Save.
- Unit/UI tests موجودة.

### G2 — الإدخال الصوتي

**الحالة: لم يبدأ التنفيذ الفعلي.**

المسار المطلوب: Voice → Text → نفس Natural Parser → Preview → Confirmation. لا كتابة مالية مباشرة من الصوت.

### G3 — المصاريف/الديون الجماعية

**الحالة: متبقية.**

تنفذ بعد تثبيت الإدخال الطبيعي/الصوتي وبحسب المواصفة الأساسية، دون إنشاء Ledger موازٍ غير منضبط.

### H — جاهزية الإصدار

**الحالة: متبقية.**

- Release signing منفصل عن debug.
- الأسرار في GitHub Secrets/بيئة آمنة.
- Release build + full tests.
- Versioning / release changelog.
- مراجعة الخصوصية وبيانات المتجر.
- APK/AAB حسب قناة التوزيع.

## حالة CI الحالية

آخر رأس قبل إصلاح هذه الدفعة: `94ce0adf3ff64431a261042ebb62e815b42f13f1`.

Android CI #851:

- `verify` ✅: Unit tests + Lint + Debug APK + Room Schema v9 verification.
- `database-tests` ❌ قبل تشغيل الاختبارات: `compileDebugAndroidTestKotlin` فشل بسبب أربعة imports غير صالحة لـ`androidx.compose.ui.test.onNode` في:
  - `DueDateUiInstrumentedTest.kt`
  - `PersonTimelineUiInstrumentedTest.kt`
  - `StatisticsScreenUiInstrumentedTest.kt`
  - `TodayUiInstrumentedTest.kt`

الإصلاح الحالي يزيل imports فقط، لأن الاستدعاء الصحيح موجود بالفعل كـ`composeRule.onNode(...)`.

## ترتيب العمل التالي

1. إعادة CI إلى الأخضر على الرأس الجديد.
2. إغلاق Claims وAttachments رسميًا بعد Evidence كامل.
3. تدقيق Adaptive UI + Accessibility.
4. تثبيت Statistics + Natural Text Entry ضمن acceptance suite.
5. تنفيذ Voice Entry.
6. تنفيذ الميزات الجماعية بحسب المواصفة.
7. جولة UI/PDF polishing شاملة.
8. Full offline acceptance + migrations/backup regression.
9. Release signing والتوزيع.

## سياسة الجودة

لا تعتبر أي مرحلة مكتملة بمجرد compile. حسب نطاقها يجب أن تمر عبر Unit + Room/instrumentation + UI + Lint + build + Idempotency/Recovery + Backup/Restore، مع تحديث `CURRENT_STATUS.md`, `PROJECT_CONTEXT.md`, `HANDOFF.md`, `CHANGELOG.md` وSchema docs عند الحاجة.
