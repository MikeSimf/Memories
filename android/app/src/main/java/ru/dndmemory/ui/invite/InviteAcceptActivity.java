package ru.dndmemory.ui.invite;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import ru.dndmemory.App;
import ru.dndmemory.R;
import ru.dndmemory.data.remote.ApiException;
import ru.dndmemory.data.remote.dto.AcceptInviteResponse;
import ru.dndmemory.data.remote.dto.EnrollResponse;
import ru.dndmemory.data.remote.dto.InvitePreviewDto;
import ru.dndmemory.data.repo.AuthRepository;
import ru.dndmemory.data.repo.InviteRepository;
import ru.dndmemory.databinding.ActivityInviteAcceptBinding;
import ru.dndmemory.ui.auth.EnrollActivity;
import ru.dndmemory.ui.common.ErrorMessages;
import ru.dndmemory.ui.common.Extras;
import ru.dndmemory.ui.common.Labels;
import ru.dndmemory.ui.common.Nav;
import ru.dndmemory.ui.common.ViewUtils;
import ru.dndmemory.util.DateFormats;
import ru.dndmemory.util.DeviceInfo;
import ru.dndmemory.util.QrUtils;
import ru.dndmemory.util.RepoCallback;

/**
 * Принятие QR-кода. Открывается по deep link {@code dndmemory://join?token=…},
 * из сканера QR или после входа.
 *
 * <p>Правило контракта (docs/api.md, раздел 3.1): <b>есть сессия → accept,
 * сессии нет → enroll</b>. QR-код регистрации устройства ({@code mode: DEVICE})
 * принимается только через {@code POST /api/devices/enroll}: токен участника этот
 * эндпоинт не принимает, и тогда экран предлагает войти или создать аккаунт.
 */
public class InviteAcceptActivity extends AppCompatActivity {

    private ActivityInviteAcceptBinding binding;
    private InviteRepository invites;
    private AuthRepository auth;
    private String token;
    private String campaignIdToOpen;
    /** Предпросмотр показал QR-код регистрации устройства. */
    private boolean deviceQr;
    /** enroll отказал: это обычное приглашение, дальше нужен вход в аккаунт. */
    private boolean loginRequired;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityInviteAcceptBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        invites = App.container(this).inviteRepository();
        auth = App.container(this).authRepository();

        binding.inviteAcceptButton.setOnClickListener(v -> onPrimaryAction());

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(@Nullable Intent intent) {
        token = tokenFrom(intent);
        if (token == null || token.isEmpty()) {
            showError(getString(R.string.invite_accept_invalid));
            return;
        }
        loadPreview();
    }

    /** Токен приходит либо extras-ом, либо из ссылки {@code dndmemory://join?token=…}. */
    @Nullable
    private String tokenFrom(@Nullable Intent intent) {
        if (intent == null) {
            return null;
        }
        if (intent.hasExtra(Extras.INVITE_TOKEN)) {
            String extra = intent.getStringExtra(Extras.INVITE_TOKEN);
            if (extra != null && !extra.isEmpty()) {
                return extra;
            }
        }
        Uri data = intent.getData();
        if (data != null) {
            String fromQuery = data.getQueryParameter("token");
            if (fromQuery != null && !fromQuery.isEmpty()) {
                return fromQuery;
            }
            return QrUtils.extractToken(data.toString());
        }
        return null;
    }

    private void loadPreview() {
        ViewUtils.show(binding.inviteAcceptProgress, true);
        ViewUtils.show(binding.inviteAcceptError, false);
        invites.preview(token, new RepoCallback<InvitePreviewDto>() {
            @Override
            public void onSuccess(@Nullable InvitePreviewDto value) {
                ViewUtils.show(binding.inviteAcceptProgress, false);
                bindPreview(value);
            }

            @Override
            public void onError(@NonNull ApiException error) {
                ViewUtils.show(binding.inviteAcceptProgress, false);
                if (error.isNotFound() || error.isConflict() || error.isForbidden()) {
                    showError(getString(R.string.invite_accept_invalid));
                } else {
                    showError(ErrorMessages.text(InviteAcceptActivity.this, error).toString());
                }
            }
        });
    }

