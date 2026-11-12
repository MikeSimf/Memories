package ru.dndmemory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dndmemory.domain.CardBlock;
import ru.dndmemory.domain.CardTemplate;
import ru.dndmemory.repo.CardTemplateRepository;
import ru.dndmemory.web.ApiException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Хранение шаблонов карточек и выбор подходящего по формуле. */
@Service
@RequiredArgsConstructor
public class CardTemplateService {

    private final CardTemplateRepository repository;

    public static class BlockDto {
        public String id;
        public String field;
        public String display;
        public float x;
        public float y;
        public float width;
        public float height;
        public float fontSize;
        public int sortOrder;
        public String text;
        public String color;
        public int zIndex;
    }

    public static class CardDto {
        public String id;
        public String name;
        public String objectType;
        public String kind;
        public String formula;
        public int priority;
        public boolean isDefault;
        public List<BlockDto> blocks = new ArrayList<>();
    }

    public static class BlockRequest {
        public String field;
        public String display = "TEXT";
        public float x;
        public float y;
        public float width = 100;
        public float height = 24;
        public float fontSize = 14;
        public String text;
        public String color;
        public int zIndex;
    }

    public static class SaveRequest {
        public UUID id;
        public String name;
        public String objectType;
        public String kind = "LIST";
        public String formula = "true";
        public int priority;
        public boolean isDefault;
        public List<BlockRequest> blocks = new ArrayList<>();
    }

    @Transactional(readOnly = true)
    public List<CardDto> all() {
        List<CardDto> result = new ArrayList<>();
        for (CardTemplate template : repository.findAllByOrderByObjectTypeAscPriorityDesc()) {
            result.add(toDto(template));
        }
        return result;
    }

    @Transactional
    public CardDto save(SaveRequest request) {
        if (request.name == null || request.name.trim().isEmpty()) {
            throw ApiException.badRequest("Укажите название карточки");
        }
        if (request.objectType == null) {
            throw ApiException.badRequest("Укажите тип объекта");
        }
        CardTemplate template = request.id == null ? new CardTemplate()
                : repository.findById(request.id).orElseThrow(() -> ApiException.notFound("Карточка"));
        if (template.getId() == null) {
            template.setId(UUID.randomUUID());
            template.setCreatedAt(Instant.now());
        }
        template.setName(request.name.trim());
        template.setObjectType(request.objectType);
        template.setKind(request.kind == null ? "LIST" : request.kind);
        template.setFormula(request.formula == null || request.formula.isBlank()
                ? "true" : request.formula.trim());
        template.setPriority(Math.max(0, request.priority));
        template.setDefault(request.isDefault);
        template.setUpdatedAt(Instant.now());
        template.getBlocks().clear();
        int order = 0;
        for (BlockRequest blockRequest : request.blocks) {
            if (blockRequest.field == null || blockRequest.field.isBlank()) continue;
            CardBlock block = new CardBlock();
            block.setId(UUID.randomUUID());
            block.setTemplate(template);
            block.setField(blockRequest.field);
            block.setDisplay("ICON".equals(blockRequest.display) ? "ICON" : "TEXT");
            block.setX(blockRequest.x);
            block.setY(blockRequest.y);
            block.setWidth(blockRequest.width);
            block.setHeight(blockRequest.height);
            block.setFontSize(blockRequest.fontSize);
            block.setSortOrder(order++);
            block.setTextValue(blockRequest.text);
            block.setColor(blockRequest.color);
            block.setZIndex(blockRequest.zIndex);
            template.getBlocks().add(block);
        }
        template = repository.save(template);
        return toDto(template);
    }

    private CardDto toDto(CardTemplate template) {
        CardDto dto = new CardDto();
        dto.id = template.getId() == null ? null : template.getId().toString();
        dto.name = template.getName();
        dto.objectType = template.getObjectType();
        dto.kind = template.getKind();
        dto.formula = template.getFormula();
        dto.priority = template.getPriority();
        dto.isDefault = template.isDefault();
        for (CardBlock block : template.getBlocks()) {
            BlockDto item = new BlockDto();
            item.id = block.getId() == null ? null : block.getId().toString();
            item.field = block.getField();
            item.display = block.getDisplay();
            item.x = block.getX();
            item.y = block.getY();
            item.width = block.getWidth();
            item.height = block.getHeight();
            item.fontSize = block.getFontSize();
            item.sortOrder = block.getSortOrder();
            item.text = block.getTextValue();
            item.color = block.getColor();
            item.zIndex = block.getZIndex();
            dto.blocks.add(item);
        }
        return dto;
    }

    @Transactional
    public void delete(UUID id) {
        repository.deleteById(id);
    }

    /** Первая карточка по убыванию приоритета, чья формула истинна. */
    @Transactional(readOnly = true)
    public CardTemplate match(String objectType, String kind, Map<String, Object> fields) {
        List<CardTemplate> candidates = repository
                .findAllByObjectTypeAndKindOrderByPriorityDesc(objectType, kind);
        for (CardTemplate template : candidates) {
            if (template.isDefault() || CardFormula.matches(template.getFormula(), fields)) {
                return template;
            }
        }
        return null;
    }
}
