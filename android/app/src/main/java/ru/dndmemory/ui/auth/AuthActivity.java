package ru.dndmemory.ui.auth;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.tabs.TabLayout;

import ru.dndmemory.App;
import ru.dndmemory.R;
import ru.dndmemory.databinding.ActivityAuthBinding;
import ru.dndmemory.data.remote.dto.ProvidersResponse;
import ru.dndmemory.ui.common.ErrorMessages;
import ru.dndmemory.ui.common.Extras;
import ru.dndmemory.ui.common.Nav;
import ru.dndmemory.ui.common.ViewModelFactory;
import ru.dndmemory.ui.common.ViewUtils;
import ru.dndmemory.util.Resource;

/** Вход и регистрация: почта с паролем плюс OAuth через Custom Tabs. */
public class AuthActivity extends AppCompatActivity {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private ActivityAuthBinding binding;
    private AuthViewModel viewModel;
    private String inviteToken;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAuthBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (getIntent() != null && getIntent().hasExtra(Extras.INVITE_TOKEN)) {
            inviteToken = getIntent().getStringExtra(Extras.INVITE_TOKEN);
        }

        viewModel = new ViewModelProvider(this, ViewModelFactory.of(
                () -> new AuthViewModel(App.container(this).authRepository())))
                .get(AuthViewModel.class);

        ViewUtils.show(binding.authInviteHint, inviteToken != null);
        setupTabs();

        binding.authLoginSubmit.setOnClickListener(v -> submitLogin());
        binding.authRegisterSubmit.setOnClickListener(v -> submitRegister());
        binding.authOauthYandex.setOnClickListener(v -> startOauth("yandex"));
        binding.authOauthVk.setOnClickListener(v -> startOauth("vk"));

        binding.authOauthYandex.setEnabled(false);
        binding.authOauthVk.setEnabled(false);

        observe();
        viewModel.loadProviders();
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

    private void submitLogin() {
        String email = text(binding.authLoginEmail.getText());
        String password = text(binding.authLoginPassword.getText());
        if (!validEmail(email)) {
            showError(getString(R.string.error_required));
            return;
        }
        if (password.isEmpty()) {
            showError(getString(R.string.error_required));
            return;
        }
        viewModel.login(email, password);
    }

    private void submitRegister() {
        String name = text(binding.authRegisterName.getText());
        String email = text(binding.authRegisterEmail.getText());
        String password = text(binding.authRegisterPassword.getText());
        if (name.isEmpty() || !validEmail(email)) {
            showError(getString(R.string.error_required));
            return;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            showError(getString(R.string.error_password_short));
            return;
        }
        viewModel.register(email, password, name);
    }

    private void startOauth(@NonNull String provider) {
        String state = viewModel.newState();
        // Запоминаем приглашение: после возврата по dndmemory://oauth вернёмся на него.
        OAuthState.set(provider, state, inviteToken);
        String url = viewModel.authorizeUrl(provider, state);
        try {
            CustomTabsIntent tabs = new CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .build();
            tabs.launchUrl(this, Uri.parse(url));
        } catch (ActivityNotFoundException e) {
            // Custom Tabs нет — открываем обычным браузером.
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        }
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

        viewModel.providers().observe(this, this::applyProviders);
    }

    private void applyProviders(@Nullable ProvidersResponse providers) {
        if (providers == null) {
            return;
        }
        binding.authOauthYandex.setEnabled(providers.isEnabled("yandex"));
        binding.authOauthVk.setEnabled(providers.isEnabled("vk"));
    }

    private void onLoggedIn() {
        if (inviteToken != null) {
            Nav.openInviteAccept(this, inviteToken);
        } else {
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

    private static String text(@Nullable CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }
}
