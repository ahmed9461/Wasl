# سياق مشروع وَصل

آخر تحديث: 2026-08-27

## الهوية

- الاسم العربي: وَصل
- الاسم الإنجليزي: Wasl
- الشعار: كل حساب له وصل
- المستودع الرسمي: `ahmed9461/Wasl`
- Application ID: `com.wasl.app`
- الفرع النشط: `agent/bootstrap-wasl-foundation`
- Pull Request: `#1` إلى `main` وما زال Draft.

## الهدف

وَصل مساعد مالي شخصي Local-first يتابع دورة الدين والحق والالتزام من الإنشاء حتى الإغلاق، مع الدفعات، الاستحقاقات، المتابعة، الأقساط، المستندات، المرفقات، النسخ الاحتياطي والحماية المحلية.

المستخدم المستهدف شخص عادي يريد معرفة ما له وما عليه دون التعامل مع برنامج محاسبة معقد. العربية وRTL تجربة أساسية وليستا ترجمة جانبية.

## حدود المنتج

وَصل ليس بنكًا أو محفظة أو بوابة دفع أو ERP أو منصة تحصيل أو جهة توثيق قانوني. تسجيل السداد يوثق واقعة داخل التطبيق ولا ينفذ تحويل أموال. المستندات والمرفقات سجلات شخصية ولا تمثل ضمانًا قانونيًا تلقائيًا.

## المرحلة الحالية

المشروع تجاوز MVP المالي الأساسي ودخل مرحلة **Post-MVP feature completion + stabilization**.

الحالة الوظيفية الحالية تشمل:

- Android أصلي بـKotlin وJetpack Compose وMaterial 3 وNavigation 3.
- وحدة `core:domain` مستقلة عن Android لمنطق المال والديون والأقساط.
- `Money` بوحدات Minor Units من نوع `Long`؛ لا Floating Point في الحساب المالي.
- Ledger append-only مع Payment وPayment Reversal وIdempotency.
- إنشاء أشخاص وحسابات متعددة للشخص نفسه دون تكرار Person.
- الاستحقاقات وتعديلها وإلغاؤها مع Audit.
- Today للاستحقاقات والوعود والأقساط والمطالبات ذات المتابعة.
- WorkManager وExact Alarm اختياري وتذكيرات متابعة عامة مستقلة عن `due_date`.
- إجراءات إشعار آمنة: دفع جزء / تم السداد / ذكرني لاحقًا، دون Ledger write من Notification callback.
- Payment Promises وخطط أقساط مع Revision history وتقدم مشتق من Ledger.
- بحث محلي ومتقدم في الأشخاص والحسابات والعمليات والمستندات والمبالغ والتواريخ.
- مستندات مالية `PAYMENT_RECEIPT`, `DEBT_RECEIPT`, `ACCOUNT_STATEMENT` من Snapshots ثابتة مع PDF وSHA-256.
- Backup/Restore تطبيقي مشفر يشمل البيانات وملفات PDF والمرفقات.
- App Lock عبر BiometricPrompt / Device Credential وسياسات Privacy و`FLAG_SECURE`.
- «طالبني» / Payment Claims محفوظة تاريخيًا ومستقلة عن Ledger.
- خزنة مرفقات محلية تربط الملفات بالدين وبحركة اختيارية مع SHA-256 وفحص سلامة.
- صفحة شخص موحدة تجمع حساباته وتعرض Timeline دون خلط العملات.
- قوالب رسائل سداد قابلة للنسخ/المشاركة دون إرسال تلقائي.
- إحصاءات موضوعية دون تصنيف الأشخاص.
- إدخال دين باللغة الطبيعية مع Parse → Draft → Preview/Confirmation → Save.

## قاعدة البيانات

- Room Schema الحالي: **v9**.
- سلسلة Migrations: `v1→v2→v3→v4→v5→v6→v7→v8→v9`.
- لا `fallbackToDestructiveMigration` في Production.
- v8 أضافت `payment_claims`.
- v9 أضافت `attachments`.
- Backup contract الحالي يستخدم Schema v9 ويشمل 12 جدولًا، إضافة إلى ملفات المستندات والمرفقات.

التفاصيل في `docs/DATABASE_SCHEMA.md`.

## بنية المستودع

