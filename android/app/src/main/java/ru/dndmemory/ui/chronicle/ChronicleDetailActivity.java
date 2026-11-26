package ru.dndmemory.ui.chronicle;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import ru.dndmemory.App;
import ru.dndmemory.R;
import ru.dndmemory.data.remote.dto.ChronicleDto;
import ru.dndmemory.ui.common.BaseCampaignActivity;
import ru.dndmemory.ui.common.Nav;
import ru.dndmemory.ui.common.ViewUtils;

/** Карточка хроники: истории и координаты. */
public class ChronicleDetailActivity extends BaseCampaignActivity {
    private String chronicleId;
    private Toolbar toolbar;
    private LinearLayout content;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!initCampaign()) return;
        chronicleId = getIntent() == null ? null : getIntent().getStringExtra("chronicleId");
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        toolbar = new Toolbar(this);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(24, 12, 24, 24);
        root.addView(content);
        setContentView(root);
        load();
    }

    private void load() {
        App.container(this).apiService().chronicle(campaignId, chronicleId).enqueue(new Callback<ChronicleDto>() {
            @Override public void onResponse(@NonNull Call<ChronicleDto> call, @NonNull Response<ChronicleDto> response) {
                runOnUiThread(() -> render(response.body()));
            }
            @Override public void onFailure(@NonNull Call<ChronicleDto> call, @NonNull Throwable t) {
                runOnUiThread(() -> ViewUtils.toast(ChronicleDetailActivity.this, R.string.maps_load_failed));
            }
        });
    }

    private void render(@Nullable ChronicleDto item) {
        if (item == null) return;
        toolbar.setTitle(item.name);
        content.removeAllViews();
        TextView date = new TextView(this);
        date.setText(item.date == null ? getString(R.string.chronicles_no_date) : formatDate(item.date));
        date.setTextSize(14);
        content.addView(date);

        TextView stories = new TextView(this);
        stories.setText(R.string.chronicle_stories);
        stories.setTextSize(18);
        stories.setPadding(0, 18, 0, 6);
        content.addView(stories);
        if (item.retellings.isEmpty()) {
            content.addView(text(R.string.chronicle_no_stories));
        } else {
            for (ChronicleDto.RetellingRef story : item.retellings) {
                TextView row = text(story.title == null || story.title.isEmpty() ? "—" : story.title);
                row.setTextColor(getResources().getColor(R.color.wiki_link, getTheme()));
                row.setOnClickListener(v -> Nav.openRetelling(this, campaignId, story.id));
                content.addView(row);
            }
        }

        TextView points = new TextView(this);
        points.setText(R.string.chronicle_points);
        points.setTextSize(18);
        points.setPadding(0, 18, 0, 6);
        content.addView(points);
        if (item.points.isEmpty()) {
            content.addView(text(R.string.chronicle_no_points));
        } else {
            for (ChronicleDto.Point point : item.points) {
                String who = point.wholeTeam ? getString(R.string.chronicle_team) : (point.heroName == null ? "—" : point.heroName);
                String row = point.mapName + " · " + who + " · " + point.order + (point.teleport ? " · " + getString(R.string.chronicle_teleport) : "");
                content.addView(text(row));
            }
        }
    }

    private TextView text(int string) {
        return text(getString(string));
    }

    private TextView text(String value) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setPadding(0, 4, 0, 4);
        return view;
    }

    private String formatDate(String value) {
        String[] parts = value.length() >= 10 ? value.substring(0, 10).split("-") : new String[0];
        return parts.length == 3 ? parts[2] + "." + parts[1] + "." + parts[0] : value;
    }
}
