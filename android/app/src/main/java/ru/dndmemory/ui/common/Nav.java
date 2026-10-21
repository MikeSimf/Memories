package ru.dndmemory.ui.common;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ru.dndmemory.ui.auth.AuthActivity;
import ru.dndmemory.ui.auth.PasswordResetActivity;
import ru.dndmemory.ui.campaign.CampaignActivity;
import ru.dndmemory.ui.campaigns.CampaignListActivity;
import ru.dndmemory.ui.entity.EntityDetailActivity;
import ru.dndmemory.ui.entity.EntityEditActivity;
import ru.dndmemory.ui.invite.InviteAcceptActivity;
import ru.dndmemory.ui.invite.InviteActivity;
import ru.dndmemory.ui.material.MaterialEditActivity;
import ru.dndmemory.ui.members.MembersActivity;
import ru.dndmemory.ui.retelling.RetellingDetailActivity;
import ru.dndmemory.ui.retelling.RetellingEditActivity;
import ru.dndmemory.ui.retelling.RetellingListActivity;
import ru.dndmemory.ui.search.SearchActivity;
import ru.dndmemory.ui.settings.SettingsActivity;

/** Переходы между экранами в одном месте. */
public final class Nav {

    private Nav() {
    }

    public static Intent auth(@NonNull Context context, @Nullable String inviteToken) {
        return auth(context, inviteToken, null);
    }

    /**
     * Экран входа.
     *
     * @param inviteToken приглашение, которое нужно принять после входа
     * @param message     пояснение, например причина обрыва сессии
     */
    public static Intent auth(@NonNull Context context, @Nullable String inviteToken,
                              @Nullable String message) {
        Intent intent = new Intent(context, AuthActivity.class);
        if (inviteToken != null && !inviteToken.isEmpty()) {
            intent.putExtra(Extras.INVITE_TOKEN, inviteToken);
        }
        if (message != null && !message.isEmpty()) {
            intent.putExtra(Extras.MESSAGE, message);
        }
        return intent;
    }

    /**
     * Экран входа сразу после выхода из аккаунта. Стек очищается: иначе кнопка
     * «назад» вернёт в список кампаний, которого больше нет.
     */
    public static void authAfterLogout(@NonNull Context context) {
        Intent intent = auth(context, null);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
    }

    /** Новый фото-материал кампании. */
    public static void createMaterial(@NonNull Context context, @NonNull String campaignId) {
        Intent intent = new Intent(context, MaterialEditActivity.class);
        intent.putExtra(Extras.CAMPAIGN_ID, campaignId);
        intent.putExtra(Extras.CREATE_NEW, true);
        context.startActivity(intent);
    }

    /** Правка существующего материала. */
    public static void editMaterial(@NonNull Context context, @NonNull String campaignId,
                                    @NonNull String materialId) {
        Intent intent = new Intent(context, MaterialEditActivity.class);
        intent.putExtra(Extras.CAMPAIGN_ID, campaignId);
        intent.putExtra(Extras.MATERIAL_ID, materialId);
        context.startActivity(intent);
    }

    /** Сброс пароля: письма нет, ссылку передают администратору. */
    public static void openPasswordReset(@NonNull Context context) {
        context.startActivity(new Intent(context, PasswordResetActivity.class));
    }

