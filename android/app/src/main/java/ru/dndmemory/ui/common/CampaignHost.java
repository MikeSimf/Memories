package ru.dndmemory.ui.common;

import androidx.annotation.NonNull;

import ru.dndmemory.ui.campaign.CampaignViewModel;

/**
 * Реализуется {@link ru.dndmemory.ui.campaign.CampaignActivity}: вкладки берут
 * из него кампанию, права и состояние синхронизации.
 */
public interface CampaignHost {

    @NonNull
    String campaignId();

    @NonNull
    CampaignViewModel campaignViewModel();
}
