# HANDOFF — وَصل

آخر تحديث: 2026-08-28

نقطة البدء لأي جلسة تطوير جديدة. ترتيب مصدر الحقيقة: الكود على الرأس الحالي → Room exported schema → GitHub Actions لنفس الرأس → `docs/CURRENT_STATUS.md` → هذا الملف.

## الحالة

- المنتج: **وَصل — Wasl**
- الشعار: **كل حساب له وصل**
- الإصدار المرشح: `0.1.0` (`versionCode = 1`).
- فرع الإنهاء المجمع: `agent/final-polish-doc-sync`.
- آخر integration head يحمل تغييرات المنتج: `5794e9a74914c8af3a3ecf750664f2f6083eaf66`.
- Room Schema: **v11**، و`1.json → 11.json` ملتزمة في Git.
- PR #10 الخاص بـDocument Templates v11 تم دمجه في فرع الإنهاء بعد نجاح Android CI الكامل.

## آخر بوابة v11 مكتملة قبل الدمج

Android CI #1017 — run `33203634720` — head `fdbb28b2aca59f7d0542eaa785d72502d695a431`:

- Unit/Lint/Debug APK ✅
- Room v11 generation/verification ✅
- Emulator instrumentation + migrations/repository/backup ✅
- Payment/Debt/Account Statement PDF evidence ✅

بعد مزامنة وثائق الإنهاء، يجب اعتماد GitHub Actions المرتبطة **بأحدث رأس على فرع الإنهاء** قبل نقل المصدر إلى `main`.

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

تم تجهيز المصدر للإصدار `0.1.0`:

- `PRIVACY_POLICY.md` موجود.
- `docs/RELEASE_CHECKLIST.md` موجود.
- `.github/workflows/release.yml` يبني APK موقعًا فقط عند وجود أسرار التوقيع الخارجية.
- `app/build.gradle.kts` لا يحتوي أسرارًا؛ يقرأ signing configuration من environment variables.

الأسرار المطلوبة خارج Git:

- `WASL_KEYSTORE_BASE64`
- `WASL_KEYSTORE_PASSWORD`
- `WASL_KEY_ALIAS`
- `WASL_KEY_PASSWORD`

بدونها تكون الحالة **Release-ready / signing pending**، وليست Published.

## المتبقي

1. نجاح Android CI على آخر رأس مجمع بعد مزامنة الوثائق.
2. نقل الرأس المجمع إلى مسار PR #1 / `main` بعد نجاح البوابة.
3. توفير مفتاح التوقيع الخارجي وتشغيل Signed Release عند الاستعداد للنشر.
4. بيانات المتجر الخارجية ووسيلة التواصل الرسمية إن كانت مطلوبة.

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
