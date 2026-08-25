# HANDOFF — الحالة الحية

آخر تحديث: 2026-08-26

## الحالة الحالية

- المشروع: **وَصل — Wasl**
- الشعار: **كل حساب له وصل**
- الإصدار: `0.1.0-dev`
- الفرع النشط: `agent/bootstrap-wasl-foundation`
- Pull Request: `#1` إلى `main`، وما زال **Draft** ومفتوحًا.
- `main` لم يُدمج ولم يُعدّل ضمن هذه المرحلة.
- قاعدة البيانات: Room Schema **v7** مع Migrations متسلسلة v1→v7 دون destructive migration.
- هوية Schema v7: `d2c9fe45f2707138bc1476639617e255`.
- آخر رأس كود خضع للتحقق الكامل: `be7f67dab355b936c2b5ce62f4710c4f63773bf3`.
- آخر تحقق كامل: **Android CI #382** — Run `32903618216` — نجاح كامل.

## ما يعمل الآن

### الحسابات والدفتر المالي

- إنشاء شخص جديد مع دينه الأول، أو إنشاء دين مستقل لشخص محفوظ دون تكرار Person.
- الاتجاهان: **لي عند الناس** و**عليّ للناس**.
- العملات الحالية: YER / SAR / USD.
- `Money` يستخدم Integer Minor Units من نوع `Long`؛ لا `Float` أو `Double` في المنطق المالي.
- Ledger append-only هو مصدر الحقيقة المالي.
- الدفعات الجزئية والنهائية محفوظة كأحداث، وتصحيح الدفعة يتم بعكس موثق لا بحذف الأصل.
- الرصيد والحالة و`closed_at` مشتقة من Replay.
- الأوامر المالية الحساسة Idempotent وتتحمل إعادة المحاولة بعد نتيجة غير مؤكدة.

### الاستحقاق والمتابعة والتنبيه القوي

- تاريخ استحقاق اختياري مع تعديل/إلغاء وAudit before/after.
- متابعة ذكية مرتبطة بالاستحقاق: قبل الموعد بيوم، يوم الموعد، بعد يومين، ثم أسبوعيًا حتى السداد.
- WorkManager Unique Work مع Recovery وإعادة جدولة Idempotent عند تغير الوقت/المنطقة الزمنية.
- `BLOCKED_PERMISSION` و`FAILED` حالات قابلة للاسترداد دون تغيير أصل الدين أو Ledger.
- Exact Alarm اختياري للمنبه القوي، وWorkManager يبقى fallback.
- فتح إعدادات Exact Alarm أو الإشعارات يتم بفعل صريح من المستخدم.

### وعود السداد والأقساط

- Promise مستقل عن Ledger و`due_date`، بحالات `PENDING / KEPT / MISSED / CANCELLED`.
- `KEPT` لا تنشئ Payment تلقائيًا.
- خطط الأقساط مستقلة عن Ledger، مع `ACTIVE / SUPERSEDED` وRevision history محفوظ.
- تقدم القسط مشتق من العمليات المالية الحقيقية، مع الثابت `paidAmount + remainingAmount == scheduledAmount`.
- Today يعرض الاستحقاقات والوعود والأقساط المستحقة/المتأخرة دون خلط نماذجها.

### البحث والتنقل

- Navigation 3 للرئيسية واليوم والبحث وتفاصيل الحساب ومركز الأقساط والمستندات والإعدادات والأمان.
- بحث Room Reactive باسم الشخص أو بيان الدين بحد نتائج واضح.
- فتح الحساب من الرئيسية أو Today أو البحث أو مركز الأقساط.

### المستندات المالية PDF

- `PAYMENT_RECEIPT`: إيصال سداد مبني من Snapshot ثابت لدفعة محفوظة.
- `DEBT_RECEIPT`: إيصال دين مستقل عن Ledger entry بعينه.
- `ACCOUNT_STATEMENT`: كشف حساب متعدد الصفحات من Snapshot ثابت لتاريخ العمليات.
- هوية مستند قابلة للتخصيص، ترقيم سنوي، metadata، SHA-256 وعدد صفحات وحالة توليد.
- مستند `READY` لا يفتح أو يشارك إذا فقد ملفه أو فشل SHA-256.
- `issued_documents` هو السجل العام، و`payment_issued_documents` View مخصصة لإيصالات السداد.
- CI يفحص PDF حقيقيًا بـ`pdfinfo` و`pdftotext` و`pdftoppm`.

