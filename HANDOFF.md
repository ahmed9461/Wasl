# HANDOFF — الحالة الحية

آخر تحديث: 2026-08-28

هذا الملف هو نقطة البدء لأي جلسة تطوير جديدة. عند التعارض، مصدر الحقيقة بالترتيب: الكود على الفرع النشط، Room exported schema، GitHub Actions للرأس نفسه، ثم `docs/CURRENT_STATUS.md` وهذا الملف.

## الحالة الحالية

- المشروع: **وَصل — Wasl**
- الشعار: **كل حساب له وصل**
- الإصدار: `0.1.0-dev`
- الفرع الرئيسي للتطوير: `agent/bootstrap-wasl-foundation`
- Pull Request: `#1` إلى `main`، مفتوح وDraft.
- الرأس الموثق الحالي: `e09efee71cea4b1734afe50a025c2a3218ec2dd5`.
- `main` لم يُدمج ولم يُغيّر ضمن هذه المرحلة.
- Room Schema الحالي: **v10**، والملف المرجعي هو `app/schemas/com.wasl.app.data.local.WaslDatabase/10.json`.
- سلسلة Migrations محفوظة صراحة من v1 حتى v10 دون destructive migration.

## آخر بوابة مرجعية كاملة

**Android CI #967 — run `33137676461` — head `e09efee71cea4b1734afe50a025c2a3218ec2dd5`.**

- Unit tests ✅
- Lint ✅
- Debug APK ✅
- Room Schema v10 generated/current verification ✅
- Android Emulator instrumentation: **123/123**، 0 failures، 0 errors، 0 skipped ✅
- Group Expense UI: **4/4** ✅
- Voice Dictation hardening: **5/5** ✅
- Natural Entry explicit-confirmation regression ✅
- Legacy individual creation + PaymentFlow regressions ✅
- Payment Receipt / Debt Receipt / Account Statement PDF evidence ✅
- Instrumentation artifact: `9672922910`
- SHA-256: `c5a10dcba796b337d53fcc988f41e2c4aab6bb518bc95734a1e638ef0fdb0a4f`

هذا الرأس **Verified**.

## ما يعمل الآن

### المصدر المالي

- أشخاص وحسابات متعددة للشخص.
- `RECEIVABLE` و`PAYABLE`.
- YER / SAR / USD دون خلط العملات.
- Money بMinor Units من نوع `Long` فقط.
- Ledger append-only.
- دفعات جزئية ونهائية وعكس دفعة موثق بدل حذف التاريخ.
- Idempotency وReplay لاشتقاق الرصيد والحالة.

### المتابعة

- Due date مع Audit.
- Today.
- WorkManager + recovery.
- Exact Alarm اختياري مع fallback.
- General Reminders مستقلة عن `due_date`.
- Payment Promises، Installment Plans/Revisions، Payment Claims.
- إجراءات الإشعار لا تكتب Ledger مباشرة.

### المستندات والملفات

- `PAYMENT_RECEIPT`, `DEBT_RECEIPT`, `ACCOUNT_STATEMENT` من immutable snapshots.
- SHA-256 وفحص سلامة قبل الفتح/المشاركة.
- Attachments/evidence vault داخل مساحة التطبيق مع hash ومسارات آمنة.
- Backup/Restore مشفر يشمل البيانات وPDF والمرفقات مع staging/FK/invariant validation وrollback.

### البحث والعرض

- Advanced Search.
- Person Timeline.
- Statistics.
- Documents Hub.
- Account Details timeline.
- Adaptive/Accessibility hardening عبر Home/Today/Documents/Search/Security/Settings/Account Details/Timeline، مع RTL/Bidi isolation واختبارات 200% Font Scale.

### الإدخال الطبيعي والصوتي

- Natural Entry: `Parser → Preview → explicit Confirmation → Save`.
- Voice Dictation مع `VoiceDictationBridge` قابل للاختبار.
- حالات الصوت: recognized / empty / cancelled / unavailable / launch failure.
- النص المعترف به يدخل نفس مسار Natural Entry؛ لا حفظ مالي قبل التأكيد.

### Group Expense — Schema v10

- العملية الجماعية الأصلية محفوظة كسياق تاريخي، وليست Ledger موازيًا.
- كل حصة تُنشأ كـDebt عادي وتبقى الدفعات والتذكيرات والمستندات على المسار المالي الموحد.
- 2+ مشاركين فريدين، حصص غير متساوية، عملة واتجاه موحدان، والمجموع يساوي الإجمالي بدقة.
- Atomic transaction + replay/idempotency + conflict detection + rollback.
- Migration 9→10 وBackup/Restore/Invariants.
- UI: «حساب فردي / عملية جماعية» → تحرير الحصص → Preview → «تأكيد وحفظ».
- Large-font/RTL وoverflow protection مغطاة بالاختبارات.

## المرحلة الحالية: Finishing

المراحل الوظيفية الرئيسية أعلاه أُغلقت ببوابات كاملة. المتبقي الآن، بالترتيب:

1. **مزامنة التوثيق** مع الرأس v10/123-test الحالي.
2. **جولة UI visual polish نهائية** على دفعات صغيرة دون تغيير السلوك أو testTags.
3. **جولة PDF polish نهائية** مع إبقاء immutable snapshots وفحوص evidence.
4. Acceptance gate كاملة بعد التلميع.
5. Release signing/distribution؛ الأسرار والkeystore خارج Git.

فرع العمل المعزول لمرحلة الإنهاء الحالية: `agent/final-polish-doc-sync`، مبني من الرأس Verified أعلاه.

## ثوابت لا تكسر

1. Ledger append-only؛ التصحيح بالعكس.
2. لا Floating Point للأموال.
3. لا خلط عملات في إجمالي واحد.
4. Promise/Claim/Reminder/Installment Plan ليست Ledger.
5. Notification/Natural/Voice لا تكتب عملية مالية مباشرة قبل Preview/Confirmation.
6. PDF يعتمد Snapshot ثابتًا.
7. READY document/attachment لا يفتح عند فقد الملف أو فشل SHA-256.
8. Backup/Restore لا يتجاوز schema/path/hash/FK/invariant validation.
9. أي Migration جديدة تأتي مع exported schema + migration tests + Backup update.
10. لا أسرار أو signing keys في Git.
11. لا دمج إلى `main` دون طلب صريح من المالك.

## أوامر التحقق المرجعية

```bash
./gradlew :core:domain:test
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```

GitHub Actions هي بوابة التسليم المرجعية لأنها تجمع build + Room schema + Emulator instrumentation + PDF evidence في بيئة نظيفة.
