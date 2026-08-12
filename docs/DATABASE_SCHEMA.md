# تصميم قاعدة البيانات

الحالة: تصميم Schema v1 قبل التنفيذ
المحرك المخطط: Room 2.8.4 فوق SQLite
المبدأ: Ledger هو مصدر الحقيقة، والجداول المشتقة Projections فقط.

## قواعد عامة

- IDs نصية ثابتة مثل UUID.
- Instant يخزن epoch milliseconds بصيغة INTEGER.
- LocalDate يخزن epoch day بصيغة INTEGER.
- Money يخزن amount_minor INTEGER وcurrency_code TEXT بطول 3.
- Foreign keys مفعلة.
- لا Cascade delete لسجل مالي.
- كل Migration تصدر Schema JSON وتملك اختبارًا.
- لا تخزن صور أو PDF كبيرة داخل BLOB؛ تخزن ملفات خاصة بالتطبيق وMetadata في DB.

## persons

| العمود | النوع | القيد |
|---|---|---|
| id | TEXT | Primary key |
| display_name | TEXT | غير فارغ |
| phone | TEXT | اختياري |
| email | TEXT | اختياري |
| photo_uri | TEXT | اختياري، URI داخلي |
| notes | TEXT | اختياري |
| created_at | INTEGER | مطلوب |
| updated_at | INTEGER | مطلوب |
| archived_at | INTEGER | اختياري |

Indexes:

- display_name للبحث.
- phone عند اعتماد Normalization واضح.
- archived_at لتصفية النشط.

## debts

| العمود | النوع | القيد |
|---|---|---|
| id | TEXT | Primary key |
| person_id | TEXT | FK persons، Restrict |
| direction | TEXT | RECEIVABLE أو PAYABLE |
| original_amount_minor | INTEGER | أكبر من صفر |
| currency_code | TEXT | ثلاث حروف |
| opened_at | INTEGER | مطلوب |
| due_date_epoch_day | INTEGER | اختياري |
| description | TEXT | اختياري |
| notes | TEXT | اختياري |
| lifecycle_state | TEXT | ACTIVE أو ARCHIVED أو VOID |
| created_at | INTEGER | مطلوب |
| updated_at | INTEGER | مطلوب |
| closed_at | INTEGER | Projection اختياري، يجب مطابقته للLedger |

Indexes:

- person_id مع opened_at.
- lifecycle_state مع due_date_epoch_day.
- currency_code مع direction.

لا يخزن current_balance كمصدر حقيقة. إذا أضيف Projection للأداء فيكون قابلًا لإعادة البناء وتوجد مقارنة آلية مع Replay.

## ledger_entries

| العمود | النوع | القيد |
|---|---|---|
| id | TEXT | Primary key |
| command_id | TEXT | Unique وIdempotency |
| debt_id | TEXT | FK debts، Restrict |
| kind | TEXT | PAYMENT أو PAYMENT_REVERSAL |
| amount_minor | INTEGER | مطلوب وموجب للدفع، null للعكس |
| currency_code | TEXT | مطلوب للدفع ومطابق للدين |
| occurred_at | INTEGER | وقت السداد للدفع |
| recorded_at | INTEGER | وقت التسجيل، مطلوب |
| reverses_entry_id | TEXT | FK ledger_entries للدفع المعكوس |
| note | TEXT | اختياري |
| reason | TEXT | مطلوب للعكس |
| sequence_number | INTEGER | متزايد داخل الدين |

Constraints منطقية داخل Transaction وDomain:

- ID وcommand_id فريدان.
- Payment لا يتجاوز الرصيد في MVP.
- Reversal يشير إلى Payment سابق في الدين نفسه.
- Unique على reverses_entry_id غير الفارغ يمنع عكس الدفعة مرتين.
- recorded_at للعكس لا يسبق Payment.
- لا Update أو Delete لصف مالي في التشغيل العادي.

Index: debt_id مع sequence_number فريد.

## debt_balance_projection

جدول اختياري يبدأ فقط إذا أثبتت القياسات حاجة الأداء.