### النسخ الاحتياطي والاستعادة

- Backup تطبيقي يدوي مشفر بكلمة مرور؛ Android Auto Backup وDevice Transfer معطلان عمدًا.
- النسخة منطقية وتشمل الجداول وملفات PDF الخاصة بالمستندات `READY`، وليست نسخًا خامًا لملف SQLite الحي.
- التشفير: AES-256-GCM مع PBKDF2-HMAC-SHA256 بـ210,000 iteration وPayload مضغوط gzip.
- قبل النسخ والاستعادة يتم التحقق من المسارات وSHA-256 والبنية وSchema.
- Restore يجهز الملفات في Stage ويفحص البيانات داخل Room مؤقتة وForeign Keys والثوابت المالية قبل استبدال الحالة الحية.
- يوجد Rollback عند فشل الاستبدال ولا تعتبر الاستعادة ناجحة قبل اكتمال مساري DB والملفات.

### الأمان والخصوصية

- قفل التطبيق منفذ عبر AndroidX `BiometricPrompt` وAndroid system authentication.
- المسموح: `BIOMETRIC_WEAK | DEVICE_CREDENTIAL` للتوافق مع الأجهزة المدعومة من minSdk 26.
- وَصل لا يخزن PIN مخصصًا ولا كلمة مرور قفل الجهاز ولا قالب بصمة.
- مهلة إعادة القفل قابلة للاختيار: فورًا، 15 ثانية، دقيقة، 5 دقائق؛ الافتراضي 15 ثانية.
- قياس المهلة يستخدم `SystemClock.elapsedRealtime()` وليس ساعة الحائط.
- حالة الجلسة تعيش في `AppLockViewModel` وتتحمل Configuration change.
- عند القفل تُمنع Pointer events وتُزال Semantics عن المحتوى المالي خلف شاشة القفل.
- تفعيل App Lock يفرض `FLAG_SECURE` حتى لو كان خيار الشاشة الآمنة المستقل معطلًا.
- إذا لم تعد مصادقة النظام متاحة يوجد Recovery صريح لتعطيل القفل دون حذف البيانات.
- تفاصيل الإشعارات الحساسة قابلة للإخفاء، وإشعارات الاستحقاق تستخدم Lock-screen public version عامة.

## قواعد ثابتة لا تكسر

1. Ledger مالي append-only؛ التصحيح بالعكس لا بالحذف أو تعديل حدث سابق.
2. Promise ليست عملية مالية ولا تغيّر الرصيد أو `due_date` تلقائيًا.
3. Installment Plan ليست Ledger ولا تنشئ رصيدًا ماليًا موازيًا.
4. الدفعات الفعلية للأقساط تأتي من Ledger.
5. Revision جديدة لخطة الأقساط تحفظ السابقة كـ`SUPERSEDED`.
6. المتابعة والتنبيه أدوات جدولة فقط؛ لا تغير الرصيد ولا تنشئ دفعة.
7. Exact Alarm اختياري وWorkManager fallback.
8. كل دين لشخص موجود حساب مستقل بمعرف مستقل.
9. الأموال لا تستخدم Floating Point.
10. المستند الصادر Snapshot تاريخي ثابت.
11. `READY` لا يعني صالحًا للفتح دون ملف صحيح وSHA-256 مطابق.
12. Backup/Restore لا يتجاوز فحوص Schema والمسارات والبصمات وForeign Keys والثوابت.
13. App Lock لا يخزن اعتمادًا سريًا مخصصًا داخل وَصل؛ المصادقة مسؤولية Android.
14. لا destructive migrations.
15. لا أسرار أو مفاتيح توقيع داخل المستودع.
16. لا دمج إلى `main` دون طلب صريح من صاحب المشروع.

