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
- آخر رأس وظيفي خضع للتحقق الكامل: `c019d3a7160c29360082b12ec1c42559d4d6127b`.
- آخر تحقق وظيفي كامل: **Android CI #458** — Run `32912759608` — نجاح كامل.

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
- إجراءات الإشعار المالية من REM-006 مثل «تم السداد / دفع جزء / ذكرني لاحقًا» **ليست ضمن هذا Slice** وتبقى مرحلة مستقلة حتى تمر بمسارات التأكيد المالي الصحيحة.

### وعود السداد والأقساط

- Promise مستقل عن Ledger و`due_date`، بحالات `PENDING / KEPT / MISSED / CANCELLED`.
- `KEPT` لا تنشئ Payment تلقائيًا.
- خطط الأقساط مستقلة عن Ledger، مع `ACTIVE / SUPERSEDED` وRevision history محفوظ.
- تقدم القسط مشتق من العمليات المالية الحقيقية، مع الثابت `paidAmount + remainingAmount == scheduledAmount`.
- Today يعرض الاستحقاقات والوعود والأقساط المستحقة/المتأخرة دون خلط نماذجها.

### البحث والتنقل

- Navigation 3 للرئيسية واليوم والبحث وتفاصيل الحساب ومركز الأقساط والمستندات والإعدادات والأمان، مع مركز تذكيرات مستقل من الإعدادات.
- بحث Room Reactive باسم الشخص أو بيان الدين بحد نتائج واضح.
- فتح الحساب من الرئيسية أو Today أو البحث أو مركز الأقساط.

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
15. لا destructive migrations.
16. لا أسرار أو مفاتيح توقيع داخل المستودع.
17. لا دمج إلى `main` دون طلب صريح من صاحب المشروع.

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

التذكيرات العامة تعيد استخدام `reminders` الحالي؛ **لم تتطلب Schema v8**.

Schema artifact الملتزم: `app/schemas/com.wasl.app.data.local.WaslDatabase/7.json`.

## التحقق الحالي

**Android CI #458** — Run `32912759608` — head `c019d3a7160c29360082b12ec1c42559d4d6127b`:

- Unit tests ✅
- Lint ✅
- Debug APK ✅
- Room Schema v7 generated/current check ✅
- Android Emulator instrumentation: **65/65** ✅
- 0 failures / 0 errors / 0 skipped ✅
- General Reminder Store/Service/Recovery regression ✅
- General Reminders Hub UI: إنشاء تذكير أسبوعي ثم إلغاؤه وحفظ `CANCELLED` ✅
- Backup/Restore: استعادة سجل `GENERAL` وقاعدة التكرار والحالة والمنطقة الزمنية ✅
- App Lock Unit/UI regression ✅
- Dark Mode + Font Scale 2.0 security UI regression ✅
- MVP end-to-end acceptance: debt → restart → payments → settlement → reversal → replacement settlement → READY PDF → encrypted backup → corruption/post-backup mutation → restore ✅
- Migration v1→v7 / v6→v7 ✅
- Backup/Restore وPDF integrity ✅
- Payment/Debt/Statement PDF evidence ✅

Artifacts من #458:

- `Wasl-debug` — `9587182455` — SHA-256 `cd8a1b686c5ad4f7e606634fb46fa314b38259c2a903c420128bb8012c74a53f`.
- `Wasl-room-schema` — `9587182763` — SHA-256 `7466781408776d618a62cade3463f9f68317fcd17e0ce6a7c61289319c8ad187`.
- `Wasl-payment-receipt-evidence` — `9587332535` — SHA-256 `015278424337baef2043225bc14c1162ef6429b94be05410bf482cc4cf58c10d`.
- `Wasl-account-document-evidence` — `9587332805` — SHA-256 `e10ba0c68d05d29d219127d0299ffdbbf1d52d9f039b8728b121e966abf10e5e`.
- `Wasl-room-instrumentation-results` — `9587333092` — SHA-256 `d4c1e2c1e72b9d58b7e2d80e3f00fe6e8beb662e6a9e414d7865c594055f36b8`.

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

- إجراءات الإشعار الآمنة من REM-006 مثل فتح الحساب/تم السداد/دفع جزء/ذكرني لاحقًا، مع بقاء أي فعل مالي خلف تأكيد صريح.
- بحث متقدم داخل العمليات والمستندات والمبالغ والتواريخ.
- مرفقات/مطالبات وإدارة ملفات حساسة عند اعتماد تفاصيلها.
- توسيع الإتاحة والتكيف مع أحجام النوافذ وTalkBack.
- تجهيز Release signing والتوزيع عندما يحين وقت الإصدار.
