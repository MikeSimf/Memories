package ru.dndmemory.ui.material;

import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

import ru.dndmemory.App;
import ru.dndmemory.R;
import ru.dndmemory.data.local.entity.MaterialRoom;
import ru.dndmemory.data.model.EntityLink;
import ru.dndmemory.databinding.ActivityMaterialDetailBinding;
import ru.dndmemory.ui.common.AvatarBinder;
import ru.dndmemory.ui.common.BottomNavActivity;
import ru.dndmemory.ui.common.ErrorMessages;
import ru.dndmemory.ui.common.Extras;
import ru.dndmemory.ui.common.Labels;
import ru.dndmemory.ui.common.Nav;
import ru.dndmemory.ui.common.ViewUtils;
import ru.dndmemory.util.AppExecutors;
import ru.dndmemory.util.RepoCallback;

/** Карточка просмотра материала: описание, ссылки и файлы. */
public class MaterialDetailActivity extends BottomNavActivity {

    private ActivityMaterialDetailBinding binding;
    private String campaignId;
    private String materialId;
    private MaterialRoom material;
    private boolean canEdit;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMaterialDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        campaignId = getIntent() == null ? null : getIntent().getStringExtra(Extras.CAMPAIGN_ID);
        materialId = getIntent() == null ? null : getIntent().getStringExtra(Extras.MATERIAL_ID);
        if (campaignId == null || materialId == null) {
            finish();
            return;
        }
        setSupportActionBar(binding.materialDetailToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        setActiveNav("materials");

        AppExecutors.get().io(() -> {
            material = App.container(this).materialRepository().find(materialId);
            ru.dndmemory.data.local.entity.CampaignRoom campaign =
                    App.container(this).database().campaignDao().find(campaignId);
            canEdit = campaign != null && ru.dndmemory.ui.common.Rights.has(
                    campaign, "entity.write");
            runOnUiThread(this::render);
            return null;
        }, new RepoCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void value) {
            }

            @Override
            public void onError(@NonNull ru.dndmemory.data.remote.ApiException error) {
            }
        });
        binding.materialDetailEdit.setOnClickListener(v -> Nav.openMaterialEdit(this, campaignId, materialId));
        binding.materialDetailDelete.setOnClickListener(v -> confirmDelete());
    }

    private void render() {
        if (material == null) {
            finish();
            return;
        }
        binding.materialDetailName.setText(material.name);
        binding.materialDetailDescription.setText(TextUtils.isEmpty(material.description)
                ? getString(R.string.attr_not_set) : material.description);
        ViewUtils.show(binding.materialDetailEdit, canEdit);
        ViewUtils.show(binding.materialDetailDelete, canEdit);

        binding.materialDetailLinks.removeAllViews();
        List<EntityLink> links = ru.dndmemory.data.Mappers.links(material.linksJson);
        ViewUtils.show(binding.materialDetailLinksTitle, !links.isEmpty());
        for (EntityLink link : links) {
            if (link == null) {
                continue;
            }
            android.widget.TextView row = new android.widget.TextView(this);
            row.setText(link.label != null ? link.label : link.name);
            row.setPadding(0, 6, 0, 6);
            if (link.targetId != null) {
                row.setTextColor(getResources().getColor(R.color.wiki_link, getTheme()));
                row.setOnClickListener(v -> Nav.openEntity(this, campaignId, link.targetId));
            }
            binding.materialDetailLinks.addView(row);
        }
        binding.materialDetailUpdated.setText(getString(R.string.material_detail_updated, material.updatedAt));
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.action_delete)
                .setMessage(R.string.material_delete_confirm)
                .setPositiveButton(R.string.action_delete, (dialog, which) ->
                        App.container(this).materialRepository().delete(materialId,
                                new RepoCallback<Void>() {
                                    @Override
                                    public void onSuccess(@Nullable Void value) {
                                        finish();
                                    }

                                    @Override
                                    public void onError(@NonNull ru.dndmemory.data.remote.ApiException error) {
                                        ViewUtils.toast(MaterialDetailActivity.this,
                                                ErrorMessages.text(MaterialDetailActivity.this, error));
                                    }
                                }))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
