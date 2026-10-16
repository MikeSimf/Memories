package ru.dndmemory.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Ключ сопоставления — правило самого класса, по нему синхронизация не создаёт дубли:
 * Hero, Location и Item сопоставляются по имени, Character — по имени и расе.
 */
class ObjectMatchKeyTest {

    private Hero hero(String name) {
        Hero hero = new Hero();
        hero.setId(UUID.randomUUID());
        hero.setName(name);
        return hero;
    }

    private Character character(String name, String race) {
        Character character = new Character();
        character.setId(UUID.randomUUID());
        character.setName(name);
        character.setRace(race);
        return character;
    }

    private Location location(String name) {
        Location location = new Location();
        location.setId(UUID.randomUUID());
        location.setName(name);
        return location;
    }

    private Item item(String name) {
        Item item = new Item();
        item.setId(UUID.randomUUID());
        item.setName(name);
        return item;
    }

    @Test
    @DisplayName("Hero сопоставляется по имени: регистр и лишние пробелы не важны")
    void heroMatchesByName() {
        assertEquals(hero("Лира Ксенофонт").matchKey(), hero("  лира   ксенофонт ").matchKey());
        assertNotEquals(hero("Лира Ксенофонт").matchKey(), hero("Силдар Холлвинтер").matchKey());
        assertEquals("matched_by_name", hero("Лира").matchReason());
    }

    @Test
    @DisplayName("Character сопоставляется по имени и расе")
    void characterMatchesByNameAndRace() {
        assertEquals(character("Стражник", "человек").matchKey(),
                character("стражник", " Человек ").matchKey());
        // одно имя, разная раса — это два разных персонажа
        assertNotEquals(character("Стражник", "человек").matchKey(),
                character("Стражник", "эльф").matchKey());
        assertNotEquals(character("Стражник", "человек").matchKey(), character("Стражник", null).matchKey());
        assertEquals("matched_by_name_and_race", character("Стражник", "человек").matchReason());
    }

    @Test
    @DisplayName("Location и Item сопоставляются по имени")
    void locationAndItemMatchByName() {
        assertEquals(location("Фандалин").matchKey(), location(" фандалин ").matchKey());
        assertEquals(item("Меч +1").matchKey(), item("меч  +1").matchKey());
        assertNotEquals(item("Меч +1").matchKey(), item("Щит +1").matchKey());
        assertEquals("matched_by_name", location("Фандалин").matchReason());
        assertEquals("matched_by_name", item("Меч +1").matchReason());
    }
}
