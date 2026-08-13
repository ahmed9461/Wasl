# سياق مشروع وَصل

آخر تحديث: 2026-08-13

## الهوية

- الاسم العربي: وَصل
- الاسم الإنجليزي: Wasl
- الشعار: كل حساب له وصل
- المستودع الرسمي: ahmed9461/Wasl
- Application ID الحالي: com.wasl.app

## الهدف

توفير مساعد مالي شخصي بسيط وموثوق يتابع دورة الدين كاملة: الإنشاء، الاستحقاق، الدفعات الجزئية والكاملة، التذكيرات، السجل، المستندات، النسخ الاحتياطي والأرشفة.

المستخدم المستهدف شخص عادي يريد معرفة ما له وما عليه دون التعامل مع برنامج محاسبة معقد. العربية وRTL تجربة أساسية وليستا ترجمة جانبية.

## الحدود

وَصل ليس بنكًا أو محفظة أو بوابة دفع أو ERP أو منصة تحصيل أو جهة توثيق قانوني. تسجيل السداد يوثق واقعة داخل التطبيق ولا ينفذ تحويل أموال.

## المرحلة الحالية

MVP Phase 1 / Persistence foundation وأول مسار إدخال فعلي.

ما يعمل الآن:

- Gradle project بوحدتي app وcore:domain.
- واجهة Compose عربية وRTL مع Light/Dark وحالات Loading وEmpty وError وSuccess.
- تمثيل دقيق للمال بالوحدات الصغرى Long.
- Debt ledger يحتفظ بالأصل ويضيف PaymentRecorded وPaymentReversed.
- اشتقاق الرصيد وحالة الدين والاستحقاق من Domain واحد.
- تجميع الأرصدة حسب الاتجاه والعملة دون خلط العملات.
- Room 2.8.4 Schema v1 للجداول persons وdebts وledger_entries مع Schema JSON مصدّر.
- Repository ذري لإنشاء شخص ودين وتسجيل دفعة وعكسها مع Idempotency وReplay بعد القراءة.
- أول مسار UI يحفظ شخصًا ودينًا ويعرض الحسابات والإجماليات حسب العملة.
- Unit tests واختبارات Room على Emulator للإغلاق وإعادة الفتح والتزامن والقيود.
- CI للبناء والاختبارات وLint واختبارات الجهاز.

ما لا يعمل بعد:

- واجهة تفاصيل الحساب وتسجيل الدفعات وعكسها.
- اختيار شخص موجود وإنشاء أكثر من دين له من الواجهة.
- التذكيرات والمنبهات.
- المستندات وPDF.
- النسخ الاحتياطي والاستعادة.
- PIN والبصمة.

## Stack المعتمد

| المجال | القرار |
|---|---|
| المنصة | Android أصلي |
| اللغة | Kotlin |
| UI | Jetpack Compose + Material 3 |
| المعمارية | UI / Domain / Data مع UDF وRepositories |
| المنطق المالي | وحدة JVM مستقلة core:domain |
| قاعدة البيانات | Room 2.8.4 + KSP 2.3.11، Schema v1 مصدّر واختبار baseline |
| الإعدادات | DataStore عند الحاجة |
| التنقل | Navigation 3 عند إضافة الوجهات الفعلية |
| الأعمال المؤجلة | WorkManager |
| التذكير المحدد | AlarmManager غير دقيق افتراضيًا، Exact فقط بطلب مستخدم صريح |
| مفاتيح التشفير | Android Keystore |
| المصادقة المحلية | BiometricPrompt + Device Credential عند التنفيذ |
| PDF | PdfDocument مع Android text layout، بعد بوابة اختبار عربية |
| البناء | AGP 9.3.1، Gradle 9.5.0، JDK 17 |
| API | min 26، compile/target 36 |

## البنية الحالية

- app: Android entry point، Compose، ViewModel، Room، Repository واختبارات الجهاز.
- core:domain: Money، CurrencyCode، Debt aggregate، ledger، summary.
- docs: عقود التصميم والهندسة.
- .github/workflows/ci.yml: حاجز التحقق الآلي.

الحدود المستهدفة عند نمو المشروع:

- UI يعتمد على Domain وواجهات Repository.
- Data يطبق Repository باستخدام Room وملفات خاصة بالتطبيق.
- لا يعتمد Domain على Android أو Room أو Compose.
- PDF والتقارير يستهلكان نفس Read models الناتجة من Domain، ولا يعيدان حساب المال.

## نموذج البيانات

التفاصيل في docs/DATABASE_SCHEMA.md. نُفذت persons وdebts وledger_entries في Schema v1. تبقى promises وreminders وinstallments وattachments وdocument_identities وdocuments وaudit_events مخططة لشرائحها، ولا تضاف كجداول فارغة قبل وجود سلوك واختبارات.

## التشغيل

افتح جذر المستودع في Android Studio حديث يدعم AGP 9.3، واستخدم JDK 17 وSDK 36. يمكن تشغيل وحدة الدومين دون Android device. يحتاج app إلى Emulator أو جهاز API 26 فأعلى.

## البيئات والأسرار

لا توجد خدمة خلفية أو مفاتيح API في المرحلة الحالية. لا يُسمح بإضافة Signing keystore أو كلمات مرور إلى المستودع. توقيع Release سيُصمم لاحقًا باستخدام GitHub Secrets أو تخزين محلي آمن.
