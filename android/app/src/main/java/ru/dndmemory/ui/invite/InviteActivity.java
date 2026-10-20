package ru.dndmemory.ui.invite;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import ru.dndmemory.App;
import ru.dndmemory.AppContainer;
import ru.dndmemory.R;
import ru.dndmemory.data.local.entity.EntityRoom;
import ru.dndmemory.data.model.MemberRole;
import ru.dndmemory.data.remote.ApiException;
import ru.dndmemory.data.remote.dto.InviteDto;
import ru.dndmemory.databinding.ActivityInviteBinding;
import ru.dndmemory.databinding.DialogInviteCreateBinding;
import ru.dndmemory.ui.common.ErrorMessages;
import ru.dndmemory.ui.common.Extras;
import ru.dndmemory.ui.common.Labels;
import ru.dndmemory.ui.common.NameIndex;
import ru.dndmemory.ui.common.Nav;
import ru.dndmemory.ui.common.ViewModelFactory;
import ru.dndmemory.ui.common.ViewUtils;
import ru.dndmemory.util.QrUtils;
import ru.dndmemory.util.RepoCallback;
import ru.dndmemory.util.Resource;

/**
 * Приглашения по QR: выпуск одноразового кода, показ QR, список и отзыв
 * (docs/api.md, раздел 5). Регистрации устройства больше нет — новый игрок
 * по QR либо регистрируется, либо входит своим аккаунтом.
 */
public class InviteActivity extends AppCompatActivity implements InviteAdapter.Listener {

    /** Срок по умолчанию — 7 дней; число использований всегда 1. */
    private static final int MEMBER_EXPIRES_HOURS = 168;

    private ActivityInviteBinding binding;
    private InviteViewModel viewModel;
    private InviteAdapter adapter;
    private String campaignId;

    private final List<EntityRoom> heroes = new ArrayList<>();
    private NameIndex nameIndex = NameIndex.of(null);

