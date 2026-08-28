# نموذج الأمان والخصوصية

آخر تحديث: 2026-08-26

## الأصول الحساسة

- أسماء الأشخاص وبيانات التواصل.
- مبالغ واتجاهات الديون والأقساط والوعود.
- الملاحظات والأوصاف وسجل العمليات.
- إيصالات PDF وكشوف الحساب وملفات النسخ الاحتياطية.
- أي مرفقات مستقبلية وصور وفواتير وتوقيعات.
- مواد التشفير ومفاتيح التوقيع المستقبلية.

## نموذج التهديد

- جهاز مفقود أو جهاز مفتوح يُترك مع شخص آخر.
- Screenshot أو Recent Apps يكشف بيانات مالية.
- إشعار يعرض اسمًا أو مبلغًا على شاشة القفل.
- تطبيق آخر يحاول قراءة ملف مشترك.
- نسخة احتياطية غير مشفرة أو معدلة.
- Path traversal أو ملف PDF تم تغييره بعد الإصدار.
- قاعدة تالفة أو Migration ناقصة أو Restore جزئي.
- Intent/Deep link خبيث.
- Log أو Crash metadata يحتوي PII.
- Dependency غير لازمة أو غير موثوقة.

## الضوابط المنفذة

### عزل التطبيق والاتصال

- Android sandbox.
- `usesCleartextTraffic=false`.
- لا Backend أو Analytics إجباريين في MVP الحالي.
- Android Auto Backup وDevice Transfer معطلان عمدًا؛ التصدير يتم فقط عبر Backup تطبيقي صريح.
- `.gitignore` يمنع Keystores وملفات الأسرار.

### قفل التطبيق

- App Lock منفذ عبر AndroidX `BiometricPrompt` stable `1.1.0`.
- المصادقة المسموحة: `BIOMETRIC_WEAK | DEVICE_CREDENTIAL` عبر Android system authentication.
- وَصل لا ينشئ ولا يخزن PIN مخصصًا، ولا يخزن كلمة مرور الجهاز أو قالب بصمة.
- تفعيل القفل يتطلب مصادقة ناجحة أولًا.
- فتح التطبيق المقفل يتطلب مصادقة ناجحة؛ إلغاء أو فشل المصادقة يبقي المحتوى مقفلًا.
- المهلة قابلة للاختيار: فورًا، 15 ثانية، دقيقة، 5 دقائق؛ الافتراضي 15 ثانية.
- حساب المهلة يستخدم `SystemClock.elapsedRealtime()` حتى لا يتأثر بتغيير التاريخ أو المنطقة الزمنية.
- `AppLockViewModel` يحافظ على حالة الجلسة عبر Configuration change، ولا تعتبر عملية التدوير خروجًا من التطبيق.
- عند القفل تُزال Semantics عن المحتوى المالي خلف شاشة القفل وتُستهلك Pointer events، فلا يبقى المحتوى الخلفي قابلًا للتفاعل أو الاكتشاف عبر Compose semantics.
- App Lock يفرض `FLAG_SECURE` حتى لو كان خيار الشاشة الآمنة المستقل غير مفعّل.
- إذا لم تعد وسيلة مصادقة Android متاحة، يظهر Recovery صريح لتعطيل App Lock دون حذف الديون أو المستندات أو النسخ.

### الشاشة والإشعارات

- خيار مستقل لـ`FLAG_SECURE` يحمي Screenshot/Recent Apps عند اختياره.
- تفعيل App Lock يفرض الحماية نفسها تلقائيًا.
- Privacy mode للإشعارات يخفي التفاصيل الحساسة عند تفعيله.
- إشعار موعد الدين يستخدم `VISIBILITY_PRIVATE` مع Public version عامة لا تعرض اسم الشخص أو المبلغ.
- PendingIntent غير قابل للتعديل ويحمل معرف الدين فقط؛ البيانات تقرأ من Repository بعد فتح التطبيق.

### سلامة المال والبيانات

- Domain يمنع Currency mismatch وOverpayment وOverflow.
- الأموال ليست Floating Point.
- Ledger append-only؛ التصحيح بعكس موثق.
- Foreign Keys مفعلة.
- الكتابات المركبة داخل Room Transactions.
- لا destructive migrations في Production.
- Schema JSON ملتزم ومقارن آليًا مع Schema التي يولدها Room في CI.

### المستندات والملفات

- PDF داخل Internal storage افتراضيًا.
- المشاركة عبر FileProvider وURI grant للقراءة.
- مسار PDF يجب أن يكون نسبيًا ومباشرة داخل مجلد `documents` وبامتداد PDF.
- مستند `READY` يتطلب SHA-256 محفوظًا، ويعاد فحص الملف قبل الفتح أو المشاركة.
- التوليد يستخدم ملفًا مؤقتًا ثم نقلًا ذريًا قدر الإمكان.
- Snapshot المالي وهوية المصدر ثابتان وقت الإصدار؛ لا يعاد تفسير مستند تاريخي من الحالة الحية.

