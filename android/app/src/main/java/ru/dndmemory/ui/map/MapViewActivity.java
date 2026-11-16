package ru.dndmemory.ui.map;

import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.CheckBox;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import ru.dndmemory.App;
import ru.dndmemory.R;
import ru.dndmemory.data.remote.dto.MapDto;
import ru.dndmemory.data.remote.dto.MapMarkerDto;
import ru.dndmemory.ui.common.BaseCampaignActivity;
import ru.dndmemory.ui.common.ImageLoader;
import ru.dndmemory.ui.common.Nav;
import ru.dndmemory.ui.common.ViewUtils;

/** Просмотр интерактивной карты: зум, перетаскивание и метки. */
public class MapViewActivity extends BaseCampaignActivity {

    private MapCanvasView canvas;
    private Toolbar toolbar;
    private String mapId;
    private List<MapMarkerDto> allMarkers = new ArrayList<>();
    private final java.util.Set<String> visibleTypes = new java.util.HashSet<>(java.util.Arrays.asList("CHARACTER", "HERO", "LOCATION", "ITEM"));
    private boolean showNames;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!initCampaign()) return;
        mapId = getIntent() == null ? null : getIntent().getStringExtra("mapId");
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        toolbar = new Toolbar(this);
        toolbar.setTitle(R.string.maps_title);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar);
        root.addView(buildFilters());
        canvas = new MapCanvasView(this, null);
        root.addView(canvas, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        canvas.setListener(marker -> com.google.android.material.snackbar.Snackbar
                .make(canvas, marker.name + " · " + typeLabel(marker.objectType),
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                .setAction("i", v -> Nav.openEntity(this, campaignId, marker.entityId))
                .show());
        load();
    }

    private String typeLabel(String type) {
        if ("LOCATION".equalsIgnoreCase(type)) return getString(R.string.tab_locations);
        if ("ITEM".equalsIgnoreCase(type)) return getString(R.string.tab_items);
        if ("HERO".equalsIgnoreCase(type)) return getString(R.string.tab_heroes);
        return getString(R.string.tab_characters);
    }

    private android.view.View buildFilters() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(16, 6, 16, 6);
        String[][] types = {{"CHARACTER", "Персонажи"}, {"HERO", "Герои"}, {"LOCATION", "Локации"}, {"ITEM", "Предметы"}};
        for (String[] type : types) {
            CheckBox box = new CheckBox(this);
            box.setText(type[1]);
            box.setChecked(true);
            box.setOnCheckedChangeListener((button, checked) -> {
                if (checked) visibleTypes.add(type[0]); else visibleTypes.remove(type[0]);
                applyFilters();
            });
            row.addView(box);
        }
        CheckBox names = new CheckBox(this);
        names.setText(R.string.maps_show_names);
        names.setOnCheckedChangeListener((button, checked) -> {
            showNames = checked;
            canvas.setShowNames(checked);
        });
        row.addView(names);
        scroll.addView(row);
        return scroll;
    }

    private void applyFilters() {
        List<MapMarkerDto> filtered = new ArrayList<>();
        for (MapMarkerDto marker : allMarkers) {
            if (marker.objectType != null && visibleTypes.contains(marker.objectType)) filtered.add(marker);
        }
        canvas.setMarkers(filtered);
    }

    private void load() {
        App.container(this).apiService().map(campaignId, mapId).enqueue(new Callback<MapDto>() {
            @Override
            public void onResponse(@NonNull Call<MapDto> call, @NonNull Response<MapDto> response) {
                MapDto map = response.body();
                if (map == null) {
                    runOnUiThread(() -> ViewUtils.toast(MapViewActivity.this, R.string.maps_load_failed));
                    return;
                }
                runOnUiThread(() -> {
                    toolbar.setTitle(map.name);
                    allMarkers = map.markers == null ? new ArrayList<>() : map.markers;
                    applyFilters();
                    canvas.setShowNames(showNames);
                    if (map.imageFileId != null) {
                        ImageLoader.loadFull(App.container(MapViewActivity.this).okHttpClient(),
                                map.imageFileId, bitmap -> runOnUiThread(() -> canvas.setBitmap(bitmap)));
                    }
                });
            }

            @Override
            public void onFailure(@NonNull Call<MapDto> call, @NonNull Throwable t) {
                runOnUiThread(() -> ViewUtils.toast(MapViewActivity.this, R.string.maps_load_failed));
            }
        });
    }
}