    private final ActivityResultLauncher<Intent> scanLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                    return;
                }
                String token = result.getData().getStringExtra(Extras.INVITE_TOKEN);
                if (token != null && !token.isEmpty()) {
                    Nav.openInviteAccept(this, token);
                }
            });

    private static final MemberRole[] ROLES = {
            MemberRole.EDITOR_HERO, MemberRole.EDITOR_MASTER, MemberRole.VIEWER};

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityInviteBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        campaignId = getIntent() == null ? null : getIntent().getStringExtra(Extras.CAMPAIGN_ID);
        if (campaignId == null) {
            finish();
            return;
        }

        AppContainer container = App.container(this);
        viewModel = new ViewModelProvider(this, ViewModelFactory.of(
                () -> new InviteViewModel(container.inviteRepository())))
                .get(InviteViewModel.class);

        setSupportActionBar(binding.inviteToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        adapter = new InviteAdapter(this);
        binding.inviteRecycler.setLayoutManager(new LinearLayoutManager(this));
        binding.inviteRecycler.setAdapter(adapter);

        binding.inviteSwipe.setOnRefreshListener(() -> viewModel.load(campaignId));
        binding.inviteCreate.setOnClickListener(v -> showCreateDialog());
        binding.inviteScan.setOnClickListener(v ->
                scanLauncher.launch(new Intent(this, ScanActivity.class)));
        binding.inviteQrClose.setOnClickListener(v ->
                ViewUtils.show(binding.inviteQrGroup, false));

        loadHeroes(container);
        observe();
        viewModel.load(campaignId);
    }

    private void loadHeroes(@NonNull AppContainer container) {
        container.entityRepository().loadHeroes(campaignId,
                new RepoCallback<List<EntityRoom>>() {
                    @Override
                    public void onSuccess(@Nullable List<EntityRoom> value) {
                        heroes.clear();
                        if (value != null) {
                            heroes.addAll(value);
                        }
                    }

                    @Override
                    public void onError(@NonNull ApiException error) {
                        heroes.clear();
                    }
                });
        container.entityRepository().loadEntities(campaignId,
                new RepoCallback<List<EntityRoom>>() {
                    @Override
                    public void onSuccess(@Nullable List<EntityRoom> value) {
                        nameIndex = NameIndex.of(value);
                    }

                    @Override
                    public void onError(@NonNull ApiException error) {
                        nameIndex = NameIndex.of(null);
                    }
                });
    }

    private void observe() {
        viewModel.listState().observe(this, resource -> {
            if (resource == null) {
                return;
            }
            binding.inviteSwipe.setRefreshing(resource.isLoading());
            ViewUtils.show(binding.inviteProgress, resource.isLoading());
            if (resource.isError()) {
                ErrorMessages.show(binding.getRoot(), resource.error);
                return;
            }
            if (resource.isSuccess()) {
                List<InviteDto> items = resource.data;
                adapter.submitList(items);
                ViewUtils.show(binding.inviteEmpty, items == null || items.isEmpty());
            }
        });

        viewModel.created().observe(this, this::showQr);

        viewModel.actionState().observe(this, resource -> {
            if (resource == null) {
                return;
            }
            if (resource.isError()) {
                ErrorMessages.show(binding.getRoot(), resource.error);
            }
            viewModel.clearActionState();
        });
    }

    private void showQr(@Nullable InviteDto invite) {
        if (invite == null) {
            return;
        }
        ViewUtils.show(binding.inviteQrGroup, true);
        binding.inviteQrTitle.setText(R.string.invite_qr_title);
        binding.inviteQrToken.setText(getString(R.string.invite_token,
                invite.token == null ? "" : invite.token));
        binding.inviteQrLink.setText(getString(R.string.invite_link,
                invite.deepLink == null ? "" : invite.deepLink));
        Bitmap bitmap = QrUtils.decodeBase64Png(invite.qrPngBase64);
        if (bitmap != null) {
            binding.inviteQrImage.setImageBitmap(bitmap);
        }
        ViewUtils.toast(this, R.string.invite_created);
    }

    /** Создание приглашения: роль, привязка к герою и срок действия. */
    private void showCreateDialog() {
        final DialogInviteCreateBinding dialog = DialogInviteCreateBinding.inflate(getLayoutInflater());

        String[] roleLabels = new String[ROLES.length];
        for (int i = 0; i < ROLES.length; i++) {
            roleLabels[i] = getString(Labels.role(ROLES[i]));
        }
        ru.dndmemory.ui.common.Dropdowns.bind(dialog.inviteCreateRole,
                java.util.Arrays.asList(roleLabels));
        dialog.inviteCreateRole.setText(roleLabels[0], false);

        List<String> heroLabels = new ArrayList<>();
        heroLabels.add(getString(R.string.retelling_author_none));
        for (EntityRoom hero : heroes) {
            if (!TextUtils.isEmpty(hero.name)) {
                heroLabels.add(hero.name);
            }
        }
        ru.dndmemory.ui.common.Dropdowns.bind(dialog.inviteCreateLinked, heroLabels);
        dialog.inviteCreateLinked.setText(heroLabels.get(0), false);
        dialog.inviteCreateExpires.setText(String.valueOf(MEMBER_EXPIRES_HOURS));

        new MaterialAlertDialogBuilder(this)
                .setView(dialog.getRoot())
                .setPositiveButton(R.string.action_create, (d, which) -> {
                    MemberRole role = selectedRole(dialog.inviteCreateRole.getText());
                    String linked = selectedHeroId(dialog.inviteCreateLinked.getText());
                    int hours = parseInt(text(dialog.inviteCreateExpires.getText()),
                            MEMBER_EXPIRES_HOURS);
                    viewModel.create(campaignId, role.name(), linked, hours);
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    @NonNull
    private MemberRole selectedRole(@Nullable CharSequence shown) {
        if (shown != null) {
            for (MemberRole role : ROLES) {
                if (getString(Labels.role(role)).contentEquals(shown)) {
                    return role;
                }
            }
        }
        return MemberRole.EDITOR_HERO;
    }

    @Nullable
    private String selectedHeroId(@Nullable CharSequence shown) {
        if (shown == null) {
            return null;
        }
        String value = shown.toString().trim();
        if (value.isEmpty() || value.equals(getString(R.string.retelling_author_none))) {
            return null;
        }
        String id = nameIndex.idFor(value);
        if (id != null) {
            return id;
        }
        for (EntityRoom hero : heroes) {
            if (value.equals(hero.name)) {
                return hero.id;
            }
        }
        return null;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String text(@Nullable CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }

    @Override
    public void onInviteClick(@NonNull InviteDto invite) {
        showQr(invite);
    }

    @Override
    public void onInviteRevoke(@NonNull final InviteDto invite) {
        new MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.invite_revoke_confirm,
                        invite.token == null ? "" : invite.token))
                .setPositiveButton(R.string.action_revoke, (dialog, which) -> {
                    if (invite.id != null) {
                        viewModel.revoke(campaignId, invite.id);
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
