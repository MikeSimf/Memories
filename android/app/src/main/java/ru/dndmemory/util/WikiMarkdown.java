package ru.dndmemory.util;

import android.content.Context;
import android.graphics.DashPathEffect;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.BackgroundColorSpan;
import android.text.style.BulletSpan;
import android.text.style.ClickableSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru.dndmemory.R;
import ru.dndmemory.data.model.EntityLink;

/**
 * Разметка описаний: markdown + ссылки {@code [[Имя]]}, {@code [[Имя|подпись]]},
 * {@code [[#uuid|подпись]]}. Разрешённые ссылки открывают карточку объекта,
 * неразрешённые рисуются пунктиром (docs/api.md, раздел 6;
 * docs/sync.md, раздел 7).
 */
public final class WikiMarkdown {

    /** Имя из текста → id объекта кампании, либо {@code null}. */
    public interface LinkResolver {
        @Nullable
        String resolveTargetId(@NonNull String name);
    }

    /** Нажатие на ссылку. {@code targetId == null} — объект не найден. */
    public interface LinkListener {
        void onWikiLinkClick(@Nullable String targetId, @NonNull String name, boolean resolved);
    }

    private static final Pattern WIKI = Pattern.compile("\\[\\[([^\\[\\]]*)\\]\\]");

    private WikiMarkdown() {
    }

    // --- Разбор ссылок ------------------------------------------------------

    /** Все ссылки текста без разрешения имён (targetId = null у неявных). */
    public static List<EntityLink> parseLinks(@Nullable String source) {
        List<EntityLink> result = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return result;
        }
        Matcher matcher = WIKI.matcher(source);
        while (matcher.find()) {
            String inner = matcher.group(1);
            if (inner == null || inner.trim().isEmpty()) {
                continue;
            }
            EntityLink link = parseOne(inner);
            // Повтор одной и той же ссылки в тексте храним один раз.
            boolean duplicate = false;
            for (EntityLink existing : result) {
                if (equalsLink(existing, link)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                result.add(link);
            }
        }
        return result;
    }

    private static EntityLink parseOne(String inner) {
        String left = inner;
        String label = null;
        int pipe = inner.indexOf('|');
        if (pipe >= 0) {
            left = inner.substring(0, pipe).trim();
            label = inner.substring(pipe + 1).trim();
        }
        EntityLink link = new EntityLink();
        if (left.startsWith("#")) {
            link.targetId = left.substring(1).trim();
            link.name = label != null && !label.isEmpty() ? label : link.targetId;
            link.resolved = !link.targetId.isEmpty();
        } else {
            link.targetId = null;
            link.name = left;
            link.resolved = false;
        }
        link.label = label != null && !label.isEmpty() ? label : link.name;
        return link;
    }

    private static boolean equalsLink(EntityLink a, EntityLink b) {
        if (a.targetId == null || b.targetId == null) {
            return a.targetId == null && b.targetId == null
                    && a.name != null && a.name.equals(b.name);
        }
        return a.targetId.equals(b.targetId) && String.valueOf(a.label).equals(String.valueOf(b.label));
    }

    /** Текст без разметки — для сниппетов в результатах поиска. */
    public static String plainText(@Nullable String source) {
        if (source == null || source.isEmpty()) {
            return "";
        }
        String text = WIKI.matcher(source).replaceAll("$1");
        text = text.replaceAll("\\*\\*(.+?)\\*\\*", "$1");
        text = text.replaceAll("(?s)`(.+?)`", "$1");
        text = text.replaceAll("(?m)^#{1,6}\\s*", "");
        text = text.replaceAll("(?m)^\\s*[-*>]\\s+", "");
        return text.trim();
    }

    // --- Отрисовка ---------------------------------------------------------

    public static CharSequence render(@NonNull Context context, @Nullable String source,
                                      @NonNull LinkResolver resolver,
                                      @NonNull LinkListener listener) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        if (source == null || source.isEmpty()) {
            return out;
        }
        int linkColor = ContextCompat.getColor(context, R.color.wiki_link);
        int brokenColor = ContextCompat.getColor(context, R.color.wiki_link_broken);
        int codeColor = ContextCompat.getColor(context, R.color.code_background);

