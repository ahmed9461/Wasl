# وَصل — الحالة الحالية وخطة الإكمال

آخر مراجعة: 2026-08-28

هذا الملف يلخص الحالة الفعلية للرأس الموثق. إذا تعارض مع وثيقة قديمة، يعتمد الكود الحالي + Room exported schema + GitHub Actions evidence للرأس نفسه أولًا.

## مسار التسليم

- المستودع: `ahmed9461/Wasl`.
- فرع PR: `agent/bootstrap-wasl-foundation`.
- PR #1 إلى `main`: مفتوح، Draft، وغير مدمج.
- الرأس الموثق: `e09efee71cea4b1734afe50a025c2a3218ec2dd5`.
- Room Database: **Schema v10**.
- Room history: `1.json → 10.json` ملتزمة في Git.
- لا destructive migration في Production.

## آخر بوابة كاملة

**Android CI #967 — run `33137676461` — head `e09efee71cea4b1734afe50a025c2a3218ec2dd5`.**

النتيجة:

- Unit tests ✅
- Lint ✅
- Debug APK ✅
- Room v10 generated/current checks ✅
- Emulator instrumentation: **123/123** ✅
- failures: 0 / errors: 0 / skipped: 0
- Group Expense UI: 4/4 ✅
- Voice Dictation: 5/5 ✅
- Natural Entry confirmation regression ✅
- Individual creation + PaymentFlow regressions ✅
- Payment/Debt/Account Statement PDF evidence ✅

Instrumentation artifact: `9672922910`  
SHA-256: `c5a10dcba796b337d53fcc988f41e2c4aab6bb518bc95734a1e638ef0fdb0a4f`

**الحكم: الرأس الحالي Verified.**

## الوظائف المغلقة

### المالية

- أشخاص وحسابات متعددة للشخص.
- RECEIVABLE / PAYABLE.
- YER / SAR / USD دون netting بين العملات.
- `Long` minor units فقط.
- Ledger append-only.
- Payment جزئي/كامل وPayment Reversal موثق.
- Idempotency/Replay.

### المتابعة

- Due dates + Audit.
- Today.
- WorkManager scheduling/recovery.
- Exact Alarm اختياري.
- General Reminders.
- Payment Promises.
- Installment Plans/Revisions.
- Payment Claims «طالبني».

### المستندات والأمان

- Payment Receipt / Debt Receipt / Account Statement.
- Immutable snapshots، document numbering، page count، SHA-256.
- Attachments/evidence vault.
- FileProvider وفحص سلامة قبل الفتح والمشاركة.
- Backup/Restore مشفر مع staging + FK/path/hash/invariant validation + rollback.
- App Lock عبر BiometricPrompt/Device Credential و`FLAG_SECURE` وسياسات خصوصية الإشعارات.

### البحث والتقارير

- Basic + Advanced Search.
- Person Timeline.
- Objective Statistics.
- Documents Hub.
- Account Details timeline.
- Payment message templates للنسخ/المشاركة فقط.

### Adaptive / RTL / Accessibility

- RTL first-class.
- `ltrIsolate()` للبيانات اللاتينية الحساسة داخل RTL.
- `WaslMaxContentWidth` و`shouldStackDenseRows()`.
- Home / Today / Documents / Search / Security / Settings / Account Details / Timeline تحمل اختبارات large-font/200% حيث يلزم.

### Natural Entry + Voice

- Natural Entry لا يحفظ قبل Preview/Confirmation.
- Voice Dictation عبر adapter قابل للاختبار.
- recognized / empty / cancelled / unavailable / launch-failure مغطاة.
- الصوت يغذي نفس Natural Entry flow ولا يكتب Ledger مباشرة.

## Group Expense — v10

Schema v10 أضاف:

- `group_expenses`
- `group_expense_shares`

التصميم:

- العملية الأصلية context تاريخي مستقل.
- كل share مرتبطة بـDebt عادي.
- 2+ أشخاص فريدين.
- عملة واتجاه موحدان.
- unequal shares مسموحة.
- مجموع shares يساوي total بدقة.
- atomic create + replay/idempotency + conflict detection + full rollback.
- Backup/Restore ومهاجرة 9→10 واختبارات invariants.

الواجهة:

- اختيار `حساب فردي / عملية جماعية`.
- أشخاص محفوظون + مبلغ لكل حصة.
- Preview إلزامي.
- `تأكيد وحفظ` هو نقطة الكتابة الوحيدة.
- retry يحافظ على IDs/command.
- overflow وlarge-font/RTL مغطاة.

## حالة قاعدة البيانات والنسخ الاحتياطي

- Schema الحالي v10.
- عدد الجداول المنطقية في Backup: **14**.
- الجداول الجديدة للجماعي تدخل Backup/Restore مع الديون والحصص وبالترتيب المرجعي الصحيح.
- ملفات PDF والمرفقات تبقى خارج Room كبايتات، مع metadata/hash داخل Room.
- Restore يرفض المحتوى غير المتوافق قبل تبديل الحالة الحية.

## المرحلة الحالية

**Functional completion/stabilization مغلقة على الرأس الموثق.**

مرحلة الإنهاء الآن:

1. مزامنة الوثائق القديمة مع v10 وCI #967.
2. Visual polish نهائي للتطبيق، على دفعات صغيرة ومن دون تغيير السلوك المالي.
3. PDF polish نهائي مع الاحتفاظ بأدلة PDF في CI.
4. Full acceptance gate بعد التلميع.
5. Release signing/distribution بعد ذلك، مع إبقاء المفاتيح والأسرار خارج Git.

فرع العمل الحالي لهذه المرحلة: `agent/final-polish-doc-sync`، مبني مباشرة من الرأس Verified `e09efee...`.

## محظورات ثابتة

- لا تعديل `main` أو merge إليه دون طلب صريح.
- لا حذف Ledger history.
- لا Float/Double للأموال.
- لا خلط عملات.
- لا direct financial commit من Notification/Natural/Voice.
- لا تخفيف اختبارات لتجاوز CI.
- لا كتابة Room schema يدويًا إذا كان المطلوب exported schema مولدًا.
- لا signing secrets في المستودع.
