# معمارية وَصل

آخر تحديث: 2026-08-27

## المبادئ

- Local database هي المصدر التشغيلي الأساسي.
- `core:domain` هو مصدر القواعد المالية ولا يعتمد على Android.
- UI لا تتعامل مع DAO أو SQL مباشرة.
- كل Write مالي يمر بأمر واضح وTransaction ذرية.
- كل Read مالي يأتي من Domain أو Read model مشتق من البيانات الدائمة.
- الخدمات الخارجية المستقبلية اختيارية ومحاطة بواجهات.
- Ledger وحده مصدر الحقيقة المالي؛ Reminder/Promise/Claim/Installment/Document/Attachment لا تتحول إلى Ledger موازٍ.

## الطبقات

### UI

Compose screens وViewModels وUiState وUser actions. مسؤولة عن جمع الإدخال والعرض والتنقل، لا عن حساب الرصيد.

مسارات الدفع والعكس والإدخال الطبيعي/الصوتي تستخدم Preview/Confirmation قبل Commit. UI لا تنفذ Payment من Notification callback أو Parsing/Voice result مباشرة.

### Domain

`Money`, `CurrencyCode`, `DebtLedger`, balance summaries, installment schedule وقواعد مالية مشتركة. لا يعتمد على Compose أو Room أو Context.

### Data

Room entities وDAO وRepository/Store implementations وMappers وTransactions، إضافة إلى Metadata للمستندات والمرفقات وعقود Backup/Restore.

Schema الحالية v9 وتضم المصادر الدائمة للأشخاص والديون والLedger والتذكيرات والتدقيق والمستندات والوعود والأقساط والمطالبات والمرفقات.

### Platform

Notifications، AlarmManager، WorkManager، Biometrics، FileProvider، PDF، filesystem vault، وAndroid speech recognition launcher. تستدعى عبر طبقات قابلة للاختبار متى أمكن؛ الإملاء الصوتي الحالي يستخدم `RecognizerIntent` مباشرة ويحتاج seam أو Adapter أوضح للاختبارات.

## اتجاه الاعتماد

- UI يعتمد على Domain وعلى Repository/Store interfaces.
- Data يعتمد على Domain ويطبق interfaces.
- Platform adapters تعتمد على Android وتعيد نتائج صريحة.
- Domain لا يعتمد على طبقات أعلى.
- PDF/Statistics/Search يقرأ من نفس الحقائق ولا يعيد كتابة قواعد رصيد مستقلة.

## تدفق القراءة

1. DAO/Store يعيد Flow أو snapshot من Room.
2. Repository يعيد بناء Domain aggregate أو Read model.
3. ViewModel يحول البيانات إلى StateFlow/UiState.
4. Compose تجمع الحالة Lifecycle-aware.
5. Formatting يحدث عند العرض باستخدام تعريف العملة والتاريخ المناسب.

### Today

يجمع مرشحين من الاستحقاقات والوعود والأقساط والمطالبات مع بقاء كل نموذج مستقل. لا يخزن نص «متأخر» في القاعدة؛ Due state والأيام مشتقة من التاريخ الحالي.

### Search

- Local search يطبع whitespace ويعامل SQLite wildcard كنصوص حرفية.
- Advanced search يغطي الأشخاص/الديون/العمليات/المستندات/المبالغ/التواريخ.
- نتائج البحث تحمل IDs وتفتح المصدر الأصلي؛ لا تنشئ نسخة مالية منفصلة.

### Person Timeline

يجمع حسابات شخص واحد بالـ`PersonId` ويعرض timeline موحدًا. العملات تبقى مجموعات منفصلة ولا تنتج «صافي» مضللًا بين YER/SAR/USD.

## تدفق الكتابة المالية

مثال Payment:

1. UI تجمع amount/date/note وتعرض review.
2. ViewModel ينشئ commandId ثابتًا للمحاولة.
3. Repository يفتح Transaction.
4. يقرأ debt + ledger الحاليين.
5. Domain يتحقق ويسجل Payment event أو يرفض.
6. يضاف Ledger entity ويعاد حساب projection.
7. Commit.
8. الآثار الجانبية بعد Commit فقط، وقابلة لإعادة المحاولة.

لا PDF ولا Notification داخل Transaction المالية.

## كتابات غير مالية

### Due schedule / Reminders

تغير scheduling metadata وAudit، ثم تنفذ WorkManager/Alarm side effects بعد Commit. فشل المنصة لا يغير الرصيد.

### Payment Promise

سجل متابعة مستقل. `KEPT` لا ينشئ Payment تلقائيًا.

### Payment Claim — «طالبني»

- يسمح فقط لدين `PAYABLE` المفتوح.
- يسجل follow-up/history وحالته.
- لا يغير Ledger أو due date.
- Backup/Restore يفحص الاتجاه والحالات والتواريخ.

### Installment Plan

خطة توقع/جدولة. Revision جديدة تحفظ السابقة `SUPERSEDED`. التقدم الحقيقي مشتق من Ledger.

