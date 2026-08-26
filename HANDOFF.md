# HANDOFF — الحالة الحية

آخر تحديث: 2026-08-26

## الحالة الحالية

- المشروع: **وَصل — Wasl**
- الشعار: **كل حساب له وصل**
- الإصدار: `0.1.0-dev`
- الفرع النشط: `agent/bootstrap-wasl-foundation`
- Pull Request: `#1` إلى `main`، وما زال **Draft** ومفتوحًا.
- `main` لم يُدمج ولم يُعدّل ضمن هذه المرحلة.
- قاعدة البيانات: Room Schema **v7** مع Migrations متسلسلة v1→v7 دون destructive migration.
- هوية Schema v7: `d2c9fe45f2707138bc1476639617e255`.
- آخر رأس وظيفي خضع للتحقق الكامل: `53faec3cd7007c6a9e318b3fa69a2f955bb2ed4d`.
- آخر تحقق وظيفي كامل: **Android CI #485** — Run `32998478006` — نجاح كامل.

## ما يعمل الآن

### الحسابات والدفتر المالي

- إنشاء شخص جديد مع دينه الأول، أو إنشاء دين مستقل لشخص محفوظ دون تكرار Person.
- الاتجاهان: **لي عند الناس** و**عليّ للناس**.
- العملات الحالية: YER / SAR / USD.
- `Money` يستخدم Integer Minor Units من نوع `Long`؛ لا `Float` أو `Double` في المنطق المالي.
- Ledger append-only هو مصدر الحقيقة المالي.
- الدفعات الجزئية والنهائية محفوظة كأحداث، وتصحيح الدفعة يتم بعكس موثق لا بحذف الأصل.
- الرصيد والحالة و`closed_at` مشتقة من Replay.
- الأوامر المالية الحساسة Idempotent وتتحمل إعادة المحاولة بعد نتيجة غير مؤكدة.

### الاستحقاق والمتابعة والتنبيه القوي

- تاريخ استحقاق اختياري مع تعديل/إلغاء وAudit before/after.
- متابعة ذكية مرتبطة بالاستحقاق: قبل الموعد بيوم، يوم الموعد، بعد يومين، ثم أسبوعيًا حتى السداد.
- WorkManager Unique Work مع Recovery وإعادة جدولة Idempotent عند تغير الوقت/المنطقة الزمنية.
- `BLOCKED_PERMISSION` و`FAILED` حالات قابلة للاسترداد دون تغيير أصل الدين أو Ledger.
- Exact Alarm اختياري للمنبه القوي، وWorkManager يبقى fallback.
- فتح إعدادات Exact Alarm أو الإشعارات يتم بفعل صريح من المستخدم.

### تذكيرات المتابعة العامة

- يوجد **تذكير متابعة عام مستقل عن `due_date`** لكل حساب/دين، ولا ينشئ عملية مالية أو يغير الرصيد.
- الأنماط المدعومة: مرة واحدة، يومي، أسبوعي، شهري.
- الحفظ أو التعديل يعيد استخدام هوية التذكير الخاصة بالحساب بدل إنشاء تكرارات، والجدولة على Android قابلة للاسترداد Idempotently.
- إلغاء التذكير يحفظ حالته `CANCELLED` بدل حذف السجل، وتلغى جدولة المنصة دون المساس بالدين أو Ledger.
- المنطقة الزمنية وموعد التنفيذ وقاعدة التكرار محفوظة صراحة، ويعاد Recovery عند الحاجة.
- فشل مزامنة WorkManager بعد حفظ Room لا يلغي الحفظ؛ تظهر حالة مزامنة قابلة للاسترداد.
- نقص صلاحية الإشعارات لا يمنع حفظ التذكير نفسه، ويعرض للمستخدم مسار إعدادات صريحًا.
- مركز **«التذكيرات»** متاح من الإعدادات، يعرض الحسابات ويسمح بإضافة/تعديل/إلغاء تذكير المتابعة لكل حساب.
- مركز التذكيرات عربي RTL، غير مصدّر خارج التطبيق، ويستخدم `FLAG_SECURE` وفق سياسة الخصوصية/القفل، و`noHistory` حتى لا يبقى مدخلًا جانبيًا يتجاوز App Lock بعد مغادرة التطبيق.
- Backup/Restore المشفر يحفظ ويستعيد سجل `GENERAL` مع `repeat_rule` والحالة والمنطقة الزمنية.

