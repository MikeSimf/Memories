package ru.dndmemory.ui.campaigns;

import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import ru.dndmemory.App;
import ru.dndmemory.AppContainer;
import ru.dndmemory.R;
import ru.dndmemory.data.local.entity.CampaignRoom;
import ru.dndmemory.databinding.ActivityCampaignListBinding;
import ru.dndmemory.databinding.DialogCampaignEditBinding;
import ru.dndmemory.ui.common.ErrorMessages;
import ru.dndmemory.ui.common.Labels;
import ru.dndmemory.ui.common.Nav;
import ru.dndmemory.ui.common.ViewModelFactory;
import ru.dndmemory.ui.common.ViewUtils;
import ru.dndmemory.data.repo.UpdateRepository;

/** Список кампаний, создание и удаление. */
public class CampaignListActivity extends AppCompatActivity implements CampaignAdapter.Listener {

    private ActivityCampaignListBinding binding;
    private CampaignListViewModel viewModel;
    private CampaignAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCampaignListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        AppContainer container = App.container(this);
        viewModel = new ViewModelProvider(this, ViewModelFactory.of(
                () -> new CampaignListViewModel(container.campaignRepository(),
                        container.authRepository(), container.syncEngine(),
                        container.syncScheduler())))
                .get(CampaignListViewModel.class);

        adapter = new CampaignAdapter(this);
        binding.campaignListRecycler.setLayoutManager(new LinearLayoutManager(this));
        binding.campaignListRecycler.setAdapter(adapter);

        binding.campaignListToolbar.inflateMenu(R.menu.menu_campaign_list);
        binding.campaignListToolbar.setOnMenuItemClickListener(this::onMenu);

        binding.campaignListSwipe.setOnRefreshListener(() -> {
            viewModel.refresh();
            viewModel.syncNow();
        });
        binding.campaignListFab.setOnClickListener(v -> showEditDialog(null));

