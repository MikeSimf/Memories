package ru.dndmemory.ui.settings;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;

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
 * Настройки: профиль (логин только для чтения, имя, почта, телефон), смена
 * пароля, режим синхронизации и выход из аккаунта.
 *
 * <p>Логин изменить нельзя, почта обязательна — на неё придёт ссылка
 * восстановления пароля (docs/api.md, раздел 3). Про устройства и отзыв доступа
 * в приложении больше ничего нет.
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
        // Веб-версия живёт на том же адресе, что и API: прод — рабочий сайт,
        // тест — тестовый адрес из сборки.
        binding.settingsWebLink.setOnClickListener(v -> openWeb(viewModel.serverUrl()));

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
                // После выхода — экран входа, а не список кампаний: данных там уже нет.
                Nav.authAfterLogout(this);
                finish();
            }
        });
    }

    /** Логин только для чтения, остальное — из профиля или из сессии. */
    private void openWeb(@Nullable String serverUrl) {
        if (serverUrl == null || serverUrl.isEmpty()) {
            return;
        }
        try {
            startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(serverUrl)));
        } catch (RuntimeException e) {
            ViewUtils.toast(this, R.string.update_no_link);
        }
    }

    private void bindProfile(@Nullable UserDto user) {
        String name = user != null && !TextUtils.isEmpty(user.displayName)
                ? user.displayName : viewModel.userName();
        String login = user != null && !TextUtils.isEmpty(user.login)
                ? user.login : viewModel.login();
        String email = user != null && user.email != null ? user.email : viewModel.email();
        String phone = user != null && user.phone != null ? user.phone : viewModel.phone();

        binding.settingsUser.setText(getString(R.string.settings_user,
                TextUtils.isEmpty(name) ? getString(R.string.attr_not_set) : name));
        binding.settingsLogin.setText(getString(R.string.settings_login_display,
                TextUtils.isEmpty(login) ? getString(R.string.attr_not_set) : login));
        binding.settingsEmail.setText(getString(R.string.settings_email_display,
                TextUtils.isEmpty(email) ? getString(R.string.attr_not_set) : email));
        binding.settingsPhone.setText(getString(R.string.settings_phone_display,
                TextUtils.isEmpty(phone) ? getString(R.string.attr_not_set) : phone));

        binding.settingsPasswordButton.setText(R.string.settings_password_change_action);

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
        String email = text(binding.settingsEmailInput.getText());
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.settingsEmailInput.setError(getString(R.string.error_email_required));
            return;
        }
        binding.settingsEmailInput.setError(null);
        viewModel.saveProfile(name, email, text(binding.settingsPhoneInput.getText()));
    }

    /** Смена пароля: вход только по паролю, поэтому текущий спрашиваем всегда. */
    private void showPasswordDialog() {
        final DialogPasswordBinding dialog = DialogPasswordBinding.inflate(getLayoutInflater());
        dialog.passwordHint.setText(R.string.settings_password_hint);

        new MaterialAlertDialogBuilder(this)
                .setView(dialog.getRoot())
                .setPositiveButton(R.string.action_save, (d, which) -> {
                    String current = text(dialog.passwordCurrent.getText());
                    String fresh = text(dialog.passwordNew.getText());
                    String repeat = text(dialog.passwordRepeat.getText());
                    if (current.isEmpty()) {
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
                    viewModel.changePassword(current, fresh);
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
