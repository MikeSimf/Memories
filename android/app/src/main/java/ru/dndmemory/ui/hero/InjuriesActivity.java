package ru.dndmemory.ui.hero;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.Button;
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
import ru.dndmemory.data.remote.dto.InjuryDto;
import ru.dndmemory.ui.common.BaseCampaignActivity;
import ru.dndmemory.ui.common.ViewUtils;

/** Травмы и увечья героя. */
public class InjuriesActivity extends BaseCampaignActivity {

    private String heroId;
    private LinearLayout list;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!initCampaign()) return;
        heroId = getIntent() == null ? null : getIntent().getStringExtra("heroId");
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Toolbar toolbar = new Toolbar(this);
        toolbar.setTitle(R.string.injuries_title);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar);
        Button add = new Button(this);
        add.setText(R.string.injury_add);
        add.setOnClickListener(v -> edit(null));
        root.addView(add);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(24, 12, 24, 24);
        root.addView(list);
        setContentView(root);
        load();
    }

    private void load() {
        App.container(this).apiService().injuries(campaignId, heroId).enqueue(new Callback<List<InjuryDto>>() {
            @Override public void onResponse(@NonNull Call<List<InjuryDto>> call, @NonNull Response<List<InjuryDto>> response) { runOnUiThread(() -> render(response.body())); }
            @Override public void onFailure(@NonNull Call<List<InjuryDto>> call, @NonNull Throwable t) { runOnUiThread(() -> ViewUtils.toast(InjuriesActivity.this, R.string.maps_load_failed)); }
        });
    }

    private void render(@Nullable List<InjuryDto> injuries) {
        list.removeAllViews();
        if (injuries == null || injuries.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.injuries_empty);
            list.addView(empty);
            return;
        }
        for (InjuryDto injury : injuries) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            TextView text = new TextView(this);
            text.setText(injury.text);
            row.addView(text);
            LinearLayout actions = new LinearLayout(this);
            Button edit = new Button(this);
            edit.setText(R.string.quest_comment_edit);
            edit.setOnClickListener(v -> edit(injury));
            Button delete = new Button(this);
            delete.setText(R.string.quest_comment_delete);
            delete.setOnClickListener(v -> delete(injury));
            actions.addView(edit);
            actions.addView(delete);
            row.addView(actions);
            list.addView(row);
        }
    }

    private void edit(@Nullable InjuryDto injury) {
        EditText text = new EditText(this);
        if (injury != null) text.setText(injury.text);
        new AlertDialog.Builder(this)
                .setTitle(injury == null ? R.string.injury_add : R.string.injury_edit)
                .setView(text)
                .setPositiveButton(R.string.action_save, (d, w) -> {
                    java.util.Map<String, Object> body = new java.util.HashMap<>();
                    body.put("text", text.getText().toString());
                    if (injury == null) {
                        App.container(this).apiService().addInjury(campaignId, heroId, body).enqueue(reload());
                    } else {
                        App.container(this).apiService().updateInjury(campaignId, heroId, injury.id, body).enqueue(reload());
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void delete(InjuryDto injury) {
        App.container(this).apiService().deleteInjury(campaignId, heroId, injury.id).enqueue(reload());
    }

    private Callback reload() {
        return new Callback() {
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) { runOnUiThread(InjuriesActivity.this::load); }
            @Override public void onFailure(@NonNull Call call, @NonNull Throwable t) { }
        };
    }
}