        observe();
        observeUpdates();
        checkForUpdates();
    }

    /** Проверка версии: обязательная — без кнопки «Позже», иначе просто предложение. */
    private void checkForUpdates() {
        App.container(this).updateRepository().check(new ru.dndmemory.util.RepoCallback<UpdateRepository.UpdateInfo>() {
            @Override
            public void onSuccess(@Nullable UpdateRepository.UpdateInfo value) {
                if (value != null) {
                    showUpdate(value);
                }
            }

            @Override
            public void onError(@NonNull ru.dndmemory.data.remote.ApiException error) {
                // Без сети проверка просто не срабатывает.
            }
        });
    }

    /** Обновление приходит и при входе, и после синхронизации. */
    private void observeUpdates() {
        ru.dndmemory.data.repo.UpdateState.info().observe(this, info -> {
            boolean available = info != null;
            ViewUtils.show(binding.campaignListUpdate, available);
            if (!available) {
                return;
            }
            binding.campaignListUpdate.setText(getString(R.string.update_available_short,
                    info.versionName == null ? "" : info.versionName));
            binding.campaignListUpdate.setOnClickListener(v -> showUpdate(info));
            if (ru.dndmemory.data.repo.UpdateState.shouldPrompt(info)) {
                showUpdate(info);
            }
        });
    }

    private void showUpdate(@NonNull final UpdateRepository.UpdateInfo info) {
        ru.dndmemory.ui.common.UpdatePrompt.show(this, info);
    }

    private void openUpdateLink(@Nullable String url) {
        if (url == null || url.isEmpty()) {
            ViewUtils.toast(this, R.string.update_no_link);
            return;
        }
        // Скачиваем и сразу открываем установщик: искать apk в загрузках не нужно.
        ru.dndmemory.ui.common.ApkInstaller.downloadAndInstall(this, url);
    }

    private boolean onMenu(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            Nav.openSettings(this);
            return true;
        }
        if (id == R.id.action_profile) {
            Nav.openProfile(this);
            return true;
        }
        if (id == R.id.action_logout) {
            confirmLogout();
            return true;
        }
        return false;
    }

    private void observe() {
        viewModel.campaigns().observe(this, campaigns -> {
            adapter.submitList(campaigns);
            boolean empty = campaigns == null || campaigns.isEmpty();
            ViewUtils.show(binding.campaignListEmpty, empty);
            ViewUtils.show(binding.campaignListRecycler, !empty);
        });

        viewModel.displayName().observe(this, name -> {
            if (name == null || name.isEmpty()) {
                ViewUtils.show(binding.campaignListSignedAs, false);
            } else {
                binding.campaignListSignedAs.setText(
                        getString(R.string.campaign_list_signed_as, name));
                ViewUtils.show(binding.campaignListSignedAs, true);
            }
        });

        viewModel.syncStatus().observe(this, status ->
                binding.campaignListSyncStatus.setText(Labels.syncStatus(status)));

        viewModel.refreshState().observe(this, resource -> {
            if (resource == null) {
                return;
            }
            boolean loading = resource.isLoading();
            binding.campaignListSwipe.setRefreshing(loading);
            ViewUtils.show(binding.campaignListProgress, loading);
            if (resource.isError()) {
                ErrorMessages.show(binding.getRoot(), resource.error);
                viewModel.clearRefreshState();
            }
        });

        viewModel.actionState().observe(this, resource -> {
            if (resource == null) {
                return;
            }
            if (resource.isError()) {
                ErrorMessages.show(binding.getRoot(), resource.error);
                viewModel.clearActionState();
            } else if (resource.isSuccess()) {
                viewModel.clearActionState();
            }
        });

        // Первичная загрузка: показываем локальные данные и обновляем их из сети.
        viewModel.refresh();
    }

    private void showEditDialog(@Nullable final CampaignRoom campaign) {
        DialogCampaignEditBinding dialogBinding =
                DialogCampaignEditBinding.inflate(getLayoutInflater());
        boolean creating = campaign == null;
        dialogBinding.campaignEditHeader.setText(creating
                ? R.string.campaign_edit_title_new : R.string.campaign_edit_title_edit);
        if (!creating) {
            dialogBinding.campaignEditTitleInput.setText(campaign.title);
            dialogBinding.campaignEditDescriptionInput.setText(campaign.description);
        }
        new MaterialAlertDialogBuilder(this)
                .setView(dialogBinding.getRoot())
                .setPositiveButton(creating ? R.string.action_create : R.string.action_save,
                        (dialog, which) -> {
                            String title = text(dialogBinding.campaignEditTitleInput.getText());
                            String description = text(dialogBinding.campaignEditDescriptionInput.getText());
                            if (TextUtils.isEmpty(title)) {
                                ViewUtils.toast(this, R.string.error_required);
                                return;
                            }
                            if (creating) {
                                viewModel.create(title, description);
                            } else {
                                viewModel.rename(campaign, title, description);
                            }
                        })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void confirmLogout() {
        new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.settings_logout_confirm)
                .setPositiveButton(R.string.action_logout, (dialog, which) ->
                        App.container(this).logout(() -> {
                            Nav.authAfterLogout(this);
                            finish();
                        }))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    @Override
    public void onCampaignClick(@NonNull CampaignRoom campaign) {
        Nav.openCampaign(this, campaign.id);
    }

    @Override
    public void onCampaignLongClick(@NonNull final CampaignRoom campaign) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(campaign.title)
                .setItems(new CharSequence[]{
                                getString(R.string.action_edit), getString(R.string.action_delete)},
                        (DialogInterface dialog, int which) -> {
                            if (which == 0) {
                                showEditDialog(campaign);
                            } else {
                                confirmDelete(campaign);
                            }
                        })
                .show();
    }

    private void confirmDelete(@NonNull final CampaignRoom campaign) {
        new MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.campaign_delete_confirm, campaign.title))
                .setPositiveButton(R.string.action_delete, (dialog, which) -> viewModel.delete(campaign))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private static String text(@Nullable CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }
}
