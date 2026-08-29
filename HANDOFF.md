# HANDOFF — وَصل

آخر تحديث: 2026-08-29

نقطة البدء لأي جلسة تطوير جديدة. ترتيب مصدر الحقيقة: الكود على الرأس الحالي → Room exported schema → GitHub Actions لنفس الرأس → `docs/CURRENT_STATUS.md` → هذا الملف.

## الحالة

- المنتج: **وَصل — Wasl**
- الشعار: **كل حساب له وصل**
- الإصدار المرشح: `0.1.0` (`versionCode = 1`).
- فرع الواجهة المرشح: `agent/ui-redesign-v0.3-corrective`.
- آخر رأس اجتاز بوابة Corrective UI الكاملة: `acb5dea0fd54897afcc56e55ee52afc99bcb0392`.
- Room Schema: **v11**، و`1.json → 11.json` ملتزمة في Git.
- الحالة الحالية: Release Candidate؛ دمج الواجهة إلى `main` ثم CI على merge commit هما الخطوتان الداخليتان المتبقيتان قبل APK التجريبي النهائي لهذه الجولة.

## آخر بوابة مكتملة

Android CI **#1097** — run `33228386198` — head `acb5dea0fd54897afcc56e55ee52afc99bcb0392`:

- Unit/Lint/Debug APK ✅
- Room v11 generation/verification ✅
- Emulator instrumentation + migrations/repository/backup ✅
- جميع اختبارات Android على المحاكي ✅
- Payment/Debt/Account Statement PDF inspection ✅
- instrumentation وPDF evidence artifacts ✅

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

تم تجهيز المصدر للإصدار `0.1.0`:

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

## المتبقي

1. فتح PR من `agent/ui-redesign-v0.3-corrective` إلى `main` ودمجه بعد بوابة الفرع الخضراء.
2. اعتماد Android CI على merge commit في `main`.
3. استخراج APK التجريبي النهائي من CI على `main`.
4. للنشر العام فقط: توفير مفتاح التوقيع الخارجي وتشغيل Signed Release واستكمال بيانات المتجر الخارجية.

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