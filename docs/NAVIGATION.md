# معمارية التنقل

آخر تحديث: 2026-08-27

الحالة: Navigation 3 هو المسار الرئيسي داخل التطبيق، مع وجهات إضافية مستقلة لبعض الأدوات المحمية. التوثيق القديم الذي حصر التنفيذ في Home/Today/Search/Account Details لم يعد صحيحًا.

## المسارات الحالية

### داخل `WaslApp` / Navigation 3

- Home / الحسابات.
- Today.
- Search الأساسي والمتقدم.
- Account Details بالـ`debtId` فقط.
- Installments Hub.
- Documents Hub.
- Settings Hub.
- Security Hub.

### مسارات/Activities مساندة

- General Reminders Hub من الإعدادات.
- Person Timeline لعرض الشخص وحساباته.
- Statistics.
- Natural Entry، ويتضمن إدخالًا نصيًا وإملاءً صوتيًا أساسيًا.

تظل البيانات المالية نفسها مقروءة من Repository بالـIDs؛ لا تحمل Routes مبالغ أو Snapshots كبيرة كمصدر حقيقة.

## القواعد

- Navigation keys صريحة وقابلة للتسلسل عند استخدامها في Navigation 3.
- Back stack يملكه App state واحد للمسارات الرئيسية.
- التفاصيل تقرأ Reactive من Repository بالـID.
- Saved state لا يحمل ملفات أو بيانات مالية كبيرة.
- System back هو المرجع؛ لا ينشأ زر رجوع يناقضه.
- أي Activity مساندة محمية لا تستخدم كمسار لتجاوز App Lock.
- `GeneralRemindersHubActivity` غير مصدرة وتستخدم `noHistory` حتى لا تبقى مدخلًا جانبيًا بعد مغادرة التطبيق.

## التدفقات المنفذة

- Home → Account Details.
- Today → Account Details → Payment review/confirm.
- Search → نتيجة → Account Details مع بقاء عبارة البحث.
- Installments Hub → Account Details.
- Notification body → Account Details.
- Notification partial/full action → Payment path داخل التطبيق، بلا auto-submit.
- Settings → Documents / Security / General Reminders حسب الخيار.
- Account Details → Person Timeline.
- Account Details → Attachment vault/actions.
- Natural Entry text → Parsed Draft → Preview/Confirmation → Repository.
- Natural Entry voice → Android Recognizer UI → recognized text → نفس Parsed Draft/Preview/Confirmation.

## الأشخاص

صفحة الشخص منفذة عبر `PersonTimelineActivity/Screen/ViewModel`:

- تعرض بيانات الشخص.
- تجمع حساباته مع إبقاء كل عملة منفصلة.
- تعرض Timeline عبر حساباته.
- تفتح حسابًا محددًا بالـDebt ID.

لا يعتمد الربط على الاسم؛ `PersonId` هو المرجع.

## المستندات والمرفقات

- Documents Hub يعرض المستندات المالية ويفتح/يشارك بعد فحص السلامة.
- Account Details يربط المستندات بالحساب المعني بدل تصدير عام مبهم.
- Attachments ترتبط بالدين وبحركة اختيارية، وتفتح/تشارك عبر مسار ملف آمن.

## Deep links وNotifications

- Intent يحمل IDs داخلية فقط وبيانات UI غير موثوقة لا تستخدم كمصدر مالي.
- Repository يتحقق من الهدف ويعيد الحالة الحالية.
- PendingIntents immutable حيث يلزم.
- `PARTIAL` / `FULL` من الإشعار يفتحان مسار الدفع مع Preview/Confirmation.
- «ذكرني لاحقًا» يغير scheduling فقط ولا يغير Ledger أو due date.

## Natural / Voice Entry

المسار النصي:

`Text → Parser → Draft → Preview → Confirmation → Repository`

الإملاء الصوتي الأساسي منفذ حاليًا:

`Voice → RecognizerIntent result → Text → Parser → Draft → Preview → Confirmation → Repository`

المتبقي هو جعل Voice adapter/result handling قابلًا للاختبار بصورة معزولة وتغطية unavailable/cancel/empty result. لا يسمح بأي مسار Voice أو AI يكتب Payment/Debt مباشرة دون confirmation.

## التكيف

المبدأ المستهدف:

- Compact: Bottom navigation للوجهات الأعلى استخدامًا، والبقية من More/Settings.
- Medium: Navigation rail عند ملاءمة الشاشة.
- Expanded: Rail/Drawer مع إمكان pane إضافي عند القوائم/التفاصيل.

Adaptive coverage بدأ لكنه غير مغلق بعد. لا تغير هوية الوجهات أو قدراتها بسبب عرض الشاشة؛ يتغير التركيب البصري فقط.

## Back stack / State

- العودة من Account Details تعيد المستخدم إلى السياق الذي فتحه متى كان داخل نفس stack.
- Search state وquery لا يعاد إنشاؤهما دون حاجة عند العودة.
- النماذج المالية ذات تغييرات غير محفوظة يجب ألا تضيع بصمت.
- Process recreation يعيد بناء الشاشة من IDs والحالة الدائمة، لا من Objects مالية كبيرة داخل route.

## ما تبقى

- توحيد بعض Activities المساندة مع Navigation 3 عندما يعطي ذلك فائدة حقيقية دون كسر الحماية أو state restoration.
- اختبار Compact/Medium/Expanded أوسع.
- Process recreation وback-stack regression لكل مسار جديد.
- TalkBack focus order والتنقل عبر الشاشات الكبيرة.
- Voice input adapter/test seam دون الاعتماد على recognizer خارجي في CI.
