package ru.dndmemory.data.remote.dto;

import java.util.ArrayList;
import java.util.List;

/** Шаблон карточки списка. */
public class CardTemplateDto {
    public String id;
    public String name;
    public String objectType;
    public String kind;
    public String formula;
    public int priority;
    public boolean isDefault;
    public List<CardBlockDto> blocks = new ArrayList<>();
}
