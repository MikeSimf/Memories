package ru.dndmemory.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Имя уходит в заголовок {@code Content-Disposition}. Tomcat не умеет кодировать в
 * нём не-ASCII и выбрасывает весь заголовок, поэтому в {@code filename=} обязано
 * приходить только латиница — на это и проверяется транслит.
 */
class FileNamesTest {

    @Test
    @DisplayName("Кириллическое имя файла без названия объекта транслитеруется")
    void transliteratesStoredName() {
        // Так выглядит файл, загруженный с телефона: название не передано.
        assertEquals("melissa.png", FileNames.forDownload(null, "Мелисса.png"));
        assertEquals("melissa", FileNames.forDownload(null, "Мелисса"));
    }

    @Test
    @DisplayName("Название объекта важнее имени файла, расширение берётся от файла")
    void prefersTitleKeepsExtension() {
        assertEquals("melissa.pdf", FileNames.forDownload("Мелисса", "扫描件.pdf"));
        assertEquals("kartochka-melissy.png", FileNames.forDownload("Карточка Мелиссы", "IMG_0042.PNG"));
    }

    @Test
    @DisplayName("В filename= не попадает ни один символ вне ASCII")
    void resultIsAlwaysAscii() {
        String[] names = {"Мелисса.png", "Карточка Мелиссы.png", "🐉 Дракон ٪.docx",
                "Йёжик — щур & цыплёнок.xlsx", "три  пробела.jpeg", "###.bin", ""};
        for (String name : names) {
            String result = FileNames.forDownload(name.isEmpty() ? null : name, name);
            for (char c : result.toCharArray()) {
                assertTrue(c < 128, "символ " + (int) c + " в имени " + result + " сломает заголовок");
            }
        }
    }

    @Test
    @DisplayName("Из мусора получается не пустое имя")
    void neverReturnsEmptyName() {
        assertEquals("file", FileNames.forDownload(null, null));
        assertEquals("file", FileNames.forDownload("###", "@@@"));
        assertEquals("archive.zip", FileNames.forDownload("??? ", "archive.zip"));
    }
}