## قاعدة البيانات الحالية — Schema v7

الجداول:

- `persons`
- `debts`
- `ledger_entries`
- `reminders`
- `audit_events`
- `document_identities`
- `issued_documents`
- `payment_promises`
- `installment_plans`
- `installments`

View:

- `payment_issued_documents`: `SELECT * FROM issued_documents WHERE document_type = 'PAYMENT_RECEIPT'`.

Migrations:

- v1→v2: reminders.
- v2→v3: audit events.
- v3→v4: document identities + issued documents.
- v4→v5: payment promises + indexes.
- v5→v6: installment plans/installments + revisions.
- v6→v7: `issued_documents.ledger_entry_id` أصبح nullable مع حفظ الصفوف السابقة وإعادة الفهارس والـView.

Schema artifact الملتزم: `app/schemas/com.wasl.app.data.local.WaslDatabase/7.json`.

## التحقق الحالي

**Android CI #382** — Run `32903618216` — head `be7f67d`:

- Unit tests ✅
- Lint ✅
- Debug APK ✅
- Room Schema v7 generated/current check ✅
- Android Emulator instrumentation: **63/63** ✅
- 0 failures / 0 errors / 0 skipped ✅
- App Lock Unit/UI regression ✅
- Dark Mode + Font Scale 2.0 security UI regression ✅
- MVP end-to-end acceptance: debt → restart → payments → settlement → reversal → replacement settlement → READY PDF → encrypted backup → corruption/post-backup mutation → restore ✅
- Migration v1→v7 / v6→v7 ✅
- Backup/Restore وPDF integrity ✅
- Payment/Debt/Statement PDF evidence ✅

Artifacts من #382:

- `Wasl-debug` — `9584098910` — SHA-256 `5dd34c6c702dcb204a2093560b89307bf374943d565c9d76206727140f6f9e38`.
- `Wasl-room-schema` — `9584099501` — SHA-256 `d1775c619dbd83745c0f61a2124b737ca7aeb67ad76889d64c00de3e5263eebf`.
- `Wasl-payment-receipt-evidence` — `9584290684` — SHA-256 `b94aed62d4e0f4b4e68803c6cb0eb63429f0fe197b2cd22c19d1232f1a5def79`.
- `Wasl-account-document-evidence` — `9584291121` — SHA-256 `b68101ddab87fcac7147bf84f894d9d213deda255c7615b3f759e473fc89b636`.
- `Wasl-room-instrumentation-results` — `9584291541` — SHA-256 `3864c9dfb4d02f483d214c48ffaf193b265ce40ef8b51f07f48ec3fc0d91a7cc`.

## ديون تقنية غير حاجبة

- توسيع اختبارات الإتاحة إلى TalkBack وفحص شاشات أكثر تحت Font Scale الكبير.
- اختبار نوافذ Compact/Expanded بصورة أوسع من شاشة الأمان الحالية.
- نقل Compose tests من API v1 إلى API v2 بعد ضبط التزامن المطلوب.
- ترقية `actions/setup-java@v4` إلى v5 وتحديث Actions التي تصدر تحذيرات Node 20/24.
- إزالة safe calls غير الضرورية في `StrongAlarmStoreInstrumentedTest`.
- إزالة `else` الزائد في `AccountDocumentPdfRenderer` بعد أن أصبح `when` exhaustive.
- تحسين زمن CI عبر Gradle configuration cache عندما يثبت أمانه.

## مرشحات المرحلة التالية

تُختار من `WASL_MASTER_PROJECT_PROMPT.md` و`SPEC.md` دون إسقاط قرار سابق:

- التذكيرات المتكررة العامة خارج سياق الاستحقاق.
- بحث متقدم داخل العمليات والمستندات والمبالغ والتواريخ.
- مرفقات/مطالبات وإدارة ملفات حساسة عند اعتماد تفاصيلها.
- توسيع الإتاحة والتكيف مع أحجام النوافذ وTalkBack.
- تجهيز Release signing والتوزيع عندما يحين وقت الإصدار.