    public static void openCampaignList(@NonNull Context context) {
        Intent intent = new Intent(context, CampaignListActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(intent);
    }

    public static void openCampaign(@NonNull Context context, @NonNull String campaignId) {
        Intent intent = new Intent(context, CampaignActivity.class);
        intent.putExtra(Extras.CAMPAIGN_ID, campaignId);
        context.startActivity(intent);
    }

    public static void openEntity(@NonNull Context context, @NonNull String campaignId,
                                  @NonNull String entityId) {
        Intent intent = new Intent(context, EntityDetailActivity.class);
        intent.putExtra(Extras.CAMPAIGN_ID, campaignId);
        intent.putExtra(Extras.ENTITY_ID, entityId);
        context.startActivity(intent);
    }

    /** Новый объект: {@code entityId} пустой, класс задан. */
    public static void createEntity(@NonNull Context context, @NonNull String campaignId,
                                    @Nullable String kind) {
        Intent intent = new Intent(context, EntityEditActivity.class);
        intent.putExtra(Extras.CAMPAIGN_ID, campaignId);
        intent.putExtra(Extras.ENTITY_TYPE, kind);
        intent.putExtra(Extras.CREATE_NEW, true);
        context.startActivity(intent);
    }

    public static void editEntity(@NonNull Context context, @NonNull String campaignId,
                                  @NonNull String entityId) {
        Intent intent = new Intent(context, EntityEditActivity.class);
        intent.putExtra(Extras.CAMPAIGN_ID, campaignId);
        intent.putExtra(Extras.ENTITY_ID, entityId);
        context.startActivity(intent);
    }

    public static void openRetelling(@NonNull Context context, @NonNull String campaignId,
                                     @NonNull String retellingId) {
        Intent intent = new Intent(context, RetellingDetailActivity.class);
        intent.putExtra(Extras.CAMPAIGN_ID, campaignId);
        intent.putExtra(Extras.RETELLING_ID, retellingId);
        context.startActivity(intent);
    }

    /** Новый пересказ: создаётся черновик. */
    public static void createRetelling(@NonNull Context context, @NonNull String campaignId) {
        Intent intent = new Intent(context, RetellingEditActivity.class);
        intent.putExtra(Extras.CAMPAIGN_ID, campaignId);
        intent.putExtra(Extras.CREATE_NEW, true);
        context.startActivity(intent);
    }

    /** Редактирование пересказа: либо существующий черновик, либо правка опубликованного. */
    public static void editRetelling(@NonNull Context context, @NonNull String campaignId,
                                     @Nullable String draftId, @Nullable String retellingId) {
        Intent intent = new Intent(context, RetellingEditActivity.class);
        intent.putExtra(Extras.CAMPAIGN_ID, campaignId);
        if (draftId != null) {
            intent.putExtra(Extras.DRAFT_ID, draftId);
        }
        if (retellingId != null) {
            intent.putExtra(Extras.RETELLING_ID, retellingId);
        }
        context.startActivity(intent);
    }

    public static void openInvites(@NonNull Context context, @NonNull String campaignId) {
        Intent intent = new Intent(context, InviteActivity.class);
        intent.putExtra(Extras.CAMPAIGN_ID, campaignId);
        context.startActivity(intent);
    }

    /** Пересказы и участники — отдельные экраны в меню шапки кампании. */
    public static void openRetellings(@NonNull Context context, @NonNull String campaignId) {
        Intent intent = new Intent(context, RetellingListActivity.class);
        intent.putExtra(Extras.CAMPAIGN_ID, campaignId);
        context.startActivity(intent);
    }

    public static void openHeroes(@NonNull Context context, @NonNull String campaignId) {
        Intent intent = new Intent(context, ru.dndmemory.ui.entity.HeroListActivity.class);
        intent.putExtra(Extras.CAMPAIGN_ID, campaignId);
        context.startActivity(intent);
    }

    public static void openMaterials(@NonNull Context context, @NonNull String campaignId) {
        Intent intent = new Intent(context, ru.dndmemory.ui.material.MaterialsActivity.class);
        intent.putExtra(Extras.CAMPAIGN_ID, campaignId);
        context.startActivity(intent);
    }

    public static void openMembers(@NonNull Context context, @NonNull String campaignId) {
        Intent intent = new Intent(context, MembersActivity.class);
        intent.putExtra(Extras.CAMPAIGN_ID, campaignId);
        context.startActivity(intent);
    }

    /** Общий поиск по кампании или поиск внутри вкладки. */
    public static void openSearch(@NonNull Context context, @NonNull String campaignId,
                                  @Nullable String kindFilter) {
        Intent intent = new Intent(context, SearchActivity.class);
        intent.putExtra(Extras.CAMPAIGN_ID, campaignId);
        if (kindFilter != null) {
            intent.putExtra(Extras.SEARCH_TYPE, kindFilter);
        }
        context.startActivity(intent);
    }

    public static void openSettings(@NonNull Context context) {
        context.startActivity(new Intent(context, SettingsActivity.class));
    }

    /** Экран принятия одноразового приглашения (QR или deep link). */
    public static void openInviteAccept(@NonNull Context context, @NonNull String token) {
        Intent intent = new Intent(context, InviteAcceptActivity.class);
        intent.putExtra(Extras.INVITE_TOKEN, token);
        context.startActivity(intent);
    }
}
