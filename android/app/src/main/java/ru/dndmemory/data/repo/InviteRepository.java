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
 * (docs/api.md, раздел 5; docs/roles.md, раздел 3).
 */
public class InviteRepository {

    private final ApiService api;
    private final AppExecutors exec;

    public InviteRepository(ApiService api, AppExecutors exec) {
        this.api = api;
        this.exec = exec;
    }

    public void create(String campaignId, String role, @Nullable String linkedEntityId,
                       int expiresInHours, int maxUses, RepoCallback<InviteDto> callback) {
        exec.io(() -> ApiCall.execute(api.createInvite(campaignId,
                new CreateInviteRequest(role, linkedEntityId, expiresInHours, maxUses))), callback);
    }

    public void createDefault(String campaignId, String role, @Nullable String linkedEntityId,
                              RepoCallback<InviteDto> callback) {
        create(campaignId, role, linkedEntityId, 168, 1, callback);
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

    public void accept(String token, RepoCallback<AcceptInviteResponse> callback) {
        exec.io(() -> ApiCall.execute(api.acceptInvite(token)), callback);
    }
}
