# نشر المشروع في معرض الأعمال: مستقل وUpwork

## الصور الجاهزة (1600×1200، نسبة 4:3)

نسختان بالأسماء نفسها: **العربية** في هذا المجلد (لمستقل)، و**الإنجليزية** في مجلد `en/` (لـ Upwork).

| الملف | المحتوى | الاستخدام |
|---|---|---|
| `01-cover.png` | الغلاف: الاسم والفكرة والمنصات | **صورة الغلاف** |
| `02-how-it-works.png` | مراحل العمل الخمس | |
| `03-dashboard.png` | لوحة التحكم | |
| `04-features.png` | المميزات | |
| `05-reliability.png` | الموثوقية والأمان | |
| `06-tech-stack.png` | التقنيات والبنية | مهمة لعملاء البرمجة |

> لوحة التحكم في الصور تصميم توضيحي وليست لقطة شاشة حقيقية. أضف معها لقطتين أو ثلاثًا حقيقية من التطبيق (Win + Shift + S) بعد إخفاء مفاتيح API وأسماء مدوناتك الحقيقية. اللقطات الحقيقية تقنع العميل أكثر.
> يمكنك تعديل النصوص في `slides.html` (العربية) أو `slides-en.html` (الإنجليزية) ثم إعادة توليد الصور (الأمر في آخر الملف).

---

## 1) مستقل (mostaql.com)

**الخطوات:** الملف الشخصي ← **أعمالي** ← **أضف عملًا جديدًا**

- **عنوان العمل:**
  تطبيق سطح مكتب لنشر المقالات تلقائيًا على Blogger وWordPress بالذكاء الاصطناعي
- **صورة العمل (الغلاف):** `01-cover.png`
- **ملفات العمل:** ارفع الصور من 02 إلى 06 بالترتيب، ثم اللقطات الحقيقية.
- **المهارات:** Java، JavaFX، برمجة تطبيقات سطح المكتب، الذكاء الاصطناعي، OpenAI API، WordPress، Blogger، أتمتة
- **رابط العمل:** رابط GitHub إن كان المستودع عامًا، أو فيديو قصير على YouTube (غير مدرج).
- **تاريخ الإنجاز:** تاريخ آخر إصدار (1.1.1).

**وصف العمل** (انسخه كما هو):

```
تطبيق سطح مكتب لنظام Windows يؤتمت نشر المقالات على مدونات Blogger ومواقع WordPress.

ترفع ملف Excel فيه قائمة العناوين، والتطبيق يعالج كل عنوان كالتالي:
• يكتب المقال بالذكاء الاصطناعي (OpenAI أو Gemini أو Claude)، بالعربية أو الإنجليزية
• يفحص جودة المقال ويعيد توليده تلقائيًا إن كان ناقصًا
• يضيف روابط داخلية «اقرأ أيضًا» لمقالات الموقع السابقة، ووصف SEO ووسومًا على WordPress
• يجلب صورة رئيسية من Unsplash أو يولّدها بالذكاء الاصطناعي
• ينشر المقال (أو يحفظه مسودة) ويسجل رابطه وحالته

مميزات أخرى:
• إدارة عدة مدونات ومواقع من قائمة واحدة، مع إعدادات مستقلة لكل موقع
• منع نشر العنوان نفسه مرتين على الموقع نفسه
• طابور نشر يكمل من حيث توقف إن أُغلق التطبيق في منتصف الدفعة
• جدولة النشر وتأخير بين المقالات، ومعاينة المقال قبل نشره
• توليد عناوين جديدة بالذكاء الاصطناعي
• تشفير مفاتيح API على الجهاز، وواجهة عربية/إنجليزية بوضع داكن وفاتح
• مثبّت Windows مستقل لا يحتاج تثبيت Java

التقنيات: Java 25، JavaFX، AtlantaFX، Maven، Blogger API (OAuth 2.0)، WordPress REST API، OkHttp، Apache POI، JUnit.
```

**نصائح لمستقل:**
- الصورة الأولى هي ما يظهر في قائمة أعمالك، فاجعل الغلاف أول صورة دائمًا.
- اربط العمل بخدمة تقدمها، مثل «برمجة أداة أتمتة مخصصة» أو «تطبيق سطح مكتب بـ JavaFX».

---

## 2) Upwork

عملاء Upwork أغلبهم يقرؤون الإنجليزية، فاستخدم النص الإنجليزي أدناه والصور من مجلد `en/`.

**الخطوات:**
1. من **Profile** ← قسم **Portfolio** ← زر **+** (Add project).
2. **Project title:**
   `AI Auto-Publisher for Blogger & WordPress (Java Desktop App)`
3. **Your role:** `Sole developer — design, development, packaging`
4. **Project description** (حد أقصى 600 حرف تقريبًا):

```
A Windows desktop app that turns a list of titles in an Excel file into published blog posts.
For each title it writes the article with OpenAI, Gemini or Claude, runs a quality check,
adds internal links (plus SEO description and tags on WordPress), fetches a header image (Unsplash or AI-generated),
then publishes to Blogger or WordPress, or saves a draft. Supports several sites with per-site
settings, duplicate protection, a resumable publishing queue, scheduling, encrypted API keys,
and an Arabic/English UI. Built with Java 25 and JavaFX, shipped as a standalone installer.
```

5. **Skills and deliverables** (حتى 5): `Java`, `JavaFX`, `Desktop Application`, `API Integration`, `WordPress`
6. **Content:** ارفع `en/01-cover.png` أولًا (يصبح الصورة المصغّرة)، ثم باقي الصور، ثم اللقطات الحقيقية. أضف **Link** لـ GitHub أو فيديو إن وُجد.
7. اضغط **Next: Preview** ثم **Publish**.

**نصائح لـ Upwork:**
- مقاس 1600×1200 (4:3) هو المقاس الذي يوصي به Upwork للصورة المصغّرة، والصور معدّة عليه.
- فيديو من 60 إلى 90 ثانية يعرض دفعة نشر حقيقية يرفع فرصك كثيرًا.
- يمكنك أيضًا عرضه في **Project Catalog** كخدمة جاهزة، مثل «I will build a custom AI content automation tool».
- أضف كلمات مثل *automation* و*AI content* و*WordPress API* في عنوان ملفك الشخصي وملخصه ليظهر المشروع في البحث.

---

## إعادة توليد الصور بعد تعديل النص

افتح `slides.html` وعدّل النص، ثم شغّل في PowerShell من هذا المجلد (غيّر `#1` واسم الملف لكل صورة من 1 إلى 6. للنسخة الإنجليزية استخدم `slides-en.html` واحفظ الصورة في `en\`):

```powershell
& "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe" --headless=new --hide-scrollbars --window-size=1600,1200 --virtual-time-budget=8000 --screenshot="$PWD\01-cover.png" "file:///$PWD/slides.html#1"
```

لعرض كل الصور في المتصفح مرة واحدة افتح `slides.html#all`.
