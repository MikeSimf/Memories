package ru.dndmemory.ui.chronicle;

import android.os.Bundle;
import android.view.Gravity;
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
import ru.dndmemory.data.remote.dto.ChronicleDto;
import ru.dndmemory.ui.common.BaseCampaignActivity;
import ru.dndmemory.ui.common.Nav;
import ru.dndmemory.ui.common.ViewUtils;

/** Список хроник кампании. */
public class ChroniclesActivity extends BaseCampaignActivity {
    private LinearLayout list;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!initCampaign()) return;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Toolbar toolbar = new Toolbar(this);
        toolbar.setTitle(R.string.chronicles_title);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(24, 12, 24, 24);
        root.addView(list);
        setContentView(root);
        load();
    }

    private void load() {
        App.container(this).apiService().chronicles(campaignId).enqueue(new Callback<List<ChronicleDto>>() {
            @Override public void onResponse(@NonNull Call<List<ChronicleDto>> call, @NonNull Response<List<ChronicleDto>> response) {
                runOnUiThread(() -> render(response.body()));
            }
            @Override public void onFailure(@NonNull Call<List<ChronicleDto>> call, @NonNull Throwable t) {
                runOnUiThread(() -> ViewUtils.toast(ChroniclesActivity.this, R.string.maps_load_failed));
            }
        });
    }

    private void render(@Nullable List<ChronicleDto> items) {
        list.removeAllViews();
        if (items == null || items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.chronicles_empty);
            list.addView(empty);
            return;
        }
        for (ChronicleDto item : items) {
            com.google.android.material.card.MaterialCardView card = new com.google.android.material.card.MaterialCardView(this);
            card.setRadius(12 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = (int) (10 * getResources().getDisplayMetrics().density);
            card.setLayoutParams(params);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(24, 18, 24, 18);
            TextView title = new TextView(this);
            title.setText(item.name + "\n" + (item.date == null ? "" : date(item.date))
                    + "   📖 " + item.retellings.size() + "   📍 " + item.points.size());
            title.setTextSize(16);
            row.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            TextView open = new TextView(this);
            open.setText(R.string.chronicle_open);
            open.setTextColor(getResources().getColor(R.color.wiki_link, getTheme()));
            open.setPadding(24, 12, 0, 12);
            open.setOnClickListener(v -> Nav.openChronicle(this, campaignId, item.id));
            row.addView(open);
            card.addView(row);
            card.setOnClickListener(v -> Nav.openChronicle(this, campaignId, item.id));
            list.addView(card);
        }
    }

    private String date(String value) {
        String[] parts = value.length() >= 10 ? value.substring(0, 10).split("-") : new String[0];
        return parts.length == 3 ? parts[2] + "." + parts[1] + "." + parts[0] : value;
    }
}
