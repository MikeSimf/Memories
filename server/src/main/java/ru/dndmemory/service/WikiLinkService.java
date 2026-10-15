package ru.dndmemory.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Разбор вики-ссылок в описаниях: [[Имя]], [[Имя|подпись]], [[#uuid|подпись]].
 * Правило одинаково для сервера, веба и Android — см. docs/sync.md, раздел 7.
 */
@Service
public class WikiLinkService {

    private static final Pattern LINK_PATTERN = Pattern.compile("\\[\\[(.+?)]]");
    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    /** Ключи индекса — имена объектов в нижнем регистре. */
    public List<WikiLink> extract(Map<String, UUID> nameIndex, String... texts) {
        Map<String, WikiLink> unique = new LinkedHashMap<>();
        if (texts == null) {
            return new ArrayList<>();
        }
        for (String text : texts) {
            if (text == null || text.isEmpty()) {
                continue;
            }
            Matcher matcher = LINK_PATTERN.matcher(text);
            while (matcher.find()) {
                String inner = matcher.group(1).trim();
                String namePart = inner;
                String label = null;
                int pipe = inner.indexOf('|');
                if (pipe >= 0) {
                    namePart = inner.substring(0, pipe).trim();
                    label = inner.substring(pipe + 1).trim();
                }
                WikiLink link = resolveOne(nameIndex, namePart, label);
                String key = (link.getTargetId() == null ? "name:" + namePart.toLowerCase(Locale.ROOT)
                        : "id:" + link.getTargetId()) + "|" + link.getLabel();
                unique.putIfAbsent(key, link);
            }
        }
        return new ArrayList<>(unique.values());
    }

    /** Имена, на которые ссылаются в тексте, но которых ещё нет в кампании. */
    public Set<String> unresolvedNames(Map<String, UUID> nameIndex, String... texts) {
        Set<String> result = new LinkedHashSet<>();
        for (WikiLink link : extract(nameIndex, texts)) {
            if (!link.isResolved() && link.getLabel() != null) {
                result.add(link.getLabel());
            }
        }
        return result;
    }

    private WikiLink resolveOne(Map<String, UUID> nameIndex, String namePart, String label) {
        if (namePart.startsWith("#")) {
            String rawId = namePart.substring(1).trim();
            if (UUID_PATTERN.matcher(rawId).matches()) {
                UUID id = UUID.fromString(rawId);
                return new WikiLink(id, label == null ? rawId : label, true);
            }
            return new WikiLink(null, label == null ? rawId : label, false);
        }
        UUID targetId = nameIndex.get(namePart.toLowerCase(Locale.ROOT));
        String display = label != null ? label : namePart;
        return new WikiLink(targetId, display, targetId != null);
    }
}
