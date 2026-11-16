package ru.dndmemory.ui.quest;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import ru.dndmemory.App;
import ru.dndmemory.R;
import ru.dndmemory.data.remote.dto.QuestDto;
import ru.dndmemory.ui.common.BaseCampaignActivity;
import ru.dndmemory.ui.common.Nav;
import ru.dndmemory.ui.common.ViewUtils;

/** Список квестов кампании. */
public class QuestsActivity extends BaseCampaignActivity {

    private LinearLayout list;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!initCampaign()) return;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Toolbar toolbar = new Toolbar(this);
        toolbar.setTitle(R.string.quests_title);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar);
        android.widget.Button add = new android.widget.Button(this);
        add.setText(R.string.quest_add);
        add.setOnClickListener(v -> editQuest(null));
        root.addView(add);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(24, 12, 24, 24);
        root.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
        load();
    }

    private void load() {
        App.container(this).apiService().quests(campaignId).enqueue(new Callback<List<QuestDto>>() {
            @Override public void onResponse(@NonNull Call<List<QuestDto>> call, @NonNull Response<List<QuestDto>> response) {
                runOnUiThread(() -> render(response.body()));
            }
            @Override public void onFailure(@NonNull Call<List<QuestDto>> call, @NonNull Throwable t) {
                runOnUiThread(() -> ViewUtils.toast(QuestsActivity.this, R.string.maps_load_failed));
            }
        });
    }

    private void render(@Nullable List<QuestDto> quests) {
        list.removeAllViews();
        if (quests == null || quests.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.quests_empty);
            list.addView(empty);
            return;
        }
        for (QuestDto quest : quests) {
            com.google.android.material.card.MaterialCardView card = new com.google.android.material.card.MaterialCardView(this);
            card.setRadius(12 * getResources().getDisplayMetrics().density);
            card.setStrokeWidth((int) getResources().getDisplayMetrics().density);
            card.setStrokeColor(0xFF8A8AA3);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = (int) (10 * getResources().getDisplayMetrics().density);
            card.setLayoutParams(params);
            TextView row = new TextView(this);
            row.setText(quest.name + "\n" + (quest.description == null ? "" : quest.description));
            row.setPadding(24, 18, 24, 18);
            row.setTextSize(16);
            row.setGravity(Gravity.CENTER_VERTICAL);
            card.addView(row);
            card.setOnClickListener(v -> Nav.openQuest(this, campaignId, quest.id));
            list.addView(card);
        }
    }

    private void editQuest(@Nullable QuestDto quest) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(24, 12, 24, 0);
        EditText name = new EditText(this);
        name.setHint(R.string.quest_name);
        if (quest != null) name.setText(quest.name);
        EditText description = new EditText(this);
        description.setHint(R.string.quest_description);
        description.setMinLines(3);
        if (quest != null) description.setText(quest.description);
        box.addView(name);
        box.addView(description);
        new AlertDialog.Builder(this)
                .setTitle(quest == null ? R.string.quest_add : R.string.quest_edit)
                .setView(box)
                .setPositiveButton(R.string.action_save, (d, w) -> {
                    java.util.Map<String, Object> body = new java.util.HashMap<>();
                    body.put("name", name.getText().toString().trim());
                    body.put("description", description.getText().toString());
                    if (quest == null) {
                        App.container(this).apiService().createQuest(campaignId, body).enqueue(simpleReload());
                    } else {
                        App.container(this).apiService().updateQuest(campaignId, quest.id, body).enqueue(simpleReload());
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private Callback<QuestDto> simpleReload() {
        return new Callback<QuestDto>() {
            @Override public void onResponse(@NonNull Call<QuestDto> call, @NonNull Response<QuestDto> response) { runOnUiThread(QuestsActivity.this::load); }
            @Override public void onFailure(@NonNull Call<QuestDto> call, @NonNull Throwable t) { runOnUiThread(() -> ViewUtils.toast(QuestsActivity.this, R.string.maps_load_failed)); }
        };
    }
}
