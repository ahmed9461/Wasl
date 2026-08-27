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

**Android CI #873 — Run `33123137824` — head `8bb69e733c2158c43913be0d2160069721d179ad`.**

نجح بالكامل:

- Unit tests ✅
- Lint ✅
- Debug APK ✅
- Room Schema v9 generated/current verification ✅
- Claims/Attachments schema checks ✅
- Android instrumentation: **99/99**، 0 failed، 0 errors، 0 skipped ✅
- اختبار Today الجديد عند **200% Font Scale** نجح ✅
- Payment Receipt PDF evidence ✅
- Debt Receipt PDF evidence ✅
- Account Statement PDF evidence ✅

Artifacts المرجعية من #873:

- `Wasl-debug`: `9667408679`
- `Wasl-room-schema`: `9667409178`
- `Wasl-payment-receipt-evidence`: `9667592361`
- `Wasl-account-document-evidence`: `9667592640`
- `Wasl-room-instrumentation-results`: `9667592982`

وبذلك أصبحت دفعة Adaptive الخاصة بـStatistics وPerson Timeline وToday مثبتة ببوابة كاملة، كما تبقى Claims وAttachments وSchema v9 مثبتة.

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

**الحالة: قيد التنفيذ الفعلي، مع دفعة أولى Verified ودفعة ثانية تحت بوابة التحقق الكاملة.**

الأساس الموجود:

- RTL على مستوى التطبيق.
- `WaslMaxContentWidth = 760.dp` مستخدم في شاشات حديثة.
- `shouldStackDenseRows()` يكدس الصفوف عند العرض الضيق أو `fontScale >= 1.3`.
- Search/Top-level navigation لديها اختبارات large-font وsemantics.

الدفعة المثبتة عبر CI #873:

- Statistics + Person Timeline تستخدم تكديسًا فعليًا للصفوف المزدحمة وتختبره عند 200% Font Scale.
- Today يكدس بطاقات الملخص، عناوين الأقسام، المبلغ/الحالة، Header الشخص، وأزرار الإذن/إعادة المحاولة عند النص الكبير.
- اختبار Today الصريح عند 200% Font Scale نجح ضمن **99/99** instrumentation.
- فشل CI #866 السابق كان Assertion هشًا مرتبطًا بالـviewport في Statistics؛ أصلح في `ee23079f...` دون حذف تغطية، ثم أثبت #873 الإصلاح.

### RTL/Bidi — تحت التحقق النهائي

commit `36c5297a92f2ee700a41c34fc2d16ca7680b8d3a`:

- `ltrIsolate()` مركزي باستخدام LRI/PDI مع منع double wrapping.
- `formatMoney()` يستخدم helper المركزي بدل literal مكرر.
- Person Timeline وDocuments Hub يعيدان استخدام `formatMoney()` بدل formatter مالي مكرر.
- عزل العملات والتواريخ/الأوقات وأرقام المستندات والهاتف والبريد والحالات التقنية وmetadata اللاتينية للمرفقات داخل RTL.
- Unit tests جديدة لـLTR isolation.

### Security / Settings — تحت التحقق النهائي

commit `c035bc56fb526e2ae8e891a526b970f717f8d39f`:

- Security entry actions تتكدس وتصبح full-width عند النص الكبير.
- Security Hub header يتكدس عند المساحة الضيقة أو Font Scale المرتفع.
- App Lock toggle يتكدس بدل ضغط الوصف والـSwitch في Row واحد.
- Security Hub يستخدم `WaslMaxContentWidth` المركزي.
- Settings header يتكدس عند النص الكبير.
- Privacy switches في Settings تتكدس عند النص الكبير.
- اختبار Security عند 200% Font Scale أصبح يثبت التكديس نفسه، لا مجرد قابلية التمرير.
- أضيف `SettingsUiInstrumentedTest` عند 200% Font Scale ويثبت التكديس ووصول/تغيير إعداد الخصوصية.

الـPR run الذي ظهر مباشرة من commit المنشأ داخل GitHub Actions كان `action_required` بلا Jobs، لذلك لا يعتبر بوابة تحقق. commit التوثيق الحالي موجود عمدًا لتشغيل Android CI طبيعية على نفس الكود؛ لا توسم دفعة RTL/Bidi + Security/Settings Verified قبل نجاحها بالكامل.

## ملاحظة استقرار CI

- فشل CI #858 السابق كان Flaky selector في `DueDateUiInstrumentedTest` بسبب `hasScrollAction()` الذي طابق أكثر من Scrollable.
- تم استبداله بـ`hasScrollToIndexAction()` للحاوية ذات الفهرسة.
- نفس SHA نجح في #859، ثم أثبت #861 الإصلاح بالكامل.
- CI concurrency يستخدم اسم الفرع (`github.head_ref || github.ref_name`) لمنع تشغيل push وPR متوازيين لنفس الرأس.
- الإلغاء التلقائي لتشغيل push الموازي عند بدء PR run هو السلوك المقصود.

## ترتيب العمل التالي

1. إغلاق بوابة CI الكاملة للرأس الحالي الذي يشمل RTL/Bidi + Security/Settings large-font.
2. إذا أخضر: متابعة Documents Hub ثم Home ثم Account Details بحسب فجوات text scaling وtouch targets وfocus/TalkBack semantics الفعلية.
3. تثبيت أي فجوات وصول باختبارات Instrumentation حقيقية، لا بمجرد تعديلات شكلية.
4. تنفيذ المصاريف/الديون الجماعية حسب المواصفة دون إنشاء Ledger موازٍ.
5. جولة UI/PDF polishing النهائية المؤجلة عمدًا حتى اكتمال الوظائف.
6. Full offline acceptance + migrations/backup regression.
7. Release signing والتوزيع.

## سياسة الجودة

لا تعتبر أي مرحلة مكتملة بمجرد compile. حسب نطاقها يجب أن تمر عبر Unit + Lint + build + Room/instrumentation + UI + Backup/Restore/PDF evidence عند الصلة. ولا تُخفف الاختبارات لإجبار CI على الأخضر؛ عند ظهور فشل يجب تمييز فشل المنتج من فشل الاختبار الهش وإصلاح السبب الحقيقي.