# القرارات المعمارية والتقنية

آخر تحديث: 2026-08-26

أي تغيير في قرار معتمد هنا يحتاج سببًا موثقًا واختبارات أثر. لا تُرقّى المكتبات لمجرد وجود إصدار أحدث.

## ADR-001 — Android أصلي بـKotlin

- الحالة: معتمد.
- القرار: تطبيق Android أصلي بلغة Kotlin.
- السبب: الوصول المباشر إلى Room وWorkManager وAlarmManager وBiometricPrompt وFileProvider وAndroid PDF مع أقل طبقات وسيطة لمنتج مالي Local-first.
- البدائل المرفوضة حاليًا: Flutter / React Native / PWA، لعدم وجود حاجة منصة ثانية تبرر Bridge إضافيًا في المسارات الحساسة.
- المصدر: https://developer.android.com/topic/architecture

## ADR-002 — طبقات واضحة ووحدتان أساسيتان

- الحالة: معتمد.
- القرار: `app` لطبقة Android و`core:domain` لمصدر الحقيقة المالي الخالي من Android.
- السبب: اختبار المال دون Emulator ومنع تسرب UI/Room إلى قواعد Ledger، مع تجنب Modularization استعراضي مبكر.
- Hilt أو وحدات إضافية لا تُضاف إلا عند وجود ضغط حقيقي.

## ADR-003 — UDF وRepository كمصدر بيانات

- الحالة: معتمد ومطبق تدريجيًا.
- القرار: UI تقرأ UiState وترسل Actions؛ ViewModels تتعامل مع Repositories/Stores؛ Room هو المصدر المحلي الأساسي.
- مرفوض: DAO مباشرة من Composable أو Global mutable state.
- السبب: حالة متوقعة، قابلية اختبار، واستعادة صحيحة بعد Configuration changes.

## ADR-004 — الأموال `Long` بوحدات صغرى

- الحالة: معتمد.
- القرار: `Money(minorUnits: Long, currency)` ولا يدخل `Float`/`Double` إلى Domain المالي.
- السبب: دقة حتمية، اكتشاف Overflow، وعدم أخطاء الكسور الثنائية.
- `BigDecimal` يعاد تقييمه فقط عند متطلبات عملات/دقة تتجاوز النموذج الحالي.

## ADR-005 — Ledger append-only والعكس بدل الحذف

- الحالة: معتمد ومطبق.
- القرار: أصل الدين ثابت، Payment يضاف كحدث، وتصحيحه يضاف `PaymentReversed` بسبب موثق. الرصيد والحالة مشتقان من Replay.
- مرفوض: تعديل `balance` كمصدر حقيقة أو حذف دفعة تاريخية.
- النتيجة: التاريخ المالي يبقى قابلًا للتدقيق وإعادة البناء.

## ADR-006 — Room 2.8.4 وMigrations صريحة

- الحالة: معتمد ومنفذ حتى **Schema v7**.
- القرار: Room 2.8.4 + KSP 2.3.11 + Schema export واختبارات Migration دون `fallbackToDestructiveMigration` في Production.
- ملفات `1.json` حتى `7.json` جزء دائم من المستودع.
- CI يولد Schema الحالية ويقارنها بالملف الملتزم.
- هوية v7: `d2c9fe45f2707138bc1476639617e255`.
- السلسلة:
  - v1→v2: reminders.
  - v2→v3: audit events.
  - v3→v4: document identities + issued documents.
  - v4→v5: payment promises.
  - v5→v6: installment plans/installments + revisions.
  - v6→v7: `issued_documents.ledger_entry_id` nullable مع حفظ البيانات وإعادة الفهارس والـView.
- `payment_issued_documents` في Migration يجب أن يطابق تعريف `@DatabaseView` الذي يتحقق منه Room.
- kotlinx-serialization Core/JSON محاذيان عبر BOM 1.9.0.
- المصادر: https://developer.android.com/training/data-storage/room وhttps://developer.android.com/jetpack/androidx/releases/room

## ADR-007 — Compose وMaterial 3 وNavigation 3

