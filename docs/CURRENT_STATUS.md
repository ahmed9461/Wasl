# وَصل — الحالة الحالية وخطة الإكمال

آخر مراجعة: 2026-08-28

هذا الملف هو نقطة الرجوع السريعة للحالة الفعلية. عند التعارض بين توثيق قديم والكود، يكون الترتيب: الكود الحالي + Room schemas الملتزمة + GitHub Actions evidence، ثم هذا الملف، ثم بقية وثائق المراحل.

## الفرع ومسار التسليم

- المستودع: `ahmed9461/Wasl`.
- الفرع النشط: `agent/bootstrap-wasl-foundation`.
- Pull Request: `#1` إلى `main` وما زال Draft.
- لا دمج إلى `main` دون طلب صريح.
- Room Database الحالية: **v9**.
- ملفات Room التاريخية **1.json → 9.json** أصبحت ملتزمة في Git، بما فيها v8 وv9 الأصليتان المسترجعتان من CI artifacts، وليستا ملفات معاد إنشاؤها بالتخمين.

## آخر بوابة كاملة مثبتة

**Android CI #861 — Run `33116906973` — head `3644b143d53b8ddcbc12f2bc11f72344130e9815`.**

نجح بالكامل:

- Unit tests ✅
- Lint ✅
- Debug APK ✅
- Room Schema v9 generated/current verification ✅
- Claims/Attachments schema checks ✅
- Android instrumentation: **98/98**، 0 failed، 0 skipped ✅
- Payment Receipt PDF evidence ✅
- Debt Receipt PDF evidence ✅
- Account Statement PDF evidence ✅

Artifacts المرجعية من #861:

- `Wasl-debug`: `9664998276`
- `Wasl-room-schema`: `9664998888`
- `Wasl-payment-receipt-evidence`: `9665255570`
- `Wasl-account-document-evidence`: `9665256369`
- `Wasl-room-instrumentation-results`: `9665257273`

وبذلك أصبحت Claims وAttachments وSchema v9 مثبتة ببوابة كاملة وليست مجرد compile.

## ما يعمل الآن

### المصدر المالي

- أشخاص وحسابات متعددة للشخص.
- `RECEIVABLE` و`PAYABLE`.
- YER / SAR / USD دون خلط العملات.
- Money بوحدات Minor Units من نوع `Long`.
- Ledger append-only.
- دفعات جزئية ونهائية.
- Payment reversal موثق بدل حذف التاريخ.
- Idempotency وReplay لاشتقاق الرصيد والحالة.

### الاستحقاق والمتابعة

- Due date قابل للتعديل/الإلغاء مع Audit.
- Today للاستحقاقات الحالية والمتأخرة.
- WorkManager scheduling + recovery.
- Exact Alarm اختياري للمنبه القوي مع fallback.
- General Reminder مستقل عن `due_date`: one-shot / daily / weekly / monthly.
- إجراءات الإشعار: فتح الحساب، دفع جزء، تم السداد، ذكرني لاحقًا.
- لا كتابة مالية مباشرة من Notification callback؛ الدفع يمر عبر Preview/Confirmation داخل التطبيق.

### الوعود والأقساط والمطالبات

- Payment Promises مستقلة عن Ledger.
- Installment Plans مع `ACTIVE / SUPERSEDED` وRevision history.
- تقدم الأقساط مشتق من الدفعات الحقيقية.
- Payment Claims «طالبني» مستقلة عن Ledger والرصيد و`due_date`.
- Today يجمع الاستحقاق/الوعد/القسط/المطالبة مع بقاء كل نموذج مستقلًا.

### المرفقات وخزنة الإثباتات

- `attachments` في Schema v9.
- ربط بالدين وبـLedger entry اختياري من نفس الدين.
- Internal storage + metadata + SHA-256 + relative path فريد.
- فتح/مشاركة آمنة عبر FileProvider.
- Backup/Restore للmetadata والملفات مع فحص المسار والبصمة.

### المستندات

- `PAYMENT_RECEIPT`.
- `DEBT_RECEIPT`.
- `ACCOUNT_STATEMENT` متعدد الصفحات.
- Immutable snapshots، ترقيم، metadata، SHA-256، page count.
- فتح/مشاركة بعد فحص سلامة الملف.

### النسخ الاحتياطي والأمان

- Backup/Restore تطبيقي مشفر بكلمة مرور.
- Backup schema v9 يشمل 12 جدولًا وملفات PDF والمرفقات.
- Restore مرحلي مع Schema/path/hash/FK/invariant validation وRollback.
- App Lock عبر BiometricPrompt / Device Credential.
- App Lock session أصبحت مشتركة على مستوى Application بدل Activity محلية.
- `ProtectedWaslActivity` يحمي Activities الحساسة ويعيد المستخدم إلى Main عند القفل.
- `FLAG_SECURE` وسياسة خصوصية الإشعارات.

