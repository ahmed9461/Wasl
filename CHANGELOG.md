# سجل التغييرات

جميع التغييرات المهمة في وَصل تسجل هنا.

## Unreleased

### Added

- المرجع التأسيسي الدائم للمشروع وملفات الذاكرة `AGENTS.md`, `PROJECT_CONTEXT.md`, `SPEC.md`, `DECISIONS.md`, `HANDOFF.md`.
- Android project أصلي بـKotlin وJetpack Compose وMaterial 3 وNavigation 3، مع Theme عربي RTL ووضعين فاتح/داكن.
- وحدة `core:domain` مستقلة للأموال والديون، مع `Money` بوحدات صغرى `Long` دون Floating Point.
- Ledger مالي append-only، دفعات جزئية/نهائية، وعكس دفعة كحدث مستقل بسبب موثق.
- Room persistence ومجموعة Migrations صريحة حتى **Schema v7** دون destructive migration.
- إنشاء شخص ودين، وإنشاء ديون مستقلة لشخص محفوظ دون تكرار Person.
- استحقاق قابل للتعديل/الإلغاء مع Audit، متابعة ذكية عبر WorkManager، ومنبه قوي اختياري عبر Exact Alarm مع fallback.
- شاشة Today للاستحقاقات والوعود والأقساط، وبحث Room Reactive وفتح الحساب من النتائج.
- Payment Promises مستقلة عن Ledger و`due_date` بحالات `PENDING / KEPT / MISSED / CANCELLED`.
- Installment Plans مع `ACTIVE / SUPERSEDED` وRevision history محفوظ، وتقدم متصالح مع Ledger.
- مستندات مالية من immutable snapshots:
  - `PAYMENT_RECEIPT`.
  - `DEBT_RECEIPT`.
  - `ACCOUNT_STATEMENT` متعدد الصفحات.
- هوية مستند قابلة للتخصيص، ترقيم سنوي، Metadata، SHA-256، page count، فتح/مشاركة عبر FileProvider بعد فحص السلامة.
- `issued_documents` كسجل مستندات موحد، و`payment_issued_documents` View خاصة بإيصالات السداد.
- Backup تطبيقي يدوي مشفر بكلمة مرور يشمل الجداول وملفات PDF `READY`.
- Backup envelope باستخدام AES-256-GCM + PBKDF2-HMAC-SHA256 بـ210,000 iteration + gzip JSON.
- Restore مرحلي عبر Stage + Room مؤقتة، مع فحص Schema والمسارات وSHA-256 وForeign Keys والثوابت المالية وRollback.
- شاشة إعدادات للخصوصية والنسخ الاحتياطي والمستندات.
- **App Lock** عبر AndroidX Biometric `1.1.0` و`BiometricPrompt` مع `BIOMETRIC_WEAK | DEVICE_CREDENTIAL`.
- مهلات App Lock: فورًا / 15 ثانية / دقيقة / 5 دقائق، باستخدام `SystemClock.elapsedRealtime()`.
- `AppLockViewModel` يحافظ على الجلسة عبر Configuration changes.
- شاشة قفل تمنع Pointer interaction وتزيل Compose semantics عن المحتوى المالي الخلفي.
- Recovery صريح لتعطيل App Lock إذا لم تعد مصادقة النظام متاحة، دون حذف بيانات المستخدم.
- App Lock يفرض `FLAG_SECURE`، مع بقاء خيار الشاشة الآمنة المستقل.
- اختبارات `AppLockViewModelTest` لمنطق التفعيل/المصادقة/المهلة/Lock now/التعطيل.
- `SecurityUiInstrumentedTest` يغطي App Lock UI وRecovery و**Dark Mode + Font Scale 2.0**.
- `MvpAcceptanceInstrumentedTest` لرحلة كاملة: إنشاء دين → restart → دفعات → سداد → عكس → سداد بديل → READY PDF → Backup مشفر → إفساد ملف/إضافة بيانات → Restore والتحقق من الحالة.
- CI يولد ويفحص ملفات PDF حقيقية بـ`pdfinfo`, `pdftotext`, `pdftoppm` ويرفع أدلة مستقلة.

### Changed

