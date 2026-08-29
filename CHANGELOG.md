# سجل التغييرات

آخر مزامنة: 2026-08-29.

## غير منشور — UI/UX Hardening v0.4

### تجربة الاستخدام

- الرئيسية تخفي العملات التي لا يوجد لها رصيد مفتوح بدل عرض بطاقات صفرية ثابتة.
- بطاقات الحساب أصبحت compact وأزيل الـplaceholder الكبير بحرف واحد.
- تحسين التخطيطات الأفقية/adaptive على الهاتف القياسي مع fallback للشاشات الضيقة والخط الكبير.
- ضغط واجهة Group Expense للاتجاه والعملات والمشاركين.
- نقل إجراء تصدير PDF في تفاصيل الحساب إلى المنطقة العلوية اليسرى ضمن Safe Area.
- تجميع إجراءات Payment Promise المعلقة داخل Action Bar منظمة، بدون كتابة دفعة مالية تلقائية عند تغيير حالة الوعد.
- ربط Documents Hub بالـAttachment Store الحقيقي.
- إزالة نصوص SHA-256 وصلاحيات/تفاصيل التخزين التقنية من UX اليومي.

### المرفقات وFile Picker

- تحصين OpenDocument ضد cancel و`uri == null` وفشل metadata/stream/provider.
- إضافة regression instrumentation لاختيار PNG وPDF، إلغاء المنتقي، URI غير قابل للقراءة، ومسارات integrity.
- الإبقاء على فتح/مشاركة الملفات مشروطين بسلامة الملف داخل app-private vault.

### هوية المستندات وصورة الرأس

- رفع Room Schema من v11 إلى **v12** مع Migration `11→12` وإضافة `banner_relative_path` و`banner_sha256` إلى `document_identities`.
- إضافة `DocumentBannerAsset` وapp-private content-addressed vault مع path/hash/image/size validation.
- إضافة snapshot codec/mapper مع fail-closed validation.
- تثبيت البانر المختار داخل immutable snapshots الخاصة بـPayment/Debt/Account Statement مع backward compatibility.
- تحديث PDF renderers للتحقق من الأصل التاريخي ورسم البانر على الصفحة الأولى دون fallback صامت عند فشل integrity.
- تحديث Encrypted Backup/Restore ليحفظ أصل البانر ومرجعه مع regression tests للاستعادة.
- إضافة واجهة لاختيار/معاينة/تغيير/إزالة صورة رأس هوية المستند.
- إضافة **crop/reposition حقيقي** بنسبة رأس PDF مع focus أفقي ورأسي قبل اعتماد الصورة.
- دعم الصور الصغيرة جدًا ومنع crop بأبعاد صفر.
- تحديد final crop حتى 1800px عرضًا، ومعاينة UI بـsampled decode حتى 1600px لتقليل استهلاك الذاكرة.
- إضافة **Preview قبل الإصدار** لإيصال الدين وكشف الحساب؛ لا يتم إنشاء المستند حتى explicit confirmation.
- إضافة اختبارات ratio/focus/downsampling/tiny image/snapshot/PDF tamper/backup restore.

### Launcher وCI

- تثبيت legacy/adaptive/round launcher resources وإضافة Android 13+ monochrome coverage.
- تعديل CI بحيث تعمل فروع PR عبر `pull_request` فقط بدل push+PR المكرر، مع concurrency namespace معزولة عن التشغيلات القديمة.

### التحقق

Functional-code gate:

Android CI **#1248** — run `33274101583` — head `9d0622f26ab6760d68362adf279da4e7e2ca69c7` ✅

- Unit tests / Lint / Debug APK ✅
- Room Schema v12 generated/verified ✅
- **149/149 instrumentation tests**، 0 failures، 0 errors، 0 skipped ✅
- Payment Receipt PDF inspection ✅
- Debt Receipt PDF inspection ✅
- Account Statement PDF inspection ✅
- debug APK / Room schema / instrumentation reports / PDF evidence artifacts ✅

مزامنة الوثائق الحالية هي آخر تغيير مخطط على الفرع قبل القبول البصري. يجب أن ينجح CI الخاص برأس الوثائق أيضًا. لا تُغلق v0.4 ولا تُدمج قبل القبول البصري/العملي المحدد في `docs/UI_UX_HARDENING_V0.4_EXECUTION_PLAN.md` وclean-install verification للأيقونة.

## واجهة احترافية — Corrective UI v0.3

