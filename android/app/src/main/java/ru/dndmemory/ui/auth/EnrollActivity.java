package ru.dndmemory.ui.auth;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import ru.dndmemory.App;
import ru.dndmemory.R;
import ru.dndmemory.data.local.SessionStore;
import ru.dndmemory.data.remote.ApiException;
import ru.dndmemory.data.remote.dto.EnrollResponse;
import ru.dndmemory.data.repo.AuthRepository;
import ru.dndmemory.databinding.ActivityEnrollBinding;
import ru.dndmemory.ui.common.Extras;
import ru.dndmemory.ui.common.Nav;
import ru.dndmemory.ui.common.ViewUtils;
import ru.dndmemory.ui.invite.ScanActivity;
import ru.dndmemory.util.DeviceInfo;
import ru.dndmemory.util.QrUtils;
import ru.dndmemory.util.RepoCallback;

/**
 * Основной вход: регистрация устройства по QR-коду мастера
 * (docs/api.md, раздел 3.1). Приложение больше не начинается с формы пароля —
 * пароль остаётся вторичным вариантом по ссылке «У меня уже есть аккаунт».
 *
 * <p>QR-код устройства одноразовый: повторное сканирование даёт
 * {@code 409 invite_invalid}. Сеть не нужна только до нажатия кнопки — сам обмен
 * токена на сессию, разумеется, требует связи.
 */
public class EnrollActivity extends AppCompatActivity {

    private ActivityEnrollBinding binding;
    private AuthRepository auth;

    /** Токен, с которым экран открыли (deep link или результат сканирования). */
    private String pendingToken;
    private boolean submitting;

    private final ActivityResultLauncher<Intent> scanLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                    return;
                }
                String scanned = result.getData().getStringExtra(Extras.INVITE_TOKEN);
                String token = QrUtils.extractToken(scanned);
                if (token == null || token.isEmpty()) {
                    showError(getString(R.string.enroll_error_invalid));
                    return;
                }
                binding.enrollCode.setText(token);
                submit(token);
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEnrollBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = App.container(this).authRepository();

        binding.enrollScan.setOnClickListener(v ->
                scanLauncher.launch(new Intent(this, ScanActivity.class)));
        binding.enrollSubmit.setOnClickListener(v -> submit(text(binding.enrollCode.getText())));
        binding.enrollExisting.setOnClickListener(v -> openPasswordLogin());

        showReason(getIntent() == null ? null : getIntent().getStringExtra(Extras.MESSAGE));

        String token = getIntent() == null ? null : getIntent().getStringExtra(Extras.INVITE_TOKEN);
        if (token != null && !token.isEmpty()) {
            pendingToken = QrUtils.extractToken(token);
            binding.enrollCode.setText(pendingToken);
            if (auth.isLoggedIn()) {
                // Контракт: есть сессия → accept. Дальше решает экран приглашения.
                Nav.openInviteAccept(this, pendingToken);
                finish();
                return;
            }
            if (pendingToken != null) {
                // Пришли по ссылке/сканированию — обмениваем токен сразу.
                submit(pendingToken);
            }
            return;
        }

        if (auth.isLoggedIn()) {
            // Сессия уже есть: экран QR не нужен.
            Nav.openCampaignList(this);
            finish();
        }
    }

    /** Сообщение о причине показа экрана: истёкшая сессия или отзыв устройства. */
    private void showReason(@Nullable String reason) {
        if (TextUtils.isEmpty(reason)) {
            ViewUtils.show(binding.enrollReason, false);
            return;
        }
        binding.enrollReason.setText(SessionStore.REVOKED_DEVICE.equals(reason)
                ? getString(R.string.enroll_revoked) : getString(R.string.enroll_expired));
        ViewUtils.show(binding.enrollReason, true);
    }

    private void submit(@Nullable String rawToken) {
        String token = QrUtils.extractToken(rawToken);
        if (token == null || token.isEmpty()) {
            showError(getString(R.string.enroll_error_required));
            return;
        }
        if (submitting) {
            return;
        }
        submitting = true;
        hideError();
        ViewUtils.show(binding.enrollProgress, true);
        setEnabled(false);

        auth.enrollDevice(token, DeviceInfo.label(), new RepoCallback<EnrollResponse>() {
            @Override
            public void onSuccess(@Nullable EnrollResponse value) {
                submitting = false;
                ViewUtils.show(binding.enrollProgress, false);
                String name = value == null || value.user == null || value.user.displayName == null
                        ? "" : value.user.displayName;
                ViewUtils.toast(EnrollActivity.this, getString(R.string.auth_welcome, name));
                Nav.openCampaignList(EnrollActivity.this);
                finish();
            }

            @Override
            public void onError(@NonNull ApiException error) {
                submitting = false;
                ViewUtils.show(binding.enrollProgress, false);
                setEnabled(true);
                showError(messageFor(error));
            }
        });
    }

    /** Понятные тексты: просроченный/использованный код, нет сети, всё остальное. */
    @NonNull
    private String messageFor(@NonNull ApiException error) {
        if (error.isNetwork()) {
            return getString(R.string.enroll_error_network);
        }
        if (error.isConflict() || error.isNotFound() || error.isForbidden()
                || "invite_invalid".equals(error.getCode())) {
            return getString(R.string.enroll_error_invalid);
        }
        if (error.isUnauthorized()) {
            return getString(R.string.enroll_error_unauthorized);
        }
        return ru.dndmemory.ui.common.ErrorMessages.text(this, error).toString();
    }

    private void openPasswordLogin() {
        // Вторичный путь: вход по почте или телефону с паролем.
        String invite = pendingToken;
        startActivity(Nav.auth(this, invite));
        finish();
    }

    private void setEnabled(boolean enabled) {
        binding.enrollScan.setEnabled(enabled);
        binding.enrollSubmit.setEnabled(enabled);
        binding.enrollExisting.setEnabled(enabled);
    }

    private void showError(@NonNull String message) {
        binding.enrollError.setText(message);
        ViewUtils.show(binding.enrollError, true);
    }

    private void hideError() {
        ViewUtils.show(binding.enrollError, false);
    }

    private static String text(@Nullable CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }

    /** Экран QR с пояснением причины (используется при отзыве доступа). */
    @NonNull
    public static Intent intent(@NonNull android.content.Context context,
                                @Nullable String reason) {
        Intent intent = new Intent(context, EnrollActivity.class);
        if (reason != null) {
            intent.putExtra(Extras.MESSAGE, reason);
        }
        return intent;
    }
}
