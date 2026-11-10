# تجربة ترقية Publisher إلى JavaFX 25

## الإعدادات

- JavaFX: `25.0.4`، مع خاصية واحدة `javafx.version` لجميع وحداته.
- JDK للبناء والتشغيل: `25`؛ ضبط `maven.compiler.release` على `25`.
- Lombok: `1.18.44` لكل من الاعتماد ومعالج annotations، لدعم JDK 25.
- Maven Enforcer يرفض JDK الأقدم برسالة واضحة قبل التجميع.
- بقي منطق النشر كما هو. أُضيف ربط `kotlin.stdlib` و`okio` في JPMS، واستُبدل اعتماد Okio متعدد المنصات بـ `okio-jvm` لتجنب تعارض اسم الوحدة.
- ضُبط native access لوحدات JavaFX وJNA في `mvn javafx:run`.

JavaFX 25 نفسه يحتاج JDK 23 على الأقل، لكن هذه التجربة تستخدم JDK 25 وفق توصية ملاحظات الإصدار. لذلك ملف JAR الناتج لهذا المشروع يحتاج Java 25. إصدارات Java وJavaFX مستقلة؛ ليس مطلوبًا عمومًا أن يتطابق رقماهما.

## البناء والتشغيل

ثبّت JDK 25 واضبط `JAVA_HOME` وProject SDK وMaven Runner JDK في IDE عليه. تأكد من أن `mvn -version` يعرض Java 25؛ قد يستخدم `java -version` إصدارًا آخر بسبب ترتيب PATH.

مثال PowerShell (غيّر المسار إلى مسار JDK المثبّت لديك):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -version
mvn clean verify
mvn javafx:run
```

لتشغيل ملف JAR المجمّع استخدم:

```powershell
java --enable-native-access=ALL-UNNAMED -jar target/publisher-1.0-SNAPSHOT.jar
```

هذه المتغيرات تخص جلسة الطرفية الحالية. نسخة JDK المحلية موجودة داخل `.tools/jdk-*`، وهي مستثناة من Git ولا يحذفها `mvn clean`. لا تضع JDK داخل `target`.

لاستخدام النسخة المحلية من جذر المشروع في PowerShell:

```powershell
$env:JAVA_HOME = (Get-ChildItem .tools -Directory -Filter 'jdk-*' | Select-Object -First 1).FullName
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -version
mvn clean verify
mvn javafx:run
```

في IDE اختر مسار JDK نفسه ضمن Project SDK وMaven Runner JDK. الطرفيات المفتوحة لا تتأثر بتغيير المتغيرات في طرفية أخرى.

## تطبيق التجربة على مشاريع أخرى

1. وحّد إصدارات وحدات JavaFX في خاصية Maven واحدة.
2. اختر JDK مناسبًا للبناء والتشغيل وتوزيع التطبيق؛ JDK 21 لا يشغّل JavaFX 25.
3. حدّث معالجات annotations مثل Lombok لتدعم JDK المختار.
4. اختبر البناء والاختبارات وتحميل FXML وCSS والثيمات والأيقونات وWebView، ثم التوزيع الفعلي.
5. راجع الاستخدامات المحذوفة، خاصة constructor القديم لـ `TransitionEvent` إن استخدمه مشروعك.
6. عند التوزيع، وفّر runtime مناسبًا مع التطبيق أو اطلب تثبيت JDK المناسب. على Linux يحتاج JavaFX 25 إلى GTK 3.20 أو أحدث.

نجاح هذه التجربة دليل على توافق اعتماديات Publisher، وليس ضمانًا لتوافق كل مشروع؛ يحتاج كل مشروع إلى فحص اعتمادياته وشاشاته وآلية توزيعه.

## نتائج التحقق على Windows

- `mvn verify`: نجح باستخدام Temurin JDK 25.0.4.1؛ 161 اختبارًا دون فشل أو تخطٍّ، وإنشاء JAR المجمّع.
- تحميل الشاشات السبع ورسم CSS والأيقونات: نجح في 28 حالة (العربية والإنجليزية × الثيم الفاتح والداكن)، في JAR المجمّع وفي تشغيل JPMS.
- WebView في JPMS: نجح تحميل HTML عربي وتشغيل JavaScript والتقاط snapshot.
- البناء على JDK 21: رُفض برسالة Enforcer المقصودة.
- صور الفحص وسجلاته موجودة مؤقتًا تحت `target/javafx25-smoke` و`target/javafx25-module-smoke` و`target/javafx25-verify.log`.
- لم يُختبر نشر فعلي أو تسجيل دخول OAuth؛ استُخدمت مجلدات بيانات معزولة دون credentials.

طريقة JAR المجمّع الحالية تُشغّل JavaFX على classpath وتعرض تحذير unnamed module. تشغيل `mvn javafx:run` يستخدم JPMS؛ لا يُعد بناء JAR واحد بديلًا عن فحص حزمة التوزيع على كل نظام مستهدف.
## المراجع

- [JavaFX 25 highlights](https://openjfx.io/highlights/25/)
- [JavaFX 25 release notes](https://github.com/openjdk/jfx/blob/master/doc-files/release-notes-25.md)
- [Lombok changelog](https://projectlombok.org/changelog)
## شريط النافذة ولوحة المعلومات

- يستخدم `AppWindow` واجهة JavaFX 25 التجريبية `HeaderBar` مع `StageStyle.EXTENDED` للنافذة الرئيسية. الأدوات داخل شريط العنوان وأزرار النظام الأصلية محفوظة، مع السحب والتكبير بسلوك HeaderBar.
- يتراجع نمط النافذة إلى `DECORATED` إذا لم تدعم المنصة `EXTENDED_WINDOW`.
- يجب تفعيل `javafx.enablePreview` **قبل** تهيئة JavaFX. يضبط Maven الخيار تلقائيًا، ويفعّله `Launcher` قبل `Application.launch`. في IDE شغّل `Launcher`، أو أضف `-Djavafx.enablePreview=true` إلى VM options عند تشغيل `MainApp` مباشرة.
- تبويب «نظرة عامة» يعرض المحاولات والنجاح والفشل، والمقالات المنتظرة عبر كل المدونات، ومعدل النجاح والنشاط اليومي وأحدث النتائج.
- الفترة والمدونة تطبّقان على السجل ومعدل النجاح والنشاط؛ عداد الطابور عالمي. «كل الفترات» يعرض إجماليات السجل، مع رسم لآخر 7 أيام. المحاولات تشمل الإعادات وليست عدد المقالات الفريدة.
- تتحدث الإحصاءات بعد إنجاز المقالات، والعودة من الشاشات، واختيار تبويب النظرة العامة، أو الضغط على «تحديث». القراءة من القرص تجري في الخلفية.
- أدوات رفع Excel والتصنيفات والجدولة وإيقاف النشر متاحة في تبويب «نشر المقالات».
- فحص التطوير: 168 اختبارًا؛ تحميل وتصفية وعرض لوحة المعلومات في اللغتين والثيمين، حالة فارغة، والتنقل إلى النشر وتشغيل MainApp مع نمط EXTENDED.
- صور فحص الداش بورد تستخدم بيانات تجريبية معزولة داخل `.tools/dashboard-smoke`؛ لا تُضاف هذه البيانات إلى سجل المستخدم.