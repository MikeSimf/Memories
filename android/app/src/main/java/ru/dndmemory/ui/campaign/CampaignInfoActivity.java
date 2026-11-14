package ru.dndmemory.ui.campaign;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
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
        binding.campaignInfoInvites.setOnClickListener(v -> Nav.openInvites(this, campaignId));
        binding.campaignInfoManageMembers.setOnClickListener(v -> Nav.openMembers(this, campaignId));
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
        java.util.Map<String, List<ru.dndmemory.data.remote.dto.DictionaryEntryDto>> groups = new java.util.LinkedHashMap<>();
        for (ru.dndmemory.data.remote.dto.DictionaryEntryDto entry : entries) {
            List<ru.dndmemory.data.remote.dto.DictionaryEntryDto> list = groups.get(entry.kind);
            if (list == null) {
                list = new ArrayList<>();
                groups.put(entry.kind, list);
            }
            list.add(entry);
        }
        for (java.util.Map.Entry<String, List<ru.dndmemory.data.remote.dto.DictionaryEntryDto>> group : groups.entrySet()) {
            binding.campaignInfoDictionaries.addView(dictionaryGroup(group.getKey(), group.getValue()));
        }
    }

    @NonNull
    private android.view.View dictionaryGroup(@NonNull String kind,
                                                @NonNull List<ru.dndmemory.data.remote.dto.DictionaryEntryDto> entries) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        header.setPadding(0, 16, 0, 10);
        TextView title = new TextView(this);
        title.setText(dictionaryKindTitle(kind));
        title.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
        header.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        int selected = 0;
        for (ru.dndmemory.data.remote.dto.DictionaryEntryDto entry : entries) if (entry.enabled) selected++;
        TextView count = new TextView(this);
        count.setText(getString(R.string.campaign_dictionary_count, selected, entries.size()));
        count.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        header.addView(count);
        TextView arrow = new TextView(this);
        arrow.setText("▾");
        arrow.setPadding(12, 0, 0, 0);
        header.addView(arrow);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        header.setOnClickListener(v -> {
            boolean show = body.getVisibility() != android.view.View.VISIBLE;
            body.setVisibility(show ? android.view.View.VISIBLE : android.view.View.GONE);
            arrow.setText(show ? "▾" : "▸");
        });
        root.addView(header);
        root.addView(body);

        okhttp3.OkHttpClient client = App.container(this).okHttpClient();
        for (ru.dndmemory.data.remote.dto.DictionaryEntryDto entry : entries) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setMinimumHeight((int) (44 * getResources().getDisplayMetrics().density));
            ImageView icon = new ImageView(this);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int size = (int) (30 * getResources().getDisplayMetrics().density);
            row.addView(icon, new LinearLayout.LayoutParams(size, size));
            if (entry.iconFileId != null && !entry.iconFileId.isEmpty()) {
                ru.dndmemory.ui.common.ImageLoader.load(client, entry.iconFileId, icon);
            }
            TextView name = new TextView(this);
            name.setText(entry.name);
            name.setPadding((int) (12 * getResources().getDisplayMetrics().density), 0, 0, 0);
            row.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            CheckBox box = new CheckBox(this);
            box.setChecked(entry.enabled);
            boolean canManage = campaign != null && Rights.canManageCampaign(campaign);
            box.setEnabled(canManage && !(entry.used && entry.enabled));
            box.setOnCheckedChangeListener((button, checked) -> {
                if (!canManage) return;
                java.util.Map<String, Object> payload = new java.util.HashMap<>();
                payload.put("kind", entry.kind);
                payload.put("name", entry.name);
                payload.put("enabled", checked);
                App.container(this).apiService().patchDictionary(campaignId, payload).enqueue(
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
            row.addView(box);
            body.addView(row);
        }
        return root;
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
        ViewUtils.show(binding.campaignInfoManageMembers, Rights.canInvite(campaign));
        ViewUtils.show(binding.campaignInfoInvites, Rights.canInvite(campaign));
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
                    StringBuilder text = new StringBuilder(
                            member.displayName == null ? "" : member.displayName);
                    text.append(" · ").append(Labels.roleText(this, member.role));
                    if (member.status != null && !"ACTIVE".equals(member.status)) {
                        text.append(" · ").append(getString(Labels.memberStatus(member.status)));
                    }
                    if (member.linkedHeroName != null && !member.linkedHeroName.isEmpty()) {
                        text.append(" · ").append(getString(R.string.members_linked_hero))
                                .append(": ").append(member.linkedHeroName);
                    }
                    row.setText(text.toString());
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

    @Override
    protected void onResume() {
        super.onResume();
        if (campaignId != null) loadMembers();
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
