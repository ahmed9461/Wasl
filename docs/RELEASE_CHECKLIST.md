# وَصل — Release Checklist

آخر تحديث: 2026-08-28

هذه القائمة هي بوابة الإصدار، ولا تستبدل `docs/CURRENT_STATUS.md` أو أدلة GitHub Actions للرأس المرشح.

## 1. Source / Git

- [ ] جميع دفعات الإنهاء مدمجة في رأس واحد.
- [ ] لا توجد تغييرات مالية غير موثقة أو destructive migration.
- [ ] exported Room schema الحالية ملتزمة في Git ومطابقة لما يولده Room في CI.
- [ ] `main` لا يستقبل إلا الرأس الذي اجتاز بوابة القبول النهائية.
- [ ] لا توجد مفاتيح توقيع أو كلمات مرور أو أسرار داخل Git.

## 2. Quality gate

- [ ] Unit tests ناجحة.
- [ ] Android lint ناجح.
- [ ] Debug APK يبنى بنجاح.
- [ ] Emulator instrumentation كامل دون failures/errors/skips غير مبررة.
- [ ] Migration tests من جميع الإصدارات المدعومة إلى schema الحالية ناجحة.
- [ ] Backup/Restore + rollback + integrity invariants ناجحة.
- [ ] RTL / large font / adaptive UI regressions ناجحة.
- [ ] Natural Entry وVoice لا يكتبان ماليًا قبل Preview/Confirmation.
- [ ] Group Expense invariants والـidempotency ناجحة.

## 3. Documents / PDF

- [ ] Payment Receipt evidence ناجح.
- [ ] Debt Receipt evidence ناجح.
- [ ] Account Statement evidence ناجح.
- [ ] document snapshot immutable ومثبت قبل rendering.
- [ ] فتح/مشاركة PDF يفحص SHA-256.
- [ ] Document identity وDocument template المختاران محفوظان داخل snapshot ولا تتغير المستندات القديمة بتعديل الإعدادات لاحقًا.
- [ ] Arabic/RTL والنصوص اللاتينية الحساسة مقروءة في PDF evidence.

## 4. Privacy / security

- [ ] `PRIVACY_POLICY.md` مطابق لسلوك النسخة المنشورة.
- [ ] لا `INTERNET` permission ما لم توجد ميزة شبكية موثقة تحتاجه.
- [ ] `android:allowBackup="false"` يبقى متسقًا مع مسار Backup/Restore الخاص بالتطبيق.
- [ ] FileProvider غير exported ويمنح URI permissions فقط عند المشاركة.
- [ ] App Lock / Biometric / Device Credential regression ناجح.
- [ ] `FLAG_SECURE` وسياسة إخفاء محتوى الإشعارات الحساسة مفعلة حيث يلزم.
- [ ] لا بيانات مالية حساسة في logs أو test artifacts العامة.

## 5. Release identity

- [ ] `versionCode` فريد وأكبر من أي إصدار منشور سابقًا.
- [ ] `versionName` لا يحتوي لاحقة `-dev` للإصدار النهائي.
- [ ] اسم التطبيق وَصل / Wasl والشعار «كل حساب له وصل» كما هما.
- [ ] applicationId النهائي `com.wasl.app` ما لم يصدر قرار صريح بتغييره قبل أول نشر عام.

## 6. Signing

يجب إنشاء/اختيار مفتاح توقيع Release واحد وحفظه خارج المستودع. القيم التالية لا تكتب داخل Git:

- Keystore file / Base64 secret.
- Store password.
- Key alias.
- Key password.

قبل النشر يجب التحقق من APK/AAB الموقع باستخدام أدوات Android الرسمية، وحفظ نسخة احتياطية آمنة من المفتاح. فقدان مفتاح التوقيع قد يمنع تحديث نفس التطبيق لاحقًا بحسب مسار التوزيع المستخدم.

## 7. Store / distribution

- [ ] وسيلة تواصل رسمية للمالك مضافة إلى بيانات المتجر وسياسة الخصوصية إذا كانت المنصة تتطلبها.
- [ ] Screenshots وأيقونة ووصف المتجر جاهزة.
- [ ] نموذج Data safety/Privacy في المتجر يطابق السلوك الفعلي للنسخة.
- [ ] اختبار تثبيت نظيف على جهاز فعلي.
- [ ] اختبار Upgrade من آخر نسخة موزعة، إن وجدت.
- [ ] اختبار Backup→Restore على نسخة الإصدار المرشحة.
- [ ] SHA-256/حجم ملف التوزيع النهائي محفوظان مع سجل الإصدار.

## قاعدة الإغلاق

لا يعلن الإصدار Production جاهزًا لمجرد نجاح `assembleRelease`. يلزم رأس Git محدد، أدلة CI لنفس الرأس، exported Room schema ملتزمة، وفحص ملف التوزيع الموقع. إذا لم تتوفر أسرار التوقيع في بيئة البناء، تكون الحالة **Release-ready / signing pending** وليست **Published**.
