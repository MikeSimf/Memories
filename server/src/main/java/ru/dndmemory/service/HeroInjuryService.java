package ru.dndmemory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dndmemory.domain.Hero;
import ru.dndmemory.domain.Member;
import ru.dndmemory.domain.MemberRole;
import ru.dndmemory.repo.HeroRepository;
import ru.dndmemory.repo.MemberRepository;
import ru.dndmemory.web.ApiException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Травмы и увечья героя: короткие текстовые записи. */
@Service
@RequiredArgsConstructor
public class HeroInjuryService {

    private final JdbcTemplate jdbc;
    private final MemberRepository members;
    private final HeroRepository heroes;

    public static class InjuryDto {
        public String id;
        public String text;
    }

    public static class InjuryRequest {
        public String text;
    }

    @Transactional(readOnly = true)
    public List<InjuryDto> list(UUID campaignId, UUID heroId, UUID userId) {
        requireRead(campaignId, heroId, userId);
        List<InjuryDto> result = new ArrayList<>();
        jdbc.query("select id, text from hero_injuries where hero_id = ? order by created_at",
                rs -> {
                    InjuryDto dto = new InjuryDto();
                    dto.id = rs.getString("id");
                    dto.text = rs.getString("text");
                    result.add(dto);
                }, heroId);
        return result;
    }

    @Transactional
    public InjuryDto add(UUID campaignId, UUID heroId, UUID userId, InjuryRequest request) {
        requireWrite(campaignId, heroId, userId);
        if (request.text == null || request.text.trim().isEmpty()) {
            throw ApiException.validation("Введите текст травмы");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("insert into hero_injuries (id, hero_id, text, created_by) values (?, ?, ?, ?)",
                id, heroId, request.text.trim(), userId);
        InjuryDto dto = new InjuryDto();
        dto.id = id.toString();
        dto.text = request.text.trim();
        return dto;
    }

    @Transactional
    public void update(UUID campaignId, UUID heroId, UUID injuryId, UUID userId, InjuryRequest request) {
        requireWrite(campaignId, heroId, userId);
        jdbc.update("update hero_injuries set text = ?, updated_at = now() where id = ? and hero_id = ?",
                request.text == null ? "" : request.text.trim(), injuryId, heroId);
    }

    @Transactional
    public void delete(UUID campaignId, UUID heroId, UUID injuryId, UUID userId) {
        requireWrite(campaignId, heroId, userId);
        jdbc.update("delete from hero_injuries where id = ? and hero_id = ?", injuryId, heroId);
    }

    private void requireRead(UUID campaignId, UUID heroId, UUID userId) {
        members.findByCampaignIdAndUserId(campaignId, userId)
                .filter(m -> m.getStatus() == ru.dndmemory.domain.MemberStatus.ACTIVE)
                .orElseThrow(() -> ApiException.forbidden("Нет доступа к кампании"));
        requireHero(campaignId, heroId);
    }

    private void requireWrite(UUID campaignId, UUID heroId, UUID userId) {
        Member member = members.findByCampaignIdAndUserId(campaignId, userId)
                .orElseThrow(() -> ApiException.forbidden("Нет доступа к кампании"));
        Hero hero = requireHero(campaignId, heroId);
        boolean manager = member.getRole() == MemberRole.OWNER || member.getRole() == MemberRole.EDITOR_MASTER;
        boolean player = member.getRole() == MemberRole.EDITOR_HERO
                && hero.getPlayerUserId() != null && hero.getPlayerUserId().equals(userId);
        if (!manager && !player) throw ApiException.forbidden("Нет прав на героя");
    }

    private Hero requireHero(UUID campaignId, UUID heroId) {
        Hero hero = heroes.findById(heroId).orElseThrow(() -> ApiException.notFound("Герой не найден"));
        if (!campaignId.equals(hero.getCampaignId()) || hero.isDeleted()) {
            throw ApiException.notFound("Герой не найден");
        }
        return hero;
    }
}
