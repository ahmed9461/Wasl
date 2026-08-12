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

تضاف في Persistence slice:

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

## CI

كل Push وPull Request يشغل:

- core:domain:test.
- app:testDebugUnitTest.
- app:lintDebug.
- app:assembleDebug.

لا يبنى Release موقعع في Foundation، ولا تحفظ مفاتيح توقيع في المستودع.

## بوابات الدمج

- لا اختبارات حمراء.
- لا Lint errors.
- Debug APK يبنى.
- Git diff مراجع.
- SPEC وHANDOFF محدثان.
- أي Test غير ممكن مذكور مع السبب والخطر.
