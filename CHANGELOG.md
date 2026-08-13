# سجل التغييرات

جميع التغييرات المهمة في وَصل تسجل هنا.

## Unreleased

### Added

- المرجع التأسيسي الدائم للمشروع.
- وثائق السياق والمواصفات والقرارات والتسليم.
- Android project skeleton بـKotlin وJetpack Compose.
- Theme عربي RTL في الوضعين الفاتح والداكن.
- وحدة Domain مستقلة للأموال والديون.
- سجل دفعات Append-only مع عكس موثق.
- اشتقاق مركزي للأرصدة والحالات حسب العملة والاتجاه.
- Unit tests لقواعد المال والسداد والعكس.
- GitHub Actions للبناء والاختبارات وLint.
- Room 2.8.4 Schema v1 للأشخاص والديون وLedger append-only مع Schema JSON مصدّر.
- Repository ذري لإنشاء الشخص والدين وتسجيل الدفعات وعكسها مع Idempotency وإغلاق مشتق.
- أول مسار Compose فعلي لإنشاء شخص ودين وعرض الحسابات والإجماليات المحفوظة.
- Parsing دقيق لمبالغ YER وSAR وUSD مع الأرقام العربية دون Floating Point.
- اختبارات Room على Android Emulator للإغلاق وإعادة الفتح والقيود والتزامن وMigration baseline.

### Changed

- تثبيت AndroidX Core 1.18.0 وLifecycle 2.10.0 على compileSdk 36 بدل إدخال منصة Android 37 غير المستقرة.
- أصبحت الشاشة التأسيسية تقرأ من Room وتعرض حالات Loading وEmpty وError وSuccess.

### Fixed

- مواءمة إصدارات AndroidX مع منصة SDK المتاحة حتى تنجح اختبارات التطبيق وLint وتجميع APK في GitHub Actions.
- إبقاء معرفات أمر الإنشاء ثابتة عبر إعادة المحاولة لمنع إنشاء دين مكرر بعد نتيجة حفظ غير مؤكدة.

### Security

- تعطيل cleartext traffic.
- تعطيل Android Auto Backup حتى بناء مسار Backup مشفر.
- استبعاد جميع نطاقات Cloud backup وDevice transfer صراحةً عبر قواعد Android الحديثة والقديمة.
- تجاهل Keystores وملفات الأسرار في Git.