### إجراءات الإشعار الآمنة — REM-006

- لمس جسم إشعار الاستحقاق أو المتابعة العامة يفتح الحساب مباشرة.
- أزرار الإشعار الثلاثة: **دفع جزء** / **تم السداد** / **ذكرني لاحقًا**؛ فتح الحساب لا يحتاج زرًا رابعًا.
- «دفع جزء» و«تم السداد» لا يكتبان Payment أو Ledger entry من Notification callback؛ ينقلان المستخدم فقط إلى مسار الدفع داخل التطبيق.
- «تم السداد» يعبئ المتبقي الحالي كاملًا بدقة العملة قبل صفحة المراجعة؛ YER وSAR وUSD مغطاة باختبار Unit مستقل.
- المراجعة والتأكيد داخل التطبيق يبقيان إلزاميين قبل أي Commit مالي؛ لا يوجد auto-submit من الإشعار.
- `MainActivity` يقبل فقط `PARTIAL` / `FULL` ويغلق الإشعار الأصلي باستخدام tag/id.
- «ذكرني لاحقًا» يستخدم Unique delayed Work قابلًا للاستبدال Idempotently، ولا يغير الرصيد أو `due_date` ولا ينشر من جديد إذا أصبح الحساب مسددًا.
- PendingIntents تستخدم `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`.
- مسارا نشر DUE_DATE وGENERAL يستخدمان سطح الإجراءات الآمن نفسه عند ملاءمته.

### وعود السداد والأقساط

- Promise مستقل عن Ledger و`due_date`، بحالات `PENDING / KEPT / MISSED / CANCELLED`.
- `KEPT` لا تنشئ Payment تلقائيًا.
- خطط الأقساط مستقلة عن Ledger، مع `ACTIVE / SUPERSEDED` وRevision history محفوظ.
- تقدم القسط مشتق من العمليات المالية الحقيقية، مع الثابت `paidAmount + remainingAmount == scheduledAmount`.
- Today يعرض الاستحقاقات والوعود والأقساط المستحقة/المتأخرة دون خلط نماذجها.

### البحث والتنقل

- Navigation 3 للرئيسية واليوم والبحث وتفاصيل الحساب ومركز الأقساط والمستندات والإعدادات والأمان، مع مركز تذكيرات مستقل من الإعدادات.
- بحث Room Reactive باسم الشخص أو بيان الدين بحد نتائج واضح.
- فتح الحساب من الرئيسية أو Today أو البحث أو مركز الأقساط أو الإشعار.

### المستندات المالية PDF

- `PAYMENT_RECEIPT`: إيصال سداد مبني من Snapshot ثابت لدفعة محفوظة.
- `DEBT_RECEIPT`: إيصال دين مستقل عن Ledger entry بعينه.
- `ACCOUNT_STATEMENT`: كشف حساب متعدد الصفحات من Snapshot ثابت لتاريخ العمليات.
- هوية مستند قابلة للتخصيص، ترقيم سنوي، metadata، SHA-256 وعدد صفحات وحالة توليد.
- مستند `READY` لا يفتح أو يشارك إذا فقد ملفه أو فشل SHA-256.
- `issued_documents` هو السجل العام، و`payment_issued_documents` View مخصصة لإيصالات السداد.
- CI يفحص PDF حقيقيًا بـ`pdfinfo` و`pdftotext` و`pdftoppm`.

### النسخ الاحتياطي والاستعادة

- Backup تطبيقي يدوي مشفر بكلمة مرور؛ Android Auto Backup وDevice Transfer معطلان عمدًا.
- النسخة منطقية وتشمل الجداول وملفات PDF الخاصة بالمستندات `READY`، وليست نسخًا خامًا لملف SQLite الحي.
- التشفير: AES-256-GCM مع PBKDF2-HMAC-SHA256 بـ210,000 iteration وPayload مضغوط gzip.
- قبل النسخ والاستعادة يتم التحقق من المسارات وSHA-256 والبنية وSchema.
- Restore يجهز الملفات في Stage ويفحص البيانات داخل Room مؤقتة وForeign Keys والثوابت المالية قبل استبدال الحالة الحية.
- يوجد Rollback عند فشل الاستبدال ولا تعتبر الاستعادة ناجحة قبل اكتمال مساري DB والملفات.
- تذكيرات المتابعة العامة جزء من النسخة المنطقية، ويختبر Restore بقاء النوع وقاعدة التكرار والحالة والمنطقة الزمنية.

