# HANDOFF — وَصل

آخر تحديث: 2026-09-17

نقطة البدء لأي جلسة تطوير جديدة. ترتيب مصدر الحقيقة: الكود على الرأس الحالي → Room exported schema → GitHub Actions لنفس الرأس → `docs/CURRENT_STATUS.md` → هذا الملف.

## الحالة الحالية

- المنتج: **وَصل — Wasl**، الشعار: **كل حساب له وصل**.
- صيانة `0.1.1` (`versionCode = 2`) مبنية على main عند `5aa482257bfd0a72771d34be77d039db316cf77e`؛ الواجهة السابقة دمجت بالفعل في أغسطس.
- Room Schema **v11** بلا تغيير، والسجل المالي والبيانات المحفوظة لم يتغيرا في هذه الجولة.
- عولج أصل أيقونة تالف، واختلاف رسم اللون/الأحادي، وبتر الكسور والفواصل في الإدخال الذكي، وخطأ تجاوز المبلغ، وتطبيع مجموعات آلاف غير صحيحة.
- التفصيل: `docs/MAINTENANCE_2026-09-17.md`.

## التحقق وحدوده

- شغلت محليًا **42 دالة اختبار** من اختبارات الدومين والإدخال الذكي والأيقونة عبر kotlinc وحزمة kotlin-test؛ نجحت كلها. أزيلت تعليقات JUnit فقط من نسخ مؤقتة لتشغيلها في البيئة المحلية، لا من المصدر.
- Gradle المحلي متعذر: `UnknownHostException: services.gradle.org` أثناء تنزيل التوزيعة. لا يُخلط الاختبار المحلي البديل مع نجاح Gradle.
- المرجع للبناء الكامل هو **Android CI للرأس نفسه**: domain/app unit tests، lint، assembleDebug وفحص Schema. راجع Checks الخاصة بطلب دمج هذه الجولة وبناء main النهائي؛ لا تستخدم نجاح CI لرأس قديم دليلًا على هذا التغيير.
- منذ تعديل CI في سبتمبر لا يوجد محاكي في البوابة الافتراضية. لم تُجرَ تجربة جهاز/محاكي في جلسة الصيانة هذه؛ نتائج أغسطس تاريخية وليست تحققًا لهذا الإصدار.

## Corrective UI v0.3

- الهوية البصرية الجديدة الداكنة/الفيروزية/الذهبية هي المرجع المعتمد.
- الأيقونة الجديدة مثبتة مع adaptive/round launcher support.
- الرئيسية: ملخص عملات وحسابات مختصر، مع فصل «إضافة حساب» عن «إدخال ذكي».
- إضافة الحساب: تدفق مباشر أقصر، والخيارات الثانوية داخل إعدادات إضافية.
- اليوم: ملخص واضح وصياغة عربية طبيعية.
- الأقساط: ملخص إجمالي/مسدد/متبقٍ + فلاتر + تقدم الخطة.
- تفاصيل الحساب: الرصيد والتقدم والإجراءات والمتابعة ضمن الشاشة نفسها.
- الإعدادات: نفس الهوية، تلقائي/داكن/فاتح، أمان، تذكيرات، نسخ احتياطي.
- اختبارات UI تعتمد testTags مستقرة للمسارات الحساسة بدل النصوص المرئية المتغيرة.

## ما هو مغلق وظيفيًا

- Ledger append-only، Payment/Reversal، partial/final payments، idempotency/replay.
- أشخاص وحسابات متعددة، RECEIVABLE/PAYABLE، YER/SAR/USD دون خلط العملات.
- Due/Today/WorkManager/Exact Alarm/General Reminders.
- Promises / Installments / Claims.
- Search / Timeline / Statistics / Documents Hub / Account Details.
- Attachments vault + FileProvider + integrity checks.
- Encrypted Backup/Restore + rollback.
- App Lock / privacy controls.
- Natural Entry + Voice مع Preview/Confirmation إلزاميين.
- Group Expense atomic مع shares تتحول إلى ديون عادية.
- RTL/Bidi/adaptive/large-font hardening.
- Payment Receipt / Debt Receipt / Account Statement من immutable snapshots.
- Document Templates v11 مع snapshot compatibility.

## Release

تم تجهيز المصدر للإصدار `0.1.1`:

- `PRIVACY_POLICY.md` موجود.
- `docs/RELEASE_CHECKLIST.md` موجود.
- `.github/workflows/release.yml` يبني APK موقعًا فقط عند وجود أسرار التوقيع الخارجية.
- `app/build.gradle.kts` لا يحتوي أسرارًا؛ يقرأ signing configuration من environment variables.

الأسرار المطلوبة خارج Git للنشر الموقّع:

- `WASL_KEYSTORE_BASE64`
- `WASL_KEYSTORE_PASSWORD`
- `WASL_KEY_ALIAS`
- `WASL_KEY_PASSWORD`

بدونها تكون الحالة **Release-ready / signing pending**، وليست Published.

## بوابة التسليم

1. لا يُعتمد الإصلاح إلا مع Android CI أخضر لطلب الدمج، ثم CI أخضر لرأس main الناتج.
2. APK التجريبي يؤخذ من artifact `Wasl-debug` للرأس المعتمد؛ سياسة الاحتفاظ يومان ولم تتغير.
3. الترقية على جهاز به بيانات مالية تتطلب نفس applicationId ونفس مفتاح التوقيع. لا تحذف التطبيق لمعالجة اختلاف توقيع؛ احتفظ بالبيانات وخذ نسخة احتياطية مشفرة أولًا.
4. النشر الموقّع يحتاج الأسرار الخارجية؛ لم يُنشأ مفتاح بديل ولم يُدّع نشر الإصدار.

## ثوابت

1. Ledger append-only؛ التصحيح بالعكس.
2. Money = integer minor units فقط.
3. لا cross-currency netting.
4. Promise/Claim/Reminder/Installment ليست Ledger.
5. Notification/Natural/Voice لا تكتب ماليًا قبل Preview/Confirmation.
6. المستند READY مبني على immutable snapshot.
7. لا فتح PDF/Attachment عند فشل integrity.
8. Restore يفحص schema/path/hash/FK/invariants قبل الاستبدال.
9. كل Migration معها exported schema + tests.
10. لا secrets أو signing keys في Git.

## أوامر التحقق

```bash
./gradlew :core:domain:test
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```

GitHub Actions هي بوابة التسليم المرجعية.