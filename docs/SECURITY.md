# نموذج الأمان والخصوصية

## الأصول الحساسة

- أسماء الأشخاص وبيانات التواصل.
- مبالغ واتجاهات الديون.
- Notes ووعود ومطالبات.
- صور وفواتير وإيصالات وتوقيعات.
- PDF والنسخ الاحتياطية.
- مفاتيح التشفير ومواد التوقيع.

## التهديدات

- جهاز مفقود أو مفتوح.
- تطبيق آخر يحاول قراءة ملف مشارك.
- Backup غير مشفر.
- Intent أو Deep link خبيث.
- Log أو Crash report يحتوي PII.
- قاعدة تالفة أو Migration ناقصة.
- استخراج مفتاح من التخزين.
- Screenshot أو Recent Apps يكشف مبلغًا.
- Dependency غير موثوقة.

## الضوابط الحالية

- Android sandbox.
- usesCleartextTraffic=false.
- allowBackup=false.
- Activity الوحيدة مصدرة فقط لأنها Launcher.
- .gitignore يمنع Keystores وenv.
- لا Backend ولا Analytics.
- Domain يرفض عملة مختلفة وOverpayment وOverflow.

## الضوابط المطلوبة قبل MVP

### قفل التطبيق

- BiometricPrompt مع Device Credential.
- سياسة Session timeout قابلة للتخصيص.
- لا PIN نصي.
- Rate limiting لمحاولات PIN.
- Recovery واضح لا يمحو البيانات بصمت.

### التشفير

- مفاتيح محلية عبر Android Keystore.
- تقييم تشفير حقول أو قاعدة كاملة بعد Prototype أداء وتوافق Backup.
- المرفقات شديدة الحساسية يمكن تشفيرها لكل ملف بمفتاح بيانات مغلف.
- لا يعاد استخدام IV مع AES-GCM.
- نسخة النقل تستخدم مفتاحًا مشتقًا من Passphrase عبر KDF موثق، لا مفتاح Keystore غير القابل للنقل.

### الملفات

- Internal storage افتراضيًا.
- FileProvider عند المشاركة.
- URI grant مؤقت ولتطبيق يختاره المستخدم.
- MIME لا يؤخذ من الاسم وحده.
- حدود حجم وHash واسم آمن.
- تنظيف الملفات المؤقتة بعد النجاح والفشل.

### Logs

مسموح:

- Event تقني عام.
- Error code.
- Duration وعدادات غير شخصية.

ممنوع:

- Person name أو phone.
- Amount أو currency مع سياق شخص.
- Description أو note.
- URI كامل أو محتوى ملف.
- SQL rows.
- Tokens أو keys.

### الشاشة والإشعارات

- خيار FLAG_SECURE للشاشات الحساسة أو التطبيق كاملًا.
- Notification privacy mode يخفي الاسم والمبلغ.
- Lock-screen visibility مضبوطة حسب اختيار المستخدم.

### Integrity

- Foreign keys وConstraints.
- Transactions.
- Backup checksum وauthenticated encryption.
- Restore في قاعدة مؤقتة قبل الاستبدال.
- PDF hash وDocument snapshot.

## Dependency policy

- مصدر رسمي أو مشروع موثوق وترخيص معروف.
- Stable افتراضيًا.
- Release notes وBreaking changes قبل الترقية.
- Lock/version catalog محدث.
- لا مكتبة Analytics أو Network غير لازمة.

## استجابة حادث

إذا اكتشف تسريب أو فساد:

1. أوقف المسار المتسبب دون حذف بيانات.
2. احفظ أدلة تقنية خالية من PII.
3. حدد النطاق والإصدارات.
4. أصلح السبب الجذري وأضف Regression test.
5. أضف Migration أو Recovery.
6. وثق التغيير الأمني في CHANGELOG وHANDOFF.
