# HANDOFF — وَصل

آخر تحديث: 2026-09-17

نقطة البدء لأي جلسة تطوير جديدة. ترتيب مصدر الحقيقة: الكود على الرأس الحالي → Room exported schema → GitHub Actions لنفس الرأس → `docs/CURRENT_STATUS.md` → هذا الملف.

## الحالة الحالية

- المنتج: **وَصل — Wasl**، الشعار: **كل حساب له وصل**.
- صيانة `0.1.1` (`versionCode = 2`) مبنية على main عند `5aa482257bfd0a72771d34be77d039db316cf77e`؛ الواجهة السابقة دمجت بالفعل في أغسطس.
- سجل التنفيذ والدمج ونتيجة بناء main لهذه الجولة: **PR #14** — https://github.com/ahmed9461/Wasl/pull/14 . اقرأ حالته الفعلية وChecks؛ هذه الوثيقة لا تستبدل نتائج البناء.
- Room Schema **v11** بلا تغيير، ولا تعديل في Ledger أو مسارات حفظ البيانات أو applicationId أو أذونات التطبيق أو مفاتيح التوقيع.
- أصلحت الأيقونة التالفة واختلاف رسم اللون/الأحادي، وبتر الكسور والفواصل في الإدخال الذكي، وتجاوز المبلغ، وتطبيع مجموعات آلاف غير صحيحة.
- أضيف رفض المبالغ المركبة غير المدعومة والعملات المتعددة، ومنع اعتبار جزء من اسم شخص مثل Sara عملة SAR. المحلل المحلي محدود الصيغ ولا يدّعي فهم جميع تراكيب العربية؛ المعاينة والتأكيد الصريح والإدخال اليدوي باقية.
- أصلح إعداد SDK بعد فشل طلب الحزمة القديمة `tools`، وفحص Schema أصبح قبل رفع APK وحذف النسخة السابقة. لا محاكي أو cache دائم جديد.
- تفاصيل التحقيق: `docs/MAINTENANCE_2026-09-17.md`، `docs/CI_MAINTENANCE_2026-09-17.md`، `docs/CONTINUATION_REVIEW_2026-09-17.md`.
- أزيلت ملفات نقل patch وأعمال Actions المؤقتة من المصدر النهائي؛ الإصلاحات مطبقة على ملفات التطبيق نفسها، وليست حزمة نقل فقط.

## التحقق وحدوده

- نجحت محليًا **45/45 دالة اختبار** من اختبارات الدومين والإدخال الذكي والأيقونة عبر kotlinc وحزمة kotlin-test بعد المراجعة الثانية. أزيلت تعليقات JUnit فقط من نسخ مؤقتة لتشغيلها في البيئة المحلية، لا من المصدر. اختبارات موارد app شغلت من مجلد الوحدة كما يفعل Gradle.
- أضيفت إجمالًا **13 دالة اختبار انحدار** تشمل حالات متعددة: 3 للدومين، 7 للإدخال الذكي، 3 للأيقونة.
- Gradle المحلي متعذر: `UnknownHostException: services.gradle.org` أثناء تنزيل التوزيعة. لا يُخلط الاختبار المحلي البديل مع نجاح Gradle.
- Android CI #1254 / run `35227619293` توقف في إعداد SDK قبل بدء اختبارات التطبيق بسبب `Failed to find package 'tools'`. حددت `packages: platform-tools` في CI وSigned Release، دون تعطيل اختبار أو تجاوز فشل.
- المرجع للبناء الكامل هو **Android CI للرأس نفسه**: domain/app unit tests، lint، assembleDebug وفحص Schema. ملخص JUnit في كل run يعرض عدد الاختبارات الحقيقي وfailures/errors/skipped. سجل PR #14 يربط آخر نتيجة مؤكدة بالرأس وAPK؛ لا تستخدم نجاح CI لرأس قديم دليلًا على هذا التغيير.
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
- `.github/workflows/release.yml` يبني APK موقعًا فقط عند وجود أسرار التوقيع الخارجية؛ اسم artifact موافق لـ0.1.1.
- `app/build.gradle.kts` لا يحتوي أسرارًا؛ يقرأ signing configuration من environment variables.

الأسرار المطلوبة خارج Git للنشر الموقّع:

- `WASL_KEYSTORE_BASE64`
- `WASL_KEYSTORE_PASSWORD`
- `WASL_KEY_ALIAS`
- `WASL_KEY_PASSWORD`

نجاح Debug CI لا يعني نجاح Signed Release أو النشر العام. لم تُنشأ مفاتيح جديدة ولم يُنفّذ نشر موقّع في جلسة الصيانة.

## بوابة التسليم والخطوة التالية

1. لا يُعتمد الإصلاح إلا مع Android CI أخضر لطلب الدمج، ثم CI أخضر لرأس main الناتج. حالة PR #14 وسجل تنفيذه هما المرجع لمعرفة هل أغلقت هذه الخطوة؛ لا تعِد دمج فرع واجهة أغسطس القديم.
2. APK التجريبي يؤخذ من artifact `Wasl-debug` للرأس المعتمد؛ سياسة الاحتفاظ يومان، والرفع فقط على main، ولا cache دائم أو محاكي في البوابة الافتراضية.
3. الترقية على جهاز به بيانات مالية تتطلب applicationId وشهادة توقيع متوافقة. لا تحذف التطبيق لمعالجة اختلاف توقيع؛ احتفظ بالبيانات وخذ نسخة احتياطية مشفرة أولًا.
4. بعد إغلاق بوابة main، التحقق اليدوي المتبقي هو مظهر الأيقونة على جهاز المالك وتوافق تحديث النسخة المثبتة. النشر العام يحتاج بوابة Signed Release والأسرار الخارجية.

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
```

اختبارات الجهاز اختيارية خارج CI الافتراضية، ولا تدّع تشغيلها دون دليل:

```bash
./gradlew :app:connectedDebugAndroidTest
```

GitHub Actions هي بوابة التسليم المرجعية.
