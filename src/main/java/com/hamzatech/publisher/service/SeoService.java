package com.hamzatech.publisher.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hamzatech.publisher.model.SeoMetadata;
import com.hamzatech.publisher.provider.ContentGenerator;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * يولّد بيانات SEO للمقال: وصف الميتا، والوسوم، والنص البديل للصورة.
 * <p>
 * يُطلب ذلك من مزود المقال نفسه في نداء قصير منفصل بدل دمجه في طلب المقال: صيغة
 * المقال (HTML) تبقى كما هي لكل المزودين، وفشل هذا النداء لا يمس المقال.
 * <p>
 * <b>لا يُفشل النشر أبدًا.</b> أي فشل — مزود لا يدعم الطلب، رد غير JSON، خطأ
 * شبكة — يعود إلى بديل مستخرج من المقال نفسه: أول فقرة وصفًا، والعنوان نصًّا
 * بديلًا، وبلا وسوم. مقال منشور ببيانات SEO بسيطة أفضل من مقال لم يُنشر.
 */
@Log4j2
public final class SeoService {

    static final int DESCRIPTION_MAX = 160;
    static final int ALT_MAX = 125;
    static final int TAG_MAX_LENGTH = 40;
    static final int MAX_TAGS = 6;

    private SeoService() {
    }

    /**
     * @param generator المزود الذي كتب المقال، أو {@code null} للبديل وحده
     */
    public static SeoMetadata generate(String title, String bodyHtml, String language, ContentGenerator generator) {
        SeoMetadata fallback = fallback(title, bodyHtml);
        if (generator == null) {
            return fallback;
        }
        try {
            String reply = generator.complete(prompt(title, plainText(bodyHtml), language));
            return merge(parse(reply), fallback);
        } catch (Exception e) {
            log.warn("Could not generate SEO data for '{}', using a fallback from the article: {}",
                    title, e.getMessage());
            return fallback;
        }
    }

    static String prompt(String title, String articleText, String language) {
        boolean english = "english".equalsIgnoreCase(language);
        String excerpt = articleText.length() > 2000 ? articleText.substring(0, 2000) : articleText;
        String lang = english ? "English" : "Arabic";
        return "You are an SEO specialist. For the blog article below, return ONLY a JSON object, "
                + "with no markdown and no other text, in exactly this shape:\n"
                + "{\"description\": \"...\", \"tags\": [\"...\"], \"imageAlt\": \"...\"}\n"
                + "Rules:\n"
                + "- description: a compelling meta description in " + lang + ", 120 to 155 characters, "
                + "containing the article's main keyword, no quotes or emojis.\n"
                + "- tags: 3 to 6 short tags in " + lang + " (1 to 3 words each) that readers would search for.\n"
                + "- imageAlt: a concise, descriptive alt text in " + lang + " for the article's header image, "
                + "under 120 characters.\n\n"
                + "Title: " + title + "\n\nArticle:\n" + excerpt;
    }

    /**
     * يقرأ رد النموذج؛ يتسامح مع أسوار Markdown ونص قبل الكائن أو بعده.
     *
     * @return البيانات المقروءة، أو {@code null} إن لم يكن في الرد كائن JSON صالح
     */
    static SeoMetadata parse(String reply) {
        if (reply == null) {
            return null;
        }
        int start = reply.indexOf('{');
        int end = reply.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        JsonObject json;
        try {
            JsonElement parsed = JsonParser.parseString(reply.substring(start, end + 1));
            if (!parsed.isJsonObject()) {
                return null;
            }
            json = parsed.getAsJsonObject();
        } catch (RuntimeException e) {
            return null;
        }

        String description = truncate(string(json, "description"), DESCRIPTION_MAX);
        String imageAlt = truncate(string(json, "imageAlt"), ALT_MAX);

        Set<String> tags = new LinkedHashSet<>();
        if (json.has("tags") && json.get("tags").isJsonArray()) {
            JsonArray array = json.getAsJsonArray("tags");
            for (JsonElement element : array) {
                if (!element.isJsonPrimitive()) {
                    continue;
                }
                String tag = clean(element.getAsString()).replaceAll("^#+", "").trim();
                if (!tag.isEmpty() && tag.length() <= TAG_MAX_LENGTH
                        && tags.stream().noneMatch(t -> t.equalsIgnoreCase(tag))) {
                    tags.add(tag);
                }
                if (tags.size() == MAX_TAGS) {
                    break;
                }
            }
        }
        return new SeoMetadata(description, new ArrayList<>(tags), imageAlt);
    }

    /** كل حقل لم يُرجعه النموذج يُؤخذ من البديل. */
    static SeoMetadata merge(SeoMetadata generated, SeoMetadata fallback) {
        if (generated == null) {
            return fallback;
        }
        return new SeoMetadata(
                generated.hasDescription() ? generated.description() : fallback.description(),
                generated.tags(),
                generated.hasImageAlt() ? generated.imageAlt() : fallback.imageAlt());
    }

    /** بيانات لا تحتاج نموذجًا: أول فقرة وصفًا والعنوان نصًّا بديلًا. */
    static SeoMetadata fallback(String title, String bodyHtml) {
        String description = null;
        if (bodyHtml != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(?is)<p[^>]*>(.*?)</p>").matcher(bodyHtml);
            while (m.find() && description == null) {
                String text = plainText(m.group(1));
                if (text.length() >= 40) {
                    description = truncate(text, DESCRIPTION_MAX);
                }
            }
        }
        return new SeoMetadata(description, List.of(), truncate(clean(title), ALT_MAX));
    }

    static String plainText(String html) {
        if (html == null) {
            return "";
        }
        String text = html.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&");
        return clean(text);
    }

    /** يقصّ عند حد كلمة ويضيف "…" حتى لا تنتهي الجملة بنصف كلمة. */
    static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        String value = clean(text);
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() <= max) {
            return value;
        }
        String cut = value.substring(0, max - 1);
        int space = cut.lastIndexOf(' ');
        if (space > max / 2) {
            cut = cut.substring(0, space);
        }
        return cut.replaceAll("[\\s,،.:;؛-]+$", "") + "…";
    }

    private static String string(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) {
            return null;
        }
        return json.get(key).getAsString();
    }

    private static String clean(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    /** للتسجيل: ملخّص قصير لما وُلّد. */
    static String summary(SeoMetadata seo) {
        return String.format(Locale.ROOT, "description=%d chars, tags=%d, alt=%s",
                seo.hasDescription() ? seo.description().length() : 0, seo.tags().size(), seo.hasImageAlt());
    }
}
