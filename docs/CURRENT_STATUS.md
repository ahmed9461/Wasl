# وَصل — الحالة الحالية

آخر مراجعة: 2026-09-17

هذا الملف يلخص الحالة الحية. عند التعارض يكون مصدر الحقيقة بالترتيب: الكود على الرأس الحالي، Room exported schema، GitHub Actions للرأس نفسه، ثم هذا الملف وبقية الوثائق.

## الإصدار الحالي في المصدر

- المستودع: `ahmed9461/Wasl`.
- الواجهة السابقة دمجت في main في أغسطس؛ صيانة الأيقونة والمبالغ موثقة في `MAINTENANCE_2026-09-17.md`.
- سجل جولة الصيانة: PR #14 — https://github.com/ahmed9461/Wasl/pull/14 . حالته وChecks وسجل التحقق فيه تحدد آخر رأس معتمد وبناء main وartifact، ولا يعتمد رأس قديم تلقائيًا.
- الإصدار: `0.1.1`، `versionCode = 2`.
- Room Database: **Schema v11**، دون تعديل في هذه الجولة.
- exported schemas ملتزمة: `1.json → 11.json`.
- لا `fallbackToDestructiveMigration` في Production.
- بوابة Debug: اختبارات Gradle + lint + assembleDebug + مطابقة Schema للرأس نفسه، ثم نجاح main قبل تسليم APK.
- النشر العام منفصل: **Signed Release غير منفذ في جولة الصيانة**، ولا يوصف التطبيق بأنه Published لمجرد نجاح Debug.

## تحقق جولة سبتمبر

نجحت **45/45 دالة اختبار مضيفة** عبر kotlinc محليًا بعد المراجعة الثانية، منها 13 دالة انحدار أضيفت لهذه الجولة. لا يتوفر تنزيل Gradle أو تشغيل Android محليًا؛ CI على الرأس نفسه هي مرجع unit/lint/APK/schema، ويظهر العدد الفعلي لاختبارات Gradle في ملخص JUnit لكل run. لم تشغل اختبارات الجهاز أو المحاكي لهذه الجولة؛ أزيل المحاكي من CI الافتراضية في سبتمبر. Schema والسجل المالي دون تعديل.

البناء الأول #1254 / run `35227619293` فشل في إعداد SDK قبل الاختبارات بسبب الحزمة القديمة `tools`. عولج السبب بتحديد `platform-tools` صراحة في CI وSigned Release. بوابة البناء لم تُخفَّف. فحص Schema ومقارنتها بالمصدر أصبح قبل رفع APK وحذف النسخة السابقة.

تفصيل الاستكمال: `CI_MAINTENANCE_2026-09-17.md` و`CONTINUATION_REVIEW_2026-09-17.md`. نتيجة الاعتماد النهائية وروابط run/commit/APK تسجل في PR #14 عند اكتمالها.

## بوابات التحقق التاريخية

### Corrective UI v0.3

Android CI **#1097** — run `33228386198` — head `acb5dea0fd54897afcc56e55ee52afc99bcb0392` نجح بالكامل في أغسطس:

- Unit tests / Lint / Debug APK ✅
- توليد وفحص Room Schema v11 ✅
- Emulator instrumentation / migration / repository / backup tests ✅
- جميع اختبارات Android على المحاكي ✅
- فحص Payment Receipt PDF ✅
- فحص Debt Receipt PDF ✅
- فحص Account Statement PDF ✅
- رفع instrumentation وPDF evidence ✅

هذه نتائج تاريخية للواجهة قبل دمجها في أغسطس، وليست بوابة قبول صيانة سبتمبر أو دليلًا على تشغيل محاكي الآن.

### Document Templates / Schema v11 baseline

Android CI **#1017** — run `33203634720` — head `fdbb28b2aca59f7d0542eaa785d72502d695a431` نجح بالكامل قبل دمج Document Templates v11.

## الواجهة الحالية

- هوية بصرية موحدة داكنة/فيروزية/ذهبية مستندة إلى التصميم المعتمد.
- أيقونة تطبيق جديدة مع adaptive/round launchers؛ موارد اللون والأحادي موحدة هندسيًا، وأصل PNG التالف أزيل.
- الرئيسية تعرض ملخصات العملات والحسابات وإجراءين منفصلين: إضافة حساب والإدخال الذكي.
- تدفق إضافة الحساب أصبح مباشرًا ومختصرًا مع نقل الخيارات الأقل استخدامًا إلى إعدادات إضافية.
- شاشة «اليوم» أعيد تنظيمها بملخص واضح وصياغات عربية طبيعية.
- شاشة الأقساط تعرض إجمالي/مسدد/متبقٍ وفلاتر وتقدم الخطة.
- تفاصيل الحساب تعرض الرصيد والتقدم والإجراءات والمتابعة داخل نفس التدفق بدل الأزرار العائمة.
- الإعدادات موحدة مع بقية التطبيق وتدعم تلقائي/داكن/فاتح، الأمان، التذكيرات، والنسخ الاحتياطي.
- التنقل والرجوع يعتمدان معرفات UI مستقرة في الاختبارات بدل النصوص المرئية.
- RTL، Bidi، large-font وadaptive behavior محفوظة.

## الوظائف المنفذة

### المالية

- أشخاص وحسابات متعددة للشخص.
- RECEIVABLE / PAYABLE.
- YER / SAR / USD دون netting مضلل بين العملات.
- Money بminor units من `Long` فقط.
- Ledger append-only؛ التصحيح بـPayment Reversal وليس حذف التاريخ.
- دفعات جزئية ونهائية، idempotency وreplay.
- إدخال المبالغ لا يبتر الكسور أو يحذف مجموعات آلاف مشوهة، ويرفض overflow بدل قيمة ملتفة أو استثناء غير معالج في المعاينة.

