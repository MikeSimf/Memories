package ru.dndmemory.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.dndmemory.domain.extender.GameEntityExtender;
import ru.dndmemory.domain.extender.ImageExtender;
import ru.dndmemory.domain.extender.NamedExtender;
import ru.dndmemory.domain.extender.SyncExtended;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Защищает архитектурное решение: экстендеры — независимые интерфейсы, общее
 * состояние объявлено ровно один раз. Если кто-то снова свяжет их наследованием или
 * продублирует поля в классах, этот тест упадёт.
 */
class ObjectExtenderTest {

    private static final List<Class<?>> EXTENDERS = List.of(
            GameEntityExtender.class, NamedExtender.class, SyncExtended.class, ImageExtender.class);

    private static final List<Class<?>> ENTITIES = List.of(
            Hero.class, Character.class, Location.class, Item.class);

    private static final List<String> COMMON_FIELDS = List.of(
            "id", "campaignId", "name", "description", "imageFileId", "deleted",
            "createdAt", "updatedAt", "createdBy", "updatedBy", "revision");

    @Test
    @DisplayName("Экстендеры — интерфейсы, и ни один не наследует другой")
    void extendersAreIndependentInterfaces() {
        for (Class<?> extender : EXTENDERS) {
            assertTrue(extender.isInterface(), extender.getSimpleName() + " должен быть интерфейсом");
            for (Class<?> other : EXTENDERS) {
                if (other == extender) {
                    continue;
                }
                assertFalse(extender.isAssignableFrom(other),
                        extender.getSimpleName() + " не должен наследовать " + other.getSimpleName());
            }
        }
    }

    @Test
    @DisplayName("Каждый класс реализует сразу несколько контрактов")
    void entitiesImplementAllContracts() {
        for (Class<?> entity : ENTITIES) {
            for (Class<?> extender : EXTENDERS) {
                assertTrue(extender.isAssignableFrom(entity),
                        entity.getSimpleName() + " должен реализовывать " + extender.getSimpleName());
            }
        }
    }

    @Test
    @DisplayName("Общие поля объявлены только в базовом классе, а не в каждом")
    void commonStateIsDeclaredOnce() throws Exception {
        for (String field : COMMON_FIELDS) {
            assertNotNull(GameEntitySupport.class.getDeclaredField(field),
                    "поле " + field + " должно быть в GameEntitySupport");
            for (Class<?> entity : ENTITIES) {
                assertThrows(NoSuchFieldException.class, () -> entity.getDeclaredField(field),
                        entity.getSimpleName() + " не должен объявлять общее поле " + field);
            }
        }
    }
}
