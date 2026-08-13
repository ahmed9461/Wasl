# خطة الاختبارات

## الهدف

إثبات صحة المال والاستمرارية والأمان، لا مجرد نجاح رسم الشاشة.

## طبقات الاختبار

### Unit — Domain

- Money arithmetic وOverflow.
- Currency mismatch.
- Debt state وDue state.
- Partial وFull payment.
- Overpayment.
- Reversal وDouble reversal.
- Duplicate IDs.
- Summary حسب العملة والاتجاه.

### Database

منفذة في Persistence slice الحالية:

- Entity constraints.
- DAO queries.
- Transaction ذرية.
- Idempotency command_id.
- Concurrent payments.
- Foreign keys.
- Replay يطابق Projection.
- Migration من كل Schema مدعوم.

### Repository integration

- Create person/debt.
- Restart وقراءة.
- Record/reverse payment.
- Error mapping.
- Cancellation لا يترك نصف كتابة.

### UI

- RTL وLTR isolation.
- أسماء طويلة وقيم كبيرة.
- Empty وError وLoading وSuccess.
- Back وCancel وkeyboard.
- Font scale وTalkBack.
- Compact وExpanded وDark.

### Platform

- Notification permission رفض وقبول.
- Inexact وExact fallback.
- Boot وTimezone.
- Biometric success/failure/lockout/key invalidation.
- FileProvider grants.
- PDF Arabic golden samples.

### Backup

- Round trip.
- Wrong passphrase.
- Corrupt header أو ciphertext.
- نسخة قديمة.
- مساحة غير كافية.
- فشل أثناء الاستبدال وRollback.

### End-to-End

السيناريو الإلزامي في SPEC قسم 22 على جهاز أو Emulator فعلي، مع Kill process وDevice restart حيث يلزم.

## اختبارات Foundation الحالية

| الملف | ما يثبته |
|---|---|
| MoneyTest | حساب صحيح، منع خلط العملات، Overflow |
| DebtLedgerTest | الأصل، الجزئي، النهائي، العكس، التكرار، الاستحقاق، ترتيب الأحداث، والنسخ الدفاعي للسجل |
| BalanceSummaryTest | فصل الاتجاهات والعملات |
| MoneyInputParserTest | Parsing دقيق للأرقام العربية والفواصل ودقة YER/SAR/USD دون Double |
| HomeViewModelTest | الشخص الجديد أو الموجود، اختيار Person ID وحد 20، أخطاء الإدخال، وثبات IDs عبر إعادة محاولة غير مؤكدة |
| TodayViewModelTest | LocalDate حسب ZoneId، الفصل والترتيب، عبور منتصف الليل، خطأ القراءة، ونتيجة Recovery |
| LocalSearchQueryTest | تطبيع المسافات، منع بحث فارغ غير محدود، ومعاملة محارف LIKE كنصوص حرفية |
| SearchViewModelTest | حد 50، وجود نتائج إضافية، خطأ القراءة وRetry، والتحديث Reactive بعد دين أو دفعة |
| ReminderTimeTest | 09:00 مدنيًا، الاستحقاق في اليوم نفسه، رفض الماضي، وإعادة بناء اللحظة عند تغير المنطقة |
| ReminderRecoveryPolicyTest | إبقاء BLOCKED دون إذن، وإعادة BLOCKED/FAILED إلى SCHEDULED، وتغير المنطقة |
| AccountDetailsViewModelTest | مراجعة الدفع، Overpayment قابل للتصحيح، العكس بسبب، وثبات Command عبر نتيجة غير مؤكدة |
| RoomWaslRepositoryInstrumentedTest | Restart، الذرية، Idempotency، تعدد ديون الشخص دون تكراره، منتقي الأشخاص المحدود، Overpayment، التزامن، Foreign keys، الإغلاق والعكس، واستعلاما Today والبحث |
| WaslDatabaseBaselineTest | Migration فعلية من Schema v1 إلى v2، حفظ بيانات الدين، وإنشاء reminders فارغة |
| PaymentFlowUiInstrumentedTest | إنشاء دين → دفع جزئي → إعادة فتح Room → بقاء المتبقي والدفعة في Timeline |
| DueDateUiInstrumentedTest | فتح تفاصيل الدين بـDeep link وعرض تاريخ الاستحقاق وموعد التذكير وحالته |
| TodayUiInstrumentedTest | التنقل إلى Today، فصل المتأخر/اليوم، استبعاد القادم، فتح الحساب، وأزرار الإذن/Retry |
| SearchUiInstrumentedTest | البحث بالوصف، فتح الحساب والرجوع إلى العبارة نفسها، وتحديث النتيجة بعد إنشاء دين ودفعة |
| ExistingPersonDebtUiInstrumentedTest | إنشاء دين أول، اختيار الشخص نفسه بالـID، إنشاء دين مستقل ثانٍ، بقاء Person واحد، وظهور الدينين في البحث |
| WorkManagerReminderSchedulerInstrumentedTest | تكرار الجدولة يترك Work delivery نشطًا واحدًا فقط |
| ReminderNotificationPublisherInstrumentedTest | إذن الإشعار وقناته وظهور Notification فعلية ذات tag ثابت وPendingIntent |

## CI

كل Push وPull Request يشغل:

- core:domain:test.
- app:testDebugUnitTest.
- app:lintDebug.
- app:assembleDebug.
- app:connectedDebugAndroidTest على Emulator API 35 بعد نجاح Job البناء.

لا يبنى Release موقعع في Foundation، ولا تحفظ مفاتيح توقيع في المستودع.

## بوابات الدمج

- لا اختبارات حمراء.
- لا Lint errors.
- Debug APK يبنى.
- اختبارات Room ورحلة الدفع على Android Emulator تنجح.
- Git diff مراجع.
- SPEC وHANDOFF محدثان.
- أي Test غير ممكن مذكور مع السبب والخطر.
