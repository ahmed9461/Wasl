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

### Security

- تعطيل cleartext traffic.
- تعطيل Android Auto Backup حتى بناء مسار Backup مشفر.
- تجاهل Keystores وملفات الأسرار في Git.
