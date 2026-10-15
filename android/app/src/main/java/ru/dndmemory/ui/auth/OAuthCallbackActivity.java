package ru.dndmemory.ui.auth;

import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import ru.dndmemory.App;
import ru.dndmemory.R;
import ru.dndmemory.databinding.ActivitySplashBinding;
import ru.dndmemory.data.remote.ApiException;
import ru.dndmemory.data.remote.dto.AuthResponse;
import ru.dndmemory.data.repo.AuthRepository;
import ru.dndmemory.ui.common.Nav;
import ru.dndmemory.ui.common.ViewUtils;
import ru.dndmemory.util.RepoCallback;

/**
 * Принимает возврат из Custom Tab по deep link {@code dndmemory://oauth}
 * (docs/api.md, раздел 3) и передаёт код на сервер.
 */
public class OAuthCallbackActivity extends AppCompatActivity {

    private ActivitySplashBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        final AuthRepository auth = App.container(this).authRepository();
        Uri data = getIntent() == null ? null : getIntent().getData();
        AuthRepository.OAuthRedirect redirect = auth.parseRedirect(data);

        if (redirect == null || !redirect.isSuccess()) {
            fail(getString(R.string.error_unauthorized));
            return;
        }
        if (!OAuthState.matches(redirect.state)) {
            // Процесс перезапустился или ответ подменён — просим повторить вход.
            fail(getString(R.string.error_unauthorized));
            return;
        }
        String provider = OAuthState.provider();
        String inviteToken = OAuthState.inviteToken();
        OAuthState.clear();
        if (provider == null) {
            fail(getString(R.string.error_unauthorized));
            return;
        }

        auth.oauthCallback(provider, redirect.code, redirect.state, new RepoCallback<AuthResponse>() {
            @Override
            public void onSuccess(@Nullable AuthResponse value) {
                // Пришли со ссылки-приглашения — возвращаемся к ней, а не в список.
                if (inviteToken != null && !inviteToken.isEmpty()) {
                    Nav.openInviteAccept(OAuthCallbackActivity.this, inviteToken);
                } else {
                    Nav.openCampaignList(OAuthCallbackActivity.this);
                }
                finish();
            }

            @Override
            public void onError(@NonNull ApiException error) {
                fail(error.getServerMessage() == null
                        ? getString(R.string.error_unknown) : error.getServerMessage());
            }
        });
    }

    private void fail(String message) {
        ViewUtils.toast(this, message);
        Nav.openCampaignList(this);
        finish();
    }
}
