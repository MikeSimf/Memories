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
import ru.dndmemory.data.remote.dto.CampaignDto;
import ru.dndmemory.data.remote.dto.CharacterDto;
import ru.dndmemory.data.remote.dto.CreateCampaignRequest;
import ru.dndmemory.data.remote.dto.CreateInviteRequest;
import ru.dndmemory.data.remote.dto.CreateRetellingRequest;
import ru.dndmemory.data.remote.dto.FileDto;
import ru.dndmemory.data.remote.dto.HealthDto;
import ru.dndmemory.data.remote.dto.HeroDto;
import ru.dndmemory.data.remote.dto.InviteDto;
import ru.dndmemory.data.remote.dto.InvitePreviewDto;
import ru.dndmemory.data.remote.dto.ItemDto;
import ru.dndmemory.data.remote.dto.LocationDto;
import ru.dndmemory.data.remote.dto.LoginRequest;
import ru.dndmemory.data.remote.dto.MemberDto;
import ru.dndmemory.data.remote.dto.PatchCampaignRequest;
import ru.dndmemory.data.remote.dto.PatchMeRequest;
import ru.dndmemory.data.remote.dto.PatchMemberRequest;
import ru.dndmemory.data.remote.dto.PatchRetellingRequest;
import ru.dndmemory.data.remote.dto.PasswordResetRequest;
import ru.dndmemory.data.remote.dto.PullResponse;
import ru.dndmemory.data.remote.dto.PushRequest;
import ru.dndmemory.data.remote.dto.PushResponse;
import ru.dndmemory.data.remote.dto.RefreshRequest;
import ru.dndmemory.data.remote.dto.RegisterRequest;
import ru.dndmemory.data.remote.dto.RetellingDto;
import ru.dndmemory.data.remote.dto.SearchResponse;
import ru.dndmemory.data.remote.dto.SetPasswordRequest;
import ru.dndmemory.data.remote.dto.SnapshotResponse;
import ru.dndmemory.data.remote.dto.SyncStateDto;
import ru.dndmemory.data.remote.dto.TokenPairDto;
import ru.dndmemory.data.remote.dto.UserDto;
import ru.dndmemory.data.remote.dto.VersionDto;

/** Полный набор эндпоинтов docs/api.md (v2). Базовый путь — {@code /api}. */
public interface ApiService {

    // --- Служебные (docs/api.md, раздел 11) --------------------------------

    @GET("api/health")
    Call<HealthDto> health();

    @GET("api/version")
    Call<VersionDto> version();

    // --- Аутентификация: только логин и пароль (раздел 3) -------------------

    @POST("api/auth/register")
    Call<AuthResponse> register(@Body RegisterRequest body);

    @POST("api/auth/login")
    Call<AuthResponse> login(@Body LoginRequest body);

    @POST("api/auth/refresh")
    Call<TokenPairDto> refresh(@Body RefreshRequest body);

    @POST("api/auth/logout")
    Call<Void> logout(@Body RefreshRequest body);

    /** Ответ всегда {@code 202}: по нему нельзя узнать, есть ли такой адрес. */
    @POST("api/auth/password-reset/request")
    Call<Void> requestPasswordReset(@Body PasswordResetRequest body);

    @GET("api/me")
    Call<UserDto> me();

    @PATCH("api/me")
    Call<UserDto> patchMe(@Body PatchMeRequest body);

    @POST("api/me/password")
    Call<Void> setPassword(@Body SetPasswordRequest body);

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

    // --- Герои (раздел 6): создаёт, правит и удаляет только владелец --------

    @GET("api/campaigns/{id}/heroes")
    Call<List<HeroDto>> heroes(@Path("id") String campaignId,
                               @Query("q") String query,
                               @Query("updatedSince") String updatedSince,
                               @Query("limit") Integer limit,
                               @Query("offset") Integer offset);

    @POST("api/campaigns/{id}/heroes")
    Call<HeroDto> createHero(@Path("id") String campaignId, @Body HeroDto body);

    @GET("api/campaigns/{id}/heroes/{heroId}")
    Call<HeroDto> hero(@Path("id") String campaignId, @Path("heroId") String heroId);

