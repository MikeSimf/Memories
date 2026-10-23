package ru.dndmemory.ui.entity;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.List;

import ru.dndmemory.App;
import ru.dndmemory.AppContainer;
import ru.dndmemory.data.remote.ApiException;
import ru.dndmemory.data.remote.dto.FileDto;
import ru.dndmemory.ui.common.ImageLoader;
import ru.dndmemory.ui.common.ImageTools;
import ru.dndmemory.R;
import ru.dndmemory.data.local.entity.CampaignRoom;
import ru.dndmemory.data.local.entity.EntityRoom;
import ru.dndmemory.data.local.entity.MemberRoom;
import ru.dndmemory.data.model.EntityType;
import ru.dndmemory.data.model.HeroItem;
import ru.dndmemory.databinding.ActivityEntityEditBinding;
import ru.dndmemory.databinding.ItemHeroItemEditBinding;
import ru.dndmemory.ui.campaign.CampaignViewModel;
import ru.dndmemory.ui.common.ErrorMessages;
import ru.dndmemory.ui.common.Extras;
import ru.dndmemory.ui.common.Labels;
import ru.dndmemory.ui.common.NameIndex;
import ru.dndmemory.ui.common.Rights;
import ru.dndmemory.ui.common.ViewModelFactory;
import ru.dndmemory.ui.common.ViewUtils;
import ru.dndmemory.ui.common.WikiLinkEditor;
import ru.dndmemory.util.AppExecutors;
import ru.dndmemory.util.RepoCallback;

/**
 * Редактор объекта. Набор полей зависит от класса (docs/api.md, раздел 6):
 * у героя — раса, класс, игрок и котомка; у персонажа — раса и краткое описание;
 * у локации своих полей нет; у предмета — владелец и необязательная ссылка.
 *
 * <p>Героя заводит и правит только владелец кампании: остальным редактор
 * не открывается (docs/roles.md).
 */
public class EntityEditActivity extends ru.dndmemory.ui.common.BottomNavActivity {

    private ActivityEntityEditBinding binding;
    private EntityEditViewModel viewModel;
    private CampaignViewModel campaignViewModel;

    private String campaignId;
    private boolean creating;
    private boolean rightsBlocked;

    private NameIndex nameIndex = NameIndex.of(null);
    private List<EntityRoom> candidates = new ArrayList<>();
    private List<MemberRoom> members = new ArrayList<>();

    private EntityType currentKind = EntityType.CHARACTER;
    private String selectedPlayerUserId;
    private String selectedOwnerId;

    private final List<ItemHeroItemEditBinding> itemRows = new ArrayList<>();

    /** Выбор фото из галереи: системный диалог, без разрешений в манифесте. */
    private ActivityResultLauncher<String> photoPicker;
    /** Выбор отдельного аватара: та же галерея, но картинка не попадает в files. */
    private ActivityResultLauncher<String> avatarPicker;
    private AppContainer container;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEntityEditBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        campaignId = getIntent() == null ? null : getIntent().getStringExtra(Extras.CAMPAIGN_ID);
        String entityId = getIntent() == null ? null : getIntent().getStringExtra(Extras.ENTITY_ID);
        String kindExtra = getIntent() == null ? null : getIntent().getStringExtra(Extras.ENTITY_TYPE);
        creating = getIntent() != null && getIntent().getBooleanExtra(Extras.CREATE_NEW, false);
        if (campaignId == null) {
            finish();
            return;
        }
        EntityType requested = EntityType.from(kindExtra);
        if (requested != null) {
            currentKind = requested;
        }

        container = App.container(this);
        photoPicker = registerForActivityResult(new ActivityResultContracts.GetContent(), this::onPhotoPicked);
        avatarPicker = registerForActivityResult(new ActivityResultContracts.GetContent(), this::onAvatarPicked);
        viewModel = new ViewModelProvider(this, ViewModelFactory.of(
                () -> new EntityEditViewModel(container.entityRepository(),
                        container.memberRepository(), AppExecutors.get())))
                .get(EntityEditViewModel.class);
        viewModel.start(campaignId, entityId, currentKind, creating);

        campaignViewModel = new ViewModelProvider(this, ViewModelFactory.of(
                () -> new CampaignViewModel(container.campaignRepository(),
                        container.authRepository(), container.syncEngine(),
                        container.syncScheduler())))
                .get(CampaignViewModel.class);
        campaignViewModel.setCampaignId(campaignId);
        campaignViewModel.refreshDetails();

        setSupportActionBar(binding.entityEditToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(titleFor(currentKind, creating));
        }

        // Ввод ссылок: «[[» сразу закрывается, после трёх букв — подсказки.
        // Справочники: по нажатию виден весь список, ввод фильтрует его.
        ru.dndmemory.ui.common.SuggestField.attach(binding.entityEditRaceInput,
                container.raceRepository()::cachedNames);
        ru.dndmemory.ui.common.SuggestField.attach(binding.entityEditKlassInput,
                container.raceRepository()::cachedClasses);
        ru.dndmemory.ui.common.SuggestField.attach(binding.entityEditLocationType,
                container.raceRepository()::cachedLocationTypes);
        ru.dndmemory.ui.common.SuggestField.attach(binding.entityEditItemType,
                container.raceRepository()::cachedItemTypes);
        // Владелец: подсказки только после трёх введённых символов.
        ru.dndmemory.ui.common.SuggestField.attach(binding.entityEditOwnerLink,
                this::ownerLabels, 3);
        container.raceRepository().refresh(new RepoCallback<java.util.List<String>>() {
            @Override
            public void onSuccess(@Nullable java.util.List<String> value) {
                applyReferenceDefaults();
            }

            @Override
            public void onError(@NonNull ApiException error) {
                // Без сети остаются значения, загруженные ранее.
            }
        });
        container.raceRepository().refreshLocationTypes(new RepoCallback<java.util.List<String>>() {
            @Override
            public void onSuccess(@Nullable java.util.List<String> value) {
                applyReferenceDefaults();
            }

            @Override
            public void onError(@NonNull ApiException error) {
            }
        });
        container.raceRepository().refreshItemTypes(new RepoCallback<java.util.List<String>>() {
            @Override
            public void onSuccess(@Nullable java.util.List<String> value) {
                applyReferenceDefaults();
            }

            @Override
            public void onError(@NonNull ApiException error) {
            }
        });
        container.raceRepository().refreshClasses(new RepoCallback<java.util.List<String>>() {
            @Override
            public void onSuccess(@Nullable java.util.List<String> value) {
                applyReferenceDefaults();
            }

            @Override
            public void onError(@NonNull ApiException error) {
            }
        });

        WikiLinkEditor.attach(binding.entityEditDescriptionInput, () -> nameIndex.names());
        WikiLinkEditor.attach(binding.entityEditShortInput, () -> nameIndex.names());

        setActiveNav(navKey(currentKind));
        // У объектов остаётся только аватар: остальные изображения живут в материалах.
        ViewUtils.show(binding.entityEditPhotoGroup, false);
        binding.entityEditSave.setOnClickListener(v -> {
            collect();
            if (!validateForm()) {
                return;
            }
            viewModel.save();
        });
        binding.entityEditDelete.setOnClickListener(v -> confirmDelete());
        ViewUtils.show(binding.entityEditDelete, !creating);

        binding.entityEditAvatarPick.setOnClickListener(v -> avatarPicker.launch("image/*"));
        binding.entityEditAvatarClear.setOnClickListener(v -> {
            viewModel.setAvatarFileId(null);
            showAvatar(null);
        });
        binding.entityEditPhotoPick.setOnClickListener(v -> photoPicker.launch("image/*"));
        binding.entityEditPhotoClear.setOnClickListener(v -> {
            viewModel.setImageFileId(null);
            showPhoto(null);
        });

