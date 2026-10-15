package ru.dndmemory.ui.campaign;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import ru.dndmemory.App;
import ru.dndmemory.AppContainer;
import ru.dndmemory.R;
import ru.dndmemory.data.local.entity.CampaignRoom;
import ru.dndmemory.databinding.ActivityCampaignBinding;
import ru.dndmemory.ui.common.CampaignHost;
import ru.dndmemory.ui.common.Extras;
import ru.dndmemory.ui.common.Labels;
import ru.dndmemory.ui.common.Nav;
import ru.dndmemory.ui.common.Rights;
import ru.dndmemory.ui.common.ViewModelFactory;
import ru.dndmemory.ui.common.ViewUtils;

/** Экран кампании: вкладки объектов, пересказов и участников. */
public class CampaignActivity extends AppCompatActivity implements CampaignHost {

    private ActivityCampaignBinding binding;
    private CampaignViewModel viewModel;
    private String campaignId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCampaignBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        campaignId = getIntent() == null ? null : getIntent().getStringExtra(Extras.CAMPAIGN_ID);
        if (campaignId == null || campaignId.isEmpty()) {
            finish();
            return;
        }

        AppContainer container = App.container(this);
        viewModel = new ViewModelProvider(this, ViewModelFactory.of(
                () -> new CampaignViewModel(container.campaignRepository(),
                        container.authRepository(), container.syncEngine(),
                        container.syncScheduler())))
                .get(CampaignViewModel.class);
        viewModel.setCampaignId(campaignId);

        setSupportActionBar(binding.campaignToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.campaignToolbar.inflateMenu(R.menu.menu_campaign);
        binding.campaignToolbar.setOnMenuItemClickListener(this::onMenu);

        binding.campaignPager.setAdapter(new CampaignPagerAdapter(this));
        binding.campaignPager.setOffscreenPageLimit(1);
        new TabLayoutMediator(binding.campaignTabs, binding.campaignPager,
                (tab, position) -> tab.setText(tabTitle(position))).attach();

        observe();
        viewModel.refresh();
    }

    private int tabTitle(int position) {
        switch (position) {
            case 0:
                return R.string.tab_heroes;
            case 1:
                return R.string.tab_npcs;
            case 2:
                return R.string.tab_locations;
            case 3:
                return R.string.tab_items;
            case 4:
                return R.string.tab_retellings;
            case 5:
            default:
                return R.string.tab_members;
        }
    }

    private boolean onMenu(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_search) {
            Nav.openSearch(this, campaignId, null);
            return true;
        }
        if (id == R.id.action_invite) {
            CampaignRoom campaign = viewModel.campaign().getValue();
            if (!Rights.canInvite(campaign)) {
                ViewUtils.toast(this, R.string.members_manage_forbidden);
                return true;
            }
            Nav.openInvites(this, campaignId);
            return true;
        }
        if (id == R.id.action_refresh) {
            viewModel.refresh();
            return true;
        }
        return false;
    }

    private void observe() {
        viewModel.campaign().observe(this, campaign -> {
            if (campaign == null) {
                return;
            }
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(campaign.title);
            }
        });
        viewModel.syncStatus().observe(this, status ->
                binding.campaignSyncStatus.setText(Labels.syncStatus(status)));
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @NonNull
    @Override
    public String campaignId() {
        return campaignId;
    }

    @NonNull
    @Override
    public CampaignViewModel campaignViewModel() {
        return viewModel;
    }
}