### Documents

تلتقط immutable snapshot ثم تولد PDF خارج Transaction المصدر. سجل `READY` لا يفتح إذا فشل فحص الملف/sha256.

### Attachments

ينسخ الملف إلى internal vault، يحسب SHA-256 ويحفظ metadata في Room. الربط بحركة اختياري ويجب أن تكون الحركة من نفس الدين.

### Natural + Voice Entry

النص:

`Text → Parser → Draft → Preview → Confirmation → Repository`

الإملاء الصوتي الحالي:

`RecognizerIntent → recognized text → نفس Parser → Draft → Preview → Confirmation → Repository`

نتيجة الصوت لا تكتب قاعدة البيانات. الخطوة المعمارية المتبقية هي فصل launch/result mapping عن Compose بما يكفي لاختبار success/cancel/empty/unavailable دون الاعتماد على تطبيق تعرف صوت خارجي في CI.

## Idempotency والتزامن

- Commands الحساسة تملك IDs فريدة.
- Retry يعيد نفس command object بدل إنشاء حدث جديد بلا داعٍ.
- القراءة والتحقق والكتابة داخل Transaction واحدة عند وجود تنافس مالي.
- IDs تولد قبل الكتابة وتبقى ثابتة عبر retry.
- Side effects تستخدم unique identities أو replace semantics بحسب النوع.

## الزمن

- `Instant` للأحداث التقنية والدفعات.
- `LocalDate` للمواعيد المدنية.
- `ZoneId` محفوظ عند الحاجة إلى معنى محلي للتذكير.
- App Lock timeout يستخدم monotonic elapsed time وليس wall clock.

## بنية الحزم الحالية

- `com.wasl.domain`: Money، Ledger، summaries، installment schedule، parsers المالية.
- `com.wasl.app`: application entry، navigation، screens/viewmodels الرئيسية، Person/Statistics/Natural Entry.
- `com.wasl.app.data`: عقود repository/stores وmodels.
- `com.wasl.app.data.local`: Room database، stores/repository وsnapshot codecs.
- `com.wasl.app.data.local.entity`: entities حتى Schema v9.
- `com.wasl.app.data.local.dao`: DAOs لكل المصادر الدائمة.
- `com.wasl.app.reminder`: WorkManager/Exact Alarm/recovery/notifications/snooze.
- `com.wasl.app.document`: PDF renderers/services وFile access للمستندات والمرفقات.
- `com.wasl.app.backup`: encrypted logical Backup/Restore v9.
- `com.wasl.app.privacy`: App Lock وPrivacy preferences.
- `com.wasl.app.ui`: Theme ومكونات UI المشتركة.

لا تنشأ وحدة Gradle جديدة قبل سبب معماري واضح مثل إعادة استخدام أو زمن بناء أو فصل Platform حقيقي.

## التعامل مع الفشل

- Validation failure: لا كتابة.
- Storage failure داخل Transaction: rollback.
- Side-effect failure بعد Commit: حالة قابلة للاسترداد، لا عكس للعملية الأصلية.
- Corrupt financial data: fail loudly، لا قيم مالية افتراضية تخفي المشكلة.
- Missing/hash-mismatched document/attachment: لا فتح أو مشاركة باعتباره سليمًا.
- Backup validation failure: لا تغيير للحالة الحية.
- Speech recognizer unavailable/cancel/empty: لا Draft مالي جديد ولا Save؛ تبقى الواجهة قابلة للمحاولة النصية.

## الأمان

- لا cleartext traffic كافتراضي.
- لا secrets/keystores في Git.
- App Lock يفوض المصادقة للنظام ولا يخزن PIN خاصًا.
- `FLAG_SECURE` وفق سياسة القفل/الخصوصية.
- PendingIntents الخاصة بالإشعارات immutable حيث يلزم.
- Notification payment action يفتح التطبيق ولا يكتب Ledger مباشرة.
- FileProvider هو بوابة المشاركة للملفات الداخلية.
- Voice recognition مجرد قناة إدخال؛ لا يمنحها صلاحية كتابة مالية مختلفة عن النص.

## التوسع

- Voice hardening: فصل adapter واختبار حالات النتائج، لا إعادة بناء Parser.
- AI: مساعد اختياري لصياغة/استخراج، لا مصدر حقيقة ولا executor مالي دون confirmation.
- Cloud Sync: Adapter اختياري لاحق، لا شرط للوظائف الأساسية.
- Group expenses: Aggregate مستقل وتوزيعات واضحة، دون ليّ DebtLedger الحالي ليصبح نموذجًا غير مناسب.

## حالة الجودة الحالية

Android CI #851 أثبت Unit/Lint/Debug build وRoom v9، وتوقف instrumentation بسبب أربعة imports Compose test قديمة. الإصلاح الحالي لا يغير المعمارية؛ يعيد تجميع الاختبارات حتى تعمل البوابة كاملة من جديد.
