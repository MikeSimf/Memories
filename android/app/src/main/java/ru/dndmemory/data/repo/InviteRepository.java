package ru.dndmemory.data.repo;

import androidx.annotation.Nullable;

import java.util.List;

import ru.dndmemory.data.remote.ApiCall;
import ru.dndmemory.data.remote.ApiService;
import ru.dndmemory.data.remote.dto.AcceptInviteResponse;
import ru.dndmemory.data.remote.dto.CreateInviteRequest;
import ru.dndmemory.data.remote.dto.InviteDto;
import ru.dndmemory.data.remote.dto.InvitePreviewDto;
import ru.dndmemory.util.AppExecutors;
import ru.dndmemory.util.RepoCallback;

/**
 * Приглашения по QR: создание, список, отзыв, публичный предпросмотр и принятие
 * (docs/api.md, раздел 5; docs/roles.md). Приглашение строго одноразовое.
 */
public class InviteRepository {

    private static final int DEFAULT_EXPIRES_HOURS = 168;

    private final ApiService api;
    private final AppExecutors exec;

    public InviteRepository(ApiService api, AppExecutors exec) {
        this.api = api;
        this.exec = exec;
    }

    public void create(String campaignId, String role, @Nullable String linkedHeroId,
                       int expiresInHours, RepoCallback<InviteDto> callback) {
        int hours = expiresInHours <= 0 ? DEFAULT_EXPIRES_HOURS : expiresInHours;
        exec.io(() -> ApiCall.execute(api.createInvite(campaignId,
                new CreateInviteRequest(role, linkedHeroId, hours))), callback);
    }

    public void createDefault(String campaignId, String role, @Nullable String linkedHeroId,
                              RepoCallback<InviteDto> callback) {
        create(campaignId, role, linkedHeroId, DEFAULT_EXPIRES_HOURS, callback);
    }

    public void list(String campaignId, RepoCallback<List<InviteDto>> callback) {
        exec.io(() -> ApiCall.execute(api.invites(campaignId)), callback);
    }

    public void revoke(String campaignId, String inviteId, RepoCallback<Void> callback) {
        exec.io(() -> {
            ApiCall.execute(api.revokeInvite(campaignId, inviteId));
            return null;
        }, callback);
    }

    /** Публичный предпросмотр — работает и без входа в аккаунт. */
    public void preview(String token, RepoCallback<InvitePreviewDto> callback) {
        exec.io(() -> ApiCall.execute(api.invitePreview(token)), callback);
    }

    /** Принятие приглашения: нужна авторизация (docs/api.md, раздел 5). */
    public void accept(String token, RepoCallback<AcceptInviteResponse> callback) {
        exec.io(() -> ApiCall.execute(api.acceptInvite(token)), callback);
    }
}
