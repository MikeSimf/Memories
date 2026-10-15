package ru.dndmemory.ui.settings;

import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import ru.dndmemory.App;
import ru.dndmemory.AppContainer;
import ru.dndmemory.R;
import ru.dndmemory.data.remote.dto.UserDto;
import ru.dndmemory.databinding.ActivitySettingsBinding;
import ru.dndmemory.databinding.DialogPasswordBinding;
import ru.dndmemory.ui.common.ErrorMessages;
import ru.dndmemory.ui.common.Nav;
import ru.dndmemory.ui.common.ViewModelFactory;
import ru.dndmemory.ui.common.ViewUtils;
import ru.dndmemory.util.AppExecutors;

/**
 * Настройки: профиль (имя, почта, телефон), пароль для входа в веб-версию,
 * режим синхронизации и выход из аккаунта.
 *
 * <p>Аккаунт, созданный по QR устройства, начинается без пароля: подсказка в
 * интерфейсе объясняет, что почта или телефон плюс пароль открывают вход в
 * веб-версию (docs/api.md, раздел 3).
 */
public class SettingsActivity extends AppCompatActivity {

    private static final int MIN_PASSWORD_LENGTH = 8;

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
        binding.settingsProfileSave.setOnClickListener(v -> saveProfile());
        binding.settingsPasswordButton.setOnClickListener(v -> showPasswordDialog());

        bindProfile(null);
        observe();
        viewModel.loadProfile();
    }

    private void observe() {
        viewModel.profile().observe(this, this::bindProfile);

        viewModel.actionState().observe(this, resource -> {
            if (resource == null) {
                return;
            }
            ViewUtils.show(binding.settingsProgress, resource.isLoading());
            binding.settingsSyncNow.setEnabled(!resource.isLoading());
            binding.settingsFullResync.setEnabled(!resource.isLoading());
            binding.settingsProfileSave.setEnabled(!resource.isLoading());
            binding.settingsPasswordButton.setEnabled(!resource.isLoading());
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

    /** Имя, контакты и статус пароля. {@code user == null} — показываем данные сессии. */
    private void bindProfile(@Nullable UserDto user) {
        String name = user != null && !TextUtils.isEmpty(user.displayName)
                ? user.displayName : viewModel.userName();
        String email = user != null && user.email != null ? user.email : viewModel.email();
        String phone = user != null && user.phone != null ? user.phone : viewModel.phone();
        boolean hasPassword = user == null ? viewModel.hasPassword() : user.hasPassword;

        binding.settingsUser.setText(getString(R.string.settings_user,
                TextUtils.isEmpty(name) ? getString(R.string.attr_not_set) : name));
        binding.settingsEmail.setText(getString(R.string.settings_email_display,
                TextUtils.isEmpty(email) ? getString(R.string.attr_not_set) : email));
        binding.settingsPhone.setText(getString(R.string.settings_phone_display,
                TextUtils.isEmpty(phone) ? getString(R.string.attr_not_set) : phone));

        binding.settingsPasswordStatus.setText(hasPassword
                ? R.string.settings_password_set : R.string.settings_password_not_set);
        binding.settingsPasswordButton.setText(hasPassword
                ? R.string.settings_password_change_action : R.string.settings_password_set_action);
        // Подсказка нужна, пока пароля нет: именно она объясняет пользу контактов.
        ViewUtils.show(binding.settingsPasswordHint, !hasPassword);

        // Поля заполняем из ответа сервера (он нормализует телефон), а до ответа —
        // из сохранённой сессии, чтобы форма не была пустой.
        boolean fromServer = user != null;
        if (fromServer || binding.settingsNameInput.getText() == null
                || binding.settingsNameInput.getText().length() == 0) {
            binding.settingsNameInput.setText(name);
        }
        if (fromServer || binding.settingsEmailInput.getText() == null
                || binding.settingsEmailInput.getText().length() == 0) {
            binding.settingsEmailInput.setText(email);
        }
        if (fromServer || binding.settingsPhoneInput.getText() == null
                || binding.settingsPhoneInput.getText().length() == 0) {
            binding.settingsPhoneInput.setText(phone);
        }
    }

    private void saveProfile() {
        String name = text(binding.settingsNameInput.getText());
        if (name.isEmpty()) {
            binding.settingsNameInput.setError(getString(R.string.error_required));
            return;
        }
        binding.settingsNameInput.setError(null);
        viewModel.saveProfile(name, text(binding.settingsEmailInput.getText()),
                text(binding.settingsPhoneInput.getText()));
    }

    /**
     * Пароль: если он уже задан, сервер требует текущий — поле показываем только
     * в этом случае (docs/api.md, раздел 3).
     */
    private void showPasswordDialog() {
        final DialogPasswordBinding dialog = DialogPasswordBinding.inflate(getLayoutInflater());
        boolean hasPassword = viewModel.hasPassword();
        ViewUtils.show(dialog.passwordCurrentGroup, hasPassword);
        dialog.passwordHint.setText(R.string.settings_password_hint);

        new MaterialAlertDialogBuilder(this)
                .setView(dialog.getRoot())
                .setPositiveButton(R.string.action_save, (d, which) -> {
                    String current = text(dialog.passwordCurrent.getText());
                    String fresh = text(dialog.passwordNew.getText());
                    String repeat = text(dialog.passwordRepeat.getText());
                    if (hasPassword && current.isEmpty()) {
                        ViewUtils.toast(this, R.string.settings_password_current_required);
                        return;
                    }
                    if (fresh.length() < MIN_PASSWORD_LENGTH) {
                        ViewUtils.toast(this, R.string.error_password_short);
                        return;
                    }
                    if (!fresh.equals(repeat)) {
                        ViewUtils.toast(this, R.string.settings_password_mismatch);
                        return;
                    }
                    viewModel.changePassword(hasPassword ? current : null, fresh);
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
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

    private static String text(@Nullable CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }
}
