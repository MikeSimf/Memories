package ru.dndmemory.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Снимок обходит шесть видов объектов курсором «вид:смещение». Если вид теряется при
 * обратном разборе, клиент возвращается на страницу раньше и не может дойти до
 * пересказов — на этом и ловится регресс.
 */
class SyncCursorTest {

    @Test
    @DisplayName("Курсор возвращается как был для всех шести видов объектов")
    void roundTripsEveryKind() {
        for (int kind = 0; kind <= 5; kind++) {
            String cursor = SyncService.encodeCursor(kind, 500);
            assertEquals(kind, SyncService.decodeKind(cursor), "вид " + kind);
            assertEquals(500, SyncService.decodeOffset(cursor));
        }
    }

    @Test
    @DisplayName("Вторая страница пересказов остаётся пересказами, а не материалами")
    void keepsRetellingsPage() {
        String secondPage = SyncService.encodeCursor(5, 20);
        assertEquals(5, SyncService.decodeKind(secondPage));
        assertEquals(20, SyncService.decodeOffset(secondPage));
    }

    @Test
    @DisplayName("Пустой и мусорный курсор читаются как начало")
    void fallsBackToStart() {
        assertEquals(0, SyncService.decodeKind(null));
        assertEquals(0, SyncService.decodeOffset(null));
        assertEquals(0, SyncService.decodeKind(""));
        assertEquals(0, SyncService.decodeOffset("не курсор"));
    }

    @Test
    @DisplayName("Выход за границы вида и смещения ограничивается, а не роняет снимок")
    void clampsOutOfRange() {
        assertEquals(5, SyncService.decodeKind(SyncService.encodeCursor(99, 1)));
        assertEquals(0, SyncService.decodeKind(SyncService.encodeCursor(-4, 1)));
        assertEquals(0, SyncService.decodeOffset(SyncService.encodeCursor(1, -7)));
    }
}
