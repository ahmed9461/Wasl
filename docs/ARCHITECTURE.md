# معمارية وَصل

## المبادئ

- Local database هي المصدر التشغيلي الأساسي.
- core:domain هو مصدر القواعد المالية ولا يعتمد على Android.
- UI لا تتعامل مع DAO أو SQL مباشرة.
- كل Write مالي يمر بأمر واحد وTransaction واحدة.
- كل Read مالي يأتي من Domain أو Projection مشتق منه.
- الخدمات الخارجية المستقبلية اختيارية ومحاطة بواجهات.

## الطبقات

### UI

Compose screens وViewModels وUiState وUser actions. مسؤولة عن Parsing الإدخال والعرض وإدارة دورة حياة الشاشة، وليست مسؤولة عن حساب الرصيد. واجهة الدفع تعرض Review منفصلًا قبل التأكيد، وواجهة العكس تجمع سببًا إلزاميًا.

### Domain

Money وCurrencyCode وDebtLedger والقواعد والـUse cases المشتركة. لا يعتمد على Compose أو Room أو Context.

### Data

Room entities وDAO وRepository implementations وMappers وTransactions وملفات المرفقات والنسخ الاحتياطي. لا تسرب Entity إلى UI.

### Platform

Notifications وAlarmManager وWorkManager وBiometrics وKeystore وFileProvider وPDF. تستدعى عبر واجهات لكي تختبر دون جهاز متى أمكن.

## اتجاه الاعتماد

- app/UI يعتمد على Domain وعلى Repository interfaces.
- Data يعتمد على Domain ويطبق Repository interfaces.
- Platform adapters تعتمد على Android وتعيد نتائج صريحة.
- Domain لا يعتمد على أي طبقة أعلى.

## تدفق القراءة

1. DAO يعيد Flow من بيانات محلية أو Projection.
2. Repository يحول Entity إلى Domain أو Read model.
3. ViewModel يحول Flow إلى StateFlow.
4. Compose تجمع الحالة بطريقة Lifecycle-aware.
5. Formatting للمبلغ يحدث في UI باستخدام Currency definition.

## تدفق الكتابة المالية

مثال تسجيل دفعة:

1. UI تجمع Amount وDate وNote وتعرض التأكيد.
2. ViewModel يرسل RecordPaymentCommand مع commandId فريد.
3. Use case يتحقق من الشكل الأساسي.
4. Repository يفتح Database transaction.
5. يقرأ الدين وLedger الحاليين داخل Transaction.
6. Domain يسجل PaymentRecorded أو يرفض.
7. Repository يضيف Ledger entity وAudit metadata.
8. يحدث Projection إن وجدت.
9. يعيد قراءة النتيجة ويتحقق من الرصيد والحالة.
10. Commit.
11. بعد نجاح Commit فقط تُجدول آثار جانبية قابلة لإعادة المحاولة مثل Reminder أو اقتراح PDF.

لا يُنشأ PDF أو Notification داخل Transaction قاعدة البيانات.

## Idempotency والتزامن

- كل Command مالي يملك commandId فريدًا مع Unique index.
- تكرار commandId يعيد النتيجة السابقة ولا يضيف حدثًا.
- القراءة والتحقق والإضافة في Transaction واحدة لمنع دفعتين تتجاوزان المتبقي.
- IDs تولد قبل الكتابة وتبقى ثابتة عبر Retry.
- إذا فشل الرد بعد احتمال Commit، يحتفظ ViewModel بكائن Command نفسه كاملًا ويعيد إرساله؛ أي تعديل من المستخدم يلغي الأمر المعلّق ويولد أمرًا جديدًا بعد المراجعة.
- لا تعتمد سلامة الكتابة على ترتيب وصول UI وحده.

## الزمن

- Instant للأحداث التقنية والدفعات والتسجيل.
- LocalDate لموعد استحقاق مدني.
- ZoneId محفوظ عند إنشاء Reminder يحتاج معنى محليًا.
- كل تحويل للعرض يستخدم Timezone الحالي أو Timezone الحدث وفق نوعه.

## الحالة

- DebtState المالي مشتق من الأصل والLedger.
- DueState مشتق من Due date واليوم.
- Lifecycle state مثل Archived أو Void منفصل عن الرصيد.
- نصوص الواجهة ليست قيم قاعدة البيانات.

## بنية الحزم الحالية

- `com.wasl.domain`: القواعد المالية وParsing الدقيق للإدخال.
- `com.wasl.app`: Application entry وComposition root وHome/Account details ViewModels وشاشات Compose وNavigation keys.
- `com.wasl.app.data`: عقود Repository وRead/Command models.
- `com.wasl.app.data.local`: Room database وRepository الذري وMappers الداخلية.
- `com.wasl.app.data.local.entity`: persons وdebts وledger_entries وreminders وعلاقات القراءة.
- `com.wasl.app.data.local.dao`: واجهات الإدخال والاستعلام بلا Update/Delete للسجل المالي، مع انتقالات حالة محدودة للتذكير.
- `com.wasl.app.reminder`: حساب الزمن المدني، WorkManager scheduler/workers، القناة وناشر الإشعار وRecovery receiver.
- `com.wasl.app.ui`: Theme، وتستقبل الشاشات الجديدة عند توسعها.

لا تنشأ وحدة Gradle جديدة قبل وجود سبب مثل زمن بناء أو إعادة استخدام أو فصل Platform واضح.

## التعامل مع الفشل

- Validation failure يعود كخطأ مستخدم ولا يكتب شيئًا.
- Storage failure يتراجع بالكامل.
- Side-effect failure بعد Commit يسجل كحالة Pending قابلة لإعادة المحاولة، ولا يعكس الدفعة.
- Corrupt data يفشل بصوت عالٍ ويمنع حسابًا مضللًا.
- لا Catch فارغ ولا قيمة افتراضية مالية تخفي فسادًا.

## التوسع

AI وCloud Sync وDocument verification ستكون Adapters اختيارية. لا تدخل إلى Domain كشرط. Group expenses تضيف Aggregate وتوزيعات مرتبطة ولا تغير Debt ledger الحالي بصورة ملتوية.
