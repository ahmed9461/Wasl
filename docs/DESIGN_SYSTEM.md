# نظام تصميم وَصل

آخر تحديث: 2026-08-28

الحالة: **Final visual foundation قيد التحقق على فرع الإنهاء.**

## الشخصية

هادئ، موثوق، إنساني، واضح. لا يشبه لوحة محاسبة شركات، ولا يستخدم ألوانًا صارخة أو ازدحامًا. الأولوية دائمًا لقراءة المال والحالة والإجراء بأقل جهد بصري.

## الألوان

### Light

| الدور | القيمة |
|---|---|
| Primary | `#0F766E` |
| On Primary | `#FFFFFF` |
| Primary Container | `#CCFBF1` |
| On Primary Container | `#134E4A` |
| Secondary | `#475569` |
| Secondary Container | `#E2E8F0` |
| Tertiary | `#B45309` |
| Tertiary Container | `#FFEDD5` |
| Background | `#F8FAF9` |
| Surface | `#FFFFFF` |
| Text Primary | `#0F172A` |
| Text Secondary | `#475569` |
| Error | `#B42318` |

### Dark

| الدور | القيمة |
|---|---|
| Primary | `#5EEAD4` |
| On Primary | `#042F2E` |
| Primary Container | `#115E59` |
| On Primary Container | `#CCFBF1` |
| Secondary | `#94A3B8` |
| Secondary Container | `#334155` |
| Tertiary | `#FDBA74` |
| Tertiary Container | `#7C2D12` |
| Background | `#0B1215` |
| Surface | `#111A1E` |
| Text Primary | `#E2E8F0` |
| Text Secondary | `#CBD5E1` |

الألوان الدلالية لا تعمل وحدها: «مسدد»، «مفتوح»، «متأخر» ونحوها يجب أن تبقى مصحوبة بنص/رمز واضح.

## Contrast baseline

الأزواج الأساسية في final palette اختيرت لتتجاوز 4.5:1 للنص العادي. أمثلة محسوبة قبل إدخال الدفعة:

- Light primary / onPrimary: **5.47:1**.
- Light primaryContainer / onPrimaryContainer: **8.41:1**.
- Light secondary / onSecondary: **7.58:1**.
- Light background / onBackground: **17.03:1**.
- Dark primary / onPrimary: **9.78:1**.
- Dark primaryContainer / onPrimaryContainer: **6.73:1**.
- Dark background / onBackground: **15.32:1**.

هذا لا يلغي فحص accessibility على الأجهزة؛ هو baseline للـtokens فقط.

## Typography

- Foundation يستخدم `SansSerif` النظامي كي لا يضيف خطًا غير مرخص أو اعتمادًا خارجيًا قبل Release.
- جميع مستويات النص المستخدمة في التطبيق معرفة صراحة داخل `WaslTypography`، بما فيها `bodySmall`, `titleSmall`, `labelMedium`, `labelSmall`.
- لا negative letter spacing للنص العربي.
- line height أوسع من Foundation القديم لتحمل تشكيل العربية وFont Scale بدون تداخل.
- Display للهوية فقط.
- Title للعناوين والمبالغ المهمة.
- Body للنصوص.
- Label للإجراءات والmetadata.

قبل Release النهائي يمكن تقييم خط عربي مرخص ومضمن فقط إذا أثبتت الأجهزة أن خط النظام غير متسق، مع إعادة بوابة accessibility/PDF كاملة.

## Shapes

سلم موحد:

- extraSmall: 8dp.
- small: 12dp.
- medium: 18dp.
- large: 24dp.
- extraLarge: 32dp.

الهدف هو مظهر حديث وواضح بدون تحويل كل العناصر إلى كبسولات مبالغ فيها.

## المسافات

شبكة 4dp:

- 4: قرب شديد.
- 8: بين عناصر مرتبطة.
- 12: مجموعات صغيرة.
- 16: padding اعتيادي.
- 20/24: بطاقات وأقسام رئيسية.
- 32: فصل أقسام كبيرة.

Touch target لا يقل عن 48dp.

## البطاقات والمبالغ

- عنوان ثم محتوى، دون فراغات ضخمة.
- كل عملة في سطر مستقل عند الملخصات متعددة العملات.
- Amount + Currency يعزلان LTR داخل RTL.
- الأصل والمدفوع والمتبقي لهم تسلسل ثابت.
- لا اختصار لقيمة مالية يخفي الدقة في شاشة تفاصيل أو مستند.
- surface containers تستخدم لبناء hierarchy هادئ بدل borders ثقيلة.

## الحالات

- Empty: يشرح ما سيظهر وكيف يبدأ المستخدم.
- Loading: لا يحجب بيانات محلية قديمة دون حاجة.
- Error: يشرح الفشل ويحافظ على الإدخال ويقدم Retry.
- Success: يؤكد العملية والمبلغ والعملة والشخص.
- Destructive: سبب واضح وتأكيد، مع Reverse/Archive عند ملاءمته.

## النماذج

- الحقول الأساسية أولًا.
- الخيارات المتقدمة قابلة للتوسيع.
- لوحة أرقام مناسبة للمبلغ.
- Parsing يعرض القيمة قبل الحفظ.
- التاريخ والوعد والتذكير مفاهيم منفصلة.
- أي إدخال Natural/Voice يمر عبر Preview/Confirmation.
- زر الحفظ يمنع النقر المكرر، وData command نفسه Idempotent.

## الوصول

- RTL هو الاتجاه الأساسي.
- `ltrIsolate()` للمال/العملات/التواريخ/المعرفات اللاتينية عند الحاجة.
- Font Scale حتى 200% دون فقد إجراء أساسي.
- الصفوف المزدحمة تستخدم adaptive stacking بدل ضغط النص.
- Content descriptions للأيقونات ذات المعنى.
- ترتيب TalkBack يطابق التسلسل البصري.
- contrast يراجع عند كل تغيير palette جديد.

## قواعد مرحلة Final Polish

1. لا تغييرات سلوكية/مالية ضمن دفعة بصرية فقط.
2. الحفاظ على testTags والsemantics الحالية إلا إذا أضيف اختبار بديل أوضح.
3. التلميع يتم بدفعات صغيرة: theme → شاشات عالية الظهور → dialogs → PDFs.
4. كل دفعة تمر Unit/Lint/APK/Room/Emulator/PDF gate قبل دمجها في فرع PR.
5. لا يعتبر الشكل النهائي معتمدًا فقط لأنه compile؛ الـCI والاختبارات الوظيفية يجب أن تبقى خضراء.
