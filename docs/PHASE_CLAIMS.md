# المرحلة A — «طالبني» / مطالبات السداد

آخر تحديث: 2026-08-27

الحالة: **منفذة وظيفيًا في الكود وSchema v8+؛ تنتظر نجاح بوابة Android instrumentation الكاملة على الرأس الحالي قبل إعلان Complete النهائي.**

المرجع الأعلى: قسم «طالبني» في `WASL_MASTER_PROJECT_PROMPT.md`.

## الهدف

تسجيل أن صاحب الحق طالب المستخدم بالسداد في حساب من اتجاه `PAYABLE`، مع إبقاء المطالبة مستقلة تمامًا عن Ledger والدفعات والرصيد وتاريخ الاستحقاق الأصلي.

## التنفيذ الحالي

تم تنفيذ:

- `PaymentClaimRecord` ونماذج الأوامر والحالات.
- `PaymentClaimStore` و`RoomPaymentClaimStore`.
- `PaymentClaimDao` و`PaymentClaimEntity`.
- Migration `7→8` وجدول `payment_claims`.
- `PaymentClaimViewModel` وواجهة الحساب.
- خيارات `TODAY / TOMORROW / SALARY / CUSTOM`.
- الحالات `ACTIVE / RESOLVED / CANCELLED`.
- إظهار المطالبة النشطة ضمن Today وأولوية المتابعة.
- Backup/Restore للصفوف والتحقق من ثوابتها.
- اختبارات Models/Resolver/ViewModel/Store/Migration/Account visibility/Today UI.

## نموذج البيانات

- `id`
- `create_command_id`
- `debt_id`
- `claimed_at`
- `follow_up_kind`: `TODAY | TOMORROW | SALARY | CUSTOM`
- `follow_up_date_epoch_day` اختياري حسب النوع
- `note` اختياري
- `status`: `ACTIVE | RESOLVED | CANCELLED`
- `created_at`
- `resolution_command_id` اختياري للحالة النشطة وإلزامي للحالة النهائية
- `resolved_at` اختياري للحالة النشطة وإلزامي للحالة النهائية
- `resolution_note` اختياري
- `updated_at`

## الثوابت

1. يسمح بإنشاء Claim فقط لدين `PAYABLE` غير مسدد بالكامل.
2. Claim لا تغير Ledger أو `originalAmount` أو balance أو `due_date`.
3. `TODAY` و`TOMORROW` يشتقان تاريخ متابعة من المنطقة الزمنية الحالية وقت الإنشاء.
4. `CUSTOM` يتطلب تاريخًا صريحًا صالحًا.
5. `SALARY` لا يخمن تاريخ راتب؛ يبقى اختيارًا معنويًا حتى توجد سياسة صريحة.
6. create/resolve/cancel commands Idempotent.
7. لا حذف فيزيائي كسلوك عادي؛ الحالة التاريخية محفوظة.
8. أي Reminder مرتبط بالمتابعة يبقى أداة scheduling لا عملية مالية.

## واجهة الحساب وToday

- زر «طالبني» يظهر للحساب `PAYABLE` المفتوح.
- المستخدم يحدد متابعة وملاحظة اختيارية ثم يراجع قبل الحفظ.
- Claim تظهر في سجل الحساب منفصلة عن الدفعات.
- Today يعرض سبب الأولوية وموعد المتابعة عند توفر تاريخ فعلي.
- لا auto-payment من Claim أو Today.

## Room / Backup

- Schema الإضافة: v8.
- Migration `7→8` فقط دون destructive migration.
- Backup v9 الحالي يشمل `payment_claims`.
- Restore يتحقق من FK والاتجاه والـenums والتواريخ والحالات وcommand IDs.

## Evidence الموجود في المستودع

- `PaymentClaimMigrationInstrumentedTest.kt`
- `PaymentClaimStoreInstrumentedTest.kt`
- `PaymentClaimAccountVisibilityInstrumentedTest.kt`
- `TodayPaymentClaimUiInstrumentedTest.kt`
- `PaymentClaimViewModelTest.kt`
- `PaymentClaimModelsTest.kt`
- `PaymentClaimFollowUpResolverTest.kt`

## تعريف الاكتمال

التنفيذ الوظيفي موجود، لكن لا توسم المرحلة **Complete/Verified** إلا بعد:

1. نجاح Unit/Lint/Debug build.
2. نجاح Migration/Store/UI instrumentation على الرأس الحالي.
3. نجاح Backup/Restore regression الذي يشمل Claims.
4. عدم تغير Ledger أو الرصيد في اختبارات الثوابت.
5. تحديث `CURRENT_STATUS.md`, `PROJECT_CONTEXT.md`, `HANDOFF.md`, `CHANGELOG.md` وSchema docs.

Android CI #851 أثبت `verify` وSchema v9، لكنه توقف قبل instrumentation بسبب imports Compose test غير مرتبطة بمنطق Claims. الإصلاح الحالي يستهدف إعادة البوابة كاملة إلى الأخضر.
