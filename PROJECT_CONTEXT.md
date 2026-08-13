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

MVP Phase 1 / Core debt-payment + audited due schedule + existing-person debts + Today + local search فوق Persistence موثوق.

ما يعمل الآن:

- Gradle project بوحدتي app وcore:domain.
- واجهة Compose عربية وRTL مع Light/Dark وحالات Loading وEmpty وError وSuccess.
- تمثيل دقيق للمال بالوحدات الصغرى Long.
- Debt ledger يحتفظ بالأصل ويضيف PaymentRecorded وPaymentReversed.
- اشتقاق الرصيد وحالة الدين والاستحقاق من Domain واحد.
- تجميع الأرصدة حسب الاتجاه والعملة دون خلط العملات.
- Room 2.8.4 Schema v3 للجداول persons وdebts وledger_entries وreminders وaudit_events مع Migrations v1→v2→v3 مختبرة.
- Repository ذري لإنشاء شخص ودين وتسجيل دفعة وعكسها مع Idempotency وReplay بعد القراءة.
- أول مسار UI يحفظ شخصًا ودينًا ويعرض الحسابات والإجماليات حسب العملة.
- نموذج إنشاء الدين يختار شخصًا جديدًا أو شخصًا محفوظًا بالـID من استعلام Room محدود، ويضيف للشخص الموجود ديونًا مستقلة دون Person مكرر.
- Repository يفصل أمر إنشاء الشخص والدين عن أمر إنشاء دين لشخص موجود، ويحفظ الدين وتذكيره ذريًا مع Idempotency بالـdebtId.
- شاشة تفاصيل حساب تفاعلية تعرض الأصل والمدفوع والمتبقي والحالة والسجل المالي كاملًا.
- تسجيل دفعة جزئية أو نهائية من UI عبر مراجعة ثم تأكيد، مع أخطاء قابلة للتصحيح وإعادة محاولة Idempotent عند غموض نتيجة الحفظ.
- عكس دفعة من UI بسبب إلزامي دون حذف الحدث الأصلي، مع إغلاق الدين وإعادة فتحه بصورة مشتقة.
- Navigation 3 بمفاتيح Serializable صريحة بين الرئيسية وتفاصيل الحساب، وقراءة التفاصيل Reactive من Room.
- Unit tests واختبارات Room على Emulator للإغلاق وإعادة الفتح والتزامن والقيود.
- اختبار UI End-to-End ينشئ دينًا، يسجل دفعة جزئية، يعيد فتح قاعدة البيانات، ويتحقق من بقاء المتبقي والسجل.
- تاريخ استحقاق اختياري في إنشاء الدين وتفاصيله، مع تذكير اختياري قرابة 09:00 حسب المنطقة الزمنية المدنية.
- تعديل أو إلغاء الاستحقاق من التفاصيل داخل Transaction واحدة مع تذكير ذي ID ثابت وحدث تدقيق before/after؛ وتنعكس إعادة الجدولة أو الإلغاء على Unique Work بعد Commit.
- تذكير WorkManager فريد وقابل للاسترداد، وقناة إشعار مستقلة وإذن Android 13+ وحالة واضحة عند رفضه.
- إعادة جدولة Idempotent عند بدء التطبيق وتغيّر الوقت أو المنطقة الزمنية، وفتح الحساب مباشرة من الإشعار.
- شاشة Today Reactive للديون النشطة غير المسددة المستحقة اليوم والمتأخرة، تفصل الحالتين وتحسب أيام التأخير من DueState وLocalDate الحاليين.
- الرئيسية وToday وجهتان علويتان بتنقل سفلي، ومن Today تفتح تفاصيل الحساب حيث تسجل الدفعة عبر مسار التأكيد القائم.
- معالجة BLOCKED_PERMISSION بطلب إذن الإشعارات أو إعدادات القناة، ومعالجة FAILED بإعادة Recovery تعيد الحالة إلى SCHEDULED قبل الجدولة.
- وجهة بحث محلية Reactive في اسم الشخص ووصف الدين، تعرض كل دين كنتيجة مستقلة وتفتح تفاصيله بالـID.
- حد بحث ظاهر قدره 50 نتيجة، مع تطبيع المسافات ومعاملة محارف SQL wildcard كنصوص حرفية.
- CI للبناء والاختبارات وLint واختبارات الجهاز.

ما لا يعمل بعد:

- البحث في العمليات والمستندات وأرقامها والتواريخ والمبالغ؛ التنفيذ الحالي يغطي الأشخاص والديون والوصف فقط.
- أنواع التذكير المتقدمة والتكرار وAlarmManager القوي/الدقيق.
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
| قاعدة البيانات | Room 2.8.4 + KSP 2.3.11، Schema v3 وMigrations v1→v2→v3 |
| الإعدادات | DataStore عند الحاجة |
| التنقل | Navigation 3 1.1.6 بمفاتيح Serializable وBack stack مملوك للتطبيق |
| الأعمال المؤجلة | WorkManager 2.11.2 مع Unique Work بحسب reminder id |
| التذكير المحدد | AlarmManager غير دقيق افتراضيًا، Exact فقط بطلب مستخدم صريح |
| مفاتيح التشفير | Android Keystore |
| المصادقة المحلية | BiometricPrompt + Device Credential عند التنفيذ |
| PDF | PdfDocument مع Android text layout، بعد بوابة اختبار عربية |
| البناء | AGP 9.3.1، Gradle 9.5.0، JDK 17 |
| API | min 26، compile/target 36 |

## البنية الحالية

- app: Android entry point، Compose، Home/Today/Search/Account details ViewModels، Navigation 3، Room، Repository واختبارات الجهاز.
- core:domain: Money، CurrencyCode، Debt aggregate، ledger، summary.
- docs: عقود التصميم والهندسة.
- .github/workflows/ci.yml: حاجز التحقق الآلي.

الحدود المستهدفة عند نمو المشروع:

- UI يعتمد على Domain وواجهات Repository.
- Data يطبق Repository باستخدام Room وملفات خاصة بالتطبيق.
- لا يعتمد Domain على Android أو Room أو Compose.
- PDF والتقارير يستهلكان نفس Read models الناتجة من Domain، ولا يعيدان حساب المال.

## نموذج البيانات

التفاصيل في docs/DATABASE_SCHEMA.md. نُفذت persons وdebts وledger_entries وreminders وaudit_events في Schema v3. يقتصر audit_events حاليًا على تغيير جدول استحقاق الدين، ويقتصر reminders على DUE_DATE بجدولة WORK؛ وتبقى promises وinstallments وattachments وdocument_identities وdocuments مخططة لشرائحها، ولا تضاف كجداول فارغة قبل وجود سلوك واختبارات.

## التشغيل

افتح جذر المستودع في Android Studio حديث يدعم AGP 9.3، واستخدم JDK 17 وSDK 36. يمكن تشغيل وحدة الدومين دون Android device. يحتاج app إلى Emulator أو جهاز API 26 فأعلى.

## البيئات والأسرار

لا توجد خدمة خلفية أو مفاتيح API في المرحلة الحالية. لا يُسمح بإضافة Signing keystore أو كلمات مرور إلى المستودع. توقيع Release سيُصمم لاحقًا باستخدام GitHub Secrets أو تخزين محلي آمن.
