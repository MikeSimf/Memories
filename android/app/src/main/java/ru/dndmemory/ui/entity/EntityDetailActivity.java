package ru.dndmemory.ui.entity;

import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.StyleSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ru.dndmemory.App;
import ru.dndmemory.AppContainer;
import ru.dndmemory.R;
import ru.dndmemory.data.local.entity.CampaignRoom;
import ru.dndmemory.data.local.entity.EntityRoom;
import ru.dndmemory.data.model.EntityLink;
import ru.dndmemory.data.model.EntityType;
import ru.dndmemory.data.remote.dto.BacklinkDto;
import ru.dndmemory.databinding.ActivityEntityDetailBinding;
import ru.dndmemory.ui.campaign.CampaignViewModel;
import ru.dndmemory.ui.common.Attrs;
import ru.dndmemory.ui.common.ErrorMessages;
import ru.dndmemory.ui.common.Extras;
import ru.dndmemory.ui.common.Labels;
import ru.dndmemory.ui.common.NameIndex;
import ru.dndmemory.ui.common.Nav;
import ru.dndmemory.ui.common.Rights;
import ru.dndmemory.ui.common.ViewModelFactory;
import ru.dndmemory.ui.common.ViewUtils;
import ru.dndmemory.util.DateFormats;
import ru.dndmemory.util.WikiMarkdown;

/** Карточка объекта: описание со ссылками [[Имя]], характеристики, обратные ссылки. */
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

        setSupportActionBar(binding.entityToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

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

        viewModel.backlinks().observe(this, this::bindBacklinks);

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
        binding.entitySubtitle.setText(getString(R.string.entity_detail_type,
                Labels.entityTypeText(this, entity.type)));
        binding.entityUpdated.setText(getString(R.string.entity_detail_updated,
                DateFormats.formatIso(entity.updatedAt)));

        if (entity.tags == null || entity.tags.isEmpty()) {
            ViewUtils.show(binding.entityTags, false);
        } else {
            binding.entityTags.setText(TextUtils.join(" · ", entity.tags));
            ViewUtils.show(binding.entityTags, true);
        }

        renderWiki(binding.entitySummary, entity.summary);
        renderWiki(binding.entityDescription, entity.description);
        bindAttrs(entity);
        bindLinks(entity);

        ViewUtils.show(binding.entityConflictBar, entity.hasConflict());
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
        // «Битая» ссылка: предлагаем создать объект с таким именем (docs/sync.md, раздел 7).
        new MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.wiki_create_suggestion, name))
                .setPositiveButton(R.string.action_create, (dialog, which) ->
                        Nav.createEntity(this, campaignId, EntityType.NPC.name()))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void bindAttrs(@NonNull EntityRoom entity) {
        binding.entityAttrs.removeAllViews();
        if (entity.attrs == null || entity.attrs.isEmpty()) {
            ViewUtils.show(binding.entityAttrsTitle, false);
            return;
        }
        EntityType type = EntityType.from(entity.type);
        List<Attrs.Field> fields = Attrs.fieldsFor(type);
        Set<String> shown = new HashSet<>();

        for (Attrs.Field field : fields) {
            String value = entity.attrs.get(field.key);
            if (value == null || value.isEmpty()) {
                continue;
            }
            addAttrRow(Labels.attrLabelText(this, field.key), attrValueText(field, value));
            shown.add(field.key);
        }
        for (java.util.Map.Entry<String, String> entry : entity.attrs.entrySet()) {
            if (shown.contains(entry.getKey()) || entry.getValue() == null
                    || entry.getValue().isEmpty()) {
                continue;
            }
            addAttrRow(Labels.attrLabelText(this, entry.getKey()), entry.getValue());
        }
        ViewUtils.show(binding.entityAttrsTitle, binding.entityAttrs.getChildCount() > 0);
    }

    private CharSequence attrValueText(@NonNull Attrs.Field field, @NonNull String value) {
        if (field.kind == Attrs.Kind.ENTITY_REF) {
            String name = nameIndex.nameFor(value);
            return name == null ? value : name;
        }
        return Labels.attrValue(this, field.key, value);
    }

    private void addAttrRow(CharSequence label, CharSequence value) {
        SpannableStringBuilder line = new SpannableStringBuilder();
        line.append(label);
        line.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, line.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        line.append(": ");
        line.append(value == null ? "" : value);

        TextView row = new TextView(this);
        row.setText(line);
        row.setPadding(0, 4, 0, 4);
        binding.entityAttrs.addView(row);
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

    private void bindBacklinks(@Nullable List<BacklinkDto> items) {
        binding.entityBacklinks.removeAllViews();
        if (items == null || items.isEmpty()) {
            ViewUtils.show(binding.entityBacklinksEmpty, true);
            return;
        }
        ViewUtils.show(binding.entityBacklinksEmpty, false);
        for (final BacklinkDto item : items) {
            TextView row = new TextView(this);
            CharSequence title = item.title == null ? "" : item.title;
            String snippet = WikiMarkdown.plainText(item.snippet);
            row.setText(snippet.isEmpty() ? title : title + " — " + snippet);
            row.setPadding(0, 6, 0, 6);
            row.setOnClickListener(v -> openBacklink(item));
            binding.entityBacklinks.addView(row);
        }
    }

    private void openBacklink(@NonNull BacklinkDto item) {
        if (item.id == null) {
            return;
        }
        if ("RETELLING".equalsIgnoreCase(item.kind)) {
            Nav.openRetelling(this, campaignId, item.id);
        } else {
            Nav.openEntity(this, campaignId, item.id);
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
                ViewUtils.toast(this, R.string.error_forbidden);
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
            ViewUtils.toast(this, R.string.error_forbidden);
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
