# القرارات المعمارية والتقنية

آخر تحديث: 2026-08-13

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
- النتيجة: Repository القادم يجب أن يحفظ إضافة الحدث وتحديث أي Projection داخل Transaction واحدة.

## ADR-006 — Room 2.8.4 لـSchema v1

- الحالة: معتمد ومنفذ للجداول المالية الأساسية
- القرار: استخدام Room 2.8.4 مع KSP 2.3.11 وتصدير Schema واختبارات Migration. يبدأ v1 بـpersons وdebts وledger_entries فقط؛ تضاف الجداول اللاحقة مع سلوكها الفعلي وMigration صريحة.
- السبب: Android-only يحتاج حلًا مستقرًا ومجربًا، والتحقق وقت الترجمة من SQL ومسار Migrations. Room موصى به رسميًا بدل SQLite المباشر.
- البدائل:
  - SQLite API مباشرة: مرفوضة لكثرة Boilerplate وضعف التحقق وقت البناء.
  - Room 3.0.1: إصدار Stable لكنه Major حديث جدًا يركز على KMP ويتطلب KSP وSQLiteDriver فقط. لا توجد حاجة KMP الآن، لذا لا نتحمل مخاطرة انتقال مبكر. يُعاد تقييمه بعد استقرار MVP وتحقق تكاملات التشفير والنسخ.
  - قاعدة بيانات سحابية: مرفوضة لأنها تكسر Local-first.
