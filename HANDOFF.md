# HANDOFF — وَصل

آخر تحديث: 2026-08-29

نقطة البدء لأي جلسة تطوير جديدة. ترتيب مصدر الحقيقة: الكود على الرأس الحالي → Room exported schema → GitHub Actions لنفس الرأس → `docs/CURRENT_STATUS.md` → هذا الملف.

## الحالة الحالية

- المنتج: **وَصل — Wasl**
- الشعار: **كل حساب له وصل**
- الإصدار المرشح: `0.1.0` (`versionCode = 1`).
- `main` الحالي يحتوي Corrective UI v0.3 بعد دمج PR #12.
- merge commit المرجعي على `main`: `15f982b9a3804861f96b454431c96ed4f8c19c04`.
- Android CI #1100 — run `33229515030` على هذا الـmerge commit نجح بالكامل: Unit/Lint/APK/Room v11/Emulator/PDF ✅.
- Room Schema: **v11**، و`1.json → 11.json` ملتزمة في Git.

## الجولة المفتوحة الآن — UI/UX Hardening v0.4

**الفرع:** `agent/ui-ux-hardening-v0.4`

**المواصفة التنفيذية الملزمة:**

`docs/UI_UX_HARDENING_V0.4_EXECUTION_PLAN.md`

> قبل تعديل أي واجهة أو اختبار في هذه الجولة، اقرأ الملف أعلاه كاملًا. لا تسقط أي بند منه، ولا تعتبر CI الأخضر وحده كافيًا إذا فشل شرط القبول البصري أو العملي.

### أبرز المشاكل المطلوب إغلاقها في v0.4

- الرئيسية لا تعرض العملات ذات الرصيد صفر.
- بطاقة الحساب تصبح compact ولا تعرض حرفًا كبيرًا غير مفهوم قبل الاسم.
- العملية الجماعية تستخدم تخطيطًا أفقيًا/مدمجًا بدل قائمة طويلة.
- تفاصيل الحساب ترتب الإجراءات في صفوف/Grid مدمجة، وتنقل زر PDF لأعلى اليسار.
- أزرار وعد السداد تصبح Action Bar منظمة.
- إصلاح crash اختيار صورة/PDF/ملف بصورة جذرية مع regression tests.
- حذف النصوص التقنية مثل SHA-256 وصلاحيات التخزين من UX اليومي.
- تنفيذ صورة رأس/بانر لهوية PDF مع Preview وsnapshot ثابت للمستند.
- تثبيت أيقونة التطبيق المرجعية الفعلية واختبار clean install على Launcher.
- مراجعة النصوص العربية وكثافة المسافات في كل شاشة يتم لمسها.

## ثوابت لا تكسر

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

## بوابة تسليم v0.4

لا دمج إلى `main` حتى تتحقق كل البنود في ملف الخطة، ثم:

```bash
./gradlew :core:domain:test
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```

بعد نجاح PR ودمجه يجب إعادة Android CI على `main`، واستخراج APK فقط من artifact الخاص بـ`main`.

## النشر

Signed Release منفصل ويحتاج الأسرار الخارجية التالية فقط عند الاستعداد للنشر العام:

- `WASL_KEYSTORE_BASE64`
- `WASL_KEYSTORE_PASSWORD`
- `WASL_KEY_ALIAS`
- `WASL_KEY_PASSWORD`

بدونها تكون الحالة signing pending وليست Published.