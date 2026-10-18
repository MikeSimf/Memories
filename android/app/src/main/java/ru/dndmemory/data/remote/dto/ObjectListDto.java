package ru.dndmemory.data.remote.dto;

import java.util.List;

/**
 * Ответ списка объектов одного класса (docs/api.md, раздел 6):
 * {@code {"items": […], "total": 27}}.
 *
 * <p>Раньше клиент ждал здесь «голый» массив и падал с
 * {@code Expected BEGIN_ARRAY but was BEGIN_OBJECT at line 1 column 2 path $},
 * хотя данные при этом доезжали синхронизацией.
 */
public class ObjectListDto<T> {

    public List<T> items;

    public long total;
}
