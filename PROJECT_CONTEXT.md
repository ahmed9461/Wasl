# سياق مشروع وَصل

آخر تحديث: 2026-08-12

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

Foundation / بداية المرحلة الأولى.

ما يعمل الآن:

- Gradle project بوحدتي app وcore:domain.
- واجهة Compose تأسيسية عربية وRTL مع Light/Dark.
- تمثيل دقيق للمال بالوحدات الصغرى Long.
- Debt ledger يحتفظ بالأصل ويضيف PaymentRecorded وPaymentReversed.
- اشتقاق الرصيد وحالة الدين والاستحقاق من Domain واحد.
- تجميع الأرصدة حسب الاتجاه والعملة دون خلط العملات.
- Unit tests لهذه القواعد.
- CI للبناء والاختبارات وLint.

ما لا يعمل بعد:

- Persistence وRoom.
- إنشاء الأشخاص والديون من الواجهة.
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
| قاعدة البيانات | Room 2.8.4 في الخطوة التالية، بعد Schema v1 واختبارات Migration |
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

- app: Android entry point، Theme، واجهة Compose.
- core:domain: Money، CurrencyCode، Debt aggregate، ledger، summary.
- docs: عقود التصميم والهندسة.
- .github/workflows/ci.yml: حاجز التحقق الآلي.

الحدود المستهدفة عند نمو المشروع:

- UI يعتمد على Domain وواجهات Repository.
- Data يطبق Repository باستخدام Room وملفات خاصة بالتطبيق.
- لا يعتمد Domain على Android أو Room أو Compose.
- PDF والتقارير يستهلكان نفس Read models الناتجة من Domain، ولا يعيدان حساب المال.

## نموذج البيانات المخطط

التفاصيل في docs/DATABASE_SCHEMA.md. الجداول الأساسية المخططة: persons، debts، ledger_entries، promises، reminders، installments، attachments، document_identities، documents، audit_events.

## التشغيل

افتح جذر المستودع في Android Studio حديث يدعم AGP 9.3، واستخدم JDK 17 وSDK 36. يمكن تشغيل وحدة الدومين دون Android device. يحتاج app إلى Emulator أو جهاز API 26 فأعلى.

## البيئات والأسرار

لا توجد خدمة خلفية أو مفاتيح API في المرحلة الحالية. لا يُسمح بإضافة Signing keystore أو كلمات مرور إلى المستودع. توقيع Release سيُصمم لاحقًا باستخدام GitHub Secrets أو تخزين محلي آمن.