### الأمان والخصوصية

- قفل التطبيق منفذ عبر AndroidX `BiometricPrompt` وAndroid system authentication.
- المسموح: `BIOMETRIC_WEAK | DEVICE_CREDENTIAL` للتوافق مع الأجهزة المدعومة من minSdk 26.
- وَصل لا يخزن PIN مخصصًا ولا كلمة مرور قفل الجهاز ولا قالب بصمة.
- مهلة إعادة القفل قابلة للاختيار: فورًا، 15 ثانية، دقيقة، 5 دقائق؛ الافتراضي 15 ثانية.
- قياس المهلة يستخدم `SystemClock.elapsedRealtime()` وليس ساعة الحائط.
- حالة الجلسة تعيش في `AppLockViewModel` وتتحمل Configuration change.
- عند القفل تُمنع Pointer events وتُزال Semantics عن المحتوى المالي خلف شاشة القفل.
- تفعيل App Lock يفرض `FLAG_SECURE` حتى لو كان خيار الشاشة الآمنة المستقل معطلًا.
- إذا لم تعد مصادقة النظام متاحة يوجد Recovery صريح لتعطيل القفل دون حذف البيانات.
- تفاصيل الإشعارات الحساسة قابلة للإخفاء، وإشعارات الاستحقاق تستخدم Lock-screen public version عامة.

## قواعد ثابتة لا تكسر

1. Ledger مالي append-only؛ التصحيح بالعكس لا بالحذف أو تعديل حدث سابق.
2. Promise ليست عملية مالية ولا تغيّر الرصيد أو `due_date` تلقائيًا.
3. Installment Plan ليست Ledger ولا تنشئ رصيدًا ماليًا موازيًا.
4. الدفعات الفعلية للأقساط تأتي من Ledger.
5. Revision جديدة لخطة الأقساط تحفظ السابقة كـ`SUPERSEDED`.
6. المتابعة والتنبيه أدوات جدولة فقط؛ لا تغير الرصيد ولا تنشئ دفعة.
7. التذكير العام مستقل عن `due_date`؛ تعديله أو إلغاؤه لا يعدل موعد الاستحقاق ولا Ledger.
8. Exact Alarm اختياري وWorkManager fallback.
9. كل دين لشخص موجود حساب مستقل بمعرف مستقل.
10. الأموال لا تستخدم Floating Point.
11. المستند الصادر Snapshot تاريخي ثابت.
12. `READY` لا يعني صالحًا للفتح دون ملف صحيح وSHA-256 مطابق.
13. Backup/Restore لا يتجاوز فحوص Schema والمسارات والبصمات وForeign Keys والثوابت.
14. App Lock لا يخزن اعتمادًا سريًا مخصصًا داخل وَصل؛ المصادقة مسؤولية Android.
15. إجراءات الإشعار المالية لا تكتب Ledger مباشرة؛ كل دفعة تمر بمراجعة وتأكيد داخل التطبيق.
16. لا destructive migrations.
17. لا أسرار أو مفاتيح توقيع داخل المستودع.
18. لا دمج إلى `main` دون طلب صريح من صاحب المشروع.

## قاعدة البيانات الحالية — Schema v7

الجداول:

- `persons`
- `debts`
- `ledger_entries`
- `reminders`
- `audit_events`
- `document_identities`
- `issued_documents`
- `payment_promises`
- `installment_plans`
- `installments`

View:

- `payment_issued_documents`: `SELECT * FROM issued_documents WHERE document_type = 'PAYMENT_RECEIPT'`.

Migrations:

- v1→v2: reminders.
- v2→v3: audit events.
- v3→v4: document identities + issued documents.
- v4→v5: payment promises + indexes.
- v5→v6: installment plans/installments + revisions.
- v6→v7: `issued_documents.ledger_entry_id` أصبح nullable مع حفظ الصفوف السابقة وإعادة الفهارس والـView.

التذكيرات العامة وإجراءات REM-006 تعيد استخدام النماذج الحالية؛ **لم تتطلب Schema v8**.

Schema artifact الملتزم: `app/schemas/com.wasl.app.data.local.WaslDatabase/7.json`.

## التحقق الحالي

