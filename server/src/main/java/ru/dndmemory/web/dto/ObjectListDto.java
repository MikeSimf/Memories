package ru.dndmemory.web.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** Ответ списка объектов одного класса: страница и общее количество подходящих. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ObjectListDto<T> {

    private List<T> items;
    private long total;
}
