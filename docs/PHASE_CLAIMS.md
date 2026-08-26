# المرحلة A — «طالبني» / مطالبات السداد

الحالة: **بدأت — التصميم الهندسي ومعايير القبول مثبتة، التنفيذ الوظيفي يلي تصحيحات 27 أغسطس مباشرة.**

المرجع: قسم «طالبني» في `WASL_MASTER_PROJECT_PROMPT.md`.

## الهدف

تسجيل أن صاحب الحق طالب المستخدم بالسداد في حساب من اتجاه `PAYABLE`، مع إبقاء هذا الحدث مستقلًا تمامًا عن الدفعات والرصيد. المطالبة لا تعني أن مبلغًا دُفع، ولا تعدّل تاريخ الدين الأصلي، ولا تنشئ Ledger entry.

## نموذج البيانات المقترح

`PaymentClaimRecord` كسجل تاريخي append-only/terminal-resolution:

- `id`
- `create_command_id`
- `debt_id`
- `claimed_at`
- `follow_up_kind`: `TODAY | TOMORROW | SALARY | CUSTOM`
- `follow_up_date` عند الحاجة
- `note` اختياري
- `status`: `ACTIVE | RESOLVED | CANCELLED`
- `resolution_command_id` اختياري
- `resolved_at` اختياري
- `resolution_note` اختياري
- `created_at`
- `updated_at`

لا يتم استخدام Boolean داخل جدول الدين لأن المطلوب الاحتفاظ بتاريخ كل مطالبة.

## الثوابت

1. يسمح بإنشاء مطالبة فقط لدين اتجاهه `PAYABLE` وغير مسدد بالكامل.
2. المطالبة لا تغيّر `Ledger` ولا `originalAmount` ولا `balance` ولا `due_date`.
3. `TODAY` و`TOMORROW` يشتقان تاريخ متابعة واضحًا من المنطقة الزمنية الحالية وقت الإنشاء.
4. `CUSTOM` يتطلب تاريخًا صريحًا غير ماضٍ.
5. `SALARY` يبقى اختيارًا معنويًا حتى يحدد المستخدم سياسة/تاريخ الراتب؛ لا نخمن تاريخ راتب من عندنا.
6. كل create/resolve command idempotent.
7. الحذف الفيزيائي غير مستخدم كسلوك عادي؛ الحسم يحفظ الحالة التاريخية.
8. أي Reminder ناتج عن المطالبة منفصل عن الدفتر المالي، وفشله لا يلغي حفظ المطالبة.

## واجهة الحساب

للحساب `PAYABLE` المفتوح:

- زر «طالبني».
- عند الضغط: عرض وقت المطالبة الحالي وخيارات:
  - سأسدد اليوم.
  - غدًا.
  - عند الراتب.
  - تاريخ مخصص.
- ملاحظة اختيارية.
- Preview/تأكيد قبل الحفظ.
- بعد الحفظ تظهر المطالبة في سجل الحساب ولا تختلط مع الدفعات.

## Today

- المطالبة النشطة ترفع أولوية الحساب.
- تعرض سبب الأولوية بوضوح: «طالبك بالسداد».
- تعرض موعد المتابعة المختار إذا كان محددًا.
- الإجراءات المالية تبقى مسارات مراجعة داخل التطبيق؛ لا auto-payment.

## التذكيرات

- عند وجود تاريخ متابعة فعلي، يمكن جدولة General Reminder/آلية منفصلة مع subject مناسب.
- لا Exact Alarm افتراضيًا لمجرد تسجيل المطالبة.
- لا إشعار إذا سُدد الحساب بالكامل قبل الموعد.

## Room / Migration

- Schema التالية: v8.
- جدول مستقل `payment_claims` مع FK إلى `debts` وindexes للـdebt/status/follow-up date/command IDs.
- Migration `7 -> 8` فقط، دون destructive migration.
- تحديث exported schema والتحقق في CI.

## Backup / Restore

يجب إضافة المطالبات إلى النسخة المنطقية المشفرة، والتحقق عند الاستعادة من:

- FK إلى debt موجود.
- enum values معروفة.
- التواريخ والحالات متسقة.
- command IDs غير متعارضة.
- `CUSTOM` لديه تاريخ.
- terminal status لديه `resolved_at`.

## الاختبارات المطلوبة قبل اعتبار المرحلة مكتملة

- Domain/model validation.
- DAO/Room insert/query/idempotency.
- Migration 7→8.
- Repository/Store create + resolve/cancel.
- رفض claim على `RECEIVABLE` أو دين مسدد.
- إثبات عدم تغيّر Ledger والرصيد.
- Today priority/query.
- UI: إنشاء مطالبة، تاريخ مخصص، إلغاء، عودة/Rotation.
- Backup/Restore round-trip.
- Lint + Debug build + instrumentation.

## تعريف الاكتمال

لا تُعلّم هذه المرحلة Complete إلا بعد نجاح البوابة كاملة وتحديث:

- `docs/CURRENT_STATUS.md`
- `HANDOFF.md`
- `CHANGELOG.md`
- Room schema exported JSON
- أي ADR عند ظهور قرار معماري جديد.
