package ru.dndmemory.ui.campaign;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

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