- أصبح Room Schema الحالي **v7**؛ `issued_documents.ledger_entry_id` nullable لدعم Debt Receipt وAccount Statement دون Ledger entry مصطنع، مع بقاء Payment Receipt مرتبطًا بحركته.
- أصبحت `payment_issued_documents` طبقة View مخصصة لإيصالات السداد بدل افتراض أن كل `issued_documents` Payment Receipt.
- أصبحت شاشة Today تجمع الاستحقاقات ووعود السداد والأقساط مع إبقاء النماذج مستقلة عن Ledger.
- أصبح Production يمرر قدرات الأقساط عبر `InstallmentAwareWaslRepository` بدل Global state أو تكرار Room logic.
- أصبح مسار النسخ الاحتياطي المدعوم داخل التطبيق صريحًا ومشفرًا؛ Android Auto Backup وDevice Transfer يبقيان معطلين عمدًا.
- أصبح قفل التطبيق يعتمد مصادقة Android نفسها بدل إنشاء PIN خاص بوَصل.
- أصبحت مهلة القفل monotonic ولا تتأثر بتغيير ساعة الجهاز/المنطقة الزمنية.
- أصبحت الحماية من Screenshot/Recent Apps مفروضة تلقائيًا عند تفعيل App Lock.
- تم تحديث `docs/DATABASE_SCHEMA.md` ليطابق Schema v7 الفعلية بدل وصف v3 القديم.

### Fixed

- محاذاة إصدارات AndroidX مع compileSdk 36 المستقر.
- تثبيت معرفات أوامر الإنشاء/الدفع/العكس عبر إعادة المحاولة لمنع تكرار العمليات بعد نتائج حفظ غير مؤكدة.
- محاذاة kotlinx-serialization Core/JSON عبر BOM 1.9.0.
- إصلاح Recovery للتذكيرات وإعادة `BLOCKED_PERMISSION/FAILED` بصورة قابلة للاسترداد دون تغيير الدين.
- إصلاح Exact Alarm UX بحيث لا تفتح إعدادات Android تلقائيًا من السويتش.
- مزامنة Room Schema v5/v6/v7 الملتزمة مع المخرجات الفعلية من Room.
- إصلاح Migration v6→v7 للـView `payment_issued_documents` ليتطابق SQL مع `@DatabaseView` حرفيًا.
- إصلاح AndroidTest بعد تعميم `DocumentSnapshot` ليتعامل Payment Receipt صراحة مع `PaymentReceiptSnapshot`.
- إصلاح Compose test imports غير الموجودة في اختبارات Today/Security.
- إصلاح `MvpAcceptanceInstrumentedTest` بعد اكتشاف `StackOverflowError`: `ContextWrapper.getFilesDir()` أصبح يشير صراحة إلى مجلد الاختبار الخارجي بدل استدعاء getter نفسه.

### Security

- `usesCleartextTraffic=false`.
- Android Auto Backup وDevice Transfer معطلان.
- Keystores وملفات الأسرار غير ملتزمة في Git.
- إشعارات الاستحقاق `VISIBILITY_PRIVATE` مع Public version عامة لا تعرض الاسم/المبلغ.
- PDF `READY` يفحص وجود الملف وSHA-256 قبل الفتح أو المشاركة.
- Backup مشفر ومصدق، مع فحص المستندات قبل النسخ وبعد فكها.
- Restore يرفض Schema أو بنية أو مسارات أو بصمات أو Foreign Keys غير صالحة قبل اعتماد الحالة.
- App Lock لا يخزن PIN مخصصًا أو كلمة مرور الجهاز أو قالب بصمة؛ التحقق مفوض إلى Android.
- المحتوى المالي خلف شاشة القفل غير قابل للتفاعل عبر Pointer أو Compose semantics.
- Recovery من غياب المصادقة لا يحذف البيانات.

### Verification

آخر بوابة كاملة لكود المرحلة:

- **Android CI #382** — Run `32903618216` — head `be7f67dab355b936c2b5ce62f4710c4f63773bf3`.
- Unit tests ✅
- Lint ✅
- Debug APK ✅
- Room Schema v7 export/current check ✅
- Android Emulator instrumentation: **63/63** ✅
- failures: 0 / errors: 0 / skipped: 0 ✅
- Payment Receipt PDF evidence ✅
- Debt Receipt PDF evidence ✅
- Account Statement PDF evidence ✅
- App Lock/Font Scale regression ✅
- MVP end-to-end acceptance ✅

Artifacts من CI #382:

- `Wasl-debug` — `9584098910` — SHA-256 `5dd34c6c702dcb204a2093560b89307bf374943d565c9d76206727140f6f9e38`.
- `Wasl-room-schema` — `9584099501` — SHA-256 `d1775c619dbd83745c0f61a2124b737ca7aeb67ad76889d64c00de3e5263eebf`.
- `Wasl-payment-receipt-evidence` — `9584290684` — SHA-256 `b94aed62d4e0f4b4e68803c6cb0eb63429f0fe197b2cd22c19d1232f1a5def79`.
- `Wasl-account-document-evidence` — `9584291121` — SHA-256 `b68101ddab87fcac7147bf84f894d9d213deda255c7615b3f759e473fc89b636`.
- `Wasl-room-instrumentation-results` — `9584291541` — SHA-256 `3864c9dfb4d02f483d214c48ffaf193b265ce40ef8b51f07f48ec3fc0d91a7cc`.
