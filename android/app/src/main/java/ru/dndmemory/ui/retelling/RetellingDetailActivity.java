package ru.dndmemory.ui.retelling;

import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import ru.dndmemory.App;
import ru.dndmemory.AppContainer;
import ru.dndmemory.R;
import ru.dndmemory.data.local.entity.CampaignRoom;
import ru.dndmemory.data.local.entity.RetellingRoom;
import ru.dndmemory.data.model.EntityLink;
import ru.dndmemory.data.model.EntityType;
import ru.dndmemory.databinding.ActivityRetellingDetailBinding;
import ru.dndmemory.ui.campaign.CampaignViewModel;
import ru.dndmemory.ui.common.ErrorMessages;
import ru.dndmemory.ui.common.Extras;
import ru.dndmemory.ui.common.Nav;
import ru.dndmemory.ui.common.NameIndex;
import ru.dndmemory.ui.common.Rights;
import ru.dndmemory.ui.common.ViewModelFactory;
import ru.dndmemory.ui.common.ViewUtils;
import ru.dndmemory.util.DateFormats;
import ru.dndmemory.util.WikiMarkdown;

/** Чтение пересказа с живыми ссылками [[Имя]]. */
public class RetellingDetailActivity extends ru.dndmemory.ui.common.BottomNavActivity {

    private ActivityRetellingDetailBinding binding;
    private RetellingDetailViewModel viewModel;
    private CampaignViewModel campaignViewModel;
    private String campaignId;
    private NameIndex nameIndex = NameIndex.of(null);
    private RetellingRoom current;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRetellingDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        campaignId = getIntent() == null ? null : getIntent().getStringExtra(Extras.CAMPAIGN_ID);
        String retellingId = getIntent() == null ? null : getIntent().getStringExtra(Extras.RETELLING_ID);
        if (campaignId == null || retellingId == null) {
            finish();
            return;
        }

        AppContainer container = App.container(this);
        viewModel = new ViewModelProvider(this, ViewModelFactory.of(
                () -> new RetellingDetailViewModel(container.retellingRepository(),
                        container.entityRepository())))
                .get(RetellingDetailViewModel.class);
        viewModel.start(campaignId, retellingId);

        campaignViewModel = new ViewModelProvider(this, ViewModelFactory.of(
                () -> new CampaignViewModel(container.campaignRepository(),
                        container.authRepository(), container.syncEngine(),
                        container.syncScheduler())))
                .get(CampaignViewModel.class);
        campaignViewModel.setCampaignId(campaignId);
        campaignViewModel.refreshDetails();

        setSupportActionBar(binding.retellingToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        binding.retellingConflictAccept.setOnClickListener(v -> viewModel.acceptServerVersion());
        binding.retellingConflictOverwrite.setOnClickListener(v -> viewModel.overwriteWithLocal());

        observe();
    }

    private void observe() {
        viewModel.retelling().observe(this, retelling -> {
            if (retelling == null) {
                return;
            }
            if (retelling.deleted) {
                finish();
                return;
            }
            current = retelling;
            bind(retelling);
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

    private void bind(@NonNull RetellingRoom retelling) {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(TextUtils.isEmpty(retelling.title)
                    ? getString(R.string.retelling_detail_title) : retelling.title);
        }
        binding.retellingTitle.setText(TextUtils.isEmpty(retelling.title)
                ? getString(R.string.retelling_detail_title) : retelling.title);

        StringBuilder meta = new StringBuilder();
        if (!TextUtils.isEmpty(retelling.sessionDate)) {
            meta.append(DateFormats.formatDate(retelling.sessionDate)).append(" · ");
        }
        meta.append(getString(R.string.retelling_edited,
                DateFormats.formatIso(retelling.updatedAt)));
        if (!TextUtils.isEmpty(retelling.authorEntityName)) {
            meta.append(" · ").append(getString(R.string.retelling_author,
                    retelling.authorEntityName));
        }
        binding.retellingMeta.setText(meta.toString());

        binding.retellingBody.setText(WikiMarkdown.render(this, retelling.body,
                name -> nameIndex.idFor(name), this::onWikiLinkClick));
        binding.retellingBody.setMovementMethod(LinkMovementMethod.getInstance());
        binding.retellingBody.setHighlightColor(Color.TRANSPARENT);

        bindLinks(retelling);
        ViewUtils.show(binding.retellingConflictBar, retelling.hasConflict());
    }

    private void onWikiLinkClick(@Nullable String targetId, @NonNull String name, boolean resolved) {
        if (resolved && targetId != null) {
            Nav.openEntity(this, campaignId, targetId);
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.wiki_create_suggestion, name))
                .setPositiveButton(R.string.action_create, (dialog, which) ->
                        Nav.createEntity(this, campaignId, EntityType.CHARACTER.name()))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void bindLinks(@NonNull RetellingRoom retelling) {
        binding.retellingLinks.removeAllViews();
        if (retelling.links == null || retelling.links.isEmpty()) {
            ViewUtils.show(binding.retellingLinksTitle, false);
            return;
        }
        ViewUtils.show(binding.retellingLinksTitle, true);
        for (EntityLink link : retelling.links) {
            TextView row = new TextView(this);
            String markup = link.targetId != null
                    ? "[[" + "#" + link.targetId + "|" + (link.label == null ? "" : link.label) + "]]"
                    : "[[" + (link.name == null ? link.label : link.name) + "]]";
            row.setText(WikiMarkdown.render(this, markup,
                    name -> nameIndex.idFor(name), this::onWikiLinkClick));
            row.setMovementMethod(LinkMovementMethod.getInstance());
            row.setHighlightColor(Color.TRANSPARENT);
            row.setPadding(0, 4, 0, 4);
            binding.retellingLinks.addView(row);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_retelling_detail, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_edit) {
            CampaignRoom campaign = campaignViewModel.campaign().getValue();
            if (current == null || !Rights.canEditRetelling(campaign, current,
                    campaignViewModel.currentUserId())) {
                ViewUtils.toast(this, R.string.error_forbidden);
                return true;
            }
            // Правка опубликованного идёт через черновик на устройстве.
            Nav.editRetelling(this, campaignId, null, current.id);
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
        if (!Rights.canDeleteRetelling(campaign, current, campaignViewModel.currentUserId())) {
            ViewUtils.toast(this, R.string.error_forbidden);
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.retelling_delete_confirm,
                        TextUtils.isEmpty(current.title) ? "" : current.title))
                .setPositiveButton(R.string.action_delete, (dialog, which) -> viewModel.delete())
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    @NonNull
    @Override
    protected String navActiveKey() {
        return "retellings";
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