        String normalized = source.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        for (String rawLine : lines) {
            String trimmed = rawLine.trim();
            if (trimmed.isEmpty()) {
                if (out.length() > 0) {
                    out.append("\n");
                }
                out.append("\n");
                continue;
            }
            if (trimmed.startsWith("### ")) {
                appendHeading(out, trimmed.substring(4), 1.15f, resolver, listener, linkColor, brokenColor, codeColor);
            } else if (trimmed.startsWith("## ")) {
                appendHeading(out, trimmed.substring(3), 1.3f, resolver, listener, linkColor, brokenColor, codeColor);
            } else if (trimmed.startsWith("# ")) {
                appendHeading(out, trimmed.substring(2), 1.5f, resolver, listener, linkColor, brokenColor, codeColor);
            } else if (trimmed.startsWith("> ")) {
                int start = out.length();
                inline(out, trimmed.substring(2), resolver, listener, linkColor, brokenColor, codeColor);
                out.append("\n");
                out.setSpan(new StyleSpan(Typeface.ITALIC), start, out.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                int start = out.length();
                inline(out, trimmed.substring(2), resolver, listener, linkColor, brokenColor, codeColor);
                out.append("\n");
                out.setSpan(new BulletSpan(16), start, out.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                inline(out, trimmed, resolver, listener, linkColor, brokenColor, codeColor);
                out.append("\n");
            }
        }
        return out;
    }

    private static void appendHeading(SpannableStringBuilder out, String text, float scale,
                                      LinkResolver resolver, LinkListener listener,
                                      int linkColor, int brokenColor, int codeColor) {
        int start = out.length();
        inline(out, text, resolver, listener, linkColor, brokenColor, codeColor);
        out.append("\n");
        out.setSpan(new RelativeSizeSpan(scale), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new StyleSpan(Typeface.BOLD), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static void inline(SpannableStringBuilder out, String text,
                               LinkResolver resolver, LinkListener listener,
                               int linkColor, int brokenColor, int codeColor) {
        int i = 0;
        boolean bold = false;
        boolean italic = false;
        boolean code = false;
        int length = text.length();
        while (i < length) {
            if (text.startsWith("**", i)) {
                bold = !bold;
                i += 2;
                continue;
            }
            if (text.startsWith("[[", i)) {
                int close = text.indexOf("]]", i + 2);
                if (close > 0) {
                    int spanStart = out.length();
                    appendWikiLink(out, text.substring(i + 2, close), resolver, listener,
                            linkColor, brokenColor);
                    applyInlineStyles(out, spanStart, bold, italic, code, codeColor);
                    i = close + 2;
                    continue;
                }
            }
            if (text.charAt(i) == '`') {
                code = !code;
                i++;
                continue;
            }
            if (!code && (text.charAt(i) == '*' || text.charAt(i) == '_')) {
                italic = !italic;
                i++;
                continue;
            }
            int start = i;
            while (i < length && !isSpecial(text, i)) {
                i++;
            }
            if (start == i) {
                // Одиночный специальный символ, который мы не распознали как разметку.
                start = i;
                i++;
            }
            int spanStart = out.length();
            out.append(text, start, i);
            applyInlineStyles(out, spanStart, bold, italic, code, codeColor);
        }
    }

    private static void applyInlineStyles(SpannableStringBuilder out, int start,
                                          boolean bold, boolean italic, boolean code, int codeColor) {
        int end = out.length();
        if (end <= start) {
            return;
        }
        if (bold) {
            out.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (italic) {
            out.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (code) {
            out.setSpan(new TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            out.setSpan(new BackgroundColorSpan(codeColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static boolean isSpecial(String text, int index) {
        char c = text.charAt(index);
        if (c == '`' || c == '*' || c == '_') {
            return true;
        }
        return c == '[' && index + 1 < text.length() && text.charAt(index + 1) == '[';
    }

    private static void appendWikiLink(SpannableStringBuilder out, String inner,
                                       LinkResolver resolver, LinkListener listener,
                                       int linkColor, int brokenColor) {
        EntityLink link = parseOne(inner);
        String targetId = link.targetId;
        if (targetId == null && link.name != null && !link.name.isEmpty()) {
            targetId = resolver.resolveTargetId(link.name);
        }
        boolean resolved = targetId != null && !targetId.isEmpty();
        String caption = link.label == null || link.label.isEmpty()
                ? (link.name == null ? inner : link.name) : link.label;

        int start = out.length();
        out.append(caption);
        int end = out.length();

        out.setSpan(new WikiClickSpan(targetId, link.name == null ? caption : link.name,
                        resolved, listener, resolved ? linkColor : brokenColor),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /** Кликабельная ссылка: разрешённая — подчёркнута, «битая» — пунктиром. */
    private static final class WikiClickSpan extends ClickableSpan {

        @Nullable
        private final String targetId;
        private final String name;
        private final boolean resolved;
        private final LinkListener listener;
        private final int color;

        WikiClickSpan(@Nullable String targetId, String name, boolean resolved,
                      LinkListener listener, int color) {
            this.targetId = targetId;
            this.name = name;
            this.resolved = resolved;
            this.listener = listener;
            this.color = color;
        }

        @Override
        public void onClick(@NonNull View widget) {
            listener.onWikiLinkClick(targetId, name, resolved);
        }

        @Override
        public void updateDrawState(@NonNull TextPaint ds) {
            ds.setColor(color);
            ds.setUnderlineText(true);
            if (!resolved) {
                // Пунктирное подчёркивание «битой» ссылки.
                ds.setPathEffect(new DashPathEffect(new float[]{6f, 4f}, 0f));
            }
        }
    }
}
