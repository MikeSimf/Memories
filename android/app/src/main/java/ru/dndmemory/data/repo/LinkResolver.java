package ru.dndmemory.data.repo;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import ru.dndmemory.data.local.dao.EntityDao;
import ru.dndmemory.data.local.entity.EntityRoom;
import ru.dndmemory.data.model.EntityLink;
import ru.dndmemory.util.WikiMarkdown;

/**
 * Разрешение ссылок {@code [[Имя]]} по локальной базе. Имена ищутся по всей
 * кампании без учёта регистра — правило совпадает с серверным, поэтому офлайн
 * и онлайн дают одинаковый результат (docs/sync.md, раздел 7).
 */
public final class LinkResolver {

    private LinkResolver() {
    }

    public static List<EntityLink> resolve(EntityDao dao, String campaignId, String... texts) {
        List<EntityLink> result = new ArrayList<>();
        if (texts == null) {
            return result;
        }
        for (String text : texts) {
            if (text == null || text.isEmpty()) {
                continue;
            }
            for (EntityLink link : WikiMarkdown.parseLinks(text)) {
                if (link.targetId == null && link.name != null) {
                    EntityRoom target = dao.resolveByName(campaignId, link.name);
                    if (target != null) {
                        link.targetId = target.id;
                        link.resolved = true;
                    }
                }
                boolean duplicate = false;
                for (EntityLink existing : result) {
                    if (existing.label != null && existing.label.equals(link.label)) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) {
                    result.add(link);
                }
            }
        }
        return result;
    }

    @Nullable
    public static String resolveTargetId(EntityDao dao, String campaignId, String name) {
        EntityRoom target = dao.resolveByName(campaignId, name);
        return target == null ? null : target.id;
    }
}
