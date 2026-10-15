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
import ru.dndmemory.data.remote.dto.DeviceDto;
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
 * Приглашения по QR и устройства кампании: создание, показ кода, список и отзыв
 * (docs/api.md, разделы 3.1 и 5).
 *
 * <p>Переключатель режима выпуска: «Приглашение участнику» — как раньше,
 * «Регистрация устройства» — QR для нового игрока (сервер сам создаст ему аккаунт).
 * Второй раздел экрана — список устройств с отзывом доступа.
 */
public class InviteActivity extends AppCompatActivity
        implements InviteAdapter.Listener, DeviceAdapter.Listener {

    private static final int MEMBER_EXPIRES_HOURS = 168;
    private static final int DEVICE_EXPIRES_HOURS = 24;

    private ActivityInviteBinding binding;
    private InviteViewModel viewModel;
    private InviteAdapter adapter;
    private DeviceAdapter deviceAdapter;
    private String campaignId;

    private final List<EntityRoom> heroes = new ArrayList<>();
    private NameIndex nameIndex = NameIndex.of(null);
    /** Какой раздел открыт: приглашения или устройства. */
    private boolean devicesSection;

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
                () -> new InviteViewModel(container.inviteRepository(),
                        container.deviceRepository())))
                .get(InviteViewModel.class);

        setSupportActionBar(binding.inviteToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        adapter = new InviteAdapter(this);
        binding.inviteRecycler.setLayoutManager(new LinearLayoutManager(this));
        binding.inviteRecycler.setAdapter(adapter);

        deviceAdapter = new DeviceAdapter(this);
        binding.deviceRecycler.setLayoutManager(new LinearLayoutManager(this));
        binding.deviceRecycler.setAdapter(deviceAdapter);

        binding.inviteSectionToggle.check(R.id.invite_section_invites);
        binding.inviteSectionToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                showSection(checkedId == R.id.invite_section_devices);
            }
        });

        binding.inviteSwipe.setOnRefreshListener(() -> viewModel.load(campaignId));
        binding.deviceSwipe.setOnRefreshListener(() -> viewModel.loadDevices(campaignId));
        binding.inviteCreate.setOnClickListener(v -> showCreateDialog());
        binding.inviteScan.setOnClickListener(v ->
                scanLauncher.launch(new Intent(this, ScanActivity.class)));
        binding.inviteQrClose.setOnClickListener(v ->
                ViewUtils.show(binding.inviteQrGroup, false));

        loadHeroes(container);
        observe();
        viewModel.load(campaignId);
    }

    /** Разделы экрана: приглашения или устройства кампании. */
    private void showSection(boolean devices) {
        devicesSection = devices;
        ViewUtils.show(binding.inviteSwipe, !devices);
        ViewUtils.show(binding.inviteEmpty, !devices && adapter.getItemCount() == 0);
        ViewUtils.show(binding.deviceSwipe, devices);
        ViewUtils.show(binding.deviceEmpty, devices && deviceAdapter.getItemCount() == 0);
        ViewUtils.show(binding.inviteActions, !devices);
        if (devices) {
            viewModel.loadDevices(campaignId);
        } else {
            viewModel.load(campaignId);
        }
    }

    private void loadHeroes(@NonNull AppContainer container) {
        container.entityRepository().loadEntities(campaignId,
                new RepoCallback<List<EntityRoom>>() {
                    @Override
                    public void onSuccess(@Nullable List<EntityRoom> value) {
                        heroes.clear();
                        if (value != null) {
                            for (EntityRoom entity : value) {
                                if ("HERO".equalsIgnoreCase(entity.type)) {
                                    heroes.add(entity);
                                }
                            }
                        }
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
                // Пустое состояние показываем только в открытом разделе.
                ViewUtils.show(binding.inviteEmpty,
                        !devicesSection && (items == null || items.isEmpty()));
            }
        });

        viewModel.deviceState().observe(this, resource -> {
            if (resource == null) {
                return;
            }
            binding.deviceSwipe.setRefreshing(resource.isLoading());
            ViewUtils.show(binding.inviteProgress, resource.isLoading());
            if (resource.isError()) {
                ErrorMessages.show(binding.getRoot(), resource.error);
                return;
            }
            if (resource.isSuccess()) {
                List<DeviceDto> items = resource.data;
                deviceAdapter.submitList(items);
                ViewUtils.show(binding.deviceEmpty,
                        devicesSection && (items == null || items.isEmpty()));
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
        binding.inviteQrTitle.setText(invite.isDevice()
                ? R.string.invite_qr_device_title : R.string.invite_qr_title);
        binding.inviteQrToken.setText(getString(R.string.invite_token,
                invite.token == null ? "" : invite.token));
        binding.inviteQrLink.setText(getString(R.string.invite_link,
                invite.deepLink == null ? "" : invite.deepLink));
        Bitmap bitmap = QrUtils.decodeBase64Png(invite.qrPngBase64);
        if (bitmap != null) {
            binding.inviteQrImage.setImageBitmap(bitmap);
        }
        ViewUtils.toast(this, invite.isDevice()
                ? R.string.invite_device_created : R.string.invite_created);
    }

    /**
     * Создание приглашения или QR устройства: режим, роль, привязка к герою,
     * срок; для устройства ещё имя игрока и подпись (docs/api.md, раздел 3.1).
     */
    private void showCreateDialog() {
        final DialogInviteCreateBinding dialog = DialogInviteCreateBinding.inflate(getLayoutInflater());

        String[] roleLabels = new String[ROLES.length];
        for (int i = 0; i < ROLES.length; i++) {
            roleLabels[i] = getString(Labels.role(ROLES[i]));
        }
        dialog.inviteCreateRole.setSimpleItems(roleLabels);
        dialog.inviteCreateRole.setText(roleLabels[0], false);

        List<String> heroLabels = new ArrayList<>();
        heroLabels.add(getString(R.string.retelling_author_none));
        for (EntityRoom hero : heroes) {
            if (!TextUtils.isEmpty(hero.name)) {
                heroLabels.add(hero.name);
            }
        }
        dialog.inviteCreateLinked.setSimpleItems(heroLabels.toArray(new String[0]));
        dialog.inviteCreateLinked.setText(heroLabels.get(0), false);

        dialog.inviteCreateMode.check(R.id.invite_create_mode_member);
        dialog.inviteCreateExpires.setText(String.valueOf(MEMBER_EXPIRES_HOURS));
        dialog.inviteCreateMaxUses.setText("1");
        applyCreateMode(dialog, false);

        dialog.inviteCreateMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                applyCreateMode(dialog, checkedId == R.id.invite_create_mode_device);
            }
        });

        new MaterialAlertDialogBuilder(this)
                .setView(dialog.getRoot())
                .setPositiveButton(R.string.action_create, (d, which) -> {
                    boolean deviceMode = dialog.inviteCreateMode.getCheckedButtonId()
                            == R.id.invite_create_mode_device;
                    MemberRole role = selectedRole(dialog.inviteCreateRole.getText());
                    String linked = selectedHeroId(dialog.inviteCreateLinked.getText());
                    int hours = parseInt(text(dialog.inviteCreateExpires.getText()),
                            deviceMode ? DEVICE_EXPIRES_HOURS : MEMBER_EXPIRES_HOURS);
                    if (deviceMode) {
                        String name = text(dialog.inviteCreateName.getText());
                        if (name.isEmpty()) {
                            ViewUtils.toast(this, R.string.invite_device_name_required);
                            return;
                        }
                        int uses = parseInt(text(dialog.inviteCreateMaxUses.getText()), 1);
                        viewModel.createEnrollment(campaignId, name, role.name(), linked,
                                hours, uses, text(dialog.inviteCreateLabel.getText()));
                    } else {
                        int uses = parseInt(text(dialog.inviteCreateMaxUses.getText()), 1);
                        viewModel.create(campaignId, role.name(), linked, hours, uses);
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    /** Поля режима устройства: имя игрока и подпись; срок по умолчанию — сутки. */
    private void applyCreateMode(@NonNull DialogInviteCreateBinding dialog, boolean device) {
        ViewUtils.show(dialog.inviteCreateNameGroup, device);
        ViewUtils.show(dialog.inviteCreateLabelGroup, device);
        dialog.inviteCreateExpires.setText(String.valueOf(
                device ? DEVICE_EXPIRES_HOURS : MEMBER_EXPIRES_HOURS));
        dialog.inviteCreateMaxUses.setText("1");
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
        return nameIndex.idFor(value);
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
    public void onDeviceRevoke(@NonNull final DeviceDto device) {
        String name = TextUtils.isEmpty(device.displayName)
                ? getString(R.string.attr_not_set) : device.displayName;
        new MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.device_revoke_confirm, name))
                .setPositiveButton(R.string.action_revoke, (dialog, which) -> {
                    if (device.id != null) {
                        viewModel.revokeDevice(campaignId, device.id);
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
