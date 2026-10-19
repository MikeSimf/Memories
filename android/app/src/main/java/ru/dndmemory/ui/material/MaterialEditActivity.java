package ru.dndmemory.ui.material;

import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import ru.dndmemory.App;
import ru.dndmemory.AppContainer;
import ru.dndmemory.R;
import ru.dndmemory.data.local.entity.MaterialRoom;
import ru.dndmemory.data.remote.ApiException;
import ru.dndmemory.data.remote.dto.FileDto;
import ru.dndmemory.databinding.ActivityMaterialEditBinding;
import ru.dndmemory.ui.common.ErrorMessages;
import ru.dndmemory.ui.common.Extras;
import ru.dndmemory.ui.common.ImageLoader;
import ru.dndmemory.ui.common.ImageTools;
import ru.dndmemory.ui.common.ViewUtils;
import ru.dndmemory.data.Mappers;
import ru.dndmemory.util.AppExecutors;
import ru.dndmemory.util.RepoCallback;

/**
 * Редактор фото-материала: картинка, название, описание со ссылками {@code [[Имя]]}.
 * Всё сохраняется локально и уходит в очередь синхронизации — можно работать
 * в самолёте, правка доедет при появлении сети.
 */
public class MaterialEditActivity extends AppCompatActivity {

    private ActivityMaterialEditBinding binding;
    private AppContainer container;
    private MaterialRoom draft;
    private String campaignId;
    private boolean creating;
    private ActivityResultLauncher<String> photoPicker;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMaterialEditBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        campaignId = getIntent() == null ? null : getIntent().getStringExtra(Extras.CAMPAIGN_ID);
        String materialId = getIntent() == null ? null : getIntent().getStringExtra(Extras.MATERIAL_ID);
        creating = getIntent() != null && getIntent().getBooleanExtra(Extras.CREATE_NEW, false);
        if (campaignId == null) {
            finish();
            return;
        }

        container = App.container(this);
        photoPicker = registerForActivityResult(new ActivityResultContracts.GetContent(), this::onPhotoPicked);

        setSupportActionBar(binding.materialToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(creating
                    ? R.string.material_edit_title_new : R.string.material_edit_title_edit);
        }

        draft = materialId == null ? container.materialRepository().createDraft(campaignId)
                : container.materialRepository().find(materialId);
        if (draft == null) {
            draft = container.materialRepository().createDraft(campaignId);
        }

        binding.materialNameInput.setText(draft.name);
        binding.materialDescriptionInput.setText(draft.description);
        showPhoto(draft.imageFileId);
        ViewUtils.show(binding.materialDelete, !creating);

        binding.materialPhotoPick.setOnClickListener(v -> photoPicker.launch("image/*"));
        binding.materialPhotoClear.setOnClickListener(v -> {
            draft.imageFileId = null;
            showPhoto(null);
        });
        binding.materialSave.setOnClickListener(v -> save());
        binding.materialDelete.setOnClickListener(v -> confirmDelete());

