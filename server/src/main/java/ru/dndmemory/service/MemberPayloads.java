package ru.dndmemory.service;

import ru.dndmemory.domain.Member;
import ru.dndmemory.domain.MemberStatus;

import java.util.LinkedHashMap;
import java.util.Map;

/** Форма записи участника в журнале синхронизации — одна для всех сервисов. */
public final class MemberPayloads {

    private MemberPayloads() {
    }

    public static Map<String, Object> of(Member member) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", member.getId());
        payload.put("campaignId", member.getCampaignId());
        payload.put("userId", member.getUserId());
        payload.put("role", member.getRole());
        payload.put("linkedHeroId", member.getLinkedHeroId());
        payload.put("status", member.getStatus());
        payload.put("deleted", member.getStatus() == MemberStatus.REMOVED);
        payload.put("updatedAt", member.getUpdatedAt());
        return payload;
    }
}