    private void bindPreview(@Nullable InvitePreviewDto preview) {
        if (preview == null || !preview.valid) {
            showError(getString(R.string.invite_accept_invalid));
            return;
        }
        campaignIdToOpen = preview.campaignId;
        deviceQr = preview.isDevice();
        binding.inviteAcceptTitle.setText(deviceQr
                ? R.string.enroll_title : R.string.invite_accept_title);
        binding.inviteAcceptCampaign.setText(getString(R.string.invite_accept_campaign,
                preview.campaignTitle == null ? "" : preview.campaignTitle));
        binding.inviteAcceptRole.setText(getString(R.string.invite_accept_role,
                Labels.roleText(this, preview.role)));
        if (TextUtils.isEmpty(preview.linkedEntityName)) {
            ViewUtils.show(binding.inviteAcceptLinked, false);
        } else {
            binding.inviteAcceptLinked.setText(getString(R.string.invite_accept_linked,
                    preview.linkedEntityName));
            ViewUtils.show(binding.inviteAcceptLinked, true);
        }
        if (TextUtils.isEmpty(preview.expiresAt)) {
            ViewUtils.show(binding.inviteAcceptExpires, false);
        } else {
            binding.inviteAcceptExpires.setText(getString(R.string.invite_accept_expires,
                    DateFormats.formatIso(preview.expiresAt)));
            ViewUtils.show(binding.inviteAcceptExpires, true);
        }
        binding.inviteAcceptButton.setText(buttonLabel());
        binding.inviteAcceptButton.setEnabled(true);
    }

    private int buttonLabel() {
        if (deviceQr) {
            return R.string.enroll_submit;
        }
        return auth.isLoggedIn() ? R.string.action_accept : R.string.action_accept_login;
    }

    private void onPrimaryAction() {
        if (token == null || token.isEmpty()) {
            finish();
            return;
        }
        if (loginRequired) {
            openLogin();
            return;
        }
        if (deviceQr) {
            // QR устройства: входа ещё нет по определению — ведём на экран регистрации.
            startActivity(enrollIntent());
            finish();
            return;
        }
        if (!auth.isLoggedIn()) {
            // Контракт: сессии нет → enroll.
            tryEnroll();
            return;
        }
        accept();
    }

    @NonNull
    private Intent enrollIntent() {
        Intent intent = EnrollActivity.intent(this, null);
        intent.putExtra(Extras.INVITE_TOKEN, token);
        return intent;
    }

    /** Обмен токена из QR на сессию — так входит новый игрок без пароля. */
    private void tryEnroll() {
        ViewUtils.show(binding.inviteAcceptProgress, true);
        binding.inviteAcceptButton.setEnabled(false);
        auth.enrollDevice(token, DeviceInfo.label(), new RepoCallback<EnrollResponse>() {
            @Override
            public void onSuccess(@Nullable EnrollResponse value) {
                ViewUtils.show(binding.inviteAcceptProgress, false);
                String name = value == null || value.user == null ? "" : value.user.displayName;
                ViewUtils.toast(InviteAcceptActivity.this, getString(R.string.auth_welcome, name));
                String id = value != null && value.campaignId != null
                        ? value.campaignId : campaignIdToOpen;
                openCampaignOrList(id);
            }

            @Override
            public void onError(@NonNull ApiException error) {
                ViewUtils.show(binding.inviteAcceptProgress, false);
                binding.inviteAcceptButton.setEnabled(true);
                if (error.isNetwork()) {
                    showError(getString(R.string.enroll_error_network));
                    return;
                }
                // Токен участника enroll не принимает: предлагаем войти или создать аккаунт.
                loginRequired = true;
                binding.inviteAcceptError.setText(R.string.invite_accept_login_required);
                ViewUtils.show(binding.inviteAcceptError, true);
                binding.inviteAcceptButton.setText(R.string.action_accept_login);
            }
        });
    }

    private void accept() {
        ViewUtils.show(binding.inviteAcceptProgress, true);
        binding.inviteAcceptButton.setEnabled(false);
        invites.accept(token, new RepoCallback<AcceptInviteResponse>() {
            @Override
            public void onSuccess(@Nullable AcceptInviteResponse value) {
                ViewUtils.show(binding.inviteAcceptProgress, false);
                ViewUtils.toast(InviteAcceptActivity.this, R.string.invite_accepted);
                String campaignId = value != null && value.campaignId != null
                        ? value.campaignId : campaignIdToOpen;
                openCampaignOrList(campaignId);
            }

            @Override
            public void onError(@NonNull ApiException error) {
                ViewUtils.show(binding.inviteAcceptProgress, false);
                binding.inviteAcceptButton.setEnabled(true);
                if (error.isConflict() || error.isNotFound() || error.isForbidden()) {
                    showError(getString(R.string.invite_accept_invalid));
                } else {
                    showError(ErrorMessages.text(InviteAcceptActivity.this, error).toString());
                }
            }
        });
    }

    private void openCampaignOrList(@Nullable String campaignId) {
        if (campaignId != null && !campaignId.isEmpty()) {
            Nav.openCampaign(this, campaignId);
        } else {
            Nav.openCampaignList(this);
        }
        finish();
    }

    /** Вход по почте или телефону: после него приглашение примется автоматически. */
    private void openLogin() {
        startActivity(Nav.auth(this, token));
        finish();
    }

    private void showError(@NonNull String message) {
        binding.inviteAcceptError.setText(message);
        ViewUtils.show(binding.inviteAcceptError, true);
        binding.inviteAcceptButton.setEnabled(false);
    }
}
