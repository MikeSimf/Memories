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
import ru.dndmemory.data.remote.dto.InvitePreviewDto;
import ru.dndmemory.data.repo.AuthRepository;
import ru.dndmemory.data.repo.InviteRepository;
import ru.dndmemory.databinding.ActivityInviteAcceptBinding;
import ru.dndmemory.ui.common.ErrorMessages;
import ru.dndmemory.ui.common.Extras;
import ru.dndmemory.ui.common.Labels;
import ru.dndmemory.ui.common.Nav;
import ru.dndmemory.ui.common.ViewUtils;
import ru.dndmemory.util.DateFormats;
import ru.dndmemory.util.QrUtils;
import ru.dndmemory.util.RepoCallback;

/**
 * Принятие приглашения. Открывается по deep link {@code dndmemory://join?token=…},
 * из сканера QR или после входа. Предпросмотр приглашения публичный
 * (docs/api.md, раздел 5), а принятие требует авторизации.
 */
public class InviteAcceptActivity extends AppCompatActivity {

    private ActivityInviteAcceptBinding binding;
    private InviteRepository invites;
    private AuthRepository auth;
    private String token;
    private String campaignIdToOpen;

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
        binding.inviteAcceptButton.setText(auth.isLoggedIn()
                ? R.string.action_accept : R.string.action_accept_login);
    }

    private void onPrimaryAction() {
        if (token == null || token.isEmpty()) {
            finish();
            return;
        }
        if (!auth.isLoggedIn()) {
            // Сначала вход: после него AuthActivity вернёт нас сюда с тем же токеном.
            startActivity(Nav.auth(this, token));
            finish();
            return;
        }
        accept();
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
                if (campaignId != null && !campaignId.isEmpty()) {
                    Nav.openCampaign(InviteAcceptActivity.this, campaignId);
                } else {
                    Nav.openCampaignList(InviteAcceptActivity.this);
                }
                finish();
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

    private void showError(@NonNull String message) {
        binding.inviteAcceptError.setText(message);
        ViewUtils.show(binding.inviteAcceptError, true);
        binding.inviteAcceptButton.setEnabled(false);
    }
}
