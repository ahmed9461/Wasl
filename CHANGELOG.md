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

### Changed

- تثبيت AndroidX Core 1.18.0 وLifecycle 2.10.0 على compileSdk 36 بدل إدخال منصة Android 37 غير المستقرة.

### Fixed

- مواءمة إصدارات AndroidX مع منصة SDK المتاحة حتى تنجح اختبارات التطبيق وLint وتجميع APK في GitHub Actions.

### Security

- تعطيل cleartext traffic.
- تعطيل Android Auto Backup حتى بناء مسار Backup مشفر.
- تجاهل Keystores وملفات الأسرار في Git.