| العمود | النوع | القيد |
|---|---|---|
| debt_id | TEXT | Primary key |
| balance_minor | INTEGER | من صفر إلى الأصل |
| last_sequence_number | INTEGER | آخر حدث مطبق |
| calculated_at | INTEGER | مطلوب |

يحدث في Transaction نفسها ويُعاد بناؤه من Ledger. اختبار Integrity يقارن عينات أو كل البيانات عند Backup وMigration.

## promises

| العمود | النوع | الوصف |
|---|---|---|
| id | TEXT | Primary key |
| debt_id | TEXT | FK |
| promised_for | INTEGER | Instant أو LocalDate حسب UX المعتمد |
| status | TEXT | UPCOMING، FULFILLED، MISSED، CANCELLED |
| fulfilled_payment_id | TEXT | FK اختياري |
| created_at | INTEGER | مطلوب |
| resolved_at | INTEGER | اختياري |
| note | TEXT | اختياري |

لا يستبدل Promise جديد القديم.

## reminders

| العمود | النوع | الوصف |
|---|---|---|
| id | TEXT | Primary key |
| subject_type | TEXT | DEBT، PROMISE، INSTALLMENT، CLAIM |
| subject_id | TEXT | معرف الهدف |
| schedule_type | TEXT | WORK، INEXACT، EXACT |
| trigger_at | INTEGER | Instant |
| zone_id | TEXT | Timezone IANA |
| repeat_rule | TEXT | صيغة موثقة أو null |
| status | TEXT | SCHEDULED، DELIVERED، SNOOZED، CANCELLED، FAILED |
| platform_request_code | INTEGER | Unique عند الحاجة |
| updated_at | INTEGER | مطلوب |

إعادة الجدولة Idempotent بحسب reminder id.

## installments

- id Primary key.
- debt_id FK.
- installment_number.
- planned_amount_minor.
- currency_code مطابق للدين.
- due_date_epoch_day.
- lifecycle state.
- Unique debt_id مع installment_number.

دفعات الأقساط لا تنشئ مصدر مال منفصلًا؛ تحتاج جدول allocation يربط Ledger payment بقسط ومبلغ موزع.

## attachments

- id.
- debt_id.
- ledger_entry_id اختياري.
- internal_uri.
- original_name.
- mime_type.
- size_bytes.
- sha256.
- created_at.
- archived_at اختياري.

Hash يستخدم للتحقق من سلامة الملف لا لكشف تشابه معلومات المستخدم خارجيًا.

## document_identities

- id.
- display_name.
- service_or_business_name.
- phone، email، address.
- logo_uri، stamp_uri، signature_uri.
- primary_color.
- footer_text.
- created_at، updated_at، archived_at.

## documents

- id.
- document_number Unique.
- type.
- debt_id اختياري.
- ledger_entry_id اختياري.
- person_id.
- currency_code وamount fields اللازمة.
- issued_at.
- template_key وtemplate_version.
- identity_snapshot_json.
- financial_snapshot_json.
- pdf_uri.
- pdf_sha256.
- verification_token_hash اختياري.
- status.

Snapshots Versioned ولا تعاد قراءتها من الهوية الحالية عند عرض نسخة تاريخية.

## audit_events

للأحداث غير الممثلة في Ledger:

- id.
- aggregate_type وaggregate_id.
- event_type.
- occurred_at.
- actor LOCAL_USER في MVP.
- before_snapshot اختياري ومحدود.
- after_snapshot اختياري ومحدود.
- reason.

لا يخزن Secret أو ملفًا أو بيانات أكثر من اللازم.

## Transactions المطلوبة

- إنشاء شخص ودين عند المسار المركب.
- تسجيل دفعة وإغلاق Projection.
- عكس دفعة وإعادة فتح Projection.
- إصدار رقم مستند وحفظ Snapshot.
- تعديل جدول أقساط.
- Restore النهائي.

## Migration policy

1. لا تستخدم destructiveMigration في Production.
2. كل Version له exported schema.
3. Migration test يبدأ من أقدم إصدار مدعوم إلى الحالي.
4. بعد Migration يعاد Replay للأرصدة وتفحص Foreign keys وUnique constraints.
5. Backup قبل Migration الكبرى عند توفر مساحة، دون تخزين نسخة غير مشفرة خارجيًا.
