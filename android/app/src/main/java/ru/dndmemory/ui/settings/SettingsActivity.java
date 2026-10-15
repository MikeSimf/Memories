package ru.dndmemory.ui.settings;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import ru.dndmemory.App;
import ru.dndmemory.AppContainer;
import ru.dndmemory.R;
import ru.dndmemory.databinding.ActivitySettingsBinding;
import ru.dndmemory.ui.common.ErrorMessages;
import ru.dndmemory.ui.common.Nav;
import ru.dndmemory.ui.common.ViewModelFactory;
import ru.dndmemory.ui.common.ViewUtils;
import ru.dndmemory.util.AppExecutors;

/** Настройки синхронизации и аккаунта. */
public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding binding;
    private SettingsViewModel viewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        AppContainer container = App.container(this);
        viewModel = new ViewModelProvider(this, ViewModelFactory.of(
                () -> new SettingsViewModel(container.authRepository(), container.settingsStore(),
                        container.syncEngine(), container.syncScheduler(), container.database(),
                        AppExecutors.get())))
                .get(SettingsViewModel.class);

        setSupportActionBar(binding.settingsToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        binding.settingsUser.setText(getString(R.string.settings_user,
                viewModel.userName().isEmpty() ? getString(R.string.attr_not_set) : viewModel.userName()));
        binding.settingsServer.setText(getString(R.string.settings_server, viewModel.serverUrl()));
        binding.settingsAbout.setText(getString(R.string.settings_about, viewModel.version()));

        String lastSync = viewModel.lastSyncAt();
        binding.settingsLastSync.setText(getString(R.string.sync_last_at,
                lastSync.isEmpty() ? getString(R.string.sync_never) : lastSync));

        binding.settingsWifiOnly.setChecked(viewModel.isWifiOnly());
        binding.settingsWifiOnly.setOnCheckedChangeListener((buttonView, isChecked) ->
                viewModel.setWifiOnly(isChecked));

        binding.settingsSyncNow.setOnClickListener(v -> viewModel.syncNow());
        binding.settingsFullResync.setOnClickListener(v -> confirmFullResync());
        binding.settingsLogout.setOnClickListener(v -> confirmLogout());

        observe();
    }

    private void observe() {
        viewModel.actionState().observe(this, resource -> {
            if (resource == null) {
                return;
            }
            ViewUtils.show(binding.settingsProgress, resource.isLoading());
            binding.settingsSyncNow.setEnabled(!resource.isLoading());
            binding.settingsFullResync.setEnabled(!resource.isLoading());
            if (resource.isError()) {
                ErrorMessages.show(binding.getRoot(), resource.error);
                viewModel.clearActionState();
            }
        });

        viewModel.messageRes().observe(this, res -> {
            if (res != null && res != 0) {
                ViewUtils.toast(this, res);
                viewModel.clearMessage();
            }
        });

        viewModel.loggedOut().observe(this, done -> {
            if (Boolean.TRUE.equals(done)) {
                Nav.openCampaignList(this);
                finish();
            }
        });
    }

    private void confirmFullResync() {
        new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.settings_full_resync_hint)
                .setPositiveButton(R.string.action_full_resync, (dialog, which) -> viewModel.fullResync())
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void confirmLogout() {
        new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.settings_logout_confirm)
                .setPositiveButton(R.string.action_logout, (dialog, which) -> viewModel.logout())
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
