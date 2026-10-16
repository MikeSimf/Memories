package ru.dndmemory.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.dndmemory.domain.extender.GameEntityExtender;
import ru.dndmemory.domain.extender.ImageExtender;
import ru.dndmemory.domain.extender.NamedExtender;
import ru.dndmemory.domain.extender.SyncExtended;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Единственное место, где объявлено общее состояние объектов справочника.
 *
 * <p>Экстендеры ({@link GameEntityExtender}, {@link NamedExtender},
 * {@link SyncExtended}, {@link ImageExtender}) — независимые интерфейсы, а этот
 * класс их реализует: Java не позволяет наследовать поля от нескольких классов,
 * поэтому хранение собрано здесь, а контракты остались раздельными. Герой, персонаж,
 * локация и предмет наследуют этот класс и объявляют свои поля.</p>
 *
 * <p>Новое общее поле добавляется одной правкой здесь и одной миграцией — все четыре
 * таблицы получают колонку заодно.</p>
 */
@MappedSuperclass
@Getter
@Setter
@NoArgsConstructor
public abstract class GameEntitySupport
        implements GameEntityExtender, NamedExtender, SyncExtended, ImageExtender {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "image_file_id")
    private UUID imageFileId;

    @Column(nullable = false)
    private boolean deleted;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(nullable = false)
    private long revision;

    /** Ключ сопоставления при синхронизации — обязателен для каждого класса. */
    @Override
    public abstract String matchKey();

    /** Часть ключа: нижний регистр, схлопнутые пробелы, обрезанные края. */
    protected static String normalizePart(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
