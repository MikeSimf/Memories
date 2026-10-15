package ru.dndmemory.ui.campaign;

import android.os.Bundle;
import android.view.Menu;
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
import ru.dndmemory.data.model.EntityType;
import ru.dndmemory.databinding.ActivityCampaignBinding;
import ru.dndmemory.ui.common.CampaignHost;
import ru.dndmemory.ui.common.Extras;
import ru.dndmemory.ui.common.Labels;
import ru.dndmemory.ui.common.Nav;
import ru.dndmemory.ui.common.Rights;
import ru.dndmemory.ui.common.ViewModelFactory;
import ru.dndmemory.ui.common.ViewUtils;

/**
 * Экран кампании: три вкладки объектов — Персонажи (герои и НПС вместе),
 * Локации и Предметы. Пересказы, Участники и Приглашения вынесены в меню шапки
 * отдельными пунктами (docs/api.md, раздел 6).
 */
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
                return R.string.tab_characters;
            case 1:
                return R.string.tab_locations;
            case 2:
            default:
                return R.string.tab_items;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_campaign, menu);
        return true;
    }

    /** Типы объектов текущей вкладки — им фильтруется поиск. */
    @Nullable
    private String currentTabFilter() {
        switch (binding.campaignPager.getCurrentItem()) {
            case 0:
                return EntityType.CHARACTERS;
            case 1:
                return EntityType.LOCATION.name();
            case 2:
                return EntityType.ITEM.name();
            default:
                return null;
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_search) {
            // Поиск открывается с фильтром текущей вкладки; чип «Всё» даёт общий поиск.
            Nav.openSearch(this, campaignId, currentTabFilter());
            return true;
        }
        if (id == R.id.action_retellings) {
            Nav.openRetellings(this, campaignId);
            return true;
        }
        if (id == R.id.action_members) {
            Nav.openMembers(this, campaignId);
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
        return super.onOptionsItemSelected(item);
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
