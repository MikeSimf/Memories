package ru.dndmemory.ui.entity;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.StyleSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

import ru.dndmemory.App;
import ru.dndmemory.AppContainer;
import ru.dndmemory.R;
import ru.dndmemory.data.local.entity.CampaignRoom;
import ru.dndmemory.data.local.entity.EntityRoom;
import ru.dndmemory.data.model.EntityLink;
import ru.dndmemory.data.model.EntityType;
import ru.dndmemory.data.model.HeroItem;
import ru.dndmemory.databinding.ActivityEntityDetailBinding;
import ru.dndmemory.ui.campaign.CampaignViewModel;
import ru.dndmemory.ui.common.AvatarBinder;
import ru.dndmemory.ui.common.ErrorMessages;
import ru.dndmemory.ui.common.Extras;
import ru.dndmemory.ui.common.Labels;
import ru.dndmemory.ui.common.NameIndex;
import ru.dndmemory.ui.common.Nav;
import ru.dndmemory.ui.common.ImageLoader;
import ru.dndmemory.ui.common.TypeColors;
import ru.dndmemory.ui.common.Rights;
import ru.dndmemory.ui.common.ViewModelFactory;
import ru.dndmemory.ui.common.ViewUtils;
import ru.dndmemory.util.DateFormats;
import ru.dndmemory.util.WikiMarkdown;

/**
 * Карточка объекта: описание со ссылками {@code [[Имя]]}, поля своего класса
 * и список упоминаний. Набор полей — настоящие поля вместо {@code attrs}:
 * у героя раса, класс, игрок и котомка; у персонажа раса и краткое описание;
 * у предмета владелец со ссылкой (docs/api.md, раздел 6).
 */
public class EntityDetailActivity extends AppCompatActivity {

    private ActivityEntityDetailBinding binding;
    private EntityDetailViewModel viewModel;
    private CampaignViewModel campaignViewModel;
    /** Снимок кампании: нужен для проверки прав, когда LiveData ещё не наблюдалась. */
    private CampaignRoom campaignSnapshot;
    private String campaignId;
    private NameIndex nameIndex = NameIndex.of(null);
    private java.util.List<EntityRoom> allObjects = java.util.Collections.emptyList();
    private EntityRoom current;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEntityDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        campaignId = getIntent() == null ? null : getIntent().getStringExtra(Extras.CAMPAIGN_ID);
        String entityId = getIntent() == null ? null : getIntent().getStringExtra(Extras.ENTITY_ID);
        if (campaignId == null || entityId == null) {
            finish();
            return;
        }

        AppContainer container = App.container(this);
        viewModel = new ViewModelProvider(this, ViewModelFactory.of(
                () -> new EntityDetailViewModel(container.entityRepository())))
                .get(EntityDetailViewModel.class);
        viewModel.start(campaignId, entityId);

        campaignViewModel = new ViewModelProvider(this, ViewModelFactory.of(
                () -> new CampaignViewModel(container.campaignRepository(),
                        container.authRepository(), container.syncEngine(),
                        container.syncScheduler())))
                .get(CampaignViewModel.class);
        campaignViewModel.setCampaignId(campaignId);
        campaignViewModel.refreshDetails();
        // Без подписки MediatorLiveData.getValue() возвращает null, и проверка
        // прав в меню отказывала владельцу («нет прав» из карточки).
        campaignViewModel.campaign().observe(this, campaign -> campaignSnapshot = campaign);

        setSupportActionBar(binding.entityToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        binding.entityConflictAccept.setOnClickListener(v -> viewModel.acceptServerVersion());
        binding.entityConflictOverwrite.setOnClickListener(v -> viewModel.overwriteWithLocal());

        observe();
    }

    private void observe() {
        viewModel.entity().observe(this, entity -> {
            if (entity == null) {
                return;
            }
            if (entity.deleted) {
                finish();
                return;
            }
            current = entity;
            bind(entity);
        });

        viewModel.allEntities().observe(this, list -> {
            allObjects = list == null ? java.util.Collections.emptyList() : list;
            renderMentions();
        });

        viewModel.nameIndex().observe(this, index -> {
            if (index == null) {
                return;
            }
            nameIndex = index;
            if (current != null) {
                bind(current);
            }
        });

        viewModel.actionState().observe(this, resource -> {
            if (resource == null) {
                return;
            }
            if (resource.isError()) {
                ErrorMessages.show(binding.getRoot(), resource.error);
            }
            viewModel.clearActionState();
        });

        viewModel.finishRequested().observe(this, finished -> {
            if (Boolean.TRUE.equals(finished)) {
                finish();
            }
        });
    }

