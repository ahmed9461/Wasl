# HANDOFF — وَصل

آخر تحديث: 2026-08-29

نقطة البدء لأي جلسة تطوير جديدة. ترتيب مصدر الحقيقة عند التعارض:

**الكود على رأس PR #13 → Room exported schema → GitHub Actions لنفس الرأس → `docs/CURRENT_STATUS.md` → هذا الملف → بقية الوثائق.**

## الحالة الحالية

- المنتج: **وَصل — Wasl**
- الشعار: **كل حساب له وصل**
- المستودع: `ahmed9461/Wasl`
- Application ID: `com.wasl.app`
- الإصدار المرشح: `0.1.0` (`versionCode = 1`) إلى أن تُغلق جولة v0.4.
- `main` يحتوي Corrective UI v0.3 بعد دمج PR #12.
- merge commit المرجعي على `main`: `15f982b9a3804861f96b454431c96ed4f8c19c04`.
- Android CI #1100 على merge commit في `main` نجح بالكامل.

## الجولة المفتوحة — UI/UX Hardening v0.4

- الفرع: `agent/ui-ux-hardening-v0.4`
- PR: **#13** — Draft.
- المواصفة التنفيذية الملزمة: `docs/UI_UX_HARDENING_V0.4_EXECUTION_PLAN.md`.
- Room على الفرع: **Schema v12**، و`1.json → 12.json` ملتزمة في Git.
- لا destructive migration في Production.

> لا تعتبر CI الأخضر وحده تعريفًا للإنجاز؛ شروط القبول البصري والعملي في خطة v0.4 إلزامية أيضًا.

## ما أُنجز في v0.4

### UI/UX Batch 1

- Home يخفي العملات ذات الرصيد المفتوح صفر.
- بطاقات الحساب compact بدون placeholder حرف كبير.
- تخطيط الهاتف القياسي أكثر أفقية وكثافة مع adaptive fallback.
- Group Expense compact/responsive للاتجاه والعملات والمشاركين.
- زر PDF السريع في تفاصيل الحساب نُقل إلى المنطقة العلوية اليسرى.
- Payment Promise actions جُمعت في Action Bar.
- Documents Hub مربوط بالـAttachment Store الحقيقي.
- Attachment picker محمي والنصوص التقنية الخاصة بـSHA-256/storage أزيلت من UX اليومي.

مرجع checkpoint: `docs/V04_BATCH1_PROGRESS.md`.

### Document Banner / Room v12

- Migration `11→12` تضيف banner path/hash لهوية المستند.
- `DocumentBannerAsset` + content-addressed app-private vault + path/hash/image validation.
- `DocumentBannerSnapshotCodec` و`DocumentIdentityBannerMapper` مع fail-closed validation.
- snapshots الخاصة بالدفع/الدين/كشف الحساب تجمد البانر المختار تاريخيًا مع backward compatibility.
- PDF renderers تتحقق من البانر التاريخي قبل الرسم ولا تستخدم fallback صامتًا عند العبث.
- Encrypted Backup/Restore يحفظ البانر ومرجعه مع اختبار استعادة.
- Documents UI تدعم اختيار/معاينة/إزالة صورة رأس المستند بصورة compact.
- اختبارات migration/snapshot/vault/backup/launcher أضيفت ضمن الجولة.

مرجع core checkpoint: `docs/V04_BANNER_CORE_PROGRESS.md`.

## وضع CI الآن

- آخر baseline كامل مثبت قبل دفعات البانر الأخيرة: Android CI #1152 — run `33254422017` — head `c990642575ca5635a68f66342828f7d1fb411e49` ✅.
- Android CI #1195 على `624b6f18...` كشف compile error واحدًا في `DocumentIdentityBannerControls.kt`: import غير صالح لـ`layout.weight`.
- أصل الخطأ أُصلح في commit `7129faaccef7dadcffa66bf5f32a7c1653cf4d31` بإزالة الاستيراد المباشر واستخدام `RowScope.weight`.
- بعد أي commit جديد على الفرع، اعتمد Android CI الخاص **برأس PR الحالي نفسه**؛ لا تعتمد run قديمًا أو workflow تطبيق مخصصًا بدل البوابة الكاملة.

## المتبقي قبل دمج v0.4

1. Android CI كامل أخضر على رأس PR #13 النهائي.
2. Regression coverage للمرفقات/File Picker: cancel، صورة سليمة، PDF سليم، URI غير صالح/غير قابل للقراءة، integrity failure.
3. قبول banner end-to-end: pick/preview/remove → snapshot → PDF فعلي مع banner → tamper fails closed → backup/restore.
4. فحص بصري يدوي للشاشات المطلوبة في خطة v0.4، بما يشمل RTL وLarge Font وDark/Light/Auto.
5. clean install للأيقونة المرجعية والتحقق من legacy/adaptive/round/monochrome resources على Launcher.
6. مزامنة `CURRENT_STATUS` و`PROJECT_CONTEXT` و`CHANGELOG` وPR body مع الرأس المقبول النهائي.
7. دمج PR #13 إلى `main` فقط بعد إغلاق البنود السابقة.
8. إعادة Android CI على merge commit في `main` ثم استخراج APK من artifact الخاص بـ`main` فقط وحساب SHA-256.

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

## أوامر البوابة المحلية/CI

```bash
./gradlew :core:domain:test
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```

GitHub Actions على رأس PR/merge commit هي بوابة التسليم المرجعية.

## النشر العام

Signed Release منفصل ويحتاج الأسرار الخارجية التالية فقط عند الاستعداد للنشر:

- `WASL_KEYSTORE_BASE64`
- `WASL_KEYSTORE_PASSWORD`
- `WASL_KEY_ALIAS`
- `WASL_KEY_PASSWORD`

بدونها تكون الحالة signing pending وليست Published.
