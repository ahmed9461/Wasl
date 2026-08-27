# مرحلة «طالبني» — Payment Claims

آخر تحديث: 2026-08-27

الحالة: **التنفيذ موجود في Schema v8+ وUI/Today/Backup؛ الإغلاق النهائي ينتظر CI الأخضر للرأس الحالي.**

## الهدف

تسجيل أن الطرف الآخر طالب المستخدم بالسداد في حساب من نوع `PAYABLE`، مع تحديد موعد متابعة، من دون إنشاء دفعة أو تعديل الرصيد أو تاريخ الاستحقاق أو Ledger.

## القواعد المعتمدة

- الميزة متاحة للحسابات `PAYABLE` النشطة وغير المسددة فقط.
- كل مطالبة سجل تاريخي مستقل ولا تستبدل المطالبة السابقة.
- خيارات المتابعة:
  - اليوم: تاريخ اليوم.
  - غدًا: اليوم التالي.
  - عند الراتب: لا يخمن التطبيق تاريخًا.
  - تاريخ مخصص: يتطلب تاريخًا صالحًا غير ماضٍ وفق validation الحالي.
- الحالات: `ACTIVE`, `RESOLVED`, `CANCELLED`.
- أوامر الإنشاء والحسم idempotent عبر command IDs فريدة.
- إنشاء أو حسم مطالبة لا يغير أصل الدين أو الرصيد أو سجل الدفعات أو `due_date`.

## التخزين

- أضيفت في Room Schema v8 وتستمر في v9.
- الجدول: `payment_claims`.
- Migration: `v7→v8`.
- المطالبات مشمولة في Backup/Restore v9.
- Restore يفحص اتجاه الدين والـenums والتواريخ وحالات resolution.

## طبقة العرض والتنفيذ

موجود:

- `PaymentClaimViewModel.kt`.
- `PaymentClaimUi.kt`.
- `PaymentClaimStore` / `RoomPaymentClaimStore`.
- `PaymentClaimDao` / `PaymentClaimEntity`.
- integration في Account Details وToday.

## Evidence الموجودة

- `PaymentClaimModelsTest`.
- `PaymentClaimFollowUpResolverTest`.
- `PaymentClaimViewModelTest`.
- `PaymentClaimMigrationInstrumentedTest`.
- `PaymentClaimStoreInstrumentedTest`.
- `PaymentClaimAccountVisibilityInstrumentedTest`.
- `TodayPaymentClaimUiInstrumentedTest`.
- Backup/Restore validation داخل خدمة النسخ واختباراتها.

## بوابة الإكمال

لا تعتبر المرحلة Verified/Complete حتى:

1. ينجح Android instrumentation كاملًا على الرأس الحالي.
2. يثبت UI أن الإنشاء لا يظهر إلا في `PAYABLE` المفتوح.
3. تظهر المطالبات ذات المتابعة المستحقة في Today.
4. ينجح create/resolve/custom-date flow.
5. يثبت عدم تغير Ledger والرصيد.
6. Migration `7→8` خضراء.
7. Backup/Restore round-trip أخضر.
8. Unit + Lint + APK + Schema v9 + instrumentation كلها خضراء.

## الحالة التالية

المرفقات وصفحة الشخص لم تعودا مراحل مستقبلية: كلاهما منفذ بالفعل في الكود (Attachments في Schema v9 وPerson Timeline بلا Migration جديدة). بعد إغلاق CI الحالي تنتقل الأولوية إلى Accessibility/Adaptive audit ثم تقوية Natural/Voice Entry، وفق `docs/CURRENT_STATUS.md`.
