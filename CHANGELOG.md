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
- `GeneralReminderService` وStore/Recovery قابلان لإعادة المحاولة بصورة Idempotent؛ الإلغاء يحفظ `CANCELLED` بدل حذف السجل.
- مركز **«التذكيرات»** من Settings لإضافة/تعديل/إلغاء تذكير المتابعة لكل حساب، مع RTL صريح وحماية الشاشة وComponent غير مصدّر.
- Backup/Restore يحفظ ويستعيد تذكيرات `GENERAL` مع `repeat_rule` والحالة وZoneId.
- **REM-006: إجراءات إشعار تفاعلية وآمنة**؛ لمس جسم الإشعار يفتح الحساب، والأزرار الثلاثة هي «دفع جزء» / «تم السداد» / «ذكرني لاحقًا».
- `ReminderNotificationActions` كمسار موحد لنوايا فتح الحساب والدفع، مع PendingIntents غير قابلة للتعديل وعدم كتابة Ledger من Notification callback.
- `ReminderSnooze` بجدولة Unique delayed Work قابلة للاستبدال Idempotently، لا تغيّر الرصيد أو `due_date` ولا تعيد إشعار حساب مسدد.
- اختبار `NotificationPaymentIntentFormattingTest` لدقة تعبئة السداد الكامل في YER/SAR/USD.
- اختبارات ناشري DUE_DATE وGENERAL وإجراءات Snooze ومسار Payment UI للتأكد أن الإشعار لا ينفذ دفعة تلقائيًا.
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
- أصبح ناشرا DUE_DATE وGENERAL يستخدمان سطح إجراءات REM-006 الآمن عند ملاءمته، دون إنشاء مصدر حقيقة مالي جديد.
- «تم السداد» من الإشعار أصبح يفتح مسار الدفع القياسي داخل التطبيق ويعبئ **المتبقي الحالي كاملًا بدقة العملة**، مع بقاء Review/Confirm إلزاميين قبل Commit.
- لمس جسم الإشعار أصبح هو «فتح الحساب»، للحفاظ على أزرار Android الثلاثة الظاهرة لـ«دفع جزء / تم السداد / ذكرني لاحقًا».
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
- إصلاح `GeneralRemindersHubUiInstrumentedTest` بعد أن كشف CI سابقًا أن زر الإلغاء قد يكون خارج viewport داخل Dialog؛ الاختبار أصبح يستخدم `performScrollTo()` قبل الضغط بدل تفسير وجود Semantics node كإمكانية ضغط فعلية.
- تثبيت تزامن `GeneralRemindersHubUiInstrumentedTest` بعد أن كشف CI #483 Race بين ظهور «أسبوعي» واكتمال `scheduler.replace()`؛ الاختبار ينتظر الآن حالة Room + side effect الفعلي ويستخدم `CopyOnWriteArrayList` بدل sleep أو تأخير اعتباطي. CI #485 أثبت الإصلاح بـ70/70.

### Security

- `usesCleartextTraffic=false`.
- Android Auto Backup وDevice Transfer معطلان.
- Keystores وملفات الأسرار غير ملتزمة في Git.
- إشعارات الاستحقاق `VISIBILITY_PRIVATE` مع Public version عامة لا تعرض الاسم/المبلغ.
- إجراءات REM-006 المالية لا تنفذ mutation من الإشعار؛ كل Payment يمر بمسار التطبيق المعتاد للمراجعة والتأكيد.
- PendingIntents الخاصة بإجراءات الإشعار `FLAG_IMMUTABLE`، و`MainActivity` يتحقق من نوايا الدفع المعروفة فقط.
- PDF `READY` يفحص وجود الملف وSHA-256 قبل الفتح أو المشاركة.
- Backup مشفر ومصدق، مع فحص المستندات قبل النسخ وبعد فكها.
- Restore يرفض Schema أو بنية أو مسارات أو بصمات أو Foreign Keys غير صالحة قبل اعتماد الحالة.
- App Lock لا يخزن PIN مخصصًا أو كلمة مرور الجهاز أو قالب بصمة؛ التحقق مفوض إلى Android.
- المحتوى المالي خلف شاشة القفل غير قابل للتفاعل عبر Pointer أو Compose semantics.
- Recovery من غياب المصادقة لا يحذف البيانات.
- مركز التذكيرات غير مصدّر ويطبق RTL وسياسة `FLAG_SECURE`/`noHistory` الملائمة لمسار القفل.

### Verification

آخر بوابة كاملة لكود المرحلة:

- **Android CI #485** — Run `32998478006` — head `53faec3cd7007c6a9e318b3fa69a2f955bb2ed4d`.
- Unit tests ✅
- Lint ✅
- Debug APK ✅
- Room Schema v7 export/current check ✅
- Android Emulator instrumentation: **70/70** ✅
- failures: 0 / errors: 0 / skipped: 0 ✅
- REM-006 DUE_DATE/GENERAL notification action regressions ✅
- Safe partial/full payment intent path without auto-submit ✅
- Currency-aware full-payment prefill for YER/SAR/USD ✅
- Snooze Unique Work / settled-account guard regression ✅
- General Reminder Store/Service/Hub/Backup regression ✅
- General Reminder UI synchronization regression discovered by #483 and fixed before #485 ✅
- Payment Receipt PDF evidence ✅
- Debt Receipt PDF evidence ✅
- Account Statement PDF evidence — sample 3 pages ✅
- App Lock/Font Scale regression ✅
- MVP end-to-end acceptance ✅
- Payment receipt markers: `PAY-2026-00042`, `AL NOOR TRADING`, `123,456.78 USD` ✅
- Debt receipt marker: `DEBT-2026-00043` ✅
- Account statement markers: `STAT-2026-00044`, `REF-35` ✅

Artifacts من CI #485:

- `Wasl-debug` — `9617704751` — SHA-256 `973a3985dd2c94d29a743488948172b444ef4e341b01005f8f9911d53468d539`.
- `Wasl-room-schema` — `9617705367` — SHA-256 `5fec4cc04f3720ba6bdb4e33499e0ca76e1d3809a68b524948706c79735d9797`.
- `Wasl-payment-receipt-evidence` — `9617967942` — SHA-256 `3e3f0cf2fbb908f68ae4def535c7325d49d7fa31282c21695216d76c8b6c036c`.
- `Wasl-account-document-evidence` — `9617968386` — SHA-256 `557a464d264d97d048d37d6fc52c6aad8cc02dd7e310e4b5d5f439d3fd24717e`.
- `Wasl-room-instrumentation-results` — `9617968909` — SHA-256 `346fb994021ed012d97598aa12b2020cc77e093b59e14aad33ebc3ce0fe9c2cb`.
