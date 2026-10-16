package ru.dndmemory.domain.extender;

import java.util.UUID;

/**
 * Объект справочника: идентификатор и кампания, которой он принадлежит.
 *
 * <p>Экстендеры намеренно <b>не связаны между собой</b>: это независимые контракты,
 * и класс объекта реализует ровно те из них, которые ему нужны. Общее состояние
 * живёт в одном месте — {@link ru.dndmemory.domain.GameEntitySupport}, потому что
 * Java не поддерживает множественное наследование полей.</p>
 */
public interface GameEntityExtender {

    UUID getId();

    void setId(UUID id);

    UUID getCampaignId();

    void setCampaignId(UUID campaignId);
}
