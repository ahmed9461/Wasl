# القرارات المعمارية والتقنية

آخر تحديث: 2026-08-25

أي تغيير في قرار معتمد هنا يحتاج سببًا موثقًا واختبارات أثر. لا تُرقّى المكتبات لمجرد وجود إصدار أحدث.

## ADR-001 — Android أصلي بـKotlin

- الحالة: معتمد
- المشكلة: المنتج Android، Local-first، ويحتاج تنبيهات موثوقة وملفات وPDF وبصمة وسلوكًا صحيحًا عبر إعادة التشغيل.
- القرار: تطبيق Android أصلي بلغة Kotlin.
- السبب: الوصول المباشر إلى AlarmManager وWorkManager وBiometricPrompt وKeystore وFileProvider، وتقليل طبقات الجسر في منتج مالي حساس.
- البدائل:
  - Flutter: جيد للمنصات المتعددة، لكنه يضيف Bridge وPlugins لمسارات Android الحساسة دون حاجة حالية لمنصة أخرى.
  - React Native: السبب نفسه مع سطح Dependencies أكبر.
  - PWA: غير مناسب لموثوقية التنبيهات المحلية والملفات والحماية المطلوبة.
- النتيجة: أفضل تكامل وأقل مخاطرة تشغيلية على Android، مقابل عدم مشاركة UI مع iOS غير المطلوب حاليًا.
- المصدر: [دليل معمارية Android](https://developer.android.com/topic/architecture)

## ADR-002 — طبقات واضحة ووحدتان فقط في البداية

- الحالة: معتمد
- القرار: وحدة app لواجهة Android، ووحدة core:domain خالية من Android لمصدر الحقيقة المالي. تبقى Data داخل app أو وحدة واحدة عند إدخال Room، ثم تُفصل فقط إذا ظهرت حاجة قياسية.
- السبب: اختبار المنطق المالي دون Emulator ومنع اعتماد الرصيد على UI، مع تجنب عشرات الوحدات المبكرة.
- البدائل:
  - وحدة app واحدة بكل شيء: رفضت لأنها تسهّل تسرب Android وRoom إلى القواعد المالية.
  - Modularization واسع من البداية: رفض لأنه يرفع زمن البناء والتعقيد قبل وجود Features.
- النتيجة: حدود قابلة للنمو دون Architecture استعراضية.
- المصدر: [توصيات معمارية Android](https://developer.android.com/topic/architecture/recommendations)

## ADR-003 — UDF وRepository كمصدر بيانات

- الحالة: معتمد للتنفيذ التدريجي
- القرار: UI تقرأ UiState غير قابل للتعديل وترسل Actions إلى ViewModel. ViewModel يستخدم Use cases عند وجود منطق مشترك، وRepositories هي بوابة Data. قاعدة البيانات المحلية هي المصدر الأساسي.
- السبب: حالة متوقعة، قابلية اختبار، واستعادة صحيحة بعد Configuration change.
- البدائل:
  - قراءة DAO مباشرة من Composables: مرفوضة بسبب الاقتران وتكرار القواعد.
  - Global mutable state: مرفوض بسبب الآثار الجانبية وصعوبة الاستعادة.
- المصدر: [بناء تطبيق Offline-first](https://developer.android.com/topic/architecture/data-layer/offline-first)

## ADR-004 — الأموال Long بوحدات صغرى

- الحالة: معتمد
- القرار: Money يحتوي minorUnits من نوع Long وCurrencyCode صريح. لا يدخل Float أو Double إلى Domain المالي.
- السبب: عمليات صحيحة حتميًا، اكتشاف Overflow، وعدم ظهور أخطاء الكسور الثنائية.
- البدائل:
  - Double: مرفوض بسبب أخطاء الدقة.
  - BigDecimal داخل قاعدة البيانات: صالح للحسابات المعقدة، لكنه يضيف تحويلًا وتخزينًا نصيًا لا يحتاجه MVP لمبالغ ذات دقة ثابتة. يعاد تقييمه إذا ظهرت عملات أو متطلبات تتجاوز Long.
- النتيجة: يجب أن تتولى طبقة العرض Parsing وFormatting وفق تعريف العملة، لا Domain ledger.

## ADR-005 — سجل مالي Append-only وعكس بدل الحذف

- الحالة: معتمد
- القرار: أصل الدين ثابت. الدفعات تسجل كأحداث، وتصحيح دفعة مالية يتم بإضافة PaymentReversed ثم العملية الصحيحة. الرصيد والحالة مشتقان بإعادة تشغيل السجل.
- السبب: حفظ التاريخ، توحيد الحساب، ومنع اختلاف UI وPDF والتقارير.
- البدائل:
  - تحديث حقل balance مباشرة: مرفوض لأنه يفقد كيفية الوصول إلى الرصيد.
  - حذف الدفعة: مرفوض لأنه يمحو أثرًا ماليًا.
- النتيجة: Repository يحفظ إضافة الحدث وتحديث Projection داخل Transaction واحدة، والواجهة تعرض العكس كحدث مستقل ولا توفر حذفًا ماليًا.

## ADR-006 — Room 2.8.4 وMigrations صريحة

- الحالة: معتمد ومنفذ حتى **Schema v7**
- القرار: استخدام Room 2.8.4 مع KSP 2.3.11 وتصدير Schema واختبارات Migration، مع سلسلة يدوية صريحة من v1 حتى v7 ودون `fallbackToDestructiveMigration` في Production.
- السبب: Android-only يحتاج حلًا مستقرًا ومجربًا، والتحقق وقت الترجمة من SQL ومسار Migrations. Room موصى به رسميًا بدل SQLite المباشر.
- البدائل:
  - SQLite API مباشرة: مرفوضة لكثرة Boilerplate وضعف التحقق وقت البناء.
  - Room 3.x: لا توجد حاجة KMP حاليًا، ولذلك لا يتحمل المشروع Major migration قبل وجود سبب فعلي.
  - قاعدة بيانات سحابية كمصدر رئيسي: مرفوضة لأنها تكسر Local-first.
- المصادر:
  - [Room موصى به بدل SQLite المباشر](https://developer.android.com/training/data-storage/room)
  - [Room 2 release notes](https://developer.android.com/jetpack/androidx/releases/room)
  - [Room 3](https://developer.android.com/jetpack/androidx/releases/room3)
  - [KSP](https://github.com/google/ksp)
- النتيجة التنفيذية:
  - ملفات `1.json` حتى `7.json` تحت `app/schemas/com.wasl.app.data.local.WaslDatabase/` جزء دائم من المستودع.
  - CI يولد Schema الحالية ويمنع انحرافها عن `7.json` الملتزم.
  - هوية Schema v7 الحالية: `d2c9fe45f2707138bc1476639617e255`.
  - v1→v2: reminders.
  - v2→v3: audit events.
  - v3→v4: document identities + issued documents.
  - v4→v5: payment promises + indexes.
  - v5→v6: installment plans/installments وتاريخ Revisions.
  - v6→v7: إعادة بناء `issued_documents` بحيث يصبح `ledger_entry_id` nullable للسماح بمستندات حساب غير مرتبطة بحركة واحدة، مع نسخ الصفوف السابقة وإعادة إنشاء الفهارس والـView.
  - تعريف `payment_issued_documents` في Migration يجب أن يطابق نص `@DatabaseView` حرفيًا؛ Room يتحقق من تعريف الـView في اختبارات Migration.
  - kotlinx-serialization Core وJSON محاذيان عبر BOM 1.9.0 لمنع خلط الإصدارات في AndroidTest runtime.

## ADR-007 — Compose وMaterial 3 وNavigation 3

- الحالة: معتمد ومطبق
- القرار: Single-activity وJetpack Compose وMaterial 3. تستخدم الواجهة Navigation 3 stable 1.1.6 بدل Router خاص، مع `NavKey` Serializable وBack stack واحد مملوك للتطبيق.
- السبب: Compose هو Toolkit الحديث الموصى به، وNavigation 3 يمنح Back stack صريحًا ويدعم الواجهات التكيفية.
- البدائل:
  - XML Views: مستقرة لكنها تزيد ازدواج UI state لمشروع جديد.
  - Router مخصص: مرفوض لعدم الحاجة وإعادة اختراع Back navigation.
  - Navigation 2: مستقر، لكن المشروع جديد والتوصية الرسمية الحالية هي Navigation 3.
- المصادر:
  - [توصيات Android: Compose وSingle activity وNavigation 3](https://developer.android.com/topic/architecture/recommendations)
  - [Navigation 3](https://developer.android.com/guide/navigation/navigation-3)
  - [Navigation bar في Compose](https://developer.android.com/develop/ui/compose/components/navigation-bar)
  - [Compose BOM](https://developer.android.com/develop/ui/compose/bom)
- النتيجة التنفيذية: الوجهات تحمل معرفات صغيرة لا كيانات كاملة، وتقرأ الحالة Reactive من Repository. توسعت الواجهة إلى الرئيسية واليوم والبحث وتفاصيل الحساب والأقساط والمستندات والإعدادات دون Global mutable navigation state.

## ADR-008 — سياسة التذكيرات والمنبهات

- الحالة: معتمد ومنفذ
- القرار:
  - WorkManager 2.11.2 للمتابعة التي تقبل نافذة تنفيذ النظام؛ كل عمل فريد باسم مشتق من reminder id.
  - Exact Alarm فقط لمنبه قوي فعّله المستخدم صراحة ويحتاج وقتًا دقيقًا.
  - WorkManager والمتابعة الذكية يبقيان fallback عند تعذر Exact Alarm.
  - Recovery idempotent يعمل عند بدء العملية وبعد تغير الوقت أو المنطقة الزمنية.
  - إذن POST_NOTIFICATIONS وحالة القناة يفحصان صراحة؛ الحظر يسجل `BLOCKED_PERMISSION` بدل تغيير أصل الدين.
  - طلب إعداد Exact Alarm لا يفتح إعدادات Android تلقائيًا عند تشغيل السويتش؛ الانتقال يحدث فقط عند فعل صريح من المستخدم.
  - تعديل الموعد يحافظ على reminder ID ويستبدل Unique Work السابق، وإزالة الموعد تعلّم السجل `CANCELLED` ثم تلغي العمل بعد نجاح Transaction.
- السبب: Exact alarms مكلفة ومقيدة، ووثائق Android توصي بعدم استخدامها للأعمال التي لا تحتاج لحظة دقيقة.
- البدائل:
  - Exact لكل تذكير: مرفوض بسبب البطارية والصلاحيات والرفض الافتراضي.
  - WorkManager لكل موعد دقيق: مرفوض لأنه لا يضمن لحظة دقيقة.
- المصادر:
  - [Schedule alarms](https://developer.android.com/develop/background-work/services/alarms)
  - [Getting started with WorkManager](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started)
  - [Unique Work](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/manage-work#unique-work)
  - [Notification runtime permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission)

## ADR-009 — الأمان Local-first

- الحالة: معتمد، ومسار النسخ الاحتياطي التطبيقي أصبح مطبقًا
- القرار:
  - لا Backend إجباري.
  - Android Keystore هو المسار المخطط للمفاتيح المحلية غير القابلة للتصدير عند تنفيذ قفل التطبيق.
  - BiometricPrompt مع Device Credential للحماية المحلية عند تنفيذها.
  - Android Auto Backup وDevice Transfer معطلان عمدًا.
  - النسخ الاحتياطي المدعوم هو Backup تطبيقي صريح يختاره المستخدم ويُحمى بكلمة مرور، ويشمل بيانات التطبيق وملفات المستندات الجاهزة.
  - `usesCleartextTraffic` معطل.
  - الملفات داخل مساحة التطبيق وتشارك عبر FileProvider عند الحاجة.
  - لا بيانات مالية في Logs أو Crash metadata.
- السبب: بيانات مالية حساسة ويجب ألا تصبح Cloud أو Logs شرط تشغيل أو مسار تسريب، كما يجب أن يظل قرار تصدير النسخة بيد المستخدم.
- البدائل:
  - Android Auto Backup غير المقيد: مرفوض لأن دورة الحياة وجهة التخزين لا تكونان تحت سيطرة وَصل بما يكفي لبيانات مالية ومستندات.
  - تخزين PIN أو مفتاح خام: مرفوض.
  - الاعتماد على حماية الجهاز وحدها لكل الحالات: غير كافٍ لخيار قفل مستقل داخل التطبيق.
- المصادر:
  - [Android Keystore](https://developer.android.com/privacy-and-security/keystore)
  - [Biometric authentication](https://developer.android.com/identity/sign-in/biometric-auth)
  - [Data backup overview](https://developer.android.com/identity/data/autobackup)

## ADR-010 — إصدارات البناء وAPI

- الحالة: معتمد كبداية
- القرار:
  - AGP 9.3.1
  - Gradle 9.5.0
  - JDK 17
  - Kotlin 2.3.21
  - Compose BOM 2026.06.01
  - AndroidX Core 1.18.0 وLifecycle 2.10.0
  - compileSdk 36 وtargetSdk 36
  - minSdk 26
- السبب:
  - تثبيت منصة مستقرة متوافقة بدل إدخال Android SDK أحدث قبل الحاجة والاختبار.
  - min 26 يوفر java.time وNotification Channels مباشرة ويقلل مسارات التوافق.
- البدائل:
  - رفع compileSdk/targetSdk لمجرد توفر إصدار أحدث: مرفوض دون اختبار أثر.
  - min 23: ممكن، لكنه يحتاج مسارات توافق إضافية؛ يعاد تقييمه عند وجود حاجة مستخدمين مثبتة.
- المصادر:
  - [AGP release notes](https://developer.android.com/build/releases/gradle-plugin)
  - [AndroidX Core releases](https://developer.android.com/jetpack/androidx/releases/core)
  - [AndroidX Lifecycle releases](https://developer.android.com/jetpack/androidx/releases/lifecycle)
  - [Target API requirements](https://developer.android.com/google/play/requirements/target-sdk)

## ADR-011 — توليد PDF عبر Android Platform مع دعم عربي

- الحالة: **معتمد ومطبق**
- القرار: استخدام `android.graphics.pdf.PdfDocument` مع رسم نص مناسب لـRTL/LTR، مع Snapshot ثابت لبيانات الهوية والمستند وقت الإصدار.
- سبب الاختيار: لا يضيف مكتبة PDF ثقيلة، يعمل Offline، ويستفيد من تشكيل النص في Android عند الرسم الصحيح.
- بوابة القبول: لم تعد نظرية؛ CI يولد ويفحص ملفات PDF فعلية من التطبيق.
- التحقق الحالي يشمل:
  - إيصال سداد عربي متعدد الصفحات مع RTL/LTR ومبلغ كبير.
  - إيصال دين.
  - كشف حساب متعدد الصفحات وتاريخ عمليات ومراجع.
  - فحص عبر `pdfinfo` و`pdftotext` وتحويل الصفحات إلى PNG عبر `pdftoppm`.
- البدائل:
  - AndroidX PDF: موجه أساسًا للعرض وليس أساس توليد المستندات المالية المطلوبة.
  - مكتبة PDF خارجية كبيرة: لا حاجة لها بعد نجاح بوابة Platform الحالية؛ تعاد المقارنة فقط عند ظهور متطلب لا يحققه التنفيذ الحالي.
- النتيجة: Platform PDF هو المسار المعتمد حاليًا، وأي استبدال له يحتاج مقارنة ترخيص/حجم/تشكيل/أمان وبوابة Regression للمستندات الحالية.

## ADR-012 — إبقاء Composition root اليدوي ما دام صغيرًا

- الحالة: معتمد ومطبق مؤقتًا
- القرار: Constructor injection يدوي. يضاف Hilt فقط عندما توجد Implementations أو Workers أو ViewModels تجعل Composition root اليدوي عبئًا حقيقيًا.
- السبب: لا توجد Dependencies تشغيلية تبرر Annotation processing وتعقيده حاليًا.
- البدائل:
  - Service locator عالمي: مرفوض.
  - Hilt من أول ملف: غير ضروري في هذه المرحلة.
- النتيجة: `WaslApplication` يبقى Composition root الصغير، والقدرات تمر عبر واجهات صريحة. يعاد تقييم Hilt عند تحول الرسم اليدوي إلى مصدر تعقيد فعلي.

## ADR-013 — سجل مستندات موحد مع Snapshots ثابتة

- الحالة: معتمد ومطبق في Schema v7
- المشكلة: إيصال السداد يرتبط بحركة Ledger واحدة، بينما إيصال الدين وكشف الحساب قد يمثلان الحساب ككل ولا يملكان Ledger entry وحيدًا. إنشاء جدول منفصل لكل نوع يكرر الهوية والترقيم والحالة والملفات والبصمات.
- القرار:
  - يبقى `issued_documents` سجل المستندات العام.
  - `document_type` يميز `PAYMENT_RECEIPT` و`DEBT_RECEIPT` و`ACCOUNT_STATEMENT` وغيرها مستقبلًا.
  - `ledger_entry_id` أصبح nullable في v7، لأن وجوده خاص بالمستندات التي تتطلب حركة محددة وليس شرطًا لكل مستند مالي.
  - View `payment_issued_documents` يبقى طبقة توافق/استعلام خاصة بإيصالات السداد فقط.
  - كل مستند يحفظ Snapshot ثابتًا وقت الإصدار، ورقم مستند، وتسلسلًا سنويًا، وحالة، ومسار PDF، وSHA-256 وعدد الصفحات.
  - إعداد المستندات يتم بأوامر Idempotent؛ إعادة المحاولة لا يجب أن تنتج مستندًا ماليًا مكررًا لنفس الأمر.
- السبب: توحيد lifecycle للمستندات مع الحفاظ على اختلاف دلالاتها، ومنع إعادة تفسير مستند تاريخي من بيانات حية تغيرت لاحقًا.
- البدائل:
  - جدول مستقل لكل نوع: مرفوض الآن بسبب تكرار البنية والمنطق.
  - ربط كل مستند قسرًا بـLedger entry: مرفوض لأنه غير صحيح دلاليًا لكشف الحساب وإيصال الدين.
  - توليد PDF مباشرة من الحالة الحية دون Snapshot: مرفوض لأنه قد يغير محتوى مستند تاريخي بعد تعديلات لاحقة.
- النتيجة: أي نوع مستند جديد يجب أن يحدد Snapshot صريحًا ودلالته، ويمكنه إعادة استخدام lifecycle العام دون كسر قيود الأنواع القائمة.

## ADR-014 — Backup يدوي مشفر واستعادة مرحلية محكومة

- الحالة: معتمد ومطبق
- المشكلة: المنتج Local-first، وAndroid Auto Backup معطل عمدًا، ومع ذلك يحتاج المستخدم مسارًا واضحًا لنقل/حفظ بياناته ومستنداته دون تسليم قاعدة البيانات الخام أو الاعتماد على Cloud إجباري.
- القرار:
  - المستخدم ينشئ النسخة ويستعيدها بفعل صريح وكلمة مرور.
  - Payload يشمل الجداول المدعومة وملفات PDF الخاصة بالمستندات `READY`.
  - قبل النسخ، يتحقق النظام من وجود ملفات المستندات وبصمة SHA-256.
  - الاستعادة تقبل Schema المدعوم فقط وتتحقق من شكل الجداول والصفوف والملفات والمسارات والبصمات.
  - الملفات تفك في مساحة Stage منفصلة، والبيانات تختبر أولًا في Room database مؤقتة.
  - قبل قبول الحالة الجديدة تُفحص Foreign Keys وثوابت مالية أساسية.
  - استبدال مجلد المستندات يملك Rollback إذا فشل استبدال قاعدة البيانات، ولا تعتبر الاستعادة ناجحة قبل اكتمال المسارين.
  - المسارات المطلقة أو Path traversal أو الملفات خارج مجلد المستندات أو PDF غير المتوقع تُرفض.
- السبب: فصل التحقق عن البيانات الحية يقلل احتمال أن تترك نسخة تالفة التطبيق في حالة جزئية، ويجعل النسخة المحمولة تشمل الدليل المستندي نفسه لا سجلاته فقط.
- البدائل:
  - نسخ ملف SQLite الخام فقط: مرفوض لأنه لا يشمل مستندات PDF ولا يوفر Contract واضحًا للتحقق.
  - Auto Backup: يبقى معطلًا لأن النسخ يجب أن يكون قرارًا صريحًا وتحت تحكم التطبيق.
  - Restore مباشر فوق الحالة الحية قبل التحقق: مرفوض لخطر الفساد الجزئي.
- النتيجة: أي توسعة مستقبلية للـSchema أو ملفات التطبيق يجب أن تحدث Contract النسخة واختبارات Create/Restore معًا؛ لا يضاف جدول أو ملف مهم إلى النسخة ضمنيًا دون اختبار استعادة.
