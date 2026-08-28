# وَصل — الحالة الحالية وخطة الإكمال

آخر مراجعة: 2026-08-28

هذا الملف هو نقطة الرجوع السريعة للحالة الفعلية. عند التعارض بين توثيق قديم والكود، يكون الترتيب: الكود الحالي + Room schemas الملتزمة + GitHub Actions evidence، ثم هذا الملف، ثم بقية وثائق المراحل.

## الفرع ومسار التسليم

- المستودع: `ahmed9461/Wasl`.
- الفرع النشط: `agent/bootstrap-wasl-foundation`.
- Pull Request: `#1` إلى `main` وما زال Draft.
- لا دمج إلى `main` دون طلب صريح.
- Room Database الحالية: **v9**.
- ملفات Room التاريخية **1.json → 9.json** ملتزمة في Git، بما فيها v8 وv9 الأصليتان المسترجعتان من CI artifacts، وليستا ملفات معاد إنشاؤها بالتخمين.

## آخر بوابة كاملة مثبتة

**Android CI #901 — Run `33127031311` — head `9fb1b98d05dadc119943f23f177245bcace3ad1b`.**

نجح بالكامل:

- Unit tests ✅
- Lint ✅
- Debug APK ✅
- Room Schema v9 generated/current verification ✅
- Claims/Attachments schema checks ✅
- Android instrumentation: **104/104**، 0 failed، 0 errors، 0 skipped ✅
- `HomeAdaptiveUiInstrumentedTest.largeFontStacksAccountCardRowsAndRetainsClick` ✅
- `HomeAdaptiveUiInstrumentedTest.largeFontStacksEverySummaryCurrencyRow` ✅
- `DocumentsAdaptiveUiInstrumentedTest.largeFontStacksHeaderAndDualActionsWithoutLosingClicks` ✅
- Security large-font tests الأربعة ✅
- Settings 200% Font Scale test ✅
- Payment Receipt PDF evidence ✅
- Debt Receipt PDF evidence ✅
- Account Statement PDF evidence ✅

Artifacts المرجعية من #901:

- `Wasl-debug`: `9668887938`
- `Wasl-room-schema`: `9668888172`
- `Wasl-payment-receipt-evidence`: `9669058109`
- `Wasl-account-document-evidence`: `9669058391`
- `Wasl-room-instrumentation-results`: `9669058737`

وبذلك أصبحت دفعات RTL/Bidi وSecurity/Settings وDocuments Hub وHome Adaptive مثبتة ببوابة كاملة، إضافة إلى Statistics وPerson Timeline وToday وClaims وAttachments وSchema v9.

## ما يعمل الآن

### المصدر المالي

- أشخاص وحسابات متعددة للشخص.
- `RECEIVABLE` و`PAYABLE`.
- YER / SAR / USD دون خلط العملات.
- Money بوحدات Minor Units من نوع `Long`.
- Ledger append-only.
- دفعات جزئية ونهائية.
- Payment reversal موثق بدل حذف التاريخ.
- Idempotency وReplay لاشتقاق الرصيد والحالة.

### الاستحقاق والمتابعة

- Due date قابل للتعديل/الإلغاء مع Audit.
- Today للاستحقاقات الحالية والمتأخرة.
- WorkManager scheduling + recovery.
- Exact Alarm اختياري للمنبه القوي مع fallback.
- General Reminder مستقل عن `due_date`: one-shot / daily / weekly / monthly.
- إجراءات الإشعار: فتح الحساب، دفع جزء، تم السداد، ذكرني لاحقًا.
- لا كتابة مالية مباشرة من Notification callback؛ الدفع يمر عبر Preview/Confirmation داخل التطبيق.

### الوعود والأقساط والمطالبات

- Payment Promises مستقلة عن Ledger.
- Installment Plans مع `ACTIVE / SUPERSEDED` وRevision history.
- تقدم الأقساط مشتق من الدفعات الحقيقية.
- Payment Claims «طالبني» مستقلة عن Ledger والرصيد و`due_date`.
- Today يجمع الاستحقاق/الوعد/القسط/المطالبة مع بقاء كل نموذج مستقلًا.

### المرفقات وخزنة الإثباتات

- `attachments` في Schema v9.
- ربط بالدين وبـLedger entry اختياري من نفس الدين.
- Internal storage + metadata + SHA-256 + relative path فريد.
- فتح/مشاركة آمنة عبر FileProvider.
- Backup/Restore للmetadata والملفات مع فحص المسار والبصمة.

