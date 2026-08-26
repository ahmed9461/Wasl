# سجل التغييرات

جميع التغييرات المهمة في وَصل تسجل هنا.

## Unreleased

### Added

- المرجع التأسيسي الدائم للمشروع وملفات الذاكرة `AGENTS.md`, `PROJECT_CONTEXT.md`, `SPEC.md`, `DECISIONS.md`, `HANDOFF.md`.
- Android project أصلي بـKotlin وJetpack Compose وMaterial 3 وNavigation 3، مع Theme عربي RTL ووضعين فاتح/داكن.
- وحدة `core:domain` مستقلة للأموال والديون، مع `Money` بوحدات صغرى `Long` دون Floating Point.
- Ledger مالي append-only، دفعات جزئية/نهائية، وعكس دفعة كحدث مستقل بسبب موثق.
- Room persistence ومجموعة Migrations صريحة حتى **Schema v7** دون destructive migration.
- إنشاء شخص ودين، وإنشاء ديون مستقلة لشخص محفوظ دون تكرار Person.
- استحقاق قابل للتعديل/الإلغاء مع Audit، متابعة ذكية عبر WorkManager، ومنبه قوي اختياري عبر Exact Alarm مع fallback.
- **تذكير متابعة عام مستقل عن `due_date`** لكل حساب، بأنماط مرة واحدة / يومي / أسبوعي / شهري، دون أي أثر على Ledger أو الرصيد.
- `GeneralReminderService` وStore/Recovery قابلان لإعادة المحاولة Idempotently؛ الإلغاء يحفظ `CANCELLED` بدل حذف السجل.
- مركز **«التذكيرات»** من Settings لإضافة/تعديل/إلغاء تذكير المتابعة لكل حساب، مع RTL صريح وحماية الشاشة وComponent غير مصدّر.
- Backup/Restore يحفظ ويستعيد تذكيرات `GENERAL` مع `repeat_rule` والحالة وZoneId.
- شاشة Today للاستحقاقات والوعود والأقساط، وبحث Room Reactive وفتح الحساب من النتائج.
- Payment Promises مستقلة عن Ledger و`due_date` بحالات `PENDING / KEPT / MISSED / CANCELLED`.
- Installment Plans مع `ACTIVE / SUPERSEDED` وRevision history محفوظ، وتقدم متصالح مع Ledger.
- مستندات مالية من immutable snapshots:
  - `PAYMENT_RECEIPT`.
  - `DEBT_RECEIPT`.
  - `ACCOUNT_STATEMENT` متعدد الصفحات.
- هوية مستند قابلة للتخصيص، ترقيم سنوي، Metadata، SHA-256، page count، فتح/مشاركة عبر FileProvider بعد فحص السلامة.
- `issued_documents` كسجل مستندات موحد، و`payment_issued_documents` View خاصة بإيصالات السداد.
- Backup تطبيقي يدوي مشفر بكلمة مرور يشمل الجداول وملفات PDF `READY`.
- Backup envelope باستخدام AES-256-GCM + PBKDF2-HMAC-SHA256 بـ210,000 iteration + gzip JSON.
- Restore مرحلي عبر Stage + Room مؤقتة، مع فحص Schema والمسارات وSHA-256 وForeign Keys والثوابت المالية وRollback.
- شاشة إعدادات للخصوصية والنسخ الاحتياطي والمستندات.
- **App Lock** عبر AndroidX Biometric `1.1.0` و`BiometricPrompt` مع `BIOMETRIC_WEAK | DEVICE_CREDENTIAL`.
- مهلات App Lock: فورًا / 15 ثانية / دقيقة / 5 دقائق، باستخدام `SystemClock.elapsedRealtime()`.
- `AppLockViewModel` يحافظ على الجلسة عبر Configuration changes.
- شاشة قفل تمنع Pointer interaction وتزيل Compose semantics عن المحتوى المالي الخلفي.
- Recovery صريح لتعطيل App Lock إذا لم تعد مصادقة النظام متاحة، دون حذف بيانات المستخدم.
- App Lock يفرض `FLAG_SECURE`، مع بقاء خيار الشاشة الآمنة المستقل.
- اختبارات `AppLockViewModelTest` لمنطق التفعيل/المصادقة/المهلة/Lock now/التعطيل.
- `SecurityUiInstrumentedTest` يغطي App Lock UI وRecovery و**Dark Mode + Font Scale 2.0**.
- `MvpAcceptanceInstrumentedTest` لرحلة كاملة: إنشاء دين → restart → دفعات → سداد → عكس → سداد بديل → READY PDF → Backup مشفر → إفساد ملف/إضافة بيانات → Restore والتحقق من الحالة.
- اختبارات General Reminder تغطي Store/Service/Recovery، إنشاء تذكير أسبوعي وإلغاءه من Hub، واستعادة Snapshot التذكير من Backup مشفر.
- CI يولد ويفحص ملفات PDF حقيقية بـ`pdfinfo`, `pdftotext`, `pdftoppm` ويرفع أدلة مستقلة.

### Changed

