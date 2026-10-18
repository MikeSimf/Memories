package ru.dndmemory.ui.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import ru.dndmemory.App;
import ru.dndmemory.AppContainer;
import ru.dndmemory.ui.campaign.CampaignViewModel;

/**
 * Общая основа экранов внутри кампании: достаёт {@code campaignId} из extras
 * и поднимает {@link CampaignViewModel}. Реализует {@link CampaignHost}, поэтому
 * вкладки работают и в {@code CampaignActivity}, и на отдельных экранах
 * «Пересказы» и «Участники».
 */
public abstract class BaseCampaignActivity extends AppCompatActivity implements CampaignHost {

    @Nullable
    protected String campaignId;
    protected CampaignViewModel viewModel;

    /**
     * Готовит кампанию и ViewModel.
     *
     * @return {@code false}, если кампания не передана: экран нужно закрыть.
     */
    protected boolean initCampaign() {
        campaignId = getIntent() == null ? null : getIntent().getStringExtra(Extras.CAMPAIGN_ID);
        if (campaignId == null || campaignId.isEmpty()) {
            finish();
            return false;
        }
        AppContainer container = App.container(this);
        viewModel = new ViewModelProvider(this, ViewModelFactory.of(
                () -> new CampaignViewModel(container.campaignRepository(),
                        container.authRepository(), container.syncEngine(),
                        container.syncScheduler())))
                .get(CampaignViewModel.class);
        viewModel.setCampaignId(campaignId);
        // Права и роль берём свежие: список кампаний отдаёт только myRole.
        viewModel.refreshDetails();
        return true;
    }

    @NonNull
    @Override
    public String campaignId() {
        return campaignId == null ? "" : campaignId;
    }

    @NonNull
    @Override
    public CampaignViewModel campaignViewModel() {
        return viewModel;
    }
}