- اعتماد اللوحة المرئية المرجعية كمرجع تصميم ملزم.
- إعادة بناء الرئيسية بهوية داكنة مدمجة، ملخص عملات مستقل، حسابات مختصرة، وزرين منفصلين لإضافة الحساب والإدخال الذكي.
- إعادة بناء إضافة الحساب كتدفق قصير وعملي؛ الاستحقاق والتذكير والمنبه ضمن خيارات إضافية.
- إعادة بناء «اليوم» بملخص صغير وصياغات عربية محسنة.
- إعادة بناء الأقساط بملخص إجمالي/مسدد/متبقٍ وفلاتر وبطاقات تقدم مدمجة.
- إعادة بناء تفاصيل الحساب برصيد بارز، تقدم السداد، إجراءات داخلية، وبيانات متابعة مختصرة.
- ضغط الإعدادات إلى مجموعات متناسقة مع تلقائي/داكن/فاتح والأمان والنسخ الاحتياطي.
- استبدال الأيقونة السابقة بأيقونة «وصل» الذهبية ذات الخلفية الداكنة واللمسة الفيروزية، مع adaptive/round resources.
- المحافظة على RTL والخطوط الكبيرة وثوابت Ledger وسلامة البيانات المالية.

### بوابة v0.3 المدمجة

Corrective UI v0.3 دُمجت إلى `main` عند `15f982b9a3804861f96b454431c96ed4f8c19c04`، وAndroid CI #1100 — run `33229515030` على merge commit نجح بالكامل ✅.

## 0.1.0 — Release Candidate

### الأساس المالي

- Android أصلي بـKotlin/Compose/Material 3/Navigation 3.
- `core:domain` مستقل وMoney بminor units من `Long`.
- Ledger append-only مع Payment / Payment Reversal وidempotency/replay.
- أشخاص وحسابات متعددة، RECEIVABLE/PAYABLE، وYER/SAR/USD دون خلط العملات.

### المتابعة

- Due dates + Audit + Today.
- WorkManager scheduling/recovery وExact Alarm اختياري.
- General Reminders.
- Payment Promises.
- Installment Plans/Revisions.
- Payment Claims «طالبني».

### البحث والعرض

- Basic/Advanced Search.
- Person Timeline.
- Objective Statistics.
- Documents Hub وAccount Details timeline.
- Adaptive/RTL/Bidi hardening واختبارات large-font للمناطق الرئيسية.

### الإدخال الطبيعي والصوتي

- Natural Entry: Parser → Preview → explicit Confirmation → Save.
- Voice Dictation بحالات recognized/empty/cancelled/unavailable/launch failure.
- لا حفظ مالي مباشر من الصوت أو الإشعار.

### Group Expense

- العملية الأصلية context تاريخي وليست Ledger موازيًا.
- كل share تتحول إلى Debt عادي.
- 2+ مشاركين، unequal shares، عملة واتجاه موحدان، exact total.
- atomic transaction + replay/idempotency + conflict detection + rollback.
- Preview/Confirmation إلزاميان.

### المستندات

- `PAYMENT_RECEIPT`, `DEBT_RECEIPT`, `ACCOUNT_STATEMENT` من immutable snapshots.
- numbering + page count + SHA-256 + integrity checks.
- Document Templates: MINIMAL / BUSINESS / CLASSIC / COMPACT / MODERN.
- القالب والهوية/البانر المختاران يثبتان تاريخيًا داخل snapshots في v0.4.

### المرفقات والنسخ والأمان

- Attachments/evidence vault داخل مساحة التطبيق مع SHA-256 ومسارات آمنة.
- FileProvider للمشاركة الصريحة وفحوص integrity.
- Backup/Restore مشفر مع staging + schema/path/hash/FK/invariant validation + rollback.
- App Lock عبر BiometricPrompt/Device Credential.
- `FLAG_SECURE` وسياسة خصوصية للإشعارات الحساسة.
- Local-first، ولا صلاحية `INTERNET` في الإصدار الحالي.

### قاعدة البيانات

- exported Room schemas `1 → 11` في baseline المدمج؛ v0.4 تضيف v12.
- migrations صريحة دون destructive migration.
- v8: payment claims.
- v9: attachments.
- v10: group expenses + shares.
- v11: document templates.
- v12: document identity banner metadata.

### Release engineering

- `versionName = 0.1.0`, `versionCode = 1`.
- `PRIVACY_POLICY.md` و`docs/RELEASE_CHECKLIST.md` موجودان.
- Signed Release workflow يستخدم secrets خارج Git، يتحقق بـ`apksigner` ويولد SHA-256.

## حالة الإصدار

`main` يحمل Corrective UI v0.3 المدمجة. v0.4 ما زالت على PR #13 Draft. بعد نجاح CI النهائي لمزامنة الوثائق يبقى القبول البصري وclean-install للأيقونة، ثم الدمج وإعادة CI على `main`. النشر العام الموقّع مرحلة منفصلة ويتطلب مفتاح توقيع وأسرار Release خارج Git.