- الحالة: معتمد ومطبق.
- القرار: Single-activity + Jetpack Compose + Material 3 + Navigation 3 stable 1.1.6 بمفاتيح صغيرة Serializable وBack stack صريح.
- الوجهات الحالية تشمل Home / Today / Search / Account Details / Installments / Documents / Settings / Security، ويُفتح مركز التذكيرات العامة من Settings كواجهة مستقلة محمية.
- مرفوض: Router خاص بلا حاجة أو حمل كيانات كاملة داخل مفاتيح التنقل.
- المصادر: https://developer.android.com/topic/architecture/recommendations وhttps://developer.android.com/guide/navigation/navigation-3

## ADR-008 — سياسة التذكيرات والمنبهات

- الحالة: معتمد ومطبق.
- القرار:
  - WorkManager 2.11.2 للمتابعة التي تقبل نافذة تنفيذ النظام، بUnique Work مشتق من reminder id.
  - Exact Alarm فقط لمنبه قوي فعّله المستخدم صراحة.
  - WorkManager يبقى fallback عند تعذر Exact Alarm.
  - Recovery Idempotent عند بدء التطبيق وتغير الوقت/المنطقة الزمنية.
  - نقص صلاحية الإشعارات يسجل `BLOCKED_PERMISSION` ولا يغير الدين.
  - إعدادات النظام لا تفتح تلقائيًا بمجرد تشغيل سويتش؛ تحتاج فعلًا صريحًا من المستخدم.
- المصادر: https://developer.android.com/develop/background-work/services/alarms وhttps://developer.android.com/develop/background-work/background-tasks/persistent/getting-started

## ADR-009 — الأمان Local-first

- الحالة: معتمد ومطبق في المسارات الحالية.
- القرار:
  - لا Backend أو Analytics إجباريين.
  - Android Auto Backup وDevice Transfer معطلان عمدًا.
  - النسخ المدعوم Backup تطبيقي مشفر يختاره المستخدم.
  - `usesCleartextTraffic=false`.
  - ملفات التطبيق داخل Internal storage وتشارك عبر FileProvider.
  - لا PII أو بيانات مالية في Logs.
  - حماية التطبيق تستخدم Android system authentication؛ لا تخزين PIN خام أو اعتماد مخصص.
- Keystore يبقى المسار المناسب إذا احتاجت مستقبلًا مفاتيح محلية غير قابلة للتصدير، لكنه ليس مفتاح النسخة المحمولة.
- المصادر: https://developer.android.com/privacy-and-security/keystore وhttps://developer.android.com/identity/sign-in/biometric-auth

## ADR-010 — إصدارات البناء وAPI

- الحالة: معتمد كبداية مستقرة.
- القرار:
  - AGP 9.3.1
  - Gradle 9.5.0
  - JDK 17
  - Kotlin 2.3.21
  - Compose BOM 2026.06.01
  - AndroidX Core 1.18.0
  - Lifecycle 2.10.0
  - compileSdk 36 / targetSdk 36 / minSdk 26
- لا ترفع المنصة أو المكتبات لمجرد توفر إصدار أحدث؛ يجب قراءة الأثر وتشغيل البوابات.

## ADR-011 — توليد PDF عبر Android Platform

- الحالة: معتمد ومطبق.
- القرار: `android.graphics.pdf.PdfDocument` مع رسم نص يدعم RTL/LTR وSnapshot ثابت لبيانات الهوية/العملية وقت الإصدار.
- السبب: Offline، بلا Dependency PDF ثقيلة، ونجح في بوابة Android الحالية.
- CI يولد ملفات حقيقية ويفحصها بـ`pdfinfo` و`pdftotext` و`pdftoppm`.
- الأنواع الحالية: Payment Receipt / Debt Receipt / Account Statement متعدد الصفحات.
- أي استبدال للمحرك يحتاج مقارنة ترخيص/حجم/تشكيل/أمان وRegression كامل للمستندات الحالية.

## ADR-012 — Composition root يدوي ما دام صغيرًا

