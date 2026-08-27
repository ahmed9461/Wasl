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

بعده دخلت ميزات إضافية تم تنفيذها فعليًا: Claims، Attachments، Person Timeline، Payment Messages، Statistics، Natural Text Entry، **Voice Dictation الأساسي** وAdaptive Search. هذه الميزات لا تعتبر مقفلة نهائيًا إلا بعد نجاح بوابة Android instrumentation على الرأس الحالي.

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
- metadata + SHA-256.
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

**الحالة: منفذ ويحتاج تثبيت نهائي عبر البوابة الكاملة.**

- `NaturalEntryActivity`.
- `NaturalEntryParser`.
- Draft منفصل.
- Confirmation service.
- Parse → Preview → Confirm → Save.
- Unit/UI tests موجودة.

### G2 — الإملاء الصوتي

**الحالة: منفذ بصورة أساسية، ويحتاج تقوية واختبارات مخصصة.**

المسار الحالي داخل `NaturalEntryActivity`:

`RecognizerIntent → recognized text → NaturalEntryParser → Preview → explicit confirmation → save`

الثابت المهم: نتيجة الصوت لا تحفظ حسابًا مباشرة؛ تدخل نفس مسار المعاينة والتأكيد.

المتبقي:

- فصل طبقة إطلاق/قراءة recognizer لتصبح قابلة للاختبار دون Activity خارجي فعلي.
- اختبار success / cancel / empty result / recognizer unavailable.
- تحسين رسالة عدم توفر خدمة التعرف.
- الحفاظ على النص المعترف به قابلًا للتحرير قبل التأكيد.

### G3 — المصاريف/الديون الجماعية

**الحالة: متبقية.**

تنفذ بعد تثبيت Natural/Voice Entry وبحسب المواصفة الأساسية، دون إنشاء Ledger موازٍ غير منضبط.

### H — جاهزية الإصدار

**الحالة: متبقية.**

- Release signing منفصل عن debug.
- الأسرار في GitHub Secrets/بيئة آمنة.
- Release build + full tests.
- Versioning / release changelog.
- مراجعة الخصوصية وبيانات المتجر.
- APK/AAB حسب قناة التوزيع.

## حالة CI الحالية

Android CI #851 على الرأس `94ce0adf...` أثبت:

- `verify` ✅: Unit tests + Lint + Debug APK + Room Schema v9 verification.
- `database-tests` ❌ قبل تشغيل الاختبارات: `compileDebugAndroidTestKotlin` فشل بسبب أربعة imports غير صالحة لـ`androidx.compose.ui.test.onNode`.

تم إعداد إصلاح يزيل imports فقط من:

- `DueDateUiInstrumentedTest.kt`
- `PersonTimelineUiInstrumentedTest.kt`
- `StatisticsScreenUiInstrumentedTest.kt`
- `TodayUiInstrumentedTest.kt`

ولا يغير Assertions أو منطق الاختبارات.

## ترتيب العمل التالي

1. إعادة CI إلى الأخضر على الرأس النهائي بعد مزامنة الوثائق.
2. إغلاق Claims وAttachments رسميًا بعد Evidence كامل.
3. تدقيق Adaptive UI + Accessibility.
4. تثبيت Statistics + Natural Text/Voice Entry ضمن acceptance suite.
5. تقوية Voice adapter والاختبارات بدل إعادة بناء الميزة من الصفر.
6. تنفيذ الميزات الجماعية بحسب المواصفة.
7. جولة UI/PDF polishing شاملة.
8. Full offline acceptance + migrations/backup regression.
9. Release signing والتوزيع.

## سياسة الجودة

لا تعتبر أي مرحلة مكتملة بمجرد compile. حسب نطاقها يجب أن تمر عبر Unit + Room/instrumentation + UI + Lint + build + Idempotency/Recovery + Backup/Restore، مع تحديث `CURRENT_STATUS.md`, `PROJECT_CONTEXT.md`, `HANDOFF.md`, `CHANGELOG.md` وSchema docs عند الحاجة.
