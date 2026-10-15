package ru.dndmemory.util;

import java.util.UUID;

/**
 * Идентификаторы генерирует клиент (docs/architecture.md, раздел 2):
 * офлайн-созданная запись сразу получает окончательный id.
 */
public final class Ids {

    private Ids() {
    }

    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    /** Идентификатор правки — ключ идемпотентности push. */
    public static String changeId() {
        return "c-" + UUID.randomUUID();
    }
}