### المستندات

- `PAYMENT_RECEIPT`.
- `DEBT_RECEIPT`.
- `ACCOUNT_STATEMENT` متعدد الصفحات.
- Immutable snapshots، ترقيم، metadata، SHA-256، page count.
- فتح/مشاركة بعد فحص سلامة الملف.

### النسخ الاحتياطي والأمان

- Backup/Restore تطبيقي مشفر بكلمة مرور.
- Backup schema v9 يشمل 12 جدولًا وملفات PDF والمرفقات.
- Restore مرحلي مع Schema/path/hash/FK/invariant validation وRollback.
- App Lock عبر BiometricPrompt / Device Credential.
- App Lock session مشتركة على مستوى Application بدل Activity محلية.
- `ProtectedWaslActivity` يحمي Activities الحساسة ويعيد المستخدم إلى Main عند القفل.
- `FLAG_SECURE` وسياسة خصوصية الإشعارات.

### الإدخال الطبيعي والصوت

- Natural Entry: `Parser → Draft/Preview → explicit Confirmation → Save`.
- Voice Dictation موجود عبر Android `RecognizerIntent` ويغذي المسار نفسه، ولا ينفذ حفظًا ماليًا مباشرًا.
- Voice hardening الحالي يعالج success/cancel/empty result وعدم وجود resolver وفشل إطلاق recognizer.
- توجد Unit tests لاختيار أول نتيجة غير فارغة، empty result، وcancel.
- لا يجب الادعاء بأن ActivityResult الخارجي للـRecognizer مغطى E2E بالكامل؛ هذه ما زالت فجوة اختبار تكامل خارجية.

## Accessibility / Adaptive UI

**الحالة: قيد التنفيذ الفعلي، والدفعات حتى Home Verified بالكامل. Account Details تحت البوابة الحالية.**

الأساس الموجود:

- RTL على مستوى التطبيق.
- `WaslMaxContentWidth = 760.dp` مستخدم في شاشات حديثة.
- `shouldStackDenseRows()` يكدس الصفوف عند العرض الضيق أو `fontScale >= 1.3`.
- Search/Top-level navigation لديها اختبارات large-font وsemantics.

### Statistics / Person Timeline / Today — Verified

- Statistics + Person Timeline تستخدم تكديسًا فعليًا للصفوف المزدحمة وتختبره عند 200% Font Scale.
- Today يكدس بطاقات الملخص، عناوين الأقسام، المبلغ/الحالة، Header الشخص، وأزرار الإذن/إعادة المحاولة عند النص الكبير.
- اختبار Today الصريح عند 200% Font Scale نجح ضمن CI #873 ثم بقي أخضر في البوابات اللاحقة.
- فشل CI #866 السابق كان Assertion هشًا مرتبطًا بالـviewport في Statistics؛ أصلح في `ee23079f...` دون حذف تغطية.

### RTL/Bidi — Verified عبر CI #889 وما بعده

commit `36c5297a92f2ee700a41c34fc2d16ca7680b8d3a`:

- `ltrIsolate()` مركزي باستخدام LRI/PDI مع منع double wrapping.
- `formatMoney()` يستخدم helper المركزي بدل literal مكرر.
- Person Timeline وDocuments Hub يعيدان استخدام `formatMoney()` بدل formatter مالي مكرر.
- عزل العملات والتواريخ/الأوقات وأرقام المستندات والهاتف والبريد والحالات التقنية وmetadata اللاتينية للمرفقات داخل RTL.
- Unit tests لـLTR isolation نجحت.

### Security / Settings — Verified عبر CI #889 وما بعده

commit `c035bc56fb526e2ae8e891a526b970f717f8d39f`:

- Security entry actions تتكدس وتصبح full-width عند النص الكبير.
- Security Hub header يتكدس عند المساحة الضيقة أو Font Scale المرتفع.
- App Lock toggle يتكدس بدل ضغط الوصف والـSwitch في Row واحد.
- Security Hub يستخدم `WaslMaxContentWidth` المركزي.
- Settings header يتكدس عند النص الكبير.
- Privacy switches في Settings تتكدس عند النص الكبير.
- `SecurityUiInstrumentedTest`: **4/4** نجحت ضمن #889.
- `SettingsUiInstrumentedTest.largeFontStacksSettingsHeaderAndPrivacyControls`: نجح ضمن #889.

### Documents Hub — Verified عبر CI #896

commit `526dfcd79a6d587a884953a36e9d1d831465c619`:

