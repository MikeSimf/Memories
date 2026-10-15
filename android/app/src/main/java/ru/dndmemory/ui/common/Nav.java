package ru.dndmemory.ui.common;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ru.dndmemory.ui.auth.AuthActivity;
import ru.dndmemory.ui.campaign.CampaignActivity;
import ru.dndmemory.ui.campaigns.CampaignListActivity;
import ru.dndmemory.ui.entity.EntityDetailActivity;
import ru.dndmemory.ui.entity.EntityEditActivity;
import ru.dndmemory.ui.invite.InviteAcceptActivity;
import ru.dndmemory.ui.invite.InviteActivity;
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
        Intent intent = new Intent(context, AuthActivity.class);
        if (inviteToken != null && !inviteToken.isEmpty()) {
            intent.putExtra(Extras.INVITE_TOKEN, inviteToken);
        }
        return intent;
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

    /** Новый объект: {@code entityId} пустой, тип задан. */
    public static void createEntity(@NonNull Context context, @NonNull String campaignId,
                                    @Nullable String type) {
        Intent intent = new Intent(context, EntityEditActivity.class);
        intent.putExtra(Extras.CAMPAIGN_ID, campaignId);
        intent.putExtra(Extras.ENTITY_TYPE, type);
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

    public static void openMembers(@NonNull Context context, @NonNull String campaignId) {
        Intent intent = new Intent(context, MembersActivity.class);
        intent.putExtra(Extras.CAMPAIGN_ID, campaignId);
        context.startActivity(intent);
    }

    /** Общий поиск по кампании или поиск внутри вкладки. */
    public static void openSearch(@NonNull Context context, @NonNull String campaignId,
                                  @Nullable String typeFilter) {
        Intent intent = new Intent(context, SearchActivity.class);
        intent.putExtra(Extras.CAMPAIGN_ID, campaignId);
        if (typeFilter != null) {
            intent.putExtra(Extras.SEARCH_TYPE, typeFilter);
        }
        context.startActivity(intent);
    }

    public static void openSettings(@NonNull Context context) {
        context.startActivity(new Intent(context, SettingsActivity.class));
    }

    public static void openInviteAccept(@NonNull Context context, @NonNull String token) {
        Intent intent = new Intent(context, InviteAcceptActivity.class);
        intent.putExtra(Extras.INVITE_TOKEN, token);
        context.startActivity(intent);
    }

    /** Экран регистрации устройства по QR — основной вход в приложение. */
    public static void openEnroll(@NonNull Context context, @Nullable String reason) {
        context.startActivity(ru.dndmemory.ui.auth.EnrollActivity.intent(context, reason));
    }
}
