# HANDOFF — الحالة الحية

آخر تحديث: 2026-08-12

## المرحلة والإصدار

- المرحلة: Foundation / MVP Phase 1
- الإصدار: 0.1.0-dev
- الفرع: agent/bootstrap-wasl-foundation

## آخر ما تم تنفيذه

- تثبيت المرجع التأسيسي في جذر المستودع.
- إنشاء ملفات ذاكرة المشروع والوثائق الهندسية.
- اختيار Stack موثق من مصادر Android الرسمية الحالية.
- إنشاء Android skeleton بـCompose وRTL وLight/Dark.
- إنشاء وحدة core:domain للمال وسجل الديون.
- إضافة اختبارات قواعد الرصيد والسداد والعكس والعملات.
- إضافة GitHub Actions للبناء والاختبار وLint.

## ما يعمل الآن

- إنشاء DebtLedger برصيد أصلي ثابت.
- إضافة دفعة جزئية أو نهائية بعملة مطابقة.
- رفض الدفعة الزائدة أو العملة المختلفة.
- عكس دفعة مرة واحدة دون حذف التاريخ.
- اشتقاق OPEN وPARTIALLY_PAID وSETTLED.
- اشتقاق حالة الاستحقاق.
- فصل إجماليات YER وSAR وUSD واتجاه لي/عليّ.
- عرض واجهة تأسيسية عربية دون إجراءات مالية زائفة.

## آخر تحقق

- نجحت اختبارات core:domain محليًا: 16/16 بلا فشل.
- نجح Gradle في تهيئة وحدة app وإظهار مهام Android، ما يتحقق من Build scripts وPlugins.
- لم يُشغّل Android compile أو Lint محليًا لعدم وجود Android SDK 36 في البيئة.
- نتيجة GitHub Actions الكاملة ما زالت Pending حتى رفع الفرع.

## غير منفذ

- Room persistence وMigrations.
- واجهات الأشخاص وإنشاء الدين والدفعات.
- Reminders وNotifications وAlarmManager.
- المستندات وPDF.
- Backup/Restore.
- PIN/Biometric.

## قيود معروفة

- الشاشة الحالية تأسيسية وليست واجهة MVP النهائية.
- لا يوجد حفظ بيانات حتى الآن.
- تعطيل Android Auto Backup مقصود حتى يتوفر Backup مشفر يتحقق من سلامته.
- لا يوجد Release signing.

## المخاطر

- يجب إثبات تكامل AGP 9.3.1 وKotlin 2.3.21 وCompose في CI.
- اختيار Room 2.8.4 يحتاج تنفيذ Schema v1 واختبارات Migration قبل أي UI مالية.
- PDF يحتاج نموذجًا عربيًا فعليًا قبل تثبيت تفاصيل القالب.

## الخطوة التالية

تنفيذ Persistence Slice كامل: Room Schema v1 للأشخاص والديون وledger entries، Repository ذري، Migration baseline، واختبارات إغلاق التطبيق وإعادة القراءة؛ ثم فقط تفعيل أول مسار UI لإنشاء شخص ودين.
