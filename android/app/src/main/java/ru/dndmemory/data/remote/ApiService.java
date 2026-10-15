package ru.dndmemory.data.remote;

import java.util.List;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;
import ru.dndmemory.data.remote.dto.AcceptInviteResponse;
import ru.dndmemory.data.remote.dto.AddMemberRequest;
import ru.dndmemory.data.remote.dto.AddMemberResponse;
import ru.dndmemory.data.remote.dto.AuthResponse;
import ru.dndmemory.data.remote.dto.BacklinkDto;
import ru.dndmemory.data.remote.dto.CampaignDto;
import ru.dndmemory.data.remote.dto.CreateCampaignRequest;
import ru.dndmemory.data.remote.dto.CreateInviteRequest;
import ru.dndmemory.data.remote.dto.CreateRetellingRequest;
import ru.dndmemory.data.remote.dto.EntityDto;
import ru.dndmemory.data.remote.dto.FileDto;
import ru.dndmemory.data.remote.dto.HealthDto;
import ru.dndmemory.data.remote.dto.InviteDto;
import ru.dndmemory.data.remote.dto.InvitePreviewDto;
import ru.dndmemory.data.remote.dto.LoginRequest;
import ru.dndmemory.data.remote.dto.MemberDto;
import ru.dndmemory.data.remote.dto.OAuthCallbackRequest;
import ru.dndmemory.data.remote.dto.PatchCampaignRequest;
import ru.dndmemory.data.remote.dto.PatchMeRequest;
import ru.dndmemory.data.remote.dto.PatchMemberRequest;
import ru.dndmemory.data.remote.dto.PatchRetellingRequest;
import ru.dndmemory.data.remote.dto.ProvidersResponse;
import ru.dndmemory.data.remote.dto.PullResponse;
import ru.dndmemory.data.remote.dto.PushRequest;
import ru.dndmemory.data.remote.dto.PushResponse;
import ru.dndmemory.data.remote.dto.RefreshRequest;
import ru.dndmemory.data.remote.dto.RegisterRequest;
import ru.dndmemory.data.remote.dto.RetellingDto;
import ru.dndmemory.data.remote.dto.SearchResponse;
import ru.dndmemory.data.remote.dto.SnapshotResponse;
import ru.dndmemory.data.remote.dto.SyncStateDto;
import ru.dndmemory.data.remote.dto.TokenPairDto;
import ru.dndmemory.data.remote.dto.UserDto;
import ru.dndmemory.data.remote.dto.VersionDto;

/** Полный набор эндпоинтов docs/api.md. Базовый путь — {@code /api}. */
public interface ApiService {

    // --- Служебные (docs/api.md, раздел 11) --------------------------------

    @GET("api/health")
    Call<HealthDto> health();

    @GET("api/version")
    Call<VersionDto> version();

    // --- Аутентификация (раздел 3) -----------------------------------------

    @POST("api/auth/register")
    Call<AuthResponse> register(@Body RegisterRequest body);

    @POST("api/auth/login")
    Call<AuthResponse> login(@Body LoginRequest body);

    @POST("api/auth/refresh")
    Call<TokenPairDto> refresh(@Body RefreshRequest body);

    @POST("api/auth/logout")
    Call<Void> logout(@Body RefreshRequest body);

    @GET("api/me")
    Call<UserDto> me();

    @PATCH("api/me")
    Call<UserDto> patchMe(@Body PatchMeRequest body);

    @GET("api/auth/providers")
    Call<ProvidersResponse> providers();

    @POST("api/auth/oauth/{provider}/callback")
    Call<AuthResponse> oauthCallback(@Path("provider") String provider,
                                     @Body OAuthCallbackRequest body);

    // --- Кампании (раздел 4) -----------------------------------------------

    @GET("api/campaigns")
    Call<List<CampaignDto>> campaigns();

    @POST("api/campaigns")
    Call<CampaignDto> createCampaign(@Body CreateCampaignRequest body);

    @GET("api/campaigns/{id}")
    Call<CampaignDto> campaign(@Path("id") String campaignId);

    @PATCH("api/campaigns/{id}")
    Call<CampaignDto> patchCampaign(@Path("id") String campaignId,
                                    @Body PatchCampaignRequest body);

    @DELETE("api/campaigns/{id}")
    Call<Void> deleteCampaign(@Path("id") String campaignId);

    // --- Участники (раздел 5) ---------------------------------------------

    @GET("api/campaigns/{id}/members")
    Call<List<MemberDto>> members(@Path("id") String campaignId);

    @POST("api/campaigns/{id}/members")
    Call<AddMemberResponse> addMember(@Path("id") String campaignId,
                                      @Body AddMemberRequest body);

    @PATCH("api/campaigns/{id}/members/{memberId}")
    Call<MemberDto> patchMember(@Path("id") String campaignId,
                                @Path("memberId") String memberId,
                                @Body PatchMemberRequest body);