- الحالة: معتمد ومطبق مؤقتًا.
- القرار: Constructor injection يدوي في `WaslApplication`. Hilt يضاف فقط عندما تصبح التركيبات اليدوية مصدر تعقيد حقيقي.
- مرفوض: Service locator عالمي.

## ADR-013 — سجل مستندات موحد مع Snapshots ثابتة

- الحالة: معتمد ومطبق في Schema v7.
- القرار:
  - `issued_documents` سجل عام للأنواع `PAYMENT_RECEIPT`, `DEBT_RECEIPT`, `ACCOUNT_STATEMENT`.
  - `ledger_entry_id` nullable لأن Payment Receipt يرتبط بحركة واحدة بينما Debt Receipt/Statement لا يحتاجان حركة مصطنعة.
  - `payment_issued_documents` View توافق خاصة بإيصالات السداد.
  - كل مستند يحفظ Snapshot ثابتًا، رقمًا سنويًا، حالة، مسار PDF، SHA-256 وعدد الصفحات.
  - أوامر الإصدار Idempotent.
- مرفوض: توليد تاريخي من الحالة الحية أو إجبار كل مستند على Ledger entry.

## ADR-014 — Backup يدوي مشفر واستعادة مرحلية

- الحالة: معتمد ومطبق.
- القرار:
  - المستخدم يبدأ Create/Restore بفعل صريح وكلمة مرور.
  - النسخة منطقية ومحمولة وتشمل الجداول المدعومة وملفات PDF `READY`.
  - AES-256-GCM + PBKDF2-HMAC-SHA256 بـ210,000 iteration + gzip JSON.
  - فحص SHA-256 للمستندات قبل النسخ وبعد فكها.
  - Restore يرفض Schema/بنية/مسار/بصمة غير صالحة.
  - الملفات تجهز في Stage والبيانات تختبر في Room مؤقتة قبل استبدال الحالة الحية.
  - Foreign Keys والثوابت المالية تفحص قبل القبول.
  - يوجد Rollback لمسار الملفات/قاعدة البيانات ولا يعلن نجاح جزئي.
- مرفوض: نسخ SQLite الخام فقط، Auto Backup غير المقيد، أو الكتابة المباشرة فوق الحالة الحية قبل التحقق.

## ADR-015 — قفل التطبيق عبر مصادقة Android لا PIN خاص بوَصل

- الحالة: **معتمد ومطبق**.
- المشكلة: البيانات المالية قد تكون على جهاز مفتوح أو يُترك مؤقتًا لشخص آخر، لكن إنشاء PIN خاص داخل التطبيق يضيف اعتمادًا سريًا جديدًا يجب تخزينه واستعادته وحمايته.
- القرار:
  - استخدام AndroidX Biometric stable `1.1.0` و`BiometricPrompt`.
  - المجموعة المعتمدة: `BIOMETRIC_WEAK | DEVICE_CREDENTIAL` بدل `BIOMETRIC_STRONG | DEVICE_CREDENTIAL` لضمان التوافق الصحيح مع نطاق minSdk 26 ومع القيود المعروفة في Android 9/10.
  - وَصل لا يخزن PIN مخصصًا أو كلمة مرور الجهاز أو بيانات بصمة؛ التحقق مسؤولية Android.
  - تفعيل القفل يحتاج مصادقة ناجحة.
  - الجلسة لها مهلة قابلة للاختيار: 0 / 15 ثانية / دقيقة / 5 دقائق، والافتراضي 15 ثانية.
  - قياس المهلة يستخدم `SystemClock.elapsedRealtime()` كي لا يتأثر بساعة الحائط أو timezone.
  - `AppLockViewModel` يحتفظ بحالة الجلسة عبر Configuration change.
  - عند القفل، واجهة المحتوى الخلفي تفقد Compose semantics وتستهلك Pointer events، ويُفرض `FLAG_SECURE`.
  - عند غياب وسيلة مصادقة Android يوجد Recovery صريح لتعطيل App Lock دون حذف بيانات.