- `app`: Android entry point، Compose UI، ViewModels، Navigation، Room، Repositories/Stores، Reminders، PDF، Backup، Privacy واختبارات Android.
- `core:domain`: Money، Debt ledger، Balance summaries، Installment schedule وقواعد مالية خالية من Android.
- `docs`: عقود التصميم والهندسة والحالة والمراحل.
- `.github/workflows/ci.yml`: بوابة البناء والاختبارات وLint وRoom schema وAndroid instrumentation وأدلة PDF.

## Stack المعتمد

| المجال | القرار |
|---|---|
| المنصة | Android أصلي |
| اللغة | Kotlin |
| UI | Jetpack Compose + Material 3 |
| المعمارية | UI / Domain / Data مع UDF وRepositories/Stores |
| المنطق المالي | `core:domain` JVM مستقل |
| قاعدة البيانات | Room 2.8.4 + KSP، Schema v9 |
| التنقل | Navigation 3 |
| الأعمال المؤجلة | WorkManager مع Unique Work |
| التنبيه القوي | Exact Alarm اختياري بطلب مستخدم صريح مع fallback |
| المصادقة المحلية | BiometricPrompt + Device Credential |
| PDF | Android PdfDocument/Text layout من Snapshots ثابتة |
| البناء | AGP 9.3.1، Gradle 9.5.0، JDK 17 |
| API | min 26، compile/target 36 |

## الثوابت المعمارية

1. Ledger هو مصدر الحقيقة المالي ويظل append-only.
2. التصحيح المالي بالعكس لا بالحذف أو تعديل الحدث الأصلي.
3. Promise وClaim وReminder وInstallment Plan ليست Ledger ولا تغيّر الرصيد تلقائيًا.
4. لا تجمع العملات المختلفة في إجمالي مالي واحد.
5. PDF والتقارير تستهلك Read models/Snapshots ولا تعيد حساب المال بقواعد موازية.
6. أي إجراء مالي قادم من إشعار أو إدخال طبيعي يجب أن يمر بمراجعة وتأكيد داخل التطبيق.
7. الملفات المهمة تخزن داخل مساحة التطبيق ويثبت سلامتها بـSHA-256.
8. أي Schema جديد يجب أن يأتي مع Migration واختبارات وBackup/Restore update في نفس المرحلة.

## حالة التحقق في 27 أغسطس 2026

آخر رأس قبل هذا التحديث كان `94ce0adf3ff64431a261042ebb62e815b42f13f1`.

- Job `verify` في Android CI #851 نجح: Unit tests + Lint + Debug APK + Room Schema v9 verification.
- Job `database-tests` توقف قبل تشغيل الاختبارات بسبب أربعة imports قديمة لـ`androidx.compose.ui.test.onNode` في ملفات AndroidTest.
- الإصلاح الجاري يزيل هذه imports فقط؛ الاستدعاءات الصحيحة تبقى `composeRule.onNode(...)`.
- لا تعتبر Claims/Attachments أو الرأس الحالي مغلقًا نهائيًا حتى تمر بوابة Android instrumentation كاملة على الرأس الجديد.

## ما تبقى وظيفيًا بعد استعادة CI الأخضر

الأولوية التالية:

1. إغلاق Claims وAttachments رسميًا بعد نجاح البوابة الكاملة وتوثيق Evidence.
2. توسيع Adaptive UI وAccessibility: Compact/Medium/Expanded، Font scale، semantics، focus وtouch targets.
3. مراجعة وتثبيت Statistics وNatural Text Entry كمرحلتين مقفلتين بالاختبارات الشاملة.
4. تنفيذ الإدخال الصوتي: Voice → Text → نفس Natural Parser → Preview → Confirmation، دون حفظ مالي مباشر من الصوت.
5. المصاريف/الديون الجماعية وفق المواصفة الأساسية.
6. جولة تحسين UI/PDF النهائية واختبار قبول شامل Offline.
7. Release signing ونسخة توزيع نهائية.

## التشغيل والأسرار

استخدم JDK 17 وAndroid SDK 36. لا توجد خدمة خلفية لازمة للوظائف الأساسية الحالية. لا تلتزم Signing keystore أو كلمات مرور أو أسرار في Git؛ توقيع Release يستخدم تخزينًا آمنًا/GitHub Secrets عند مرحلة الإصدار.
