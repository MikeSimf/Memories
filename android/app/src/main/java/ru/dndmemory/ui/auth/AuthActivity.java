package ru.dndmemory.ui.auth;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputLayout;

import ru.dndmemory.App;
import ru.dndmemory.R;
import ru.dndmemory.databinding.ActivityAuthBinding;
import ru.dndmemory.ui.common.ErrorMessages;
import ru.dndmemory.ui.common.Extras;
import ru.dndmemory.ui.common.Nav;
import ru.dndmemory.ui.common.ViewModelFactory;
import ru.dndmemory.ui.common.ViewUtils;

/**
 * Вход и регистрация. Вход — только логин и пароль; внешних провайдеров нет
 * (docs/redesign-v2.md, раздел 1). Сессия хранится на телефоне, поэтому логин
 * и пароль вводятся один раз.
 *
 * <p>Если экран открыт по приглашению, после входа приглашение принимается,
 * а регистрация сразу присоединяет к кампании ({@code inviteToken}).
 */
public class AuthActivity extends AppCompatActivity {

    /** Пароль: от 8 до 100 символов (docs/api.md, раздел 3). */
    private static final int MIN_PASSWORD_LENGTH = 8;

    private ActivityAuthBinding binding;
    private AuthViewModel viewModel;
    private String inviteToken;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAuthBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        inviteToken = getIntent() == null ? null : getIntent().getStringExtra(Extras.INVITE_TOKEN);
        String tab = getIntent() == null ? null : getIntent().getStringExtra(Extras.AUTH_TAB);
        String message = getIntent() == null ? null : getIntent().getStringExtra(Extras.MESSAGE);

        viewModel = new ViewModelProvider(this, ViewModelFactory.of(
                () -> new AuthViewModel(App.container(this).authRepository())))
                .get(AuthViewModel.class);

        ViewUtils.show(binding.authInviteHint, !TextUtils.isEmpty(inviteToken));
        setupTabs();

        binding.authLoginSubmit.setOnClickListener(v -> submitLogin());
        binding.authRegisterSubmit.setOnClickListener(v -> submitRegister());
        binding.authForgotPassword.setOnClickListener(v -> Nav.openPasswordReset(this));
        binding.authHaveInvite.setOnClickListener(v -> askInviteToken());

        if (message != null && !message.isEmpty()) {
            showError(getString(R.string.session_expired));
        }
        if (Extras.AUTH_TAB_REGISTER.equals(tab)) {
            selectTab(1);
        }

        observe();
    }

    private void setupTabs() {
        TabLayout tabs = binding.authTabs;
        tabs.addTab(tabs.newTab().setText(R.string.auth_tab_login));
        tabs.addTab(tabs.newTab().setText(R.string.auth_tab_register));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                boolean login = tab.getPosition() == 0;
                ViewUtils.show(binding.authLoginGroup, login);
                ViewUtils.show(binding.authRegisterGroup, !login);
                hideError();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }

    private void selectTab(int position) {
        TabLayout.Tab tab = binding.authTabs.getTabAt(position);
        if (tab != null) {
            tab.select();
        }
    }

    private void submitLogin() {
        String login = text(binding.authLoginField.getText());
        String password = text(binding.authLoginPassword.getText());
        if (login.isEmpty() || password.isEmpty()) {
            showError(getString(R.string.error_required));
            return;
        }
        viewModel.login(login, password);
    }

    private void submitRegister() {
        String name = text(binding.authRegisterName.getText());
        String login = text(binding.authRegisterLogin.getText());
        String email = text(binding.authRegisterEmail.getText());
        String password = text(binding.authRegisterPassword.getText());
        if (name.isEmpty() || !validLogin(login) || !validEmail(email)) {
            showError(getString(R.string.error_required));
            return;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            showError(getString(R.string.error_password_short));
            return;
        }
        viewModel.register(login, email, password, name, inviteToken);
    }

    /** Приглашение можно ввести вручную: {@code по ссылке dndmemory://join?token=…}. */
    private void askInviteToken() {
        final TextInputLayout layout = new TextInputLayout(this);
        layout.setHint(getString(R.string.invite_token_hint));
        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        layout.addView(input);
        int padding = (int) (getResources().getDisplayMetrics().density * 24);
        layout.setPadding(padding, padding / 2, padding, 0);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.auth_have_invite)
                .setView(layout)
                .setPositiveButton(R.string.action_accept, (dialog, which) -> {
                    String token = input.getText() == null ? "" : input.getText().toString().trim();
                    if (!token.isEmpty()) {
                        Nav.openInviteAccept(this, token);
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void observe() {
        viewModel.result().observe(this, resource -> {
            if (resource == null) {
                return;
            }
            boolean loading = resource.isLoading();
            ViewUtils.show(binding.authProgress, loading);
            setFormEnabled(!loading);
            if (resource.isError()) {
                showError(ErrorMessages.text(this, resource.error));
            } else if (resource.isSuccess()) {
                onLoggedIn();
            }
        });
    }

    /**
     * Регистрация по приглашению уже присоединила к кампании — сразу открываем
     * список. После входа приглашение ещё нужно принять.
     */
    private void onLoggedIn() {
        boolean registering = binding.authTabs.getSelectedTabPosition() == 1;
        if (inviteToken != null && !registering) {
            Nav.openInviteAccept(this, inviteToken);
        } else {
            if (inviteToken != null) {
                ViewUtils.toast(this, R.string.invite_accepted);
            }
            Nav.openCampaignList(this);
        }
        finish();
    }

    private void setFormEnabled(boolean enabled) {
        binding.authLoginSubmit.setEnabled(enabled);
        binding.authRegisterSubmit.setEnabled(enabled);
    }

    private void showError(CharSequence message) {
        binding.authError.setText(message);
        ViewUtils.show(binding.authError, true);
    }

    private void hideError() {
        ViewUtils.show(binding.authError, false);
    }

    private static boolean validEmail(String email) {
        return !TextUtils.isEmpty(email) && Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    /** Логин: 3–32 символа из {@code a–z}, {@code 0–9}, {@code _}, {@code .}, {@code -}. */
    private static boolean validLogin(String login) {
        return !TextUtils.isEmpty(login) && login.matches("[A-Za-z0-9_.\\-]{3,32}");
    }

    private static String text(@Nullable CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }
}
