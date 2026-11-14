package ru.dndmemory.ui.map;

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
import ru.dndmemory.data.remote.dto.MapDto;
import ru.dndmemory.ui.common.BaseCampaignActivity;
import ru.dndmemory.ui.common.Nav;
import ru.dndmemory.ui.common.ViewUtils;

/** Список интерактивных карт кампании (только просмотр). */
public class MapsActivity extends BaseCampaignActivity {

    private LinearLayout list;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!initCampaign()) return;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Toolbar toolbar = new Toolbar(this);
        toolbar.setTitle(R.string.maps_title);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(24, 16, 24, 16);
        root.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
        load();
    }

    private void load() {
        App.container(this).apiService().maps(campaignId).enqueue(new Callback<List<MapDto>>() {
            @Override
            public void onResponse(@NonNull Call<List<MapDto>> call, @NonNull Response<List<MapDto>> response) {
                runOnUiThread(() -> render(response.body()));
            }

            @Override
            public void onFailure(@NonNull Call<List<MapDto>> call, @NonNull Throwable t) {
                runOnUiThread(() -> ViewUtils.toast(MapsActivity.this, R.string.maps_load_failed));
            }
        });
    }

    private void render(@Nullable List<MapDto> maps) {
        list.removeAllViews();
        if (maps == null || maps.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.maps_empty);
            list.addView(empty);
            return;
        }
        for (MapDto map : maps) {
            TextView row = new TextView(this);
            row.setText(map.name + "\n" + getString(R.string.maps_markers_count, map.markerCount));
            row.setTextSize(17);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(8, 18, 8, 18);
            row.setBackgroundResource(android.R.drawable.list_selector_background);
            row.setOnClickListener(v -> Nav.openMap(this, campaignId, map.id));
            list.addView(row);
        }
    }
}
