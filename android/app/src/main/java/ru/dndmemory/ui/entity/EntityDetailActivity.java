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
public class EntityDetailActivity extends ru.dndmemory.ui.common.BottomNavActivity {

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
            renderAllLinks(current);
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
        EntityType type = EntityType.fromOrCharacter(entity.kind);
        setActiveNav(navKey(type));
        binding.entityTypeStripe.setBackgroundColor(TypeColors.of(this, type));
        binding.entityTypeChip.setText(Labels.entityTypeText(this, entity.kind));
        binding.entityTypeChip.setTextColor(TypeColors.of(this, type));
        binding.entityTypeChip.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(TypeColors.soft(this, type)));
        ViewUtils.show(binding.entitySubtitle, false);
        renderSystemInfo(entity);

        // Фото грузится с сервера авторизованным клиентом и кэшируется в памяти.
        final String photoId = entity.imageFileId;
        ImageLoader.load(App.container(this).okHttpClient(), photoId, binding.entityPhoto);
        binding.entityPhoto.setOnClickListener(v -> openPhoto(photoId));

        renderWiki(binding.entityDescription, entity.description);
        ViewUtils.show(binding.entityDescriptionTitle, true);
        if (TextUtils.isEmpty(entity.description)) {
            binding.entityDescription.setText(R.string.attr_not_set);
        }
        bindFields(entity);
        renderAllLinks(entity);

        ViewUtils.show(binding.entityConflictBar, entity.hasConflict());
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

    /** Авторы и время создания/правки плюс когда кампания синхронизировалась. */
    private void renderSystemInfo(@NonNull EntityRoom entity) {
        ru.dndmemory.util.AppExecutors.get().io(() -> {
            java.util.List<ru.dndmemory.data.local.entity.MemberRoom> members =
                    App.container(this).database().memberDao().findAllNow(campaignId);
            String createdBy = null;
            String updatedBy = null;
            for (ru.dndmemory.data.local.entity.MemberRoom member : members) {
                if (member == null || member.userId == null) {
                    continue;
                }
                if (member.userId.equals(entity.createdBy)) {
                    createdBy = member.displayName;
                }
                if (member.userId.equals(entity.updatedBy)) {
                    updatedBy = member.displayName;
                }
            }
            ru.dndmemory.data.local.entity.SyncStateRoom state =
                    App.container(this).database().syncStateDao().find(campaignId);
            String syncedAt = state == null ? null : state.lastSyncAt;
            String createdName = entity.createdByName != null && !entity.createdByName.isEmpty()
                    ? entity.createdByName : (createdBy == null ? "—" : createdBy);
            String updatedName = entity.updatedByName != null && !entity.updatedByName.isEmpty()
                    ? entity.updatedByName : (updatedBy == null ? "—" : updatedBy);
            runOnUiThread(() -> binding.entityUpdated.setText(getString(
                    R.string.entity_detail_system_info,
                    createdName, DateFormats.formatIso(entity.createdAt),
                    updatedName, DateFormats.formatIso(entity.updatedAt),
                    syncedAt == null ? "—" : DateFormats.formatIso(syncedAt))));
            return null;
        }, new ru.dndmemory.util.RepoCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void value) {
            }

            @Override
            public void onError(@NonNull ru.dndmemory.data.remote.ApiException error) {
            }
        });
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
                case CHARACTER:
                    addPersonRow(getString(R.string.attr_race), entity.race, "race", null);
                    addPersonRow(getString(R.string.attr_klass), entity.klass, "class", null);
                    addPersonRow(getString(R.string.attr_gender), Labels.gender(this, entity.gender),
                            null, Labels.genderSymbol(this, entity.gender));
                    if (kind == EntityType.CHARACTER) {
                        addPersonRow(getString(R.string.attr_short_description),
                                entity.shortDescription, null, null);
                    } else {
                        addFieldRow(getString(R.string.attr_player_name), entity.playerName);
                    }
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
        // Заголовок «Поля» больше не показываем: поля говорят сами за себя.
        ViewUtils.show(binding.entityFieldsTitle, false);
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

    /** Строка карточки героя/персонажа: значение и значок в конце строки. */
    private void addPersonRow(@NonNull CharSequence label, @Nullable CharSequence value,
                              @Nullable String icon, @Nullable String symbol) {
        if (value == null || value.length() == 0) {
            value = getString(R.string.attr_not_set);
        }
        android.view.View row = getLayoutInflater().inflate(
                R.layout.item_field_row, binding.entityFields, false);
        android.widget.TextView labelView = row.findViewById(R.id.field_row_label);
        android.widget.TextView valueView = row.findViewById(R.id.field_row_value);
        android.widget.ImageView iconView = row.findViewById(R.id.field_row_icon);
        android.widget.TextView symbolView = row.findViewById(R.id.field_row_symbol);
        labelView.setText(label);
        valueView.setText(value);
        if (symbol != null && !symbol.isEmpty()) {
            symbolView.setText(symbol);
            ViewUtils.show(symbolView, true);
        }
        if (icon != null) {
            int color = androidx.core.content.ContextCompat.getColor(this, R.color.text_muted);
            if ("race".equals(icon)) {
                ru.dndmemory.ui.common.TypeIcons.applyRace(iconView, value.toString(), color);
                ViewUtils.show(iconView, true);
            } else if ("class".equals(icon)) {
                if (ru.dndmemory.ui.common.ClassIcons.isKnown(value.toString())) {
                    iconView.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                    iconView.setColorFilter(color);
                    ru.dndmemory.ui.common.ClassIcons.apply(iconView, value.toString(), color);
                    ViewUtils.show(iconView, true);
                }
            }
        }
        binding.entityFields.addView(row);
    }

    /** Строка «Подпись: значение»; при {@code linkId} строка открывает объект. */
    private void addFieldRow(@NonNull CharSequence label, @Nullable CharSequence value,
                             @Nullable final String linkId) {
        if (value == null || value.length() == 0) {
            value = getString(R.string.attr_not_set);
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

    @Nullable
    private EntityRoom findObject(@Nullable String id) {
        if (id == null) {
            return null;
        }
        for (EntityRoom room : allObjects) {
            if (room != null && id.equals(room.id)) {
                return room;
            }
        }
        return null;
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
            int byType = Integer.compare(mentionOrder(left.kind), mentionOrder(right.kind));
            if (byType != 0) {
                return byType;
            }
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

        // Повести и материалы — такие же источники упоминаний.
        final String mentionedId = targetId;
        final boolean onlyPeople = location;
        ru.dndmemory.util.AppExecutors.get().io(() -> {
            java.util.List<ru.dndmemory.data.local.entity.RetellingRoom> retellings =
                    App.container(this).database().retellingDao().findAllNow(campaignId);
            java.util.List<ru.dndmemory.data.local.entity.MaterialRoom> materials =
                    App.container(this).database().materialDao().findAllNow(campaignId);
            runOnUiThread(() -> {
                if (binding == null || current == null || !mentionedId.equals(current.id)) {
                    return;
                }
                for (ru.dndmemory.data.local.entity.RetellingRoom item : retellings) {
                    if (item == null || item.deleted || !hasLink(item.links, mentionedId)) {
                        continue;
                    }
                    addTextMention(item.title, R.string.search_kind_retelling,
                            v -> Nav.openRetelling(this, campaignId, item.id));
                }
                if (!onlyPeople) {
                    for (ru.dndmemory.data.local.entity.MaterialRoom item : materials) {
                        if (item == null || item.deleted
                                || !hasLink(ru.dndmemory.data.Mappers.links(item.linksJson), mentionedId)) {
                            continue;
                        }
                        addTextMention(item.name, R.string.tab_materials,
                                v -> Nav.openMaterial(this, campaignId, item.id));
                    }
                }
                boolean hasAny = binding.entityMentions.getChildCount() > 0;
                ViewUtils.show(binding.entityMentionsTitle, hasAny);
                ViewUtils.show(binding.entityMentionsHint, hasAny && onlyPeople);
            });
            return null;
        }, new ru.dndmemory.util.RepoCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void value) {
            }

            @Override
            public void onError(@NonNull ru.dndmemory.data.remote.ApiException error) {
            }
        });
    }

    /** Порядок упоминаний: персонажи, локации, предметы, герои, истории, материалы. */
    private static int mentionOrder(@Nullable String kind) {
        if (kind == null) {
            return 9;
        }
        switch (kind.toUpperCase(java.util.Locale.ROOT)) {
            case "CHARACTER":
                return 0;
            case "LOCATION":
                return 1;
            case "ITEM":
                return 2;
            case "HERO":
                return 3;
            case "RETELLING":
                return 4;
            case "MATERIAL":
                return 5;
            default:
                return 9;
        }
    }

    private static boolean hasLink(@Nullable java.util.List<EntityLink> links, @NonNull String id) {
        if (links == null) {
            return false;
        }
        for (EntityLink link : links) {
            if (link != null && id.equals(link.targetId)) {
                return true;
            }
        }
        return false;
    }

    /** Строка упоминания без аватара: повествование или материал. */
    private void addTextMention(@Nullable String name, int typeRes,
                                @NonNull android.view.View.OnClickListener onClick) {
        android.view.View row = getLayoutInflater().inflate(
                R.layout.item_backlink, binding.entityMentions, false);
        android.widget.ImageView avatar = row.findViewById(R.id.backlink_avatar);
        android.widget.TextView title = row.findViewById(R.id.backlink_name);
        android.widget.TextView subtitle = row.findViewById(R.id.backlink_subtitle);
        avatar.setImageResource(R.drawable.ic_retelling);
        avatar.setColorFilter(androidx.core.content.ContextCompat.getColor(this, R.color.text_muted));
        title.setText(name);
        subtitle.setText(typeRes);
        row.setOnClickListener(onClick);
        binding.entityMentions.addView(row);
    }

    /** Все ссылки карточки: исходящие, обратные, владелец и его предметы. */
    private void renderAllLinks(@NonNull EntityRoom entity) {
        java.util.LinkedHashMap<String, EntityRoom> all = new java.util.LinkedHashMap<>();
        for (EntityLink link : entity.links) {
            if (link.targetId == null) continue;
            EntityRoom target = findObject(link.targetId);
            if (target != null && !target.id.equals(entity.id)) all.put(target.id, target);
        }
        for (EntityRoom item : allObjects) {
            if (item == null || item.id.equals(entity.id)) continue;
            for (EntityLink link : item.links) {
                if (entity.id.equals(link.targetId)) {
                    all.put(item.id, item);
                    break;
                }
            }
        }
        // Владелец предмета — такая же ссылка; у владельца виден его предмет.
        if ("ITEM".equalsIgnoreCase(entity.kind)) {
            EntityRoom owner = findObject(entity.ownerCharacterId);
            if (owner == null) owner = findObject(entity.ownerHeroId);
            if (owner != null && !owner.id.equals(entity.id)) all.put(owner.id, owner);
        }
        for (EntityRoom item : allObjects) {
            if (item == null || !"ITEM".equalsIgnoreCase(item.kind)) continue;
            if (entity.id.equals(item.ownerCharacterId) || entity.id.equals(item.ownerHeroId)) {
                all.put(item.id, item);
            }
        }

        java.util.List<EntityRoom> sorted = new java.util.ArrayList<>(all.values());
        sorted.sort((a, b) -> {
            int byType = Integer.compare(linkOrder(a.kind), linkOrder(b.kind));
            return byType != 0 ? byType : a.name.compareToIgnoreCase(b.name);
        });

        binding.entityLinks.removeAllViews();
        binding.entityLinksTitle.setText(R.string.entity_links);
        ViewUtils.show(binding.entityLinksTitle, !sorted.isEmpty());
        String currentKind = null;
        for (EntityRoom item : sorted) {
            if (!item.kind.equalsIgnoreCase(currentKind == null ? "" : currentKind)) {
                currentKind = item.kind;
                android.widget.TextView header = new android.widget.TextView(this);
                header.setText(Labels.entityTypeText(this, item.kind));
                header.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);
                header.setPadding(0, 12, 0, 4);
                binding.entityLinks.addView(header);
            }
            android.view.View row = getLayoutInflater().inflate(
                    R.layout.item_backlink, binding.entityLinks, false);
            android.widget.ImageView avatar = row.findViewById(R.id.backlink_avatar);
            android.widget.ImageView classIcon = row.findViewById(R.id.backlink_class_icon);
            android.widget.TextView name = row.findViewById(R.id.backlink_name);
            android.widget.TextView subtitle = row.findViewById(R.id.backlink_subtitle);
            AvatarBinder.bind(this, avatar, item, App.container(this).okHttpClient());
            AvatarBinder.bindClassIcon(this, classIcon, item);
            name.setText(item.name);
            subtitle.setText(Labels.entityTypeText(this, item.kind));
            row.setOnClickListener(v -> Nav.openEntity(this, campaignId, item.id));
            binding.entityLinks.addView(row);
        }
        ViewUtils.show(binding.entityMentionsTitle, false);
        binding.entityMentions.removeAllViews();
    }

    private static int linkOrder(String kind) {
        if (kind == null) return 9;
        switch (kind.toUpperCase(java.util.Locale.ROOT)) {
            case "CHARACTER": return 0;
            case "LOCATION": return 1;
            case "ITEM": return 2;
            case "HERO": return 3;
            case "RETELLING": return 4;
            case "MATERIAL": return 5;
            default: return 9;
        }
    }

    private void bindLinks(@NonNull EntityRoom entity) {
        binding.entityLinks.removeAllViews();
        if (entity.links == null || entity.links.isEmpty()) {
            ViewUtils.show(binding.entityLinksTitle, false);
            return;
        }
        ViewUtils.show(binding.entityLinksTitle, true);
        binding.entityLinksTitle.setText(R.string.entity_links);
        for (EntityLink link : entity.links) {
            EntityRoom target = link.targetId == null ? null : findObject(link.targetId);
            if (target != null) {
                // Ссылка ведёт на объект кампании — показываем строку с аватаром.
                android.view.View row = getLayoutInflater().inflate(
                        R.layout.item_backlink, binding.entityLinks, false);
                android.widget.ImageView avatar = row.findViewById(R.id.backlink_avatar);
                android.widget.ImageView classIcon = row.findViewById(R.id.backlink_class_icon);
                android.widget.TextView name = row.findViewById(R.id.backlink_name);
                android.widget.TextView subtitle = row.findViewById(R.id.backlink_subtitle);
                AvatarBinder.bind(this, avatar, target, App.container(this).okHttpClient());
                AvatarBinder.bindClassIcon(this, classIcon, target);
                name.setText(link.label == null || link.label.isEmpty() ? target.name : link.label);
                subtitle.setText(Labels.entityTypeText(this, target.kind));
                row.setOnClickListener(v -> Nav.openEntity(this, campaignId, target.id));
                binding.entityLinks.addView(row);
                continue;
            }
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