- Header المستندات يتكدس عند النص الكبير بدل Row ثابت.
- فتح/مشاركة PDF الجاهز تستخدم مكوّن Adaptive مشترك.
- فتح/مشاركة المرفقات تستخدم المكوّن نفسه، مع الحفاظ على disabled state عند فشل سلامة الملف.
- في الوضع المكدس تصبح الأزرار full-width؛ وفي الوضع الطبيعي تبقى بجانب بعضها.
- `DocumentsAdaptiveUiInstrumentedTest` عند 200% Font Scale يثبت التكديس والنقر على الإجراءين مباشرة، ونجح ضمن **102/102** في #896.
- لا تغيير في منطق إصدار PDF أو immutable snapshots أو فحوص SHA-256.

### Home — Verified عبر CI #901

commit `09b79974d43269b153095a861935ead8df4efb16`:

- رأس بطاقة الحساب يكدس شارة الاتجاه تحت هوية الشخص عند النص الكبير/العرض الضيق.
- المتبقي وشارة حالة الحساب يتكدسان بدل الضغط داخل Row واحد.
- أصل الدين والمبلغ يتحولان إلى تخطيط رأسي عند النص الكبير.
- صفوف العملة/القيمة في بطاقات الملخص المالي تتكدس لكل YER/SAR/USD عند النص الكبير.
- `Card(onClick)` للحساب بقي بنفس السلوك ولم يتغير منطق فتح الحساب.
- `HomeAdaptiveUiInstrumentedTest` يختبر عند 200% Font Scale بطاقة حساب جزئي السداد حقيقية من `DebtLedger`، استمرار النقر، وصفوف العملات الثلاثة.
- الاختباران نجحا ضمن **104/104** في #901.
- لا تغيير في Create Debt dialog أو منطق Ledger/الرصيد/العملات.

### Account Details — تحت البوابة الحالية

commit `afc95ef2b63b48a780cfac0efc14d13a1f84a054`:

- Hero direction/state badges تتحول من Row ثابت إلى تكديس فعلي عند النص الكبير/العرض الضيق.
- بطاقتا `أصل الدين` و`المدفوع` تتكدسان full-width عند النص الكبير وتبقيان عمودين في الوضع الطبيعي.
- عنوان `سجل العمليات` وشارة `سجل موثق` يتكدسان عند النص الكبير بدل الضغط في Row واحد.
- أضيف `AccountDetailsAdaptiveUiInstrumentedTest` باختبارين عند 200% Font Scale للمكونات الثلاثة مباشرة.
- التغيير Layout-only ولا يلمس AccountDetailsViewModel أو Room أو Ledger أو dialogs.

هذه الدفعة لا توسم Verified قبل نجاح Android CI كاملة على الرأس الحالي.

## ملاحظة استقرار CI

- فشل CI #858 السابق كان Flaky selector في `DueDateUiInstrumentedTest` بسبب `hasScrollAction()` الذي طابق أكثر من Scrollable.
- تم استبداله بـ`hasScrollToIndexAction()` للحاوية ذات الفهرسة.
- نفس SHA نجح في #859، ثم أثبت #861 الإصلاح بالكامل.
- CI concurrency يستخدم اسم الفرع (`github.head_ref || github.ref_name`) لمنع تشغيل push وPR متوازيين لنفس الرأس.
- الإلغاء التلقائي لتشغيل push الموازي عند بدء PR run هو السلوك المقصود.

## ترتيب العمل التالي

1. إغلاق بوابة CI الكاملة لدفعة Account Details الحالية.
2. إذا أخضر: مراجعة touch targets والـsemantics للعناصر المخصصة وأي identifiers/dates متبقية داخل RTL.
3. تنفيذ المصاريف/الديون الجماعية حسب المواصفة دون إنشاء Ledger موازٍ.
4. جولة UI/PDF polishing النهائية المؤجلة عمدًا حتى اكتمال الوظائف.
5. Full offline acceptance + migrations/backup regression.
6. Release signing والتوزيع.

## سياسة الجودة

لا تعتبر أي مرحلة مكتملة بمجرد compile. حسب نطاقها يجب أن تمر عبر Unit + Lint + build + Room/instrumentation + UI + Backup/Restore/PDF evidence عند الصلة. ولا تُخفف الاختبارات لإجبار CI على الأخضر؛ عند ظهور فشل يجب تمييز فشل المنتج من فشل الاختبار الهش وإصلاح السبب الحقيقي.