- البدائل:
  - PIN خاص بوَصل: مرفوض حاليًا لأنه يخلق سرًا جديدًا ومسار Recovery إضافيًا دون ضرورة.
  - قفل بالبصمة فقط: مرفوض لأنه أقل مرونة من fallback إلى Device Credential.
  - الاعتماد على قفل الجهاز وحده بلا App Lock: لا يلبي حالات مشاركة جهاز مفتوح مؤقتًا.
- التحقق:
  - Unit tests لمنطق session/timeout.
  - Compose instrumentation للشاشة وRecovery.
  - Dark Mode + Font Scale 2.0 regression.
  - CI #458: بقيت اختبارات App Lock ضمن Android instrumentation الكامل 65/65 دون فشل.
- المصدر: https://developer.android.com/identity/sign-in/biometric-auth

## ADR-016 — تذكير متابعة عام مستقل عن تاريخ الاستحقاق

- الحالة: **معتمد ومطبق**.
- المشكلة: المستخدم قد يريد متابعة حساب في موعد يختاره حتى لو لم يكن للدين `due_date`، أو يريد تكرار متابعة لا يغيّر معنى الاستحقاق المدني ولا السجل المالي.
- القرار:
  - التذكير العام نموذج جدولة مستقل عن `due_date` وعن Ledger، مرتبط بحساب/دين محدد.
  - يعاد استخدام جدول `reminders` الحالي ونوع `GENERAL`؛ لم تُنشأ حقيقة مالية موازية ولم تحتج الميزة Schema v8.
  - الأنماط الحالية: مرة واحدة، يومي، أسبوعي، شهري.
  - موعد التنفيذ يحفظ كـInstant مع ZoneId وقاعدة تكرار صريحة كي يبقى معنى الجدولة واضحًا بعد Restart أو تغير المنطقة الزمنية.
  - التعديل يعيد استخدام هوية التذكير الخاصة بالحساب ويستبدل جدولة المنصة بدل إنشاء تذكيرات مكررة.
  - الإلغاء يحفظ `CANCELLED` ولا يحذف سجل التذكير، ثم يحاول إلغاء جدولة Android.
  - Room هي الحقيقة المحلية أولًا؛ إذا فشلت مزامنة WorkManager بعد Commit يبقى التغيير محفوظًا ويطلب Recovery بدل التراجع عن بيانات المستخدم.
  - Recovery وUnique Work يبقيان Idempotent، والـWorker يتحقق من الحالة الحالية قبل نشر إشعار حتى لا يرسل تذكيرًا أُلغي بعد جدولة قديمة.
  - رفض Notification permission لا يمنع حفظ التذكير، ويظهر مسارًا صريحًا لإعداد الإشعارات.
  - مركز التذكيرات من Settings يسمح بإضافة/تعديل/إلغاء التذكير لكل حساب، مع RTL صريح وحماية الشاشة، وهو Component غير مصدّر و`noHistory` لمنع تجاوز App Lock عبر سجل Activity جانبي.
  - Backup/Restore المنطقي المشفر يحفظ ويستعيد نوع `GENERAL` وقاعدة التكرار والحالة وZoneId.
- ثوابت:
  - لا يغير التذكير العام أصل الدين أو رصيده أو حالته المالية.
  - لا يغير `due_date` ولا ينشئ Audit ماليًا أو Ledger entry.
  - لا يحول إجراء إشعار إلى Payment تلقائيًا.
- مؤجل عمدًا:
  - REM-006: «تم السداد»، «دفع جزء»، «ذكرني لاحقًا» وأي إجراء مالي من الإشعار تبقى Slice مستقلة؛ أي فعل مالي يجب أن يمر بمراجعة وتأكيد آمنين.
- التحقق:
  - Store tests للهوية الواحدة والتعديل والإلغاء والحالات.
  - UI instrumentation ينشئ تذكيرًا أسبوعيًا من Hub ثم يلغي ويؤكد `CANCELLED` في Room.
  - Backup/Restore instrumentation يعيد Snapshot التذكير العام بعد تغيير الحالة الحية.
  - CI #458 — Run `32912759608`: Android instrumentation **65/65**، صفر failures/skips، مع بقاء Room Schema v7 وPDF regressions خضراء.
- المصدر: https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started