- أصبح Room Schema الحالي **v7**؛ `issued_documents.ledger_entry_id` nullable لدعم Debt Receipt وAccount Statement دون Ledger entry مصطنع، مع بقاء Payment Receipt مرتبطًا بحركته.
- التذكيرات العامة تعيد استخدام جدول `reminders` الحالي ونوع `GENERAL`، لذلك لم تتطلب Schema v8 أو جدولًا ماليًا موازيًا.
- أصبحت `payment_issued_documents` طبقة View مخصصة لإيصالات السداد بدل افتراض أن كل `issued_documents` Payment Receipt.
- أصبحت شاشة Today تجمع الاستحقاقات ووعود السداد والأقساط مع إبقاء النماذج مستقلة عن Ledger.
- أصبح Production يمرر قدرات الأقساط عبر `InstallmentAwareWaslRepository` بدل Global state أو تكرار Room logic.
- أصبح مسار النسخ الاحتياطي المدعوم داخل التطبيق صريحًا ومشفرًا؛ Android Auto Backup وDevice Transfer يبقيان معطلين عمدًا.
- أصبح قفل التطبيق يعتمد مصادقة Android نفسها بدل إنشاء PIN خاص بوَصل.
- أصبحت مهلة القفل monotonic ولا تتأثر بتغيير ساعة الجهاز/المنطقة الزمنية.
- أصبحت الحماية من Screenshot/Recent Apps مفروضة تلقائيًا عند تفعيل App Lock.
- مركز التذكيرات يستخدم `noHistory` حتى لا يبقى Activity جانبيًا يمكن الرجوع إليه بعد مغادرة التطبيق متجاوزًا مسار القفل المعتاد.
- تم تحديث `docs/DATABASE_SCHEMA.md` ليطابق Schema v7 الفعلية بدل وصف v3 القديم.

### Fixed

- محاذاة إصدارات AndroidX مع compileSdk 36 المستقر.
- تثبيت معرفات أوامر الإنشاء/الدفع/العكس عبر إعادة المحاولة لمنع تكرار العمليات بعد نتائج حفظ غير مؤكدة.
- محاذاة kotlinx-serialization Core/JSON عبر BOM 1.9.0.
- إصلاح Recovery للتذكيرات وإعادة `BLOCKED_PERMISSION/FAILED` بصورة قابلة للاسترداد دون تغيير الدين.
- إصلاح Exact Alarm UX بحيث لا تفتح إعدادات Android تلقائيًا من السويتش.
- مزامنة Room Schema v5/v6/v7 الملتزمة مع المخرجات الفعلية من Room.
- إصلاح Migration v6→v7 للـView `payment_issued_documents` ليتطابق SQL مع `@DatabaseView` حرفيًا.
- إصلاح AndroidTest بعد تعميم `DocumentSnapshot` ليتعامل Payment Receipt صراحة مع `PaymentReceiptSnapshot`.
- إصلاح Compose test imports غير الموجودة في اختبارات Today/Security.
- إصلاح `MvpAcceptanceInstrumentedTest` بعد اكتشاف `StackOverflowError`: `ContextWrapper.getFilesDir()` أصبح يشير صراحة إلى مجلد الاختبار الخارجي بدل استدعاء getter نفسه.
- إصلاح `GeneralRemindersHubUiInstrumentedTest` بعد أن كشف CI أن زر الإلغاء قد يكون خارج viewport داخل Dialog؛ الاختبار أصبح يستخدم `performScrollTo()` قبل الضغط بدل تفسير وجود Semantics node كإمكانية ضغط فعلية.

### Security

- `usesCleartextTraffic=false`.
- Android Auto Backup وDevice Transfer معطلان.
- Keystores وملفات الأسرار غير ملتزمة في Git.
- إشعارات الاستحقاق `VISIBILITY_PRIVATE` مع Public version عامة لا تعرض الاسم/المبلغ.
- PDF `READY` يفحص وجود الملف وSHA-256 قبل الفتح أو المشاركة.
- Backup مشفر ومصدق، مع فحص المستندات قبل النسخ وبعد فكها.
- Restore يرفض Schema أو بنية أو مسارات أو بصمات أو Foreign Keys غير صالحة قبل اعتماد الحالة.
- App Lock لا يخزن PIN مخصصًا أو كلمة مرور الجهاز أو قالب بصمة؛ التحقق مفوض إلى Android.
- المحتوى المالي خلف شاشة القفل غير قابل للتفاعل عبر Pointer أو Compose semantics.
- Recovery من غياب المصادقة لا يحذف البيانات.
- مركز التذكيرات غير مصدّر ويطبق RTL وسياسة `FLAG_SECURE`/`noHistory` الملائمة لمسار القفل.

### Verification

آخر بوابة كاملة لكود المرحلة:

- **Android CI #458** — Run `32912759608` — head `c019d3a7160c29360082b12ec1c42559d4d6127b`.
- Unit tests ✅
- Lint ✅
- Debug APK ✅
- Room Schema v7 export/current check ✅
- Android Emulator instrumentation: **65/65** ✅
- failures: 0 / errors: 0 / skipped: 0 ✅
- General Reminder Store/Service/Hub/Backup regression ✅
- Payment Receipt PDF evidence ✅
- Debt Receipt PDF evidence ✅
- Account Statement PDF evidence — sample 3 pages ✅
- App Lock/Font Scale regression ✅
- MVP end-to-end acceptance ✅

Artifacts من CI #458:

- `Wasl-debug` — `9587182455` — SHA-256 `cd8a1b686c5ad4f7e606634fb46fa314b38259c2a903c420128bb8012c74a53f`.
- `Wasl-room-schema` — `9587182763` — SHA-256 `7466781408776d618a62cade3463f9f68317fcd17e0ce6a7c61289319c8ad187`.
- `Wasl-payment-receipt-evidence` — `9587332535` — SHA-256 `015278424337baef2043225bc14c1162ef6429b94be05410bf482cc4cf58c10d`.
- `Wasl-account-document-evidence` — `9587332805` — SHA-256 `e10ba0c68d05d29d219127d0299ffdbbf1d52d9f039b8728b121e966abf10e5e`.
- `Wasl-room-instrumentation-results` — `9587333092` — SHA-256 `d4c1e2c1e72b9d58b7e2d80e3f00fe6e8beb662e6a9e414d7865c594055f36b8`.
