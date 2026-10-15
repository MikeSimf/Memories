package ru.dndmemory.domain;

/**
 * Состояние пересказа. DRAFT существует только на устройстве автора и на сервер
 * не отправляется (см. docs/sync.md), поэтому на сервере хранятся только PUBLISHED.
 */
public enum RetellingStatus {
    DRAFT,
    PUBLISHED
}
