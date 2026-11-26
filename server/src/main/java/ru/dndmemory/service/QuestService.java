package ru.dndmemory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dndmemory.domain.Member;
import ru.dndmemory.domain.MemberRole;
import ru.dndmemory.repo.MemberRepository;
import ru.dndmemory.repo.UserRepository;
import ru.dndmemory.web.ApiException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Квесты кампании и комментарии к ним. */
@Service
@RequiredArgsConstructor
public class QuestService {

    private final JdbcTemplate jdbc;
    private final MemberRepository members;
    private final UserRepository users;

    public static class CommentDto {
        public String id;
        public String text;
        public String createdByName;
        public String createdAt;
        public String updatedByName;
        public String updatedAt;
    }

    public static class QuestDto {
        public String id;
        public String name;
        public String description;
        public String avatarFileId;
        public String createdByName;
        public String updatedByName;
        public String createdAt;
        public String updatedAt;
        public List<CommentDto> comments = new ArrayList<>();
    }

    public static class QuestRequest {
        public String name;
        public String description;
        public String avatarFileId;
    }

    public static class CommentRequest {
        public String text;
    }

    @Transactional(readOnly = true)
    public List<QuestDto> list(UUID campaignId, UUID userId) {
        requireMember(campaignId, userId);
        List<QuestDto> result = new ArrayList<>();
        jdbc.query("select q.*, cu.display_name as created_by_name, uu.display_name as updated_by_name from quests q "
                        + "left join users cu on cu.id = q.created_by left join users uu on uu.id = q.updated_by "
                        + "where q.campaign_id = ? and q.deleted = false order by lower(q.name)",
                rs -> {
                    QuestDto dto = new QuestDto();
                    dto.id = rs.getString("id");
                    dto.name = rs.getString("name");
                    dto.description = rs.getString("description");
                    dto.avatarFileId = rs.getString("avatar_file_id");
                    dto.createdByName = rs.getString("created_by_name");
                    dto.updatedByName = rs.getString("updated_by_name");
                    dto.createdAt = rs.getString("created_at");
                    dto.updatedAt = rs.getString("updated_at");
                    result.add(dto);
                }, campaignId);
        return result;
    }

    @Transactional(readOnly = true)
    public QuestDto get(UUID campaignId, UUID questId, UUID userId) {
        requireMember(campaignId, userId);
        QuestDto dto = jdbc.query("select q.*, cu.display_name as created_by_name, uu.display_name as updated_by_name "
                        + "from quests q left join users cu on cu.id = q.created_by left join users uu on uu.id = q.updated_by "
                        + "where q.id = ? and q.campaign_id = ? and q.deleted = false",
                rs -> {
                    if (!rs.next()) return null;
                    QuestDto x = new QuestDto();
                    x.id = rs.getString("id");
                    x.name = rs.getString("name");
                    x.description = rs.getString("description");
                    x.avatarFileId = rs.getString("avatar_file_id");
                    x.createdByName = rs.getString("created_by_name");
                    x.updatedByName = rs.getString("updated_by_name");
                    x.createdAt = rs.getString("created_at");
                    x.updatedAt = rs.getString("updated_at");
                    return x;
                }, questId, campaignId);
        if (dto == null) throw ApiException.notFound("Квест не найден");
        jdbc.query("select c.*, cu.display_name as created_by_name, uu.display_name as updated_by_name "
                        + "from quest_comments c left join users cu on cu.id = c.created_by "
                        + "left join users uu on uu.id = c.updated_by "
                        + "where c.quest_id = ? and c.deleted = false order by c.created_at",
                rs -> {
                    CommentDto comment = new CommentDto();
                    comment.id = rs.getString("id");
                    comment.text = rs.getString("text");
                    comment.createdByName = rs.getString("created_by_name");
                    comment.createdAt = rs.getString("created_at");
                    comment.updatedByName = rs.getString("updated_by_name");
                    comment.updatedAt = rs.getString("updated_at");
                    dto.comments.add(comment);
                }, questId);
        return dto;
    }

    @Transactional
    public QuestDto create(UUID campaignId, UUID userId, QuestRequest request) {
        requireWriter(campaignId, userId);
        if (request.name == null || request.name.trim().isEmpty()) throw ApiException.validation("Укажите название квеста");
        UUID id = UUID.randomUUID();
        jdbc.update("insert into quests (id, campaign_id, name, description, avatar_file_id, created_by, updated_by) "
                        + "values (?, ?, ?, ?, ?, ?, ?)",
                id, campaignId, request.name.trim(), request.description, uuidOrNull(request.avatarFileId), userId, userId);
        return get(campaignId, id, userId);
    }

    @Transactional
    public QuestDto update(UUID campaignId, UUID questId, UUID userId, QuestRequest request) {
        requireWriter(campaignId, userId);
        if (request.name == null || request.name.trim().isEmpty()) throw ApiException.validation("Укажите название квеста");
        int rows = jdbc.update("update quests set name = ?, description = ?, avatar_file_id = ?, updated_by = ?, updated_at = now() "
                        + "where id = ? and campaign_id = ? and deleted = false",
                request.name.trim(), request.description, uuidOrNull(request.avatarFileId), userId, questId, campaignId);
        if (rows == 0) throw ApiException.notFound("Квест не найден");
        return get(campaignId, questId, userId);
    }

    @Transactional
    public void delete(UUID campaignId, UUID questId, UUID userId) {
        requireWriter(campaignId, userId);
        jdbc.update("update quests set deleted = true, updated_at = now() where id = ? and campaign_id = ?", questId, campaignId);
    }

    @Transactional
    public CommentDto addComment(UUID campaignId, UUID questId, UUID userId, CommentRequest request) {
        requireWriter(campaignId, userId);
        if (request.text == null || request.text.trim().isEmpty()) throw ApiException.validation("Введите комментарий");
        UUID id = UUID.randomUUID();
        jdbc.update("insert into quest_comments (id, quest_id, text, created_by) values (?, ?, ?, ?)",
                id, questId, request.text.trim(), userId);
        CommentDto dto = new CommentDto();
        dto.id = id.toString();
        dto.text = request.text.trim();
        dto.createdByName = users.findById(userId).map(u -> u.getDisplayName()).orElse(null);
        return dto;
    }

    @Transactional
    public void updateComment(UUID campaignId, UUID questId, UUID commentId, UUID userId, CommentRequest request) {
        requireWriter(campaignId, userId);
        jdbc.update("update quest_comments set text = ?, updated_by = ?, updated_at = now() where id = ? and quest_id = ?",
                request.text == null ? "" : request.text.trim(), userId, commentId, questId);
    }

    @Transactional
    public void deleteComment(UUID campaignId, UUID questId, UUID commentId, UUID userId) {
        requireWriter(campaignId, userId);
        jdbc.update("update quest_comments set deleted = true where id = ? and quest_id = ?", commentId, questId);
    }

    private Member requireMember(UUID campaignId, UUID userId) {
        return members.findByCampaignIdAndUserId(campaignId, userId)
                .filter(m -> m.getStatus() == ru.dndmemory.domain.MemberStatus.ACTIVE)
                .orElseThrow(() -> ApiException.forbidden("Нет доступа к кампании"));
    }

    private void requireWriter(UUID campaignId, UUID userId) {
        Member member = requireMember(campaignId, userId);
        if (member.getRole() == MemberRole.VIEWER) throw ApiException.forbidden("Наблюдатель не может менять квесты");
    }

    private static UUID uuidOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try { return UUID.fromString(value); } catch (RuntimeException e) { return null; }
    }
}