        // Подсказки [[Имя]] в описании.
        ru.dndmemory.ui.common.WikiLinkEditor.attach(binding.materialDescriptionInput,
                () -> nameIndex());
    }

    /** Имена объектов кампании для подсказок ссылок. */
    @NonNull
    private java.util.List<String> nameIndex() {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (ru.dndmemory.data.local.entity.EntityRoom room
                : container.entityRepository().findAllNow(campaignId)) {
            if (room.name != null && !room.name.isEmpty()) {
                names.add(room.name);
            }
        }
        return names;
    }

    private void save() {
        String name = binding.materialNameInput.getText() == null ? ""
                : binding.materialNameInput.getText().toString().trim();
        if (name.isEmpty()) {
            ViewUtils.toast(this, R.string.error_required);
            return;
        }
        draft.name = name;
        draft.description = binding.materialDescriptionInput.getText() == null ? ""
                : binding.materialDescriptionInput.getText().toString().trim();
        ViewUtils.show(binding.materialProgress, true);
        binding.materialSave.setEnabled(false);
        container.materialRepository().save(draft, new RepoCallback<MaterialRoom>() {
            @Override
            public void onSuccess(@Nullable MaterialRoom value) {
                ViewUtils.show(binding.materialProgress, false);
                binding.materialSave.setEnabled(true);
                ViewUtils.toast(MaterialEditActivity.this, R.string.saved);
                container.syncScheduler().syncNow(campaignId);
                finish();
            }

            @Override
            public void onError(@NonNull ApiException error) {
                ViewUtils.show(binding.materialProgress, false);
                binding.materialSave.setEnabled(true);
                ErrorMessages.show(binding.getRoot(), error);
            }
        });
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.material_delete_confirm,
                        TextUtils.isEmpty(draft.name) ? getString(R.string.material_no_photo) : draft.name))
                .setPositiveButton(R.string.action_delete, (dialog, which) ->
                        container.materialRepository().delete(draft.id, new RepoCallback<Void>() {
                            @Override
                            public void onSuccess(@Nullable Void value) {
                                container.syncScheduler().syncNow(campaignId);
                                finish();
                            }

                            @Override
                            public void onError(@NonNull ApiException error) {
                                ErrorMessages.show(binding.getRoot(), error);
                            }
                        }))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    // --- Фото ---------------------------------------------------------------

    private void onPhotoPicked(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }
        ViewUtils.toast(this, R.string.entity_photo_uploading);
        binding.materialPhotoPick.setEnabled(false);
        AppExecutors.get().io(() -> {
            final byte[] data;
            try {
                data = ImageTools.compress(this, uri);
            } catch (java.io.IOException e) {
                throw ApiException.unexpected(e);
            }
            if (data == null) {
                throw ApiException.unexpected(new IllegalArgumentException(
                        getString(R.string.entity_photo_too_big)));
            }
            container.fileRepository().upload(data, "image/jpeg", "material.jpg",
                    new RepoCallback<FileDto>() {
                        @Override
                        public void onSuccess(@Nullable FileDto value) {
                            binding.materialPhotoPick.setEnabled(true);
                            if (value != null && value.id != null) {
                                draft.imageFileId = value.id;
                                showPhoto(value.id);
                            }
                        }

                        @Override
                        public void onError(@NonNull ApiException error) {
                            binding.materialPhotoPick.setEnabled(true);
                            ViewUtils.toast(MaterialEditActivity.this,
                                    getString(R.string.entity_photo_failed,
                                            ErrorMessages.text(MaterialEditActivity.this, error)));
                        }
                    });
            return null;
        }, new RepoCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void value) {
            }

            @Override
            public void onError(@NonNull ApiException error) {
                binding.materialPhotoPick.setEnabled(true);
                ViewUtils.toast(MaterialEditActivity.this, R.string.entity_photo_too_big);
            }
        });
    }

    private void showPhoto(@Nullable String fileId) {
        boolean has = fileId != null && !fileId.isEmpty();
        ViewUtils.show(binding.materialPhotoClear, has);
        if (!has) {
            binding.materialPhoto.setImageDrawable(null);
            ViewUtils.show(binding.materialPhoto, false);
            return;
        }
        ImageLoader.load(container.okHttpClient(), fileId, binding.materialPhoto);
        binding.materialPhoto.setOnClickListener(v -> openPhoto(fileId));
    }

    private void openPhoto(@NonNull String fileId) {
        ImageLoader.loadFull(container.okHttpClient(), fileId, bitmap -> {
            android.widget.ImageView view = new android.widget.ImageView(this);
            view.setAdjustViewBounds(true);
            view.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            view.setImageBitmap(bitmap);
            new androidx.appcompat.app.AlertDialog.Builder(this,
                    android.R.style.Theme_DeviceDefault_NoActionBar)
                    .setView(view)
                    .setPositiveButton(R.string.action_close, null)
                    .show();
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