    @DELETE("api/campaigns/{id}/members/{memberId}")
    Call<Void> removeMember(@Path("id") String campaignId,
                            @Path("memberId") String memberId);

    // --- Приглашения (раздел 5) -------------------------------------------

    @POST("api/campaigns/{id}/invites")
    Call<InviteDto> createInvite(@Path("id") String campaignId,
                                 @Body CreateInviteRequest body);

    @GET("api/campaigns/{id}/invites")
    Call<List<InviteDto>> invites(@Path("id") String campaignId);

    @DELETE("api/campaigns/{id}/invites/{inviteId}")
    Call<Void> revokeInvite(@Path("id") String campaignId,
                            @Path("inviteId") String inviteId);

    /** Публичный предпросмотр приглашения — без авторизации. */
    @GET("api/invites/{token}")
    Call<InvitePreviewDto> invitePreview(@Path("token") String token);

    @POST("api/invites/{token}/accept")
    Call<AcceptInviteResponse> acceptInvite(@Path("token") String token);

    // --- Объекты (раздел 6) -----------------------------------------------

    @GET("api/campaigns/{id}/entities")
    Call<List<EntityDto>> entities(@Path("id") String campaignId,
                                   @Query("type") String type,
                                   @Query("q") String query,
                                   @Query("updatedSince") String updatedSince,
                                   @Query("limit") Integer limit,
                                   @Query("offset") Integer offset);

    @POST("api/campaigns/{id}/entities")
    Call<EntityDto> createEntity(@Path("id") String campaignId, @Body EntityDto body);

    @GET("api/campaigns/{id}/entities/{entityId}")
    Call<EntityDto> entity(@Path("id") String campaignId,
                           @Path("entityId") String entityId);

    @PATCH("api/campaigns/{id}/entities/{entityId}")
    Call<EntityDto> patchEntity(@Path("id") String campaignId,
                                @Path("entityId") String entityId,
                                @Header("If-Match") Integer ifMatchRevision,
                                @Body EntityDto body);

    @DELETE("api/campaigns/{id}/entities/{entityId}")
    Call<Void> deleteEntity(@Path("id") String campaignId,
                            @Path("entityId") String entityId);

    @GET("api/campaigns/{id}/entities/{entityId}/backlinks")
    Call<List<BacklinkDto>> backlinks(@Path("id") String campaignId,
                                      @Path("entityId") String entityId);

    // --- Пересказы (раздел 7) ---------------------------------------------

    @GET("api/campaigns/{id}/retellings")
    Call<List<RetellingDto>> retellings(@Path("id") String campaignId,
                                        @Query("q") String query,
                                        @Query("authorEntityId") String authorEntityId,
                                        @Query("limit") Integer limit,
                                        @Query("offset") Integer offset);

    @POST("api/campaigns/{id}/retellings")
    Call<RetellingDto> createRetelling(@Path("id") String campaignId,
                                       @Body CreateRetellingRequest body);

    @GET("api/campaigns/{id}/retellings/{retellingId}")
    Call<RetellingDto> retelling(@Path("id") String campaignId,
                                 @Path("retellingId") String retellingId);

    @PATCH("api/campaigns/{id}/retellings/{retellingId}")
    Call<RetellingDto> patchRetelling(@Path("id") String campaignId,
                                      @Path("retellingId") String retellingId,
                                      @Header("If-Match") Integer ifMatchRevision,
                                      @Body PatchRetellingRequest body);

    @DELETE("api/campaigns/{id}/retellings/{retellingId}")
    Call<Void> deleteRetelling(@Path("id") String campaignId,
                               @Path("retellingId") String retellingId);

    // --- Поиск (раздел 8) --------------------------------------------------

    @GET("api/campaigns/{id}/search")
    Call<SearchResponse> search(@Path("id") String campaignId,
                                @Query("q") String query,
                                @Query("types") String types,
                                @Query("limit") Integer limit);

    // --- Синхронизация (раздел 9, docs/sync.md) ---------------------------

    @GET("api/campaigns/{id}/sync/changes")
    Call<PullResponse> pullChanges(@Path("id") String campaignId,
                                   @Query("since") long since,
                                   @Query("limit") int limit);

    @POST("api/campaigns/{id}/sync/changes")
    Call<PushResponse> pushChanges(@Path("id") String campaignId,
                                   @Body PushRequest body);

    @GET("api/campaigns/{id}/sync/state")
    Call<SyncStateDto> syncState(@Path("id") String campaignId);

    @GET("api/campaigns/{id}/sync/snapshot")
    Call<SnapshotResponse> snapshot(@Path("id") String campaignId,
                                    @Query("limit") Integer limit,
                                    @Query("cursor") String cursor);

    // --- Файлы (раздел 10) -------------------------------------------------

    @Multipart
    @POST("api/files")
    Call<FileDto> uploadFile(@Part MultipartBody.Part file);
}