    private void bind(@NonNull EntityRoom entity) {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(TextUtils.isEmpty(entity.name)
                    ? getString(R.string.entity_detail_title) : entity.name);
        }
        binding.entityTitle.setText(TextUtils.isEmpty(entity.name)
                ? getString(R.string.attr_not_set) : entity.name);
        EntityType type = EntityType.fromOrCharacter(entity.kind);
        binding.entityTypeStripe.setBackgroundColor(TypeColors.of(this, type));
        ru.dndmemory.ui.common.AvatarBinder.bind(this, binding.entityAvatar, entity,
                App.container(this).okHttpClient());
        ru.dndmemory.ui.common.AvatarBinder.bindClassIcon(this, binding.entityClassIcon, entity);
        binding.entityTypeChip.setText(Labels.entityTypeText(this, entity.kind));
        binding.entityTypeChip.setTextColor(TypeColors.of(this, type));
        binding.entityTypeChip.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(TypeColors.soft(this, type)));
        ViewUtils.setTextOrGone(binding.entitySubtitle, Labels.entitySubtitle(this, entity));
        binding.entityUpdated.setText(getString(R.string.entity_detail_updated,
                DateFormats.formatIso(entity.updatedAt)));

        // Фото грузится с сервера авторизованным клиентом и кэшируется в памяти.
        final String photoId = entity.imageFileId;
        ImageLoader.load(App.container(this).okHttpClient(), photoId, binding.entityPhoto);
        binding.entityPhoto.setOnClickListener(v -> openPhoto(photoId));

        renderWiki(binding.entityDescription, entity.description);
        ViewUtils.show(binding.entityDescriptionTitle,
                !TextUtils.isEmpty(entity.description));
        bindFields(entity);
        bindLinks(entity);
        renderMentions();

        ViewUtils.show(binding.entityConflictBar, entity.hasConflict());
    }

    /** Все файлы объекта: фото открываются, документы скачиваются. */
    private void renderFiles(@NonNull EntityRoom entity) {
        binding.entityFiles.removeAllViews();
        java.util.List<ru.dndmemory.data.model.Attachment> files =
                ru.dndmemory.data.Mappers.files(entity.filesJson);
        for (final ru.dndmemory.data.model.Attachment file : files) {
            android.widget.LinearLayout row = (android.widget.LinearLayout) getLayoutInflater()
                    .inflate(R.layout.item_attachment, binding.entityFiles, false);
            android.widget.ImageView thumb = row.findViewById(R.id.attachment_thumb);
            android.widget.TextView name = row.findViewById(R.id.attachment_name);
            com.google.android.material.button.MaterialButton action =
                    row.findViewById(R.id.attachment_action);
            name.setText(file.name == null || file.name.isEmpty() ? file.id : file.name);
            if (file.isImage()) {
                ImageLoader.load(App.container(this).okHttpClient(), file.id, thumb);
                action.setText(R.string.file_open);
                action.setOnClickListener(v -> openPhoto(file.id));
            } else {
                boolean downloaded = ru.dndmemory.ui.common.FileDownloads.isDownloaded(this, file.id);
                String saved = downloaded
                        ? ru.dndmemory.ui.common.FileDownloads.savedName(this, file.id) : null;
                name.setText(saved != null ? saved : (file.name == null ? file.id : file.name));
                action.setText(downloaded ? R.string.file_open : R.string.update_download);
                action.setOnClickListener(v -> {
                    if (ru.dndmemory.ui.common.FileDownloads.isDownloaded(this, file.id)) {
                        ru.dndmemory.ui.common.FileDownloads.open(this, file.id);
                    } else {
                        ru.dndmemory.ui.common.FileDownloads.download(this, file.id, entity.name,
                                file.contentType, () -> renderFiles(entity));
                    }
                });
            }
            binding.entityFiles.addView(row);
        }
        ViewUtils.show(binding.entityFiles, !files.isEmpty());
    }

    /** Полноэкранный просмотр фото: по нажатию на картинку в карточке. */
    private void openPhoto(@Nullable String fileId) {
        if (fileId == null || fileId.isEmpty()) {
            return;
        }
        ImageLoader.loadFull(App.container(this).okHttpClient(), fileId, bitmap -> {
            ImageView view = new ImageView(this);
            view.setAdjustViewBounds(true);
            view.setScaleType(ImageView.ScaleType.FIT_CENTER);
            view.setImageBitmap(bitmap);
            new androidx.appcompat.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_NoActionBar)
                    .setView(view)
                    .setPositiveButton(R.string.action_close, null)
                    .show();
        });
    }

    /** Текст с markdown и живыми ссылками [[Имя]]. */
    private void renderWiki(@NonNull TextView target, @Nullable String source) {
        if (source == null || source.trim().isEmpty()) {
            ViewUtils.show(target, false);
            return;
        }
        ViewUtils.show(target, true);
        target.setText(WikiMarkdown.render(this, source,
                name -> nameIndex.idFor(name),
                this::onWikiLinkClick));
        target.setMovementMethod(LinkMovementMethod.getInstance());
        target.setHighlightColor(Color.TRANSPARENT);
    }

    private void onWikiLinkClick(@Nullable String targetId, @NonNull String name, boolean resolved) {
        if (resolved && targetId != null) {
            Nav.openEntity(this, campaignId, targetId);
            return;
        }
        // «Битая» ссылка: предлагаем создать объект с таким именем (docs/sync.md, раздел 8).
        new MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.wiki_create_suggestion, name))
                .setPositiveButton(R.string.action_create, (dialog, which) ->
                        Nav.createEntity(this, campaignId, EntityType.CHARACTER.name()))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    /** Поля своего класса — вместо прежних attrs (docs/redesign-v2.md, раздел 5). */
    private void bindFields(@NonNull EntityRoom entity) {
        binding.entityFields.removeAllViews();
        EntityType kind = EntityType.from(entity.kind);
        if (kind != null) {
            switch (kind) {
                case HERO:
                    addFieldRow(getString(R.string.attr_gender), Labels.gender(this, entity.gender));
                    addFieldRow(getString(R.string.attr_race), entity.race);
                    addFieldRow(getString(R.string.attr_klass), entity.klass);
                    addFieldRow(getString(R.string.attr_player_name), entity.playerName);
                    break;
                case CHARACTER:
                    addFieldRow(getString(R.string.attr_gender), Labels.gender(this, entity.gender));
                    addFieldRow(getString(R.string.attr_race), entity.race);
                    addFieldRow(getString(R.string.attr_short_description), entity.shortDescription);
                    break;
                case ITEM:
                    addFieldRow(getString(R.string.entity_item_type), entity.itemType);
                    addFieldRow(getString(R.string.attr_owner), ownerText(entity));
                    break;
                case LOCATION:
                    addFieldRow(getString(R.string.entity_location_type), entity.locationType);
                    break;
                default:
                    break;
            }
        }
        bindHeroItems(entity);
        ViewUtils.show(binding.entityFieldsTitle,
                binding.entityFields.getChildCount() > 0);
    }

    /** Владелец предмета: ссылка важнее строки «как написано в мире». */
    @Nullable
    private String ownerText(@NonNull EntityRoom entity) {
        String linkedName = entity.ownerHeroId != null
                ? nameIndex.nameFor(entity.ownerHeroId) : null;
        if (linkedName == null && entity.ownerCharacterId != null) {
            linkedName = nameIndex.nameFor(entity.ownerCharacterId);
        }
        if (linkedName != null) {
            return linkedName;
        }
        if (entity.ownerHeroName != null) {
            return entity.ownerHeroName;
        }
        if (entity.ownerCharacterName != null) {
            return entity.ownerCharacterName;
        }
        return entity.ownerName;
    }

    private void bindHeroItems(@NonNull EntityRoom entity) {
        binding.entityItems.removeAllViews();
        List<HeroItem> items = entity.items;
        if (items == null || items.isEmpty()) {
            ViewUtils.show(binding.entityItemsTitle, false);
            return;
        }
        ViewUtils.show(binding.entityItemsTitle, true);
        for (final HeroItem item : items) {
            CharSequence text = Labels.heroItemText(this, item);
            if (text == null) {
                continue;
            }
            addFieldRow(getString(R.string.attr_items), text, item.itemId);
        }
    }

    private void addFieldRow(@NonNull CharSequence label, @Nullable CharSequence value) {
        addFieldRow(label, value, null);
    }

    /** Строка «Подпись: значение»; при {@code linkId} строка открывает объект. */
    private void addFieldRow(@NonNull CharSequence label, @Nullable CharSequence value,
                             @Nullable final String linkId) {
        if (value == null || value.length() == 0) {
            return;
        }
        SpannableStringBuilder line = new SpannableStringBuilder();
        line.append(label);
        line.setSpan(new StyleSpan(Typeface.BOLD), 0, line.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        line.append(": ");
        line.append(value);

        TextView row = new TextView(this);
        row.setText(line);
        row.setPadding(0, 4, 0, 4);
        if (linkId != null) {
            row.setTextColor(getResources().getColor(R.color.wiki_link, getTheme()));
            row.setOnClickListener(v -> Nav.openEntity(this, campaignId, linkId));
        }
        binding.entityFields.addView(row);
    }

    /**
     * Обратные ссылки: кто упоминает эту карточку. У локации — только герои
     * и персонажи, чтобы можно было найти «кто здесь был» по имени и аватару.
     */
    private void renderMentions() {
        if (current == null || binding == null) {
            return;
        }
        boolean location = EntityType.fromOrCharacter(current.kind) == EntityType.LOCATION;
        String targetId = current.id;
        java.util.List<EntityRoom> mentions = new java.util.ArrayList<>();
        for (EntityRoom room : allObjects) {
            if (room == null || room.deleted || targetId.equals(room.id)) {
                continue;
            }
            if (location && EntityType.fromOrCharacter(room.kind) != EntityType.HERO
                    && EntityType.fromOrCharacter(room.kind) != EntityType.CHARACTER) {
                continue;
            }
            boolean mentionsTarget = false;
            if (room.links != null) {
                for (EntityLink link : room.links) {
                    if (link != null && targetId.equals(link.targetId)) {
                        mentionsTarget = true;
                        break;
                    }
                }
            }
            if (mentionsTarget) {
                mentions.add(room);
            }
        }
        mentions.sort((left, right) -> {
            String a = left.name == null ? "" : left.name;
            String b = right.name == null ? "" : right.name;
            return a.compareToIgnoreCase(b);
        });

        binding.entityMentions.removeAllViews();
        boolean any = !mentions.isEmpty();
        ViewUtils.show(binding.entityMentionsTitle, any);
        ViewUtils.show(binding.entityMentionsHint, any && location);
        if (!any) {
            return;
        }
        binding.entityMentionsTitle.setText(location
                ? R.string.entity_mentions_location : R.string.entity_mentions);
        for (final EntityRoom room : mentions) {
            android.view.View row = getLayoutInflater().inflate(
                    R.layout.item_backlink, binding.entityMentions, false);
            android.widget.ImageView avatar = row.findViewById(R.id.backlink_avatar);
            android.widget.ImageView classIcon = row.findViewById(R.id.backlink_class_icon);
            android.widget.TextView name = row.findViewById(R.id.backlink_name);
            android.widget.TextView subtitle = row.findViewById(R.id.backlink_subtitle);
            AvatarBinder.bind(this, avatar, room, App.container(this).okHttpClient());
            AvatarBinder.bindClassIcon(this, classIcon, room);
            name.setText(room.name);
            subtitle.setText(Labels.entityTypeText(this, room.kind));
            row.setOnClickListener(v -> Nav.openEntity(this, campaignId, room.id));
            binding.entityMentions.addView(row);
        }
    }

    private void bindLinks(@NonNull EntityRoom entity) {
        binding.entityLinks.removeAllViews();
        if (entity.links == null || entity.links.isEmpty()) {
            ViewUtils.show(binding.entityLinksTitle, false);
            return;
        }
        ViewUtils.show(binding.entityLinksTitle, true);
        for (EntityLink link : entity.links) {
            TextView row = new TextView(this);
            String markup = link.targetId != null
                    ? "[[" + "#" + link.targetId + "|" + (link.label == null ? "" : link.label) + "]]"
                    : "[[" + (link.name == null ? link.label : link.name) + "]]";
            row.setText(WikiMarkdown.render(this, markup,
                    name -> nameIndex.idFor(name), this::onWikiLinkClick));
            row.setMovementMethod(LinkMovementMethod.getInstance());
            row.setHighlightColor(Color.TRANSPARENT);
            row.setPadding(0, 4, 0, 4);
            binding.entityLinks.addView(row);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_entity_detail, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_edit) {
            CampaignRoom campaign = campaignSnapshot;
            String userId = campaignViewModel.currentUserId();
            if (current == null || !Rights.canWriteEntity(campaign, current, userId)) {
                android.util.Log.w("DndRights", "правка запрещена: роль="
                        + (campaign == null ? null : campaign.myRole) + " права="
                        + (campaign == null ? null : campaign.myPermissions) + " класс="
                        + (current == null ? null : current.kind));
                ViewUtils.toast(this, current != null
                        && EntityType.HERO.name().equalsIgnoreCase(current.kind)
                        ? R.string.entity_hero_owner_only : R.string.error_forbidden);
                return true;
            }
            Nav.editEntity(this, campaignId, current.id);
            return true;
        }
        if (id == R.id.action_delete) {
            confirmDelete();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void confirmDelete() {
        if (current == null) {
            return;
        }
        CampaignRoom campaign = campaignSnapshot;
        String userId = campaignViewModel.currentUserId();
        if (!Rights.canDeleteEntity(campaign, current, userId)) {
            ViewUtils.toast(this, EntityType.HERO.name().equalsIgnoreCase(current.kind)
                    ? R.string.entity_hero_owner_only : R.string.error_forbidden);
            return;
        }
        final String name = TextUtils.isEmpty(current.name)
                ? getString(R.string.entity_detail_title) : current.name;
        new MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.entity_edit_delete_confirm, name))
                .setPositiveButton(R.string.action_delete, (dialog, which) -> viewModel.delete())
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