- المصادر:
  - [Room موصى به بدل SQLite المباشر](https://developer.android.com/training/data-storage/room)
  - [Room 2 release notes](https://developer.android.com/jetpack/androidx/releases/room)
  - [Room 3.0 والفروق الجوهرية](https://developer.android.com/jetpack/androidx/releases/room3)
  - [KSP](https://github.com/google/ksp)
- النتيجة التنفيذية:
  - `app/schemas/com.wasl.app.data.local.WaslDatabase/1.json` جزء دائم من المستودع.
  - لا يوجد `fallbackToDestructiveMigration` في Production.
  - Version 1 هو Baseline ولا يحتاج Migration سابقة؛ كل Version تالٍ يضاف إلى `ALL_MIGRATIONS` ويختبر من أقدم إصدار مدعوم.

## ADR-007 — Compose وMaterial 3 وNavigation 3

- الحالة: Compose معتمد؛ Navigation 3 معتمد عند إضافة أكثر من وجهة فعلية
- القرار: Single-activity وJetpack Compose وMaterial 3. تستخدم الواجهة Navigation 3 stable بدل بناء Router خاص عندما يبدأ مسار الشاشات.
- السبب: Compose هو Toolkit الحديث الموصى به، وNavigation 3 يمنح Back stack صريحًا ويدعم الواجهات التكيفية.
- البدائل:
  - XML Views: مستقرة لكنها تزيد ازدواج UI state لمشروع جديد.
  - Router مخصص: مرفوض لعدم الحاجة وإعادة اختراع Back navigation.
  - Navigation 2: مستقر، لكن المشروع جديد والتوصية الرسمية الحالية هي Navigation 3.
- المصادر:
  - [توصيات Android: Compose وSingle activity وNavigation 3](https://developer.android.com/topic/architecture/recommendations)
  - [Navigation 3](https://developer.android.com/guide/navigation/navigation-3)
  - [Compose BOM](https://developer.android.com/develop/ui/compose/bom)

## ADR-008 — سياسة التذكيرات والمنبهات

- الحالة: معتمد للتنفيذ اللاحق
- القرار:
  - WorkManager للعمل المؤجل أو الدوري غير الدقيق.
  - AlarmManager غير الدقيق لتذكير مستخدم بوقت يمكن أن يتحمل نافذة.
  - Exact Alarm فقط لمنبه قوي فعّله المستخدم صراحة ويحتاج لحظة دقيقة.
  - التحقق من صلاحية Exact Alarm وتقديم Fallback واضح.
  - إعادة الجدولة بعد Boot أو تغيير الوقت عندما يتطلب التنفيذ ذلك.
- السبب: Exact alarms مكلفة ومقيدة، ووثائق Android توصي بعدم استخدامها للأعمال الدورية.
- البدائل:
  - Exact لكل تذكير: مرفوض بسبب البطارية والصلاحيات والرفض الافتراضي.
  - WorkManager لكل موعد دقيق: مرفوض لأنه لا يضمن لحظة دقيقة.
- المصادر:
  - [Schedule alarms](https://developer.android.com/develop/background-work/services/alarms)
  - [Task scheduling](https://developer.android.com/develop/background-work/background-tasks/persistent)

## ADR-009 — الأمان Local-first

- الحالة: معتمد كسياسة، التنفيذ تدريجي
- القرار:
  - لا Backend إجباري.
  - Android Keystore لمفاتيح التشفير غير القابلة للتصدير.
  - BiometricPrompt مع Device Credential للحماية المحلية عند التنفيذ.
  - Auto Backup معطل حتى يتوفر Backup تطبيقي مشفر ومُصدّر باختيار المستخدم.
  - usesCleartextTraffic معطل.
  - الملفات داخل مساحة التطبيق وتشارك لاحقًا عبر FileProvider.
  - لا بيانات مالية في Logs أو Crash metadata.
- السبب: بيانات مالية حساسة ويجب ألا تصبح Cloud أو Logs شرط تشغيل أو تسريبًا.
- البدائل:
  - تخزين PIN أو مفتاح خام: مرفوض.
  - الاعتماد على حماية الجهاز وحدها لكل الحالات: غير كافٍ لخيار قفل مستقل داخل التطبيق.
- المصادر:
  - [Android Keystore](https://developer.android.com/privacy-and-security/keystore)
  - [Biometric authentication](https://developer.android.com/identity/sign-in/biometric-auth)

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
  - AGP 9.3 يدعم API 37 ويتطلب Gradle 9.5 وJDK 17، لكن منصة android-37 غير متاحة عبر sdkmanager المستقر بتاريخ القرار.
  - Core 1.19.0 وLifecycle 2.11.0 يتطلبان compileSdk 37؛ لذلك ثُبّت آخر إصدارين مستقرين متوافقين مع منصة 36 بدل إدخال SDK تجريبي.
  - Android 17 ما زال Beta بتاريخ القرار، لذلك يبقى compileSdk وtargetSdk على 36 ولا يشترك MVP في منصة غير نهائية.
  - target 36 يطابق متطلبات 2026 ويمنح وقتًا لاختبار تغييرات Android 17 قبل رفعه.
  - min 26 يوفر java.time وNotification Channels مباشرة ويغطي أجهزة حديثة دون Desugaring إضافي.
- البدائل:
  - compileSdk أو targetSdk 37 الآن: مرفوض مؤقتًا لأن Android 17 غير نهائي ولأن منصة 37 غير منشورة في قناة SDK المستقرة.
  - min 23: ممكن، لكنه يحتاج Desugaring ومسارات توافق إضافية؛ يعاد تقييمه إذا أثبتت بيانات المستخدمين حاجة حقيقية.
- المصادر:
  - [AGP 9.3 compatibility](https://developer.android.com/build/releases/agp-9-3-0-release-notes)
  - [AndroidX Core releases](https://developer.android.com/jetpack/androidx/releases/core)
  - [AndroidX Lifecycle releases](https://developer.android.com/jetpack/androidx/releases/lifecycle)
  - [Android 17 release notes](https://developer.android.com/about/versions/17/release-notes)
  - [Target API requirements](https://developer.android.com/google/play/requirements/target-sdk)

## ADR-011 — توليد PDF عبر Platform مع بوابة عربية

- الحالة: معتمد مبدئيًا بشرط نجاح Prototype
- القرار: البدء بـandroid.graphics.pdf.PdfDocument وStaticLayout أو TextLayout مناسب للرسم العربي، مع Snapshot ثابت لبيانات الهوية والمستند وقت الإصدار.
- سبب الاختيار: لا يضيف مكتبة ثقيلة، يعمل Offline، ويستخدم تشكيل النص في Android عند الرسم الصحيح.
- بوابة القبول قبل اعتماد التنفيذ: PDF عربي متعدد الصفحات يحتوي RTL وLTR ومبالغ كبيرة وخطًا مضمنًا أو ثابتًا، ويطابق بيانات Domain في اختبار.
- البدائل:
  - AndroidX PDF: موجه للعرض وما زال Alpha، وليس أساس توليد مستندات مالية.
  - مكتبة PDF خارجية كبيرة: تؤجل حتى يثبت أن Platform لا يحقق Arabic shaping أو الطباعة.
- النتيجة: إذا فشل Prototype، يوثق قرار بديل بمقارنة الترخيص والحجم والتشكيل والأمان.

## ADR-012 — عدم إدخال Hilt في أول Slice

- الحالة: معتمد ومطبق مؤقتًا
- القرار: Constructor injection يدوي في البداية. يضاف Hilt فقط عندما توجد عدة Implementations أو Workers أو ViewModels تجعل Composition root اليدوي عبئًا حقيقيًا.
- السبب: لا توجد Dependencies تشغيلية الآن تبرر Annotation processing وتعقيده.
- البدائل:
  - Service locator عالمي: مرفوض.
  - Hilt من أول ملف: غير ضروري في هذه المرحلة.
- النتيجة: `WaslApplication` هو Composition root الصغير، و`RoomWaslRepository` و`HomeViewModel` يقبلان Dependencies من Constructors. يعاد تقييم Hilt عند دخول Workers أو تعدد Implementations.