### الإدخال الطبيعي والصوت

- Natural Entry: `Parser → Draft/Preview → explicit Confirmation → Save`.
- Voice Dictation موجود عبر Android `RecognizerIntent` ويغذي المسار نفسه، ولا ينفذ حفظًا ماليًا مباشرًا.
- Voice hardening الحالي يعالج success/cancel/empty result وعدم وجود resolver وفشل إطلاق recognizer.
- توجد Unit tests لاختيار أول نتيجة غير فارغة، empty result، وcancel.
- لا يجب الادعاء بأن ActivityResult الخارجي للـRecognizer مغطى E2E بالكامل؛ هذه ما زالت فجوة اختبار تكامل خارجية.

## Accessibility / Adaptive UI

**الحالة: قيد التنفيذ الفعلي، وليست مجرد تدقيق نظري.**

الأساس الموجود:

- RTL على مستوى التطبيق.
- `formatMoney()` يعزل النص المالي باستخدام Unicode Bidi isolation.
- `WaslMaxContentWidth = 760.dp` مستخدم في شاشات حديثة.
- `shouldStackDenseRows()` يكدس الصفوف عند العرض الضيق أو `fontScale >= 1.3`.
- Search/Top-level navigation لديها اختبارات large-font وsemantics.

التحسينات الجديدة:

- commit `7a722563c85b7bf065445661c94f894568b6e9e0`: Statistics + Person Timeline تستخدم تكديسًا فعليًا للصفوف المزدحمة وتختبره عند 200% Font Scale.
- CI #866 على هذا التغيير شغل **98 اختبارًا**؛ فشل اختبار واحد فقط في `StatisticsScreenUiInstrumentedTest` لأن الاختبار مرر إلى أسفل الشاشة ثم طالب أن يبقى نص أعلى الشاشة ظاهرًا. هذا كان Assertion مرتبطًا بالـviewport، وليس فشلًا وظيفيًا أو في Room.
- commit `ee23079f70f556853337521aa340510d4c110d0a`: جعل Assertion حتميًا بفحص النص العلوي قبل التمرير دون حذف تغطية.
- commit `774d5df6b96961fb6cb66f288036678e60ae2fd3`: Today أصبح يكدس عند النص الكبير:
  - بطاقات ملخص اليوم.
  - عناوين الأقسام والعداد.
  - المبلغ + حالة الاستحقاق.
  - أزرار فتح الحساب/الإذن/إعادة المحاولة.
  - Header الشخص + شارة الاتجاه.
  - المبلغ/الحالة في الأقساط والوعود والمطالبات.
- أضيف اختبار Today صريح عند **200% Font Scale** يتحقق من التكديس ووصول زر إذن الإشعارات.

الرأس الحالي يحتاج بوابة CI كاملة جديدة بعد هذه الدفعة قبل وسم Adaptive الجزئي Verified.

## ملاحظة استقرار CI

- فشل CI #858 السابق كان Flaky selector في `DueDateUiInstrumentedTest` بسبب `hasScrollAction()` الذي طابق أكثر من Scrollable.
- تم استبداله بـ`hasScrollToIndexAction()` للحاوية ذات الفهرسة.
- نفس SHA نجح في #859، ثم أثبت #861 الإصلاح بالكامل.
- CI concurrency أصبح يستخدم اسم الفرع (`github.head_ref || github.ref_name`) لمنع تشغيل push وPR متوازيين لنفس الرأس؛ إلغاء #860 عند بدء #861 كان السلوك المقصود.

## ترتيب العمل التالي

1. تشغيل بوابة CI كاملة على الرأس الحالي الذي يشمل Adaptive Today والإصلاح الحتمي لاختبار Statistics.
2. إذا أخضر: متابعة Accessibility/Adaptive audit لبقية الشاشات، خصوصًا touch targets، صفوف الأزرار، text scaling، focus/TalkBack semantics، وRTL/LTR للأرقام والتواريخ والمعرفات.
3. تثبيت أي فجوات وصول باختبارات Instrumentation حقيقية، لا بمجرد تعديلات شكلية.
4. تنفيذ المصاريف/الديون الجماعية حسب المواصفة دون إنشاء Ledger موازٍ.
5. جولة UI/PDF polishing النهائية المؤجلة عمدًا حتى اكتمال الوظائف.
6. Full offline acceptance + migrations/backup regression.
7. Release signing والتوزيع.

## سياسة الجودة

لا تعتبر أي مرحلة مكتملة بمجرد compile. حسب نطاقها يجب أن تمر عبر Unit + Lint + build + Room/instrumentation + UI + Backup/Restore/PDF evidence عند الصلة. ولا تُخفف الاختبارات لإجبار CI على الأخضر؛ عند ظهور فشل يجب تمييز فشل المنتج من فشل الاختبار الهش وإصلاح السبب الحقيقي.