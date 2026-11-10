package com.hamzatech.publisher.service;

/**
 * التعليمات التي تُرسَل لنموذج الذكاء الاصطناعي لكتابة مقال.
 * <p>
 * كان كل مزود يبني تعليماته بنفسه في سطر واحد ("اكتب مقالًا بعنوان ...")، فخرجت
 * المقالات قصيرة وعامة ومختلفة الشكل بحسب المزود. الآن تُبنى في مكان واحد من
 * إعدادات المستخدم (الطول، الأسئلة الشائعة، تعليمات إضافية)، وكل مزود يرسلها
 * بالصيغة التي تفهمها واجهته.
 *
 * @param system تعليمات الدور والتنسيق
 * @param user   طلب المقال نفسه
 */
public record ArticlePrompt(String system, String user) {

    /**
     * @param length       {@code "short"} أو {@code "medium"} أو {@code "long"}؛
     *                     أي قيمة أخرى تُعامَل كـ{@code "medium"}
     * @param includeFaq   إضافة قسم أسئلة شائعة في آخر المقال
     * @param instructions تعليمات إضافية من المستخدم، أو {@code null}
     */
    public record Options(String length, boolean includeFaq, String instructions) {

        public static Options fromConfig() {
            ConfigService config = ConfigService.getInstance();
            return new Options(config.getArticleLength(), config.isArticleFaq(), config.getArticleInstructions());
        }
    }

    public static ArticlePrompt forArticle(String title, String targetLanguage) {
        return forArticle(title, targetLanguage, Options.fromConfig());
    }

    public static ArticlePrompt forArticle(String title, String targetLanguage, Options options) {
        boolean english = "english".equalsIgnoreCase(targetLanguage);
        int words = wordCount(options.length());
        String extra = options.instructions() == null ? "" : options.instructions().trim();

        StringBuilder system = new StringBuilder();
        if (english) {
            system.append("You are a professional SEO content writer who writes original, genuinely useful blog articles.\n")
                    .append("Rules:\n")
                    .append("- Write in natural, fluent English for human readers, not for search engines.\n")
                    .append("- Target about ").append(words).append(" words.\n")
                    .append("- Open with a short introduction that states what the reader will gain, without repeating the title.\n")
                    .append("- Use <h2> for main sections and <h3> for subsections, <p> for paragraphs, <ul>/<ol> for lists, <strong> for key terms.\n")
                    .append("- Give concrete, practical details and examples; avoid filler and generic statements.\n")
                    .append("- End with a short conclusion.\n");
            if (options.includeFaq()) {
                system.append("- Before the conclusion, add a section <h2>Frequently Asked Questions</h2> with 3 to 5 questions, each as <h3> followed by a <p> answer.\n");
            }
            system.append("- Output HTML only: no <html>, <head>, <body> or <h1> tags, no markdown, no code fences, and no text before or after the article.");
        } else {
            system.append("أنت كاتب محتوى عربي محترف متخصص في SEO، تكتب مقالات أصلية ومفيدة فعلًا للقارئ.\n")
                    .append("القواعد:\n")
                    .append("- اكتب بعربية فصحى سلسة وطبيعية موجّهة للقارئ لا لمحركات البحث.\n")
                    .append("- الطول المستهدف حوالي ").append(words).append(" كلمة.\n")
                    .append("- ابدأ بمقدمة قصيرة توضح ما سيستفيده القارئ، دون تكرار العنوان.\n")
                    .append("- استخدم <h2> للأقسام الرئيسية و<h3> للأقسام الفرعية، و<p> للفقرات، و<ul>/<ol> للقوائم، و<strong> للمصطلحات المهمة.\n")
                    .append("- قدّم تفاصيل وأمثلة عملية محددة، وتجنب الحشو والعبارات العامة.\n")
                    .append("- اختم بخاتمة قصيرة.\n");
            if (options.includeFaq()) {
                system.append("- قبل الخاتمة أضف قسم <h2>الأسئلة الشائعة</h2> فيه من 3 إلى 5 أسئلة، كل سؤال في <h3> تليه إجابته في <p>.\n");
            }
            system.append("- أرجع HTML فقط: بدون وسوم <html> أو <head> أو <body> أو <h1>، وبدون Markdown أو ```، وبدون أي نص قبل المقال أو بعده.");
        }

        if (!extra.isEmpty()) {
            system.append(english ? "\n\nAdditional instructions from the site owner:\n" : "\n\nتعليمات إضافية من صاحب الموقع:\n")
                    .append(extra);
        }

        String user = english
                ? "Write the article titled: " + title
                : "اكتب المقال بعنوان: " + title;

        return new ArticlePrompt(system.toString(), user);
    }

    /**
     * حد أقصى لرموز الإخراج يتسع للطول المطلوب.
     * <p>
     * العربية تستهلك رموزًا أكثر من الإنجليزية للكلمة الواحدة، والنماذج التي
     * "تفكّر" قبل الكتابة تحسب تفكيرها من الحد نفسه — حد ضيّق كان يقطع المقال في
     * منتصفه بصمت.
     */
    public static int maxOutputTokens(String length) {
        return switch (normalizedLength(length)) {
            case "short" -> 8_000;
            case "long" -> 24_000;
            default -> 16_000;
        };
    }

    static int wordCount(String length) {
        return switch (normalizedLength(length)) {
            case "short" -> 600;
            case "long" -> 2000;
            default -> 1200;
        };
    }

    private static String normalizedLength(String length) {
        return length == null ? "medium" : length.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** النص كاملًا لواجهة لا تفصل تعليمات النظام عن الطلب. */
    public String combined() {
        return system + "\n\n" + user;
    }
}
