package ru.dndmemory.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.*;
import java.util.UUID;

/** Поле внутри шаблона карточки. */
@Entity
@Table(name = "card_blocks")
@Getter
@Setter
@NoArgsConstructor
public class CardBlock {

    @Id
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private CardTemplate template;

    @Column(nullable = false, length = 48)
    private String field;

    @Column(nullable = false, length = 16)
    private String display = "TEXT";

    @Column(nullable = false)
    private float x;

    @Column(nullable = false)
    private float y;

    @Column(nullable = false)
    private float width = 100;

    @Column(nullable = false)
    private float height = 24;

    @Column(name = "font_size", nullable = false)
    private float fontSize = 14;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** Текстовая подпись, если блок не привязан к полю. */
    @Column(name = "text_value", columnDefinition = "text")
    private String textValue;

    @Column(length = 16)
    private String color;

    @Column(name = "z_index", nullable = false)
    private int zIndex;
}