**Android CI #485** — Run `32998478006` — head `53faec3cd7007c6a9e318b3fa69a2f955bb2ed4d`:

- Unit tests ✅
- Lint ✅
- Debug APK ✅
- Room Schema v7 generated/current check ✅
- Android Emulator instrumentation: **70/70** ✅
- 0 failures / 0 errors / 0 skipped ✅
- REM-006: DUE_DATE/GENERAL Notification actions، Safe payment intents، Snooze Unique Work وCurrency-aware full-payment prefill ✅
- General Reminder Store/Service/Recovery regression ✅
- General Reminders Hub UI: إنشاء تذكير أسبوعي ثم إلغاؤه وحفظ `CANCELLED` ✅
- Backup/Restore: استعادة سجل `GENERAL` وقاعدة التكرار والحالة والمنطقة الزمنية ✅
- App Lock Unit/UI regression ✅
- Dark Mode + Font Scale 2.0 security UI regression ✅
- MVP end-to-end acceptance: debt → restart → payments → settlement → reversal → replacement settlement → READY PDF → encrypted backup → corruption/post-backup mutation → restore ✅
- Migration v1→v7 / v6→v7 ✅
- Backup/Restore وPDF integrity ✅
- Payment/Debt/Statement PDF evidence ✅
- Payment receipt markers: `PAY-2026-00042`, `AL NOOR TRADING`, `123,456.78 USD` ✅
- Debt receipt marker: `DEBT-2026-00043` ✅
- Account statement markers: `STAT-2026-00044`, `REF-35`، وعينة 3 صفحات ✅

CI #483 — Run `32991533877` — كشف قبل #485 Race واحدة في `GeneralRemindersHubUiInstrumentedTest`: كانت الشاشة تعرض حالة «أسبوعي» قبل اكتمال side effect الخاص بالـscheduler، فقرأ الاختبار قائمة التسجيل مبكرًا. أصلح الاختبار بالانتظار على **حالة Room + scheduler side effect** مع `CopyOnWriteArrayList` بدل sleep أو تأخير اعتباطي؛ #485 أثبت الإصلاح بـ70/70.

Artifacts من #485:

- `Wasl-debug` — `9617704751` — SHA-256 `973a3985dd2c94d29a743488948172b444ef4e341b01005f8f9911d53468d539`.
- `Wasl-room-schema` — `9617705367` — SHA-256 `5fec4cc04f3720ba6bdb4e33499e0ca76e1d3809a68b524948706c79735d9797`.
- `Wasl-payment-receipt-evidence` — `9617967942` — SHA-256 `3e3f0cf2fbb908f68ae4def535c7325d49d7fa31282c21695216d76c8b6c036c`.
- `Wasl-account-document-evidence` — `9617968386` — SHA-256 `557a464d264d97d048d37d6fc52c6aad8cc02dd7e310e4b5d5f439d3fd24717e`.
- `Wasl-room-instrumentation-results` — `9617968909` — SHA-256 `346fb994021ed012d97598aa12b2020cc77e093b59e14aad33ebc3ce0fe9c2cb`.

## ديون تقنية غير حاجبة

- توسيع اختبارات الإتاحة إلى TalkBack وفحص شاشات أكثر تحت Font Scale الكبير.
- اختبار نوافذ Compact/Expanded بصورة أوسع من شاشة الأمان الحالية.
- نقل Compose tests من API v1 إلى API v2 بعد ضبط التزامن المطلوب.
- ترقية `actions/setup-java@v4` إلى v5 وتحديث Actions التي تصدر تحذيرات Node 20/24.
- إزالة safe calls غير الضرورية في `StrongAlarmStoreInstrumentedTest`.
- إزالة `else` الزائد في `AccountDocumentPdfRenderer` بعد أن أصبح `when` exhaustive.
- تحسين زمن CI عبر Gradle configuration cache عندما يثبت أمانه.

## مرشحات المرحلة التالية

تُختار من `WASL_MASTER_PROJECT_PROMPT.md` و`SPEC.md` دون إسقاط قرار سابق:

- بحث متقدم داخل العمليات والمستندات والمبالغ والتواريخ.
- مرفقات/مطالبات وإدارة ملفات حساسة عند اعتماد تفاصيلها.
- توسيع الإتاحة والتكيف مع أحجام النوافذ وTalkBack.
- تجهيز Release signing والتوزيع عندما يحين وقت الإصدار.
