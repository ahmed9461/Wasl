# معمارية التنقل

الحالة: عقد تصميم قبل تنفيذ Navigation 3.

## الوجهات العليا

- الرئيسية.
- اليوم.
- الحسابات.
- الأشخاص.
- المستندات.
- المزيد.

داخل المزيد:

- التقارير.
- التنبيهات.
- الإعدادات.

داخل الإعدادات:

- الهوية وهويات المستندات.
- العملات.
- التنبيهات.
- الخصوصية.
- النسخ الاحتياطي.
- المظهر.
- عام.

## التكيف

- Compact: Bottom navigation لأكثر الوجهات استخدامًا، و«المزيد» للبقية.
- Medium: Navigation rail.
- Expanded: Rail أو Navigation drawer مع pane ثانٍ عند شاشة قائمة وتفاصيل.

لا تتغير أسماء الوجهات أو قدرتها لمجرد تغير العرض.

## Back stack

- كل Top-level destination يحتفظ بحالته الأساسية عند الانتقال.
- ضغط Back داخل تفاصيل يعود إلى القائمة نفسها مع موضعها وفلاترها.
- Back من Root يعيد الوجهة السابقة أو يخرج وفق سلوك Android.
- لا تستخدم زر رجوع مخصص يناقض System back.
- إلغاء نموذج مالي يعرض تأكيدًا إذا وجدت تغييرات غير محفوظة.

## المسارات

- Person list → Person details → Debt details → Ledger entry details.
- Today → Subject details → إجراء.
- Documents → Document preview.
- Search → نوع نتيجة → تفاصيل أصلية.

## Deep links وNotifications

- كل رابط يحمل ID داخليًا فقط، ولا يثق بنصوص أو Amount قادمة من Intent.
- يتحقق Repository أن الهدف موجود ومسموح قبل العرض.
- PendingIntent يحدد mutability المناسبة.
- الضغط على إجراء مالي في Notification لا يتجاوز Confirmation عند الحاجة.
- لا تصدر Activities إضافية دون ضرورة.

## قواعد Navigation 3

- Keys أنواع صريحة وليست Routes نصية مبعثرة.
- Back stack يملكه App state واحد.
- Parameters الصغيرة فقط في Key؛ البيانات تقرأ من Repository بالID.
- Saved state لا يحمل ملفات أو Snapshots مالية كبيرة.
- اختبار Process recreation لكل مسار رئيسي.