        binding.entityEditAddItem.setOnClickListener(v -> addItemRow(null));
        binding.entityEditPlayer.setOnItemClickListener((parent, view, position, id) ->
                selectedPlayerUserId = playerIdAt(position));
        observe();
    }

    /**
     * Значения по умолчанию: пустое поле справочника показывает «Неизвестно»,
     * чтобы выбор всегда был определённым. Заполненные поля не трогаем.
     */
    private void applyReferenceDefaults() {
        applyDefault(binding.entityEditRaceInput, container.raceRepository().cachedNames());
        applyDefault(binding.entityEditKlassInput, container.raceRepository().cachedClasses());
        applyDefault(binding.entityEditLocationType, container.raceRepository().cachedLocationTypes());
        applyDefault(binding.entityEditItemType, container.raceRepository().cachedItemTypes());
    }

    /** Подписи поля «Пол»: порядок совпадает с кодами в {@link #genderCode}. */
    @NonNull
    private java.util.List<String> genderLabels() {
        java.util.List<String> out = new java.util.ArrayList<>();
        out.add(getString(R.string.gender_unknown));
        out.add(getString(R.string.gender_female));
        out.add(getString(R.string.gender_male));
        return out;
    }

    @NonNull
    private String genderLabel(@Nullable String code) {
        if ("female".equalsIgnoreCase(code)) {
            return getString(R.string.gender_female);
        }
        if ("male".equalsIgnoreCase(code)) {
            return getString(R.string.gender_male);
        }
        return getString(R.string.gender_unknown);
    }

    @Nullable
    private String genderCode(@Nullable CharSequence label) {
        String value = label == null ? "" : label.toString().trim();
        if (value.equals(getString(R.string.gender_female))) {
            return "female";
        }
        if (value.equals(getString(R.string.gender_male))) {
            return "male";
        }
        return "unknown";
    }

    private void applyDefault(@NonNull android.widget.EditText field,
                              @NonNull java.util.List<String> values) {
        if (values.isEmpty()) {
            return;
        }
        if (field.getText() != null && !field.getText().toString().trim().isEmpty()) {
            return;
        }
        String fallback = values.contains(getString(R.string.ref_unknown))
                ? getString(R.string.ref_unknown) : values.get(0);
        field.setText(fallback);
    }

    /**
     * Проверка обязательных полей до сохранения: пустое название, раса и класс
     * не из справочника. Ошибки показываются под полями, а не тостом.
     */
    private boolean validateForm() {
        boolean ok = true;

        String name = text(binding.entityEditNameInput.getText());
        if (name.isEmpty()) {
            binding.entityEditNameGroup.setError(getString(R.string.error_name_required));
            ok = false;
        } else {
            binding.entityEditNameGroup.setError(null);
        }

        if (currentKind == EntityType.HERO || currentKind == EntityType.CHARACTER) {
            String race = text(binding.entityEditRaceInput.getText());
            if (container.raceRepository().cachedNames().isEmpty()) {
                binding.entityEditRaceGroup.setError(null); // справочник не загрузился
            } else if (!container.raceRepository().cachedNames().contains(race)) {
                binding.entityEditRaceGroup.setError(getString(R.string.error_race_from_list));
                ok = false;
            } else {
                binding.entityEditRaceGroup.setError(null);
            }
        }

        if (currentKind == EntityType.HERO || currentKind == EntityType.CHARACTER) {
            String klass = text(binding.entityEditKlassInput.getText());
            if (container.raceRepository().cachedClasses().isEmpty()) {
                binding.entityEditKlassGroup.setError(null);
            } else if (!container.raceRepository().cachedClasses().contains(klass)) {
                binding.entityEditKlassGroup.setError(getString(R.string.error_class_from_list));
                ok = false;
            } else {
                binding.entityEditKlassGroup.setError(null);
            }
        }

        if (!ok) {
            ViewUtils.toast(this, R.string.error_check_fields);
        }
        return ok;
    }

    private static String text(@Nullable CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }

    /** Ключ раздела для нижней панели. */
    @NonNull
    private static String navKey(@NonNull EntityType type) {
        switch (type) {
            case LOCATION:
                return "locations";
            case ITEM:
                return "items";
            case HERO:
            case CHARACTER:
            default:
                return "characters";
        }
    }

    /** Заголовок называет класс: тип выбирается один раз, при открытии экрана. */
    private int titleFor(@NonNull EntityType kind, boolean create) {
        switch (kind) {
            case HERO:
                return create ? R.string.entity_create_hero : R.string.entity_edit_hero;
            case LOCATION:
                return create ? R.string.entity_create_location : R.string.entity_edit_location;
            case ITEM:
                return create ? R.string.entity_create_item : R.string.entity_edit_item;
            case CHARACTER:
            default:
                return create ? R.string.entity_create_character : R.string.entity_edit_character;
        }
    }

    private void observe() {
        viewModel.state().observe(this, state -> {
            if (state == null) {
                return;
            }
            nameIndex = state.nameIndex;
            candidates = state.candidates;
            members = state.members;
            currentKind = EntityType.fromOrCharacter(state.entity.kind);

            binding.entityEditNameInput.setText(state.entity.name);
            binding.entityEditDescriptionInput.setText(state.entity.description);
            selectedPlayerUserId = state.entity.playerUserId;
            selectedOwnerId = state.entity.ownerHeroId != null
                    ? state.entity.ownerHeroId : state.entity.ownerCharacterId;

            bindFieldsForKind();
            showPhoto(state.entity.imageFileId);
            showAvatar(state.entity.avatarFileId);
            renderFiles();
            checkRights();
        });

        campaignViewModel.campaign().observe(this, campaign -> checkRights());

        viewModel.actionState().observe(this, resource -> {
            if (resource == null) {
                return;
            }
            ViewUtils.show(binding.entityEditProgress, resource.isLoading());
            binding.entityEditSave.setEnabled(!resource.isLoading());
            if (resource.isError()) {
                ErrorMessages.show(binding.getRoot(), resource.error);
                viewModel.clearActionState();
            }
        });

        viewModel.finishRequested().observe(this, finished -> {
            if (Boolean.TRUE.equals(finished)) {
                ViewUtils.toast(this, R.string.saved);
                finish();
            }
        });
    }

    // --- Аватар -----------------------------------------------------------

    /**
     * Выбрали аватар: сжимаем и загружаем отдельно от галереи. В {@code files}
     * картинка не попадает — иначе она стала бы ещё и фото объекта.
     */
    private void onAvatarPicked(@Nullable android.net.Uri uri) {
        if (uri == null || container == null) {
            return;
        }
        ViewUtils.toast(this, R.string.entity_avatar_uploading);
        binding.entityEditAvatarPick.setEnabled(false);
        AppExecutors.get().io(() -> {
            byte[] data = ImageTools.compress(this, uri);
            if (data == null) {
                throw ru.dndmemory.data.remote.ApiException.unexpected(
                        new IllegalArgumentException(getString(R.string.entity_photo_too_big)));
            }
            container.fileRepository().upload(data, "image/jpeg", "avatar.jpg",
                    new RepoCallback<FileDto>() {
                        @Override
                        public void onSuccess(@Nullable FileDto value) {
                            binding.entityEditAvatarPick.setEnabled(true);
                            if (value == null || value.id == null) {
                                ViewUtils.toast(EntityEditActivity.this, R.string.entity_photo_too_big);
                                return;
                            }
                            viewModel.setAvatarFileId(value.id);
                            showAvatar(value.id);
                        }

                        @Override
                        public void onError(@NonNull ru.dndmemory.data.remote.ApiException error) {
                            binding.entityEditAvatarPick.setEnabled(true);
                            ViewUtils.toast(EntityEditActivity.this,
                                    getString(R.string.entity_avatar_failed,
                                            ErrorMessages.text(EntityEditActivity.this, error)));
                        }
                    });
            return null;
        }, new RepoCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void value) {
            }

            @Override
            public void onError(@NonNull ru.dndmemory.data.remote.ApiException error) {
                binding.entityEditAvatarPick.setEnabled(true);
                ViewUtils.toast(EntityEditActivity.this,
                        getString(R.string.entity_avatar_failed,
                                ErrorMessages.text(EntityEditActivity.this, error)));
            }
        });
    }

    /** Превью аватара: без картинки показываем силуэт категории. */
    private void showAvatar(@Nullable String fileId) {
        boolean has = fileId != null && !fileId.isEmpty();
        ViewUtils.show(binding.entityEditAvatarClear, has);
        if (has) {
            binding.entityEditAvatar.clearColorFilter();
            binding.entityEditAvatar.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            ImageLoader.load(container.okHttpClient(), fileId, binding.entityEditAvatar);
        } else {
            binding.entityEditAvatar.setImageResource(Labels.entityIcon(currentKind.name()));
            binding.entityEditAvatar.setColorFilter(androidx.core.content.ContextCompat.getColor(
                    this, R.color.text_muted));
        }
    }

    // --- Фото -------------------------------------------------------------

    /** Выбрали картинку: сжимаем в фоне и сразу загружаем на сервер. */
    private void onPhotoPicked(@Nullable android.net.Uri uri) {
        if (uri == null || container == null) {
            return;
        }
        ViewUtils.toast(this, R.string.entity_photo_uploading);
        binding.entityEditPhotoPick.setEnabled(false);
        AppExecutors.get().io(() -> {
            byte[] data = ImageTools.compress(this, uri);
            if (data == null) {
                throw ru.dndmemory.data.remote.ApiException.unexpected(
                        new IllegalArgumentException(getString(R.string.entity_photo_too_big)));
            }
            container.fileRepository().upload(data, "image/jpeg", "photo.jpg",
                    new RepoCallback<FileDto>() {
                        @Override
                        public void onSuccess(@Nullable FileDto value) {
                            binding.entityEditPhotoPick.setEnabled(true);
                            if (value == null || value.id == null) {
                                ViewUtils.toast(EntityEditActivity.this, R.string.entity_photo_too_big);
                                return;
                            }
                            viewModel.setImageFileId(value.id);
                            viewModel.addFile(new ru.dndmemory.data.model.Attachment(
                                    value.id, "photo-" + viewModel.files().size() + ".jpg",
                                    value.contentType == null ? "image/jpeg" : value.contentType,
                                    value.size));
                            renderFiles();
                        }

                        @Override
                        public void onError(@NonNull ru.dndmemory.data.remote.ApiException error) {
                            binding.entityEditPhotoPick.setEnabled(true);
                            ViewUtils.toast(EntityEditActivity.this,
                                    getString(R.string.entity_photo_failed,
                                            ErrorMessages.text(EntityEditActivity.this, error)));
                        }
                    });
            return null;
        }, new RepoCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void value) {
            }

            @Override
            public void onError(@NonNull ru.dndmemory.data.remote.ApiException error) {
                binding.entityEditPhotoPick.setEnabled(true);
                ViewUtils.toast(EntityEditActivity.this,
                        getString(R.string.entity_photo_failed,
                                ErrorMessages.text(EntityEditActivity.this, error)));
            }
        });
    }

    /** Галерея файлов объекта: миниатюра, имя и кнопка «Убрать». */
    private void renderFiles() {
        binding.entityEditFiles.removeAllViews();
        java.util.List<ru.dndmemory.data.model.Attachment> files = viewModel.files();
        for (final ru.dndmemory.data.model.Attachment file : files) {
            android.widget.LinearLayout row = (android.widget.LinearLayout)
                    getLayoutInflater().inflate(R.layout.item_attachment, binding.entityEditFiles, false);
            android.widget.ImageView thumb = row.findViewById(R.id.attachment_thumb);
            android.widget.TextView name = row.findViewById(R.id.attachment_name);
            com.google.android.material.button.MaterialButton action =
                    row.findViewById(R.id.attachment_action);
            name.setText(file.name == null || file.name.isEmpty() ? file.id : file.name);
            if (file.isImage()) {
                ImageLoader.load(container.okHttpClient(), file.id, thumb);
            }
            action.setText(R.string.file_remove);
            action.setOnClickListener(v -> {
                viewModel.removeFile(file.id);
                renderFiles();
            });
            binding.entityEditFiles.addView(row);
        }
        ViewUtils.show(binding.entityEditFiles, !files.isEmpty());
    }

    /** Превью фото: пустой id прячет картинку и кнопку «Убрать». */
    private void showPhoto(@Nullable String fileId) {
        boolean has = fileId != null && !fileId.isEmpty();
        ViewUtils.show(binding.entityEditPhotoClear, has);
        if (!has) {
            binding.entityEditPhoto.setImageDrawable(null);
            ViewUtils.show(binding.entityEditPhoto, false);
            return;
        }
        ImageLoader.load(container.okHttpClient(), fileId, binding.entityEditPhoto);
    }

    /**
     * Героя ведёт только владелец: если прав нет, редактор закрывается, чтобы
     * объект нельзя было даже открыть на изменение (docs/roles.md).
     */
    private void checkRights() {
        CampaignRoom campaign = campaignViewModel.campaign().getValue();
        if (campaign == null || rightsBlocked) {
            return;
        }
        if (!Rights.canWrite(campaign, currentKind)) {
            rightsBlocked = true;
            android.util.Log.w("DndRights", "редактор закрыт: роль=" + campaign.myRole
                    + " права=" + campaign.myPermissions + " класс=" + currentKind);
            ViewUtils.toast(this, currentKind.requiresHeroPermission()
                    ? R.string.entity_hero_owner_only : R.string.error_forbidden);
            finish();
        }
    }

    /** Показывает поля текущего класса и заполняет их значениями объекта. */
    private void bindFieldsForKind() {
        EntityRoom draft = currentDraft();

        boolean hero = currentKind == EntityType.HERO;
        boolean character = currentKind == EntityType.CHARACTER;
        boolean item = currentKind == EntityType.ITEM;

        ViewUtils.show(binding.entityEditRaceGroup, hero || character);
        ViewUtils.show(binding.entityEditGenderGroup, hero || character);
        ViewUtils.show(binding.entityEditKlassGroup, hero || character);
        ViewUtils.show(binding.entityEditPlayerGroup, hero);
        ViewUtils.show(binding.entityEditShortGroup, character);
        ViewUtils.show(binding.entityEditOwnerLinkGroup, item);
        ViewUtils.show(binding.entityEditItemsGroup, hero);
        ViewUtils.show(binding.entityEditLocationTypeGroup, currentKind == EntityType.LOCATION);
        ViewUtils.show(binding.entityEditItemTypeGroup, currentKind == EntityType.ITEM);
        ViewUtils.show(binding.entityEditFieldsTitle, hero || character || item);

        if (draft != null) {
            binding.entityEditRaceInput.setText(draft.race);
            binding.entityEditGender.setText(genderLabel(draft.gender), false);
            binding.entityEditKlassInput.setText(draft.klass);
            binding.entityEditLocationType.setText(draft.locationType);
            binding.entityEditItemType.setText(draft.itemType);
            binding.entityEditShortInput.setText(draft.shortDescription);
            binding.entityEditNameInput.setText(draft.name);
            binding.entityEditDescriptionInput.setText(draft.description);
        }
        applyReferenceDefaults();

        bindPlayerDropdown();
        bindOwnerDropdown();
        bindItems();
    }

    @Nullable
    private EntityRoom currentDraft() {
        EntityEditViewModel.EditState state = viewModel.state().getValue();
        return state == null ? null : state.entity;
    }

    private void bindPlayerDropdown() {
        List<String> labels = new ArrayList<>();
        labels.add(getString(R.string.entity_player_none));
        for (MemberRoom member : members) {
            if (member.status != null && "REMOVED".equalsIgnoreCase(member.status)) {
                continue;
            }
            labels.add(TextUtils.isEmpty(member.displayName)
                    ? getString(R.string.attr_not_set) : member.displayName);
        }
        ru.dndmemory.ui.common.Dropdowns.bind(binding.entityEditPlayer, labels);
        ru.dndmemory.ui.common.Dropdowns.bind(binding.entityEditGender, genderLabels());
        EntityRoom draft = currentDraft();
        String currentId = selectedPlayerUserId != null ? selectedPlayerUserId
                : (draft == null ? null : draft.playerUserId);
        String shown = labels.get(0);
        for (int i = 1; i < labels.size(); i++) {
            if (playerIdAt(i) != null && playerIdAt(i).equals(currentId)) {
                shown = labels.get(i);
                break;
            }
        }
        binding.entityEditPlayer.setText(shown, false);
        selectedPlayerUserId = playerIdAt(indexOf(labels, shown));
    }

    @Nullable
    private String playerIdAt(int position) {
        if (position <= 0) {
            return null;
        }
        List<MemberRoom> active = activeMembers();
        int index = position - 1;
        return index < active.size() ? active.get(index).userId : null;
    }

    @NonNull
    private List<MemberRoom> activeMembers() {
        List<MemberRoom> out = new ArrayList<>();
        for (MemberRoom member : members) {
            if (member.status == null || !"REMOVED".equalsIgnoreCase(member.status)) {
                out.add(member);
            }
        }
        return out;
    }

    /**
     * Владелец предмета: одно поле с автокомплитом по героям и персонажам.
     * При выборе имя сразу превращается в ссылку на объект.
     */
    private void bindOwnerDropdown() {
        EntityRoom draft = currentDraft();
        String currentId = selectedOwnerId != null ? selectedOwnerId
                : (draft == null ? null : (draft.ownerHeroId != null
                        ? draft.ownerHeroId : draft.ownerCharacterId));
        EntityRoom owner = ownerById(currentId);
        selectedOwnerId = owner == null ? null : owner.id;
        binding.entityEditOwnerLink.setText(owner == null ? "" : displayName(owner), false);
    }

    /** Подсказки владельца: имена героев и персонажей кампании. */
    @NonNull
    private List<String> ownerLabels() {
        List<String> labels = new ArrayList<>();
        for (EntityRoom hero : ownerCandidates(EntityType.HERO)) {
            labels.add(displayName(hero));
        }
        for (EntityRoom character : ownerCandidates(EntityType.CHARACTER)) {
            labels.add(displayName(character));
        }
        return labels;
    }

    /** Имя из поля → объект-владелец; совпадение без учёта регистра. */
    @Nullable
    private EntityRoom ownerByName(@Nullable String name) {
        String needle = name == null ? "" : name.trim();
        if (needle.isEmpty()) {
            return null;
        }
        for (EntityRoom hero : ownerCandidates(EntityType.HERO)) {
            if (needle.equalsIgnoreCase(displayName(hero))) {
                return hero;
            }
        }
        for (EntityRoom character : ownerCandidates(EntityType.CHARACTER)) {
            if (needle.equalsIgnoreCase(displayName(character))) {
                return character;
            }
        }
        return null;
    }

    @NonNull
    private List<EntityRoom> ownerCandidates(EntityType kind) {
        List<EntityRoom> out = new ArrayList<>();
        for (EntityRoom candidate : candidates) {
            if (kind.name().equalsIgnoreCase(candidate.kind)
                    && !TextUtils.isEmpty(candidate.name)) {
                out.add(candidate);
            }
        }
        return out;
    }

    @Nullable
    private String ownerIdAt(int position) {
        if (position <= 0) {
            return null;
        }
        List<EntityRoom> all = new ArrayList<>();
        all.addAll(ownerCandidates(EntityType.HERO));
        all.addAll(ownerCandidates(EntityType.CHARACTER));
        int index = position - 1;
        return index < all.size() ? all.get(index).id : null;
    }

    @Nullable
    private EntityRoom ownerById(@Nullable String id) {
        if (id == null) {
            return null;
        }
        for (EntityRoom candidate : candidates) {
            if (id.equals(candidate.id)) {
                return candidate;
            }
        }
        return null;
    }

    private static int indexOf(@NonNull List<String> labels, @NonNull String value) {
        for (int i = 0; i < labels.size(); i++) {
            if (labels.get(i).equals(value)) {
                return i;
            }
        }
        return 0;
    }

    private static String displayName(@NonNull EntityRoom room) {
        return TextUtils.isEmpty(room.name) ? "" : room.name;
    }

    // --- Котомка героя --------------------------------------------------------

    private void bindItems() {
        binding.entityEditItems.removeAllViews();
        itemRows.clear();
        EntityRoom draft = currentDraft();
        if (draft == null || draft.items == null) {
            return;
        }
        for (HeroItem item : draft.items) {
            addItemRow(item);
        }
    }

    private void addItemRow(@Nullable HeroItem item) {
        ItemHeroItemEditBinding row = ItemHeroItemEditBinding.inflate(
                getLayoutInflater(), binding.entityEditItems, false);
        if (item != null) {
            row.heroItemName.setText(item.name);
            if (item.quantity != null) {
                row.heroItemQuantity.setText(String.valueOf(item.quantity));
            }
            row.heroItemNotes.setText(item.notes);
        }
        row.heroItemRemove.setOnClickListener(v -> {
            binding.entityEditItems.removeView(row.getRoot());
            itemRows.remove(row);
        });
        binding.entityEditItems.addView(row.getRoot());
        itemRows.add(row);
    }

    @NonNull
    private List<HeroItem> collectItems() {
        List<HeroItem> out = new ArrayList<>();
        for (ItemHeroItemEditBinding row : itemRows) {
            HeroItem item = new HeroItem();
            item.name = text(row.heroItemName.getText());
            if (item.name.isEmpty()) {
                continue;
            }
            item.quantity = parseInt(row.heroItemQuantity.getText());
            String notes = text(row.heroItemNotes.getText());
            item.notes = notes.isEmpty() ? null : notes;
            out.add(item);
        }
        return out;
    }

    @Nullable
    private static Integer parseInt(@Nullable CharSequence value) {
        String raw = value == null ? "" : value.toString().trim();
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // --- Сохранение -----------------------------------------------------------

    /** Переносит значения формы в ViewModel перед сохранением. */
    private void collect() {
        viewModel.setKind(currentKind);
        viewModel.setName(text(binding.entityEditNameInput.getText()));
        viewModel.setDescription(text(binding.entityEditDescriptionInput.getText()));

        if (currentKind == EntityType.HERO) {
            viewModel.setRace(text(binding.entityEditRaceInput.getText()));
            viewModel.setGender(genderCode(text(binding.entityEditGender.getText())));
            viewModel.setKlass(text(binding.entityEditKlassInput.getText()));
            MemberRoom player = memberById(selectedPlayerUserId);
            viewModel.setPlayerUserId(selectedPlayerUserId,
                    player == null ? null : player.displayName);
            viewModel.setItems(collectItems());
        } else if (currentKind == EntityType.CHARACTER) {
            viewModel.setRace(text(binding.entityEditRaceInput.getText()));
            viewModel.setGender(genderCode(text(binding.entityEditGender.getText())));
            viewModel.setKlass(text(binding.entityEditKlassInput.getText()));
            viewModel.setShortDescription(text(binding.entityEditShortInput.getText()));
        } else if (currentKind == EntityType.LOCATION) {
            viewModel.setLocationType(text(binding.entityEditLocationType.getText()));
        } else if (currentKind == EntityType.ITEM) {
            viewModel.setItemType(text(binding.entityEditItemType.getText()));
            String ownerText = text(binding.entityEditOwnerLink.getText());
            EntityRoom owner = ownerByName(ownerText);
            viewModel.setOwnerLink(owner);
            viewModel.setOwnerName(owner != null ? owner.name
                    : (ownerText.isEmpty() ? null : ownerText));
        }
    }

    @Nullable
    private MemberRoom memberById(@Nullable String userId) {
        if (userId == null) {
            return null;
        }
        for (MemberRoom member : members) {
            if (userId.equals(member.userId)) {
                return member;
            }
        }
        return null;
    }

    private void confirmDelete() {
        String name = text(binding.entityEditNameInput.getText());
        String title = name.isEmpty() ? getString(R.string.entity_detail_title) : name;
        new MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.entity_edit_delete_confirm, title))
                .setPositiveButton(R.string.action_delete, (dialog, which) -> viewModel.delete())
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

}