### المتابعة

- Due date + audit.
- Today.
- WorkManager scheduling/recovery.
- Exact Alarm اختياري.
- General Reminders.
- Payment Promises.
- Installment Plans/Revisions.
- Payment Claims «طالبني».

### البحث والعرض

- Basic/Advanced Search.
- Person Timeline.
- Objective Statistics.
- Documents Hub.
- Account Details timeline.
- رسائل سداد جاهزة للنسخ/المشاركة فقط.
- RTL first-class وBidi isolation وadaptive/large-font hardening للشاشات الرئيسية.

### Natural Entry / Voice

- `Parser → Preview → explicit Confirmation → Save`.
- Voice Dictation يغذي نفس مسار Natural Entry.
- recognized / empty / cancelled / unavailable / launch failure مغطاة باختبارات المصدر.
- لا كتابة مالية من الصوت أو الإشعار قبل التأكيد الصريح.
- يشترط المحلل المحلي مبلغًا كاملًا وصريحًا قبل عملة واحدة. صيغ الآلاف اللفظية البسيطة المعتمدة مدعومة؛ التراكيب المختلطة والمعقدة غير المدعومة والمبالغ/العملات المتعددة تحتاج تصحيحًا يدويًا، ولا تؤخذ بادئتها مبلغًا نهائيًا.
- لا تستنتج العملة من جزء داخل اسم الشخص مثل Sara.

### Group Expense

- العملية الجماعية الأصلية سياق تاريخي وليست Ledger موازيًا.
- كل share تنشئ Debt عاديًا.
- 2+ مشاركين فريدين، unequal shares، عملة واتجاه موحدان، exact total.
- atomic create + replay/idempotency + conflict detection + rollback.
- Preview/Confirmation إلزاميان.

### المستندات وRoom v11

- `PAYMENT_RECEIPT`, `DEBT_RECEIPT`, `ACCOUNT_STATEMENT` من immutable snapshots.
- Document Templates محفوظة في `document_templates` منذ v11.
- أنماط القوالب الحالية: MINIMAL / BUSINESS / CLASSIC / COMPACT / MODERN.
- القالب المختار يثبت داخل snapshot للمستند؛ تعديل الإعدادات لاحقًا لا يعيد تفسير مستند قديم.
- `11.json` مولد من Room ومثبت في Git، وليس ملفًا مكتوبًا يدويًا.
- SHA-256 وpage count وفحوص سلامة قبل فتح/مشاركة PDF.

### الملفات والأمان

- Attachments/evidence vault داخل مساحة التطبيق مع metadata وSHA-256 ومسارات مقيدة.
- FileProvider غير exported للمشاركة الصريحة.
- Backup/Restore مشفر مع staging + schema/path/hash/FK/invariant validation + rollback.
- App Lock عبر BiometricPrompt / Device Credential.
- `FLAG_SECURE` وسياسة خصوصية للإشعارات الحساسة.
- التطبيق الحالي Local-first ولا يطلب صلاحية `INTERNET`.

## قاعدة البيانات الحالية

Schema **v11** يضيف `document_templates` فوق v10، ليصبح عدد جداول Room المنطقية **15**.

سلسلة migrations محفوظة صراحة حتى `10→11`، وكل ترقية جديدة يجب أن تأتي مع exported schema واختبارات migration وBackup/Restore update عند الحاجة.

## الإصدار والتوقيع

- `versionName = 0.1.1`، `versionCode = 2`.
- مسار Signed Release موجود في `.github/workflows/release.yml`.
- توقيع Release يقرأ فقط متغيرات/Secrets خارج Git:
  - `WASL_KEYSTORE_BASE64`
  - `WASL_KEYSTORE_PASSWORD`
  - `WASL_KEY_ALIAS`
  - `WASL_KEY_PASSWORD`
- Workflow الإصدار يبني APK موقعًا، يتحقق منه بـ`apksigner`، ويولد SHA-256؛ لم تُشغَّل هذه البوابة في جولة الصيانة ولم تُنشأ مفاتيح جديدة.
- لا يوصف التطبيق بأنه **Published** قبل تشغيل بوابة Signed Release بنجاح واستكمال التوزيع.

راجع `PRIVACY_POLICY.md` و`docs/RELEASE_CHECKLIST.md` قبل التوزيع العام.

## بوابة اعتماد نسخة التثبيت

1. تحقق من إغلاق PR #14 ونجاح Android CI لرأس main الناتج في سجل الطلب؛ لا تعد إلى خطوة دمج واجهة أغسطس المنتهية.
2. APK التجريبي النهائي يجب أن يأتي من main المعتمد، artifact `Wasl-debug`، والاحتفاظ يومان. لا رفع APK من فروع المراجعة ولا cache دائم أو محاكي في CI الافتراضية.
3. على جهاز المالك يبقى التحقق من شكل الأيقونة وتوافق توقيع التحديث. اختلاف شهادة التوقيع ليس مبررًا لحذف التطبيق وبياناته.
4. للنشر العام فقط: استخدام أسرار التوقيع الفعلية وتشغيل Signed Release واستكمال بيانات منصة التوزيع.

## ثوابت لا تكسر

- لا حذف Ledger history.
- لا Float/Double للأموال.
- لا خلط عملات في إجمالي واحد.
- Promise/Claim/Reminder/Installment ليست Ledger.
- Notification/Natural/Voice لا تنفذ commit ماليًا مباشرًا.
- PDF يعتمد snapshot ثابتًا.
- لا فتح READY PDF/Attachment عند فقد الملف أو فشل SHA-256.
- لا Restore يتجاوز schema/path/hash/FK/invariant validation.
- لا Migration بلا exported schema + tests.
- لا signing keys أو passwords داخل Git.
