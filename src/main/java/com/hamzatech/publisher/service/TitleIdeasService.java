package com.hamzatech.publisher.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.hamzatech.publisher.provider.ContentGenerator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * يقترح عناوين مقالات لموضوع أو كلمة مفتاحية.
 * <p>
 * يغني عن تجهيز ملف Excel يدويًا: العناوين المختارة تُحفظ ملفًّا عاديًّا
 * ({@link ExcelService#writeTitles}) يمر في خط النشر نفسه دون أي فرع جديد.
 * <p>
 * بخلاف {@link SeoService} هذا الطلب يبدأه المستخدم وينتظر نتيجته، فالفشل يُرمى
 * ليُعرض له بدل أن يُخفى خلف بديل.
 */
public final class TitleIdeasService {

    public static final int MIN_COUNT = 3;
    public static final int MAX_COUNT = 30;
    static final int MAX_TITLE_LENGTH = 150;

    private TitleIdeasService() {
    }

    /**
     * @param topic    الموضوع أو الكلمة المفتاحية
     * @param count    عدد العناوين المطلوب، يُحصر بين {@link #MIN_COUNT} و{@link #MAX_COUNT}
     * @param language {@code "arabic"} أو {@code "english"}
     * @return عناوين بلا تكرار؛ قد تقل عن {@code count} إن أعاد النموذج أقل
     * @throws IOException إن فشل الطلب أو لم يحمل الرد أي عنوان
     */
    public static List<String> generate(String topic, int count, String language, ContentGenerator generator)
            throws IOException {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic is required");
        }
        int wanted = Math.clamp(count, MIN_COUNT, MAX_COUNT);
        String reply = generator.complete(prompt(topic.trim(), wanted, language));
        List<String> titles = parse(reply, wanted);
        if (titles.isEmpty()) {
            throw new IOException("لم يُرجع النموذج أي عنوان صالح");
        }
        return titles;
    }

    static String prompt(String topic, int count, String language) {
        boolean english = "english".equalsIgnoreCase(language);
        String lang = english ? "English" : "Arabic";
        return "You are an experienced blog editor and SEO strategist. Suggest " + count
                + " blog article titles in " + lang + " about: " + topic + "\n"
                + "Rules:\n"
                + "- Each title targets a different search intent or angle (how-to, list, comparison, "
                + "guide, mistakes, questions people ask, beginner vs advanced), so no two articles overlap.\n"
                + "- Specific and useful, 40 to 70 characters, natural " + lang + ", no clickbait, "
                + "no emojis, no quotation marks, no numbering.\n"
                + "- Use a number in some titles only where it fits naturally.\n"
                + "Return ONLY a JSON array of strings, with no markdown and no other text.";
    }

    /**
     * يقرأ الرد: مصفوفة JSON إن وُجدت، وإلا سطرًا لكل عنوان — النماذج تخالف
     * الصيغة أحيانًا وتعيد قائمة مرقّمة، ورفض رد مفيد لذلك لا معنى له.
     */
    static List<String> parse(String reply, int max) {
        if (reply == null) {
            return List.of();
        }
        List<String> raw = new ArrayList<>();
        int start = reply.indexOf('[');
        int end = reply.lastIndexOf(']');
        boolean parsedJson = false;
        if (start >= 0 && end > start) {
            try {
                JsonElement parsed = JsonParser.parseString(reply.substring(start, end + 1));
                if (parsed.isJsonArray()) {
                    JsonArray array = parsed.getAsJsonArray();
                    for (JsonElement element : array) {
                        if (element.isJsonPrimitive()) {
                            raw.add(element.getAsString());
                        }
                    }
                    parsedJson = true;
                }
            } catch (RuntimeException ignored) {
                // ليس JSON صالحًا؛ نجرّب الأسطر
            }
        }
        if (!parsedJson) {
            raw.addAll(List.of(reply.split("\\R")));
        }

        Set<String> seen = new LinkedHashSet<>();
        List<String> titles = new ArrayList<>();
        for (String line : raw) {
            String title = clean(line);
            if (title.isEmpty() || title.length() > MAX_TITLE_LENGTH || title.startsWith("```")) {
                continue;
            }
            if (seen.add(title.toLowerCase(Locale.ROOT))) {
                titles.add(title);
            }
            if (titles.size() == max) {
                break;
            }
        }
        return titles;
    }

    /** يزيل الترقيم والنقاط وعلامات الاقتباس والتنسيق حول العنوان. */
    static String clean(String line) {
        if (line == null) {
            return "";
        }
        String title = line.trim()
                .replaceFirst("^(?:[-*•]+|\\d+[.)\\-:]|[٠-٩]+[.)\\-:])\\s*", "")
                .replace("**", "")
                .replaceAll("\\s+", " ")
                .trim()
                // الفاصلة قبل فك الاقتباس: «عنوان»، تنتهي بفاصلة لا بعلامة اقتباس
                .replaceAll("[,،]$", "")
                .trim();
        while (title.length() >= 2 && isQuote(title.charAt(0)) && isQuote(title.charAt(title.length() - 1))) {
            title = title.substring(1, title.length() - 1).trim();
        }
        // سطر مثل "[" أو "]," من JSON مكسور
        if (title.matches("[\\[\\]{},\"']*")) {
            return "";
        }
        return title.replaceAll("[,،]$", "").trim();
    }

    private static boolean isQuote(char c) {
        return c == '"' || c == '\'' || c == '“' || c == '”' || c == '«' || c == '»';
    }
}