### النسخ الاحتياطي والاستعادة

- Backup يدوي مشفر بكلمة مرور يختاره المستخدم.
- Payload منطقي ومحمول، وليس نسخة خامًا من SQLite الحية.
- AES-256-GCM يوفر authenticated encryption.
- KDF: PBKDF2-HMAC-SHA256 بـ210,000 iteration مع Salt مستقل.
- Payload الداخلي gzip JSON ويشمل الجداول المدعومة وملفات PDF الخاصة بالمستندات `READY`.
- قبل النسخ: فحص وجود ملفات المستندات وSHA-256.
- عند Restore: رفض Schema غير المدعوم، البنية غير المتوقعة، المسارات المطلقة/الخارجة، الملفات غير المطابقة والبصمات الفاشلة.
- الملفات تُفك في Stage، والبيانات تُختبر أولًا في Room مؤقتة.
- تُفحص Foreign Keys وثوابت مالية قبل اعتماد الحالة الجديدة.
- استبدال الملفات وقاعدة البيانات له مسار Rollback ولا يعلن النجاح في حالة جزئية.
- مصفوفات كلمات المرور المؤقتة تُمسح بعد الاستخدام في مسارات الاختبار/الخدمة التي تملكها.

### Logs

مسموح:

- Event تقني عام.
- Error code محدود.
- Duration وعدادات غير شخصية.

ممنوع:

- Person name أو phone.
- Amount أو currency مع سياق شخص.
- Description أو note.
- URI كامل أو محتوى ملف.
- SQL rows.
- Backup passphrase أو tokens أو keys.

## ما لم يُنفذ بعد ولا يجب الادعاء بوجوده

- تشفير كامل لقاعدة البيانات وهي ساكنة عبر SQLCipher أو بديل.
- تشفير كل حقل/مرفق محلي بمفتاح Android Keystore.
- PIN مستقل خاص بوَصل؛ القرار الحالي عمدًا هو عدم إنشاء PIN مخصص.
- Remote wipe أو Cloud recovery.
- Certificate pinning؛ لا توجد حاليًا شبكة Backend تتطلبه.
- Release signing للإنتاج داخل CI.

إذا اعتمد تشفير At-rest مستقبلًا، يجب قياس أثره على Room وMigration وBackup/Restore والأداء قبل اعتماده، وعدم جعل مفتاح Keystore غير القابل للنقل مفتاح النسخة المحمولة.

## اختبارات الأمان الحالية

- `AppLockViewModelTest`: التهيئة، المصادقة، المهلة، العودة قبل/بعد المهلة، Lock now والتعطيل.
- `SecurityUiInstrumentedTest`: شاشة الأمان، Recovery، الزر، Dark Mode وFont Scale 2.0.
- `BackupRestoreInstrumentedTest`: Round-trip وكلمة مرور خاطئة دون تغيير الحالة الحية.
- `AccountDocumentBackupInstrumentedTest`: استعادة سجل PDF جاهز والملف وبصمته.
- Migration/Room tests: القيود والذرية وIdempotency والتزامن.
- PDF tests: توليد ملفات فعلية وفحص سلامتها ومحتواها/صفحاتها.
- `MvpAcceptanceInstrumentedTest`: رحلة مالية كاملة مع restart، عكس، PDF، Backup، فساد متعمد للملف، بيانات بعد النسخة، ثم Restore.
- CI #382: 63/63 Android instrumentation، 0 فشل/خطأ/تخطي.

## Dependency policy

- Stable افتراضيًا ومن مصدر رسمي/موثوق وترخيص معروف.
- لا ترقية لمجرد وجود إصدار أحدث؛ تقرأ Release notes وBreaking changes أولًا.
- لا مكتبة Analytics أو Network بلا حاجة منتج واضحة.
- أي Dependency أمنية جديدة تحتاج اختبار أثر وRollback plan.

## استجابة حادث

إذا اكتشف تسريب أو فساد:

1. أوقف المسار المتسبب دون حذف بيانات المستخدم.
2. احفظ أدلة تقنية خالية من PII.
3. حدد النطاق والإصدارات المتأثرة.
4. أصلح السبب الجذري وأضف Regression test.
5. أضف Migration أو Recovery إن لزم.
6. وثق التغيير في `CHANGELOG.md` و`HANDOFF.md` و`DECISIONS.md` إذا غيّر قرارًا معماريًا.