    @PATCH("api/campaigns/{id}/heroes/{heroId}")
    Call<HeroDto> patchHero(@Path("id") String campaignId, @Path("heroId") String heroId,
                            @Header("If-Match") Integer ifMatchRevision,
                            @Body HeroDto body);

    @DELETE("api/campaigns/{id}/heroes/{heroId}")
    Call<Void> deleteHero(@Path("id") String campaignId, @Path("heroId") String heroId);

    // --- Персонажи (НПС) ---------------------------------------------------

    @GET("api/campaigns/{id}/characters")
    Call<List<CharacterDto>> characters(@Path("id") String campaignId,
                                        @Query("q") String query,
                                        @Query("updatedSince") String updatedSince,
                                        @Query("limit") Integer limit,
                                        @Query("offset") Integer offset);

    @POST("api/campaigns/{id}/characters")
    Call<CharacterDto> createCharacter(@Path("id") String campaignId,
                                       @Body CharacterDto body);

    @GET("api/campaigns/{id}/characters/{characterId}")
    Call<CharacterDto> character(@Path("id") String campaignId,
                                 @Path("characterId") String characterId);

    @PATCH("api/campaigns/{id}/characters/{characterId}")
    Call<CharacterDto> patchCharacter(@Path("id") String campaignId,
                                      @Path("characterId") String characterId,
                                      @Header("If-Match") Integer ifMatchRevision,
                                      @Body CharacterDto body);

    @DELETE("api/campaigns/{id}/characters/{characterId}")
    Call<Void> deleteCharacter(@Path("id") String campaignId,
                               @Path("characterId") String characterId);

    // --- Локации -----------------------------------------------------------

    @GET("api/campaigns/{id}/locations")
    Call<List<LocationDto>> locations(@Path("id") String campaignId,
                                      @Query("q") String query,
                                      @Query("updatedSince") String updatedSince,
                                      @Query("limit") Integer limit,
                                      @Query("offset") Integer offset);

    @POST("api/campaigns/{id}/locations")
    Call<LocationDto> createLocation(@Path("id") String campaignId,
                                     @Body LocationDto body);

    @GET("api/campaigns/{id}/locations/{locationId}")
    Call<LocationDto> location(@Path("id") String campaignId,
                               @Path("locationId") String locationId);

    @PATCH("api/campaigns/{id}/locations/{locationId}")
    Call<LocationDto> patchLocation(@Path("id") String campaignId,
                                    @Path("locationId") String locationId,
                                    @Header("If-Match") Integer ifMatchRevision,
                                    @Body LocationDto body);

    @DELETE("api/campaigns/{id}/locations/{locationId}")
    Call<Void> deleteLocation(@Path("id") String campaignId,
                              @Path("locationId") String locationId);

    // --- Предметы ----------------------------------------------------------

    @GET("api/campaigns/{id}/items")
    Call<List<ItemDto>> items(@Path("id") String campaignId,
                              @Query("q") String query,
                              @Query("updatedSince") String updatedSince,
                              @Query("limit") Integer limit,
                              @Query("offset") Integer offset);

    @POST("api/campaigns/{id}/items")
    Call<ItemDto> createItem(@Path("id") String campaignId, @Body ItemDto body);

    @GET("api/campaigns/{id}/items/{itemId}")
    Call<ItemDto> item(@Path("id") String campaignId, @Path("itemId") String itemId);

    @PATCH("api/campaigns/{id}/items/{itemId}")
    Call<ItemDto> patchItem(@Path("id") String campaignId, @Path("itemId") String itemId,
                            @Header("If-Match") Integer ifMatchRevision,
                            @Body ItemDto body);

    @DELETE("api/campaigns/{id}/items/{itemId}")
    Call<Void> deleteItem(@Path("id") String campaignId, @Path("itemId") String itemId);

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
                                @Query("kinds") String kinds,
                                @Query("limit") Integer limit);

    // --- Синхронизация (раздел 9, docs/sync.md) ---------------------------

    @GET("api/campaigns/{id}/sync/changes")
    Call<PullResponse> pullChanges(@Path("id") String campaignId,
                                   @Query("since") long since,
                                   @Query("limit") int limit,
                                   @Query("clientId") String clientId);

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
