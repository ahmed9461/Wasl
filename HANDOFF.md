# HANDOFF — وَصل

آخر تحديث: 2026-08-29

نقطة البدء لأي جلسة تطوير جديدة. ترتيب مصدر الحقيقة عند التعارض:

**الكود على رأس PR #13 → Room exported schema → GitHub Actions لنفس الرأس → `docs/CURRENT_STATUS.md` → هذا الملف → بقية الوثائق.**

## الحالة الحالية

- المنتج: **وَصل — Wasl**
- الشعار: **كل حساب له وصل**
- المستودع: `ahmed9461/Wasl`
- Application ID: `com.wasl.app`
- الإصدار المرشح: `0.1.0` (`versionCode = 1`).
- `main` يحتوي Corrective UI v0.3 بعد دمج PR #12 عند `15f982b9a3804861f96b454431c96ed4f8c19c04`.
- Android CI #1100 على merge commit في `main` نجح بالكامل.

## الجولة المفتوحة — UI/UX Hardening v0.4

- الفرع: `agent/ui-ux-hardening-v0.4`
- PR: **#13 — Draft**.
- المواصفة التنفيذية: `docs/UI_UX_HARDENING_V0.4_EXECUTION_PLAN.md`.
- Room: **Schema v12**، exported schemas `1.json → 12.json`.
- functional-code checkpoint قبل مزامنة الوثائق: `9d0622f26ab6760d68362adf279da4e7e2ca69c7`.
- Android CI #1248 — run `33274101583` على checkpoint أعلاه: **نجاح كامل**.
- instrumentation: **149 tests / 0 failures / 0 errors / 0 skipped**.

## ما أُنجز في v0.4

- Home/Account cards/Group Expense/Account Details/Payment Promises جرى ضغطها وتحسين التكيف مع الهاتف والخط الكبير.
- Documents Hub يستخدم Attachment Store الحقيقي، وFile Picker محمي ومغطى باختبارات cancel/PNG/PDF/unreadable URI/integrity.
- Room v12 تضيف banner path/hash لهوية المستند.
- Banner vault خاص بالتطبيق ومحتوى البانر يتحقق بـSHA-256/path/image/size بصورة fail-closed.
- البانر يثبت داخل immutable document snapshot، ويرسم تاريخيًا في Payment/Debt/Account Statement PDFs بعد التحقق.
- Backup/Restore يحفظ أصل البانر ومرجعه.
- UI تدعم picker/preview/change/remove ثم **crop/reposition** بنسبة رأس PDF مع تحكم أفقي ورأسي.
- crop output محدود حتى 1800px عرضًا، وpreview decode محدود حتى 1600px لتجنب استهلاك ذاكرة غير ضروري.
- Debt Receipt / Account Statement يتطلبان **Preview ثم explicit confirmation** قبل الإصدار.
- launcher resources تشمل legacy/adaptive/round/monochrome، مع اختبار Android 13+ للـmonochrome.
- CI workflow على فروع PR لا يكرر push+PR runs، وconcurrency معزولة عن التشغيلات القديمة.

## البوابة الآلية المثبتة

Android CI #1248 — run `33274101583` ✅

- unit tests / lint / debug build ✅
- Room v12 generated/verified ✅
- 149 instrumentation tests، صفر فشل/أخطاء/تخطّي ✅
- Payment Receipt PDF inspection ✅
- Debt Receipt PDF inspection ✅
- Account Statement PDF inspection ✅
- artifacts: debug APK + Room schema + instrumentation reports + PDF evidence ✅

بعد commit مزامنة الوثائق يجب انتظار CI النهائي لذلك الرأس؛ لا تعتمد #1248 بوصفه CI للرأس الجديد، رغم أن الكود الوظيفي لا يتغير.

## الخطوات التالية — لا تتجاوز الترتيب

1. تأكد أن CI الخاص بcommit مزامنة الوثائق أخضر بالكامل.
2. استخدم `Wasl-debug` من **رأس PR النهائي** كـPR test/debug APK للقبول البصري، وليس كإصدار نهائي منشور.
3. راجع يدويًا: Home، العملات المتعددة، Add Account، Group Expense، Account Details/PDF action، Promise states، Documents/Attachments، banner crop/preview/export، Dark/Light/Auto، RTL، Large Font.
4. نفذ clean install وتحقق بصريًا من launcher icon على Android حديث؛ الاختبار الآلي للموارد لا يغني عن شكل Launcher الفعلي/cache.
5. بعد اعتماد القبول البصري فقط: حوّل PR #13 من Draft إلى Ready وادمجه إلى `main`.
6. شغّل/انتظر Android CI على merge commit في `main`.
7. استخرج APK الاختبار النهائي من artifact الخاص بـ`main` واحسب SHA-256.
8. Signed public release مرحلة منفصلة بعد توفير أسرار التوقيع الخارجية.

## الثوابت

1. Ledger append-only؛ التصحيح بالعكس.
2. Money = integer minor units فقط.
3. لا cross-currency netting.
4. Promise/Claim/Reminder/Installment ليست Ledger.
5. Notification/Natural/Voice لا تكتب ماليًا قبل Preview/Confirmation.
6. Group Expense ليست Ledger موازية.
7. المستند READY مبني على immutable snapshot.
8. لا فتح PDF/Attachment عند فشل integrity.
9. Restore يفحص schema/path/hash/FK/invariants قبل الاستبدال.
10. كل Migration معها exported schema + tests.
11. لا secrets أو signing keys في Git.

## أوامر البوابة

```bash
./gradlew :core:domain:test
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```

GitHub Actions على رأس PR/merge commit هي بوابة التسليم المرجعية.

## النشر العام

Signed Release يحتاج خارجيًا فقط:

- `WASL_KEYSTORE_BASE64`
- `WASL_KEYSTORE_PASSWORD`
- `WASL_KEY_ALIAS`
- `WASL_KEY_PASSWORD`

بدون Signed Release ناجح تكون الحالة signing pending وليست Published.
