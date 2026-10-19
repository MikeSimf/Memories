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
import ru.dndmemory.ui.common.FileDownloads;
import ru.dndmemory.ui.common.ImageLoader;
import ru.dndmemory.ui.common.FileTools;
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
    private ActivityResultLauncher<String> documentPicker;

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
        photoPicker = registerForActivityResult(new ActivityResultContracts.GetContent(),
                uri -> onFilePicked(uri, true));
        documentPicker = registerForActivityResult(new ActivityResultContracts.GetContent(),
                uri -> onFilePicked(uri, false));

        setSupportActionBar(binding.materialToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(creating
                    ? R.string.material_edit_title_new : R.string.material_edit_title_edit);
        }

        // Room нельзя читать в главном потоке: карточка открывалась и падала.
        if (materialId == null) {
            draft = container.materialRepository().createDraft(campaignId);
            bindDraft();
        } else {
            ViewUtils.show(binding.materialProgress, true);
            AppExecutors.get().io(() -> container.materialRepository().find(materialId),
                    new RepoCallback<MaterialRoom>() {
                        @Override
                        public void onSuccess(@Nullable MaterialRoom value) {
                            ViewUtils.show(binding.materialProgress, false);
                            draft = value != null ? value
                                    : container.materialRepository().createDraft(campaignId);
                            bindDraft();
                        }

                        @Override
                        public void onError(@NonNull ApiException error) {
                            ViewUtils.show(binding.materialProgress, false);
                            draft = container.materialRepository().createDraft(campaignId);
                            bindDraft();
                        }
                    });
        }

        binding.materialPhotoPick.setOnClickListener(v -> photoPicker.launch("image/*"));
        binding.materialFileDocument.setOnClickListener(v -> documentPicker.launch("*/*"));
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

    private String mimeOf(@Nullable Uri uri) {
        String type = uri == null ? null : getContentResolver().getType(uri);
        return type == null ? "application/octet-stream" : type;
    }

    private String fileNameOf(@Nullable Uri uri, boolean image) {
        String fallback = image ? "photo.jpg" : "document";
        if (uri == null) {
            return fallback;
        }
        String mime = mimeOf(uri);
        String ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
        return ext == null ? fallback : fallback.replaceAll("\\..*$", "") + "." + ext;
    }

    // --- Фото ---------------------------------------------------------------

    /** Выбор файла: картинка сразу показывается, документ предлагается скачать. */
    private void onFilePicked(@Nullable Uri uri, boolean image) {
        if (uri == null) {
            return;
        }
        ViewUtils.toast(this, R.string.entity_photo_uploading);
        binding.materialPhotoPick.setEnabled(false);
        AppExecutors.get().io(() -> {
            final byte[] data;
            try {
                // Картинку сжимаем, документ (pdf/docx) отправляем как есть.
                data = image
                        ? ImageTools.compress(this, uri)
                        : FileTools.read(this, uri, FileTools.MAX_BYTES);
            } catch (java.io.IOException e) {
                throw ApiException.unexpected(e);
            }
            if (data == null) {
                throw ApiException.unexpected(new IllegalArgumentException(
                        getString(R.string.entity_photo_too_big)));
            }
            final String contentType = image ? "image/jpeg" : mimeOf(uri);
            final String fileName = fileNameOf(uri, image);
            container.fileRepository().upload(data, contentType, fileName,
                    new RepoCallback<FileDto>() {
                        @Override
                        public void onSuccess(@Nullable FileDto value) {
                            binding.materialPhotoPick.setEnabled(true);
                            if (value != null && value.id != null) {
                                java.util.List<ru.dndmemory.data.model.Attachment> files =
                                        ru.dndmemory.data.Mappers.files(draft.filesJson);
                                files.add(new ru.dndmemory.data.model.Attachment(value.id, fileName,
                                        contentType, value.size));
                                draft.filesJson = ru.dndmemory.data.Mappers.filesJson(files);
                                renderFiles();
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

    /** Показывает загруженный материал: поля, файлы и кнопку удаления. */
    private void bindDraft() {
        if (draft == null) {
            return;
        }
        binding.materialNameInput.setText(draft.name);
        binding.materialDescriptionInput.setText(draft.description);
        showPhoto(draft.imageFileId);
        renderFiles();
        ViewUtils.show(binding.materialDelete, !creating);
    }

    /** Список файлов материала: миниатюра картинки или имя документа. */
    private void renderFiles() {
        binding.materialFiles.removeAllViews();
        java.util.List<ru.dndmemory.data.model.Attachment> files =
                ru.dndmemory.data.Mappers.files(draft.filesJson);
        for (final ru.dndmemory.data.model.Attachment file : files) {
            android.widget.LinearLayout row = (android.widget.LinearLayout) getLayoutInflater()
                    .inflate(R.layout.item_attachment, binding.materialFiles, false);
            android.widget.ImageView thumb = row.findViewById(R.id.attachment_thumb);
            android.widget.TextView name = row.findViewById(R.id.attachment_name);
            com.google.android.material.button.MaterialButton action =
                    row.findViewById(R.id.attachment_action);

            name.setText(file.name == null || file.name.isEmpty() ? file.id : file.name);
            if (file.isImage()) {
                ImageLoader.load(container.okHttpClient(), file.id, thumb);
                action.setText(R.string.file_open);
                action.setOnClickListener(v -> openPhoto(file.id));
            } else {
                // Документ: отметка «скачано» и скачивание в «Загрузки».
                boolean downloaded = FileDownloads.isDownloaded(this, file.id);
                name.append(downloaded ? "\n" + getString(R.string.file_downloaded) : "");
                action.setText(downloaded ? R.string.file_open : R.string.update_download);
                action.setOnClickListener(v -> {
                    if (FileDownloads.isDownloaded(MaterialEditActivity.this, file.id)) {
                        ViewUtils.toast(MaterialEditActivity.this, R.string.file_downloaded);
                    } else {
                        FileDownloads.download(MaterialEditActivity.this, file.id, file.name);
                        renderFiles();
                    }
                });
            }

            final String fileId = file.id;
            row.setOnLongClickListener(v -> {
                java.util.List<ru.dndmemory.data.model.Attachment> list =
                        ru.dndmemory.data.Mappers.files(draft.filesJson);
                java.util.Iterator<ru.dndmemory.data.model.Attachment> it = list.iterator();
                while (it.hasNext()) {
                    if (fileId.equals(it.next().id)) {
                        it.remove();
                    }
                }
                draft.filesJson = ru.dndmemory.data.Mappers.filesJson(list);
                renderFiles();
                return true;
            });
            binding.materialFiles.addView(row);
        }
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
