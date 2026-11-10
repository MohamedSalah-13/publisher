package com.hamzatech.publisher.service;

import com.hamzatech.publisher.model.PublishLog;
import com.hamzatech.publisher.model.PublishTarget;
import lombok.extern.log4j.Log4j2;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * يضيف إلى المقال روابط لمقالات سابقة ذات صلة على الموقع نفسه.
 * <p>
 * الروابط الداخلية تساعد محركات البحث على فهم بنية الموقع وتُبقي القارئ فيه.
 * المصدر سجل النشر لأنه يحمل الرابط الحقيقي لكل مقال نُشر — لا حاجة لنداء شبكة.
 * <p>
 * الصلة تُقاس بالكلمات المشتركة في العنوان بعد حذف الكلمات العامة. مقال بلا
 * مقالات ذات صلة لا يأخذ روابط إطلاقًا: رابط عشوائي أسوأ من لا شيء.
 * <p>
 * لا يُفشل النشر أبدًا: أي خطأ في قراءة السجل يعني مقالًا بلا روابط.
 */
@Log4j2
public final class InternalLinks {

    static final int MAX_LINKS = 3;
    private static final int MIN_TOKEN_LENGTH = 3;

    /** كلمات تتكرر في العناوين ولا تدل على موضوع. */
    private static final Set<String> STOP_WORDS = Set.of(
            // عربية
            "في", "من", "على", "إلى", "الى", "عن", "مع", "كيف", "كيفية", "طريقة", "طرق", "ما", "هو", "هي",
            "أفضل", "افضل", "أهم", "اهم", "دليل", "نصائح", "شامل", "الشامل", "كامل", "الكامل", "لماذا",
            "متى", "أين", "هل", "بين", "عند", "كل", "لكل", "خطوة", "بخطوة", "مقال", "جديد", "سنة", "عام",
            // إنجليزية
            "the", "and", "for", "with", "how", "what", "why", "when", "where", "your", "you", "are",
            "best", "top", "guide", "complete", "ultimate", "tips", "ways", "step", "from", "into", "about",
            "this", "that", "new", "year", "beginners");

    /** مقال سابق صالح للربط. */
    record Candidate(String title, String url, LocalDateTime publishedAt) {
    }

    private InternalLinks() {
    }

    /**
     * @param originalTitle العنوان كما في ملف Excel — كما يُخزَّن في السجل، فبه يُستبعد المقال نفسه
     * @param contentTitle  العنوان الذي كُتب منه المقال (قد يكون مترجمًا)، فبه تُقاس الصلة
     * @param target        الوجهة؛ {@code null} (المعاينة قبل اختيار مدونة) يعني بلا روابط
     * @param language      لغة المقال؛ لا يُربط إلا بعناوين بلغته
     * @return الجسم مع قسم "اقرأ أيضًا"، أو كما هو إن لم توجد مقالات ذات صلة
     */
    public static String addTo(String bodyHtml, String originalTitle, String contentTitle,
                               PublishTarget target, String language) {
        String title = contentTitle;
        if (target == null) {
            return bodyHtml;
        }
        try {
            boolean english = "english".equalsIgnoreCase(language);
            List<Candidate> related = pickRelated(title,
                    candidates(PublishLogService.getInstance().getAllLogs(), target.getId(), originalTitle, english),
                    MAX_LINKS);
            if (related.isEmpty()) {
                return bodyHtml;
            }
            log.debug("Linking '{}' to {} related article(s)", title, related.size());
            return appendSection(bodyHtml, related, english);
        } catch (Exception e) {
            log.warn("Could not add internal links to '{}': {}", title, e.getMessage());
            return bodyHtml;
        }
    }

    /**
     * المقالات المنشورة فعلًا على الوجهة نفسها، بلغة المقال، بلا تكرار، عدا العنوان
     * نفسه (إعادة محاولة مقال لا تربطه بنسخته السابقة).
     */
    static List<Candidate> candidates(List<PublishLog> logs, String targetId, String excludeTitle, boolean english) {
        String excluded = normalise(excludeTitle);
        Map<String, Candidate> byUrl = new LinkedHashMap<>();
        for (PublishLog entry : logs) {
            if (!entry.isSuccess() || entry.isDraft() || entry.getPostUrl() == null || entry.getPostUrl().isBlank()
                    || entry.getTitle() == null || !targetId.equals(entry.getBlogId())) {
                continue;
            }
            if (normalise(entry.getTitle()).equals(excluded) || isEnglish(entry.getTitle()) != english) {
                continue;
            }
            byUrl.putIfAbsent(entry.getPostUrl(),
                    new Candidate(entry.getTitle().trim(), entry.getPostUrl(), entry.getPublishDate()));
        }
        return new ArrayList<>(byUrl.values());
    }

    /** الأكثر صلة أولًا، والأحدث عند التساوي؛ ما لا يشارك العنوان أي كلمة يُستبعد. */
    static List<Candidate> pickRelated(String title, List<Candidate> candidates, int max) {
        Set<String> words = tokens(title);
        if (words.isEmpty()) {
            return List.of();
        }
        record Scored(Candidate candidate, int score) {
        }
        return candidates.stream()
                .map(c -> {
                    Set<String> shared = new HashSet<>(tokens(c.title()));
                    shared.retainAll(words);
                    return new Scored(c, shared.size());
                })
                .filter(s -> s.score() > 0)
                .sorted(Comparator.comparingInt(Scored::score).reversed()
                        .thenComparing(s -> s.candidate().publishedAt(),
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(max)
                .map(Scored::candidate)
                .toList();
    }

    static String appendSection(String bodyHtml, List<Candidate> links, boolean english) {
        StringBuilder section = new StringBuilder("\n<h3>")
                .append(english ? "Read also" : "اقرأ أيضًا")
                .append("</h3>\n<ul>\n");
        for (Candidate link : links) {
            section.append("<li><a href=\"").append(escape(link.url())).append("\">")
                    .append(escape(link.title())).append("</a></li>\n");
        }
        section.append("</ul>");
        return bodyHtml + section;
    }

    /** كلمات العنوان الدالة: بلا كلمات عامة، وبلا "ال" التعريف حتى تتطابق "السياحة" و"سياحة". */
    static Set<String> tokens(String text) {
        Set<String> result = new HashSet<>();
        if (text == null) {
            return result;
        }
        for (String raw : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            String word = raw;
            if (STOP_WORDS.contains(word)) {
                continue;
            }
            for (String prefix : new String[]{"وال", "بال", "لل", "ال"}) {
                if (word.startsWith(prefix) && word.length() - prefix.length() >= MIN_TOKEN_LENGTH) {
                    word = word.substring(prefix.length());
                    break;
                }
            }
            if (word.length() >= MIN_TOKEN_LENGTH && !STOP_WORDS.contains(word)) {
                result.add(word);
                // واو العطف ملتصقة بالكلمة ("وإسطنبول"). لا تُحذف دائمًا لأن كلمات
                // تبدأ بالواو أصلًا ("وصفة")، فتُضاف الصيغتان
                if (word.startsWith("و") && word.length() - 1 >= MIN_TOKEN_LENGTH) {
                    result.add(word.substring(1));
                }
            }
        }
        return result;
    }

    /** عنوان إنجليزي إن غلبت عليه الحروف اللاتينية. */
    static boolean isEnglish(String text) {
        int latin = 0;
        int arabic = 0;
        for (char c : text.toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) latin++;
            else if (c >= 0x0600 && c <= 0x06FF) arabic++;
        }
        return latin > arabic;
    }

    private static String normalise(String title) {
        return title == null ? "" : title.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
