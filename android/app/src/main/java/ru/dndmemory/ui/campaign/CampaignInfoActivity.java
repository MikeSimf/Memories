package ru.dndmemory.ui.campaign;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.CheckBox;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import ru.dndmemory.App;
import ru.dndmemory.R;
import ru.dndmemory.data.local.entity.CampaignRoom;
import ru.dndmemory.data.local.entity.MemberRoom;
import ru.dndmemory.databinding.ActivityCampaignInfoBinding;
import ru.dndmemory.ui.common.BottomNavActivity;
import ru.dndmemory.ui.common.ErrorMessages;
import ru.dndmemory.ui.common.Extras;
import ru.dndmemory.ui.common.Labels;
import ru.dndmemory.ui.common.Nav;
import ru.dndmemory.ui.common.Rights;
import ru.dndmemory.ui.common.ViewUtils;
import ru.dndmemory.util.AppExecutors;
import ru.dndmemory.util.RepoCallback;

/** Карточка кампании: обложка, название, описание и участники. */
public class CampaignInfoActivity extends BottomNavActivity {

    private ActivityCampaignInfoBinding binding;
    private CampaignViewModel viewModel;
    private String campaignId;
    private CampaignRoom campaign;
    private boolean dictionariesLoaded;
    private String section = "main";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCampaignInfoBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        campaignId = getIntent() == null ? null : getIntent().getStringExtra(Extras.CAMPAIGN_ID);
        if (campaignId == null || campaignId.isEmpty()) {
            finish();
            return;
        }
        setSupportActionBar(binding.campaignInfoToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        viewModel = new androidx.lifecycle.ViewModelProvider(this,
                ru.dndmemory.ui.common.ViewModelFactory.of(
                        () -> new CampaignViewModel(App.container(this).campaignRepository(),
                                App.container(this).authRepository(),
                                App.container(this).syncEngine(),
                                App.container(this).syncScheduler())))
                .get(CampaignViewModel.class);
        viewModel.setCampaignId(campaignId);

        setupSections();
        binding.campaignInfoEdit.setOnClickListener(v -> edit());
        binding.campaignInfoDelete.setOnClickListener(v -> confirmDelete());
        // Room нельзя читать в главном потоке: карточка заполняется после загрузки.
        AppExecutors.get().io(() -> {
            CampaignRoom loaded = App.container(this).database().campaignDao().find(campaignId);
            runOnUiThread(() -> {
                campaign = loaded;
                render();
            });
            return null;
        }, new RepoCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void value) {
            }

            @Override
            public void onError(@NonNull ru.dndmemory.data.remote.ApiException error) {
            }
        });
        loadMembers();
    }


    private void setupSections() {
        String[][] items = {
                {"main", getString(R.string.campaign_section_main)},
                {"members", getString(R.string.tab_members)},
                {"dictionaries", getString(R.string.campaign_dictionaries_title)},
                {"sync", getString(R.string.campaign_sync_title)},
                {"actions", getString(R.string.campaign_actions_title)},
        };
        for (String[] item : items) {
            com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(this);
            chip.setText(item[1]);
            chip.setCheckable(true);
            chip.setTag(item[0]);
            chip.setOnClickListener(v -> showSection(item[0]));
            binding.campaignInfoSections.addView(chip);
            if ("main".equals(item[0])) chip.setChecked(true);
        }
        showSection("main");
    }

    private void showSection(@NonNull String key) {
        section = key;
        binding.campaignInfoMainSection.setVisibility("main".equals(key) ? android.view.View.VISIBLE : android.view.View.GONE);
        binding.campaignInfoMembersSection.setVisibility("members".equals(key) ? android.view.View.VISIBLE : android.view.View.GONE);
        binding.campaignInfoDictionariesSection.setVisibility("dictionaries".equals(key) ? android.view.View.VISIBLE : android.view.View.GONE);
        binding.campaignInfoSyncSection.setVisibility("sync".equals(key) ? android.view.View.VISIBLE : android.view.View.GONE);
        binding.campaignInfoActionsSection.setVisibility("actions".equals(key) ? android.view.View.VISIBLE : android.view.View.GONE);
        for (int i = 0; i < binding.campaignInfoSections.getChildCount(); i++) {
            android.view.View child = binding.campaignInfoSections.getChildAt(i);
            if (child instanceof com.google.android.material.chip.Chip) {
                ((com.google.android.material.chip.Chip) child).setChecked(key.equals(child.getTag()));
            }
        }
        if ("dictionaries".equals(key) && !dictionariesLoaded) {
            loadDictionaries();
        }
        if ("sync".equals(key) && campaign != null) {
            binding.campaignInfoSyncText.setText(getString(R.string.campaign_sync_summary,
                    campaign.latestRevision, campaign.updatedAt == null ? "—" : campaign.updatedAt));
        }
    }

    private void loadDictionaries() {
        dictionariesLoaded = true;
        App.container(this).apiService().campaignDictionaries(campaignId).enqueue(
                new retrofit2.Callback<List<ru.dndmemory.data.remote.dto.DictionaryEntryDto>>() {
                    @Override
                    public void onResponse(@NonNull retrofit2.Call<List<ru.dndmemory.data.remote.dto.DictionaryEntryDto>> call,
                                           @NonNull retrofit2.Response<List<ru.dndmemory.data.remote.dto.DictionaryEntryDto>> response) {
                        runOnUiThread(() -> renderDictionaries(response.body()));
                    }

                    @Override
                    public void onFailure(@NonNull retrofit2.Call<List<ru.dndmemory.data.remote.dto.DictionaryEntryDto>> call,
                                          @NonNull Throwable t) {
                        dictionariesLoaded = false;
                        runOnUiThread(() -> ViewUtils.toast(CampaignInfoActivity.this,
                                R.string.file_download_failed));
                    }
                });
    }

    private void renderDictionaries(@Nullable List<ru.dndmemory.data.remote.dto.DictionaryEntryDto> entries) {
        binding.campaignInfoDictionaries.removeAllViews();
        if (entries == null || entries.isEmpty()) {
            return;
        }
        String currentKind = null;
        for (ru.dndmemory.data.remote.dto.DictionaryEntryDto entry : entries) {
            if (!entry.kind.equals(currentKind)) {
                currentKind = entry.kind;
                android.widget.TextView title = new android.widget.TextView(this);
                title.setText(dictionaryKindTitle(currentKind));
                title.setPadding(0, 12, 0, 4);
                binding.campaignInfoDictionaries.addView(title);
            }
            CheckBox box = new CheckBox(this);
            box.setText(entry.name);
            box.setChecked(entry.enabled);
            boolean canManage = campaign != null && Rights.canManageCampaign(campaign);
            box.setEnabled(canManage && !(entry.used && entry.enabled));
            box.setOnCheckedChangeListener((button, checked) -> {
                if (!canManage) return;
                java.util.Map<String, Object> body = new java.util.HashMap<>();
                body.put("kind", entry.kind);
                body.put("name", entry.name);
                body.put("enabled", checked);
                App.container(this).apiService().patchDictionary(campaignId, body).enqueue(
                        new retrofit2.Callback<Void>() {
                            @Override
                            public void onResponse(@NonNull retrofit2.Call<Void> call, @NonNull retrofit2.Response<Void> response) {
                                if (!response.isSuccessful()) {
                                    runOnUiThread(() -> box.setChecked(!checked));
                                }
                            }

                            @Override
                            public void onFailure(@NonNull retrofit2.Call<Void> call, @NonNull Throwable t) {
                                runOnUiThread(() -> box.setChecked(!checked));
                            }
                        });
            });
            binding.campaignInfoDictionaries.addView(box);
        }
    }

    @NonNull
    private String dictionaryKindTitle(@Nullable String kind) {
        if ("RACE".equals(kind)) return getString(R.string.campaign_dictionary_races);
        if ("ITEM_TYPE".equals(kind)) return getString(R.string.campaign_dictionary_items);
        return getString(R.string.campaign_dictionary_locations);
    }

    private void render() {
        if (campaign == null) {
            return;
        }
        binding.campaignInfoName.setText(campaign.title);
        binding.campaignInfoDescription.setText(TextUtils.isEmpty(campaign.description)
                ? getString(R.string.campaign_info_no_description) : campaign.description);
        boolean canEdit = Rights.canManageCampaign(campaign);
        boolean owner = Rights.canDeleteCampaign(campaign);
        ViewUtils.show(binding.campaignInfoEdit, canEdit);
        ViewUtils.show(binding.campaignInfoDelete, owner);
    }

    private void loadMembers() {
        AppExecutors.get().io(() -> {
            List<MemberRoom> members = App.container(this).database()
                    .memberDao().findAllNow(campaignId);
            runOnUiThread(() -> {
                binding.campaignInfoMembers.removeAllViews();
                for (MemberRoom member : members) {
                    android.widget.TextView row = new android.widget.TextView(this);
                    row.setText(getString(R.string.campaign_info_member,
                            member.displayName == null ? "" : member.displayName,
                            Labels.roleText(this, member.role)));
                    row.setPadding(0, 6, 0, 6);
                    binding.campaignInfoMembers.addView(row);
                }
                if (members.isEmpty()) {
                    android.widget.TextView empty = new android.widget.TextView(this);
                    empty.setText(R.string.campaign_info_no_members);
                    binding.campaignInfoMembers.addView(empty);
                }
            });
            return null;
        }, new RepoCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void value) {
            }

            @Override
            public void onError(@NonNull ru.dndmemory.data.remote.ApiException error) {
            }
        });
    }

    private void edit() {
        if (campaign == null) {
            return;
        }
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setPadding(32, 16, 32, 0);
        EditText title = new EditText(this);
        title.setHint(R.string.campaign_create_title);
        title.setText(campaign.title);
        EditText description = new EditText(this);
        description.setHint(R.string.campaign_create_description);
        description.setText(campaign.description);
        box.addView(title);
        box.addView(description);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.campaign_info_edit)
                .setView(box)
                .setPositiveButton(R.string.action_save, (dialog, which) -> {
                    String nextTitle = title.getText().toString().trim();
                    if (nextTitle.isEmpty()) {
                        return;
                    }
                    App.container(this).campaignRepository().rename(campaign, nextTitle,
                            description.getText().toString().trim(),
                            new RepoCallback<CampaignRoom>() {
                                @Override
                                public void onSuccess(@Nullable CampaignRoom value) {
                                    campaign = value;
                                    render();
                                }

                                @Override
                                public void onError(@NonNull ru.dndmemory.data.remote.ApiException error) {
                                    ViewUtils.toast(CampaignInfoActivity.this,
                                            ErrorMessages.text(CampaignInfoActivity.this, error));
                                }
                            });
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.campaign_info_delete)
                .setMessage(R.string.campaign_info_delete_confirm)
                .setPositiveButton(R.string.action_delete, (dialog, which) ->
                        App.container(this).campaignRepository().delete(campaignId,
                                new RepoCallback<Void>() {
                                    @Override
                                    public void onSuccess(@Nullable Void value) {
                                        Nav.openCampaigns(CampaignInfoActivity.this);
                                        finish();
                                    }

                                    @Override
                                    public void onError(@NonNull ru.dndmemory.data.remote.ApiException error) {
                                        ViewUtils.toast(CampaignInfoActivity.this,
                                                ErrorMessages.text(CampaignInfoActivity.this, error));
                                    }
                                }))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    @NonNull
    @Override
    protected String navActiveKey() {
        return "home";
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
