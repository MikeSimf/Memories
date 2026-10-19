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
    private String campaignId;
    private NameIndex nameIndex = NameIndex.of(null);
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

        ViewUtils.show(binding.entityConflictBar, entity.hasConflict());
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
                    addFieldRow(getString(R.string.attr_race), entity.race);
                    addFieldRow(getString(R.string.attr_klass), entity.klass);
                    addFieldRow(getString(R.string.attr_player_name), entity.playerName);
                    break;
                case CHARACTER:
                    addFieldRow(getString(R.string.attr_race), entity.race);
                    addFieldRow(getString(R.string.attr_short_description), entity.shortDescription);
                    break;
                case ITEM:
                    addFieldRow(getString(R.string.attr_owner), ownerText(entity));
                    break;
                case LOCATION:
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
            CampaignRoom campaign = campaignViewModel.campaign().getValue();
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
        CampaignRoom campaign = campaignViewModel.campaign().getValue();
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
