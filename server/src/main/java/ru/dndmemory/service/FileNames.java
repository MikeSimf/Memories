package ru.dndmemory.service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Имена скачиваемых файлов: транслит с русского и обрезка длинных названий.
 *
 * <p>Нужно, чтобы вместо {@code document.pdf} и {@code 9f3c…} пользователь видел
 * понятное имя: «Карта Фандалина.pdf». Транслит латиницей — потому что имя
 * уходит в заголовок {@code Content-Disposition} и в файловую систему телефона.
 */
public final class FileNames {

    /** Предел длины основы имени: остальное обрезаем, расширение сохраняем. */
    private static final int MAX_BASE = 60;

    private static final Map<Character, String> TRANSLIT = new HashMap<>();

    static {
        String[] ru = {"а", "б", "в", "г", "д", "е", "ё", "ж", "з", "и", "й", "к", "л", "м",
                "н", "о", "п", "р", "с", "т", "у", "ф", "х", "ц", "ч", "ш", "щ", "ъ", "ы",
                "ь", "э", "ю", "я"};
        String[] lat = {"a", "b", "v", "g", "d", "e", "e", "zh", "z", "i", "y", "k", "l", "m",
                "n", "o", "p", "r", "s", "t", "u", "f", "h", "ts", "ch", "sh", "sch", "", "y",
                "", "e", "yu", "ya"};
        for (int i = 0; i < ru.length; i++) {
            TRANSLIT.put(ru[i].charAt(0), lat[i]);
        }
    }

    private FileNames() {
    }

    /**
     * Имя для скачивания: транслит названия плюс расширение исходного файла.
     *
     * @param title       название материала или объекта
     * @param originalName исходное имя файла: из него берётся расширение
     */
    public static String forDownload(String title, String originalName) {
        String extension = extensionOf(originalName);
        String base = slug(title);
        if (base.isEmpty()) {
            base = slug(stripExtension(originalName));
        }
        if (base.isEmpty()) {
            base = "file";
        }
        if (base.length() > MAX_BASE) {
            base = base.substring(0, MAX_BASE);
        }
        return extension.isEmpty() ? base : base + "." + extension;
    }

    /** Латиница в нижнем регистре, слова через дефис, без мусорных символов. */
    public static String slug(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        boolean lastDash = false;
        for (char raw : value.toLowerCase(Locale.ROOT).toCharArray()) {
            String mapped = TRANSLIT.get(raw);
            if (mapped != null) {
                out.append(mapped);
                lastDash = false;
                continue;
            }
            if ((raw >= 'a' && raw <= 'z') || (raw >= '0' && raw <= '9')) {
                out.append(raw);
                lastDash = false;
            } else if (!lastDash && out.length() > 0) {
                out.append('-');
                lastDash = true;
            }
        }
        String result = out.toString();
        while (result.endsWith("-")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    public static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT).trim();
        if (extension.isEmpty() || extension.length() > 8) {
            return "";
        }
        for (char c : extension.toCharArray()) {
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'))) {
                return "";
            }
        }
        return extension;
    }

    private static String stripExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot <= 0 ? fileName : fileName.substring(0, dot);
    }
}
