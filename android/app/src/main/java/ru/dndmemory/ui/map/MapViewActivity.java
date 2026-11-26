package ru.dndmemory.ui.map;

import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.CheckBox;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.SeekBar;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.slider.RangeSlider;
import android.widget.Toast;

import ru.dndmemory.data.remote.dto.ChronicleDto;

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
    private final List<ChronicleDto> chronicles = new ArrayList<>();
    private LinearLayout chroniclePanel;
    private ChipGroup chronicleChecks;
    private TextView chronicleLabel;
    private RangeSlider rangeSlider;
    private int rangeStart;
    private int rangeEnd;
    private boolean showTeam = true;
    private final java.util.Set<String> selectedHeroes = new java.util.HashSet<>();

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
        chroniclePanel = buildChroniclePanel();
        root.addView(chroniclePanel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
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

    private LinearLayout buildChroniclePanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(16, 6, 16, 8);
        panel.setVisibility(android.view.View.GONE);
        chronicleLabel = new TextView(this);
        chronicleLabel.setTextSize(13);
        panel.addView(chronicleLabel);
        rangeSlider = new RangeSlider(this);
        rangeSlider.setValueFrom(0f);
        rangeSlider.setValueTo(1f);
        rangeSlider.setValues(0f, 1f);
        rangeSlider.addOnChangeListener((slider, value, fromUser) -> {
            java.util.List<Float> values = slider.getValues();
            if (values.size() < 2) return;
            rangeStart = Math.round(values.get(0));
            rangeEnd = Math.round(values.get(1));
            updateChronicleLabel();
            rebuildRoutes();
        });
        panel.addView(rangeSlider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        chronicleChecks = new ChipGroup(this);
        chronicleChecks.setSingleLine(false);
        chronicleChecks.setChipSpacingHorizontal((int) (6 * getResources().getDisplayMetrics().density));
        chronicleChecks.setChipSpacingVertical((int) (4 * getResources().getDisplayMetrics().density));
        panel.addView(chronicleChecks, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return panel;
    }

    private void applyChronicles(@NonNull List<ChronicleDto> items) {
        chronicles.clear();
        chronicles.addAll(items);
        chronicleChecks.removeAllViews();
        selectedHeroes.clear();
        boolean hasPoints = false;
        java.util.Map<String, String[]> heroes = new java.util.LinkedHashMap<>();
        for (ChronicleDto chronicle : chronicles) {
            for (ChronicleDto.Point point : chronicle.points) {
                if (!mapId.equals(point.mapId)) continue;
                hasPoints = true;
                if (!point.wholeTeam && point.heroId != null) heroes.put(point.heroId, new String[]{point.heroName, point.heroColor});
            }
        }
        chroniclePanel.setVisibility(hasPoints ? android.view.View.VISIBLE : android.view.View.GONE);
        if (!hasPoints) { canvas.setRoutes(new ArrayList<>()); return; }
        rangeStart = 0;
        rangeEnd = Math.max(0, chronicles.size() - 1);
        rangeSlider.setValueFrom(0f);
        rangeSlider.setValueTo(Math.max(1, chronicles.size() - 1));
        if (chronicles.size() > 1) rangeSlider.setValues((float) rangeStart, (float) rangeEnd);
        else rangeSlider.setValues(0f, 1f);
        Chip team = new Chip(this);
        team.setText(R.string.chronicle_team);
        team.setCheckable(true);
        team.setChecked(true);
        team.setOnCheckedChangeListener((button, checked) -> { showTeam = checked; rebuildRoutes(); });
        chronicleChecks.addView(team);
        for (java.util.Map.Entry<String, String[]> entry : heroes.entrySet()) {
            Chip chip = new Chip(this);
            chip.setText(entry.getValue()[0] == null ? "герой" : entry.getValue()[0]);
            chip.setCheckable(true);
            chip.setOnCheckedChangeListener((button, checked) -> {
                if (checked) selectedHeroes.add(entry.getKey()); else selectedHeroes.remove(entry.getKey());
                rebuildRoutes();
            });
            chronicleChecks.addView(chip);
        }
        updateChronicleLabel();
        rebuildRoutes();
    }

    private void updateChronicleLabel() {
        if (chronicles.isEmpty()) { chronicleLabel.setText(""); return; }
        String first = chronicles.get(Math.max(0, Math.min(rangeStart, chronicles.size() - 1))).name;
        String last = chronicles.get(Math.max(0, Math.min(rangeEnd, chronicles.size() - 1))).name;
        chronicleLabel.setText(first.equals(last) ? first : first + " → " + last);
    }

    private void rebuildRoutes() {
        List<MapCanvasView.Route> routes = new ArrayList<>();
        for (int index = rangeStart; index <= rangeEnd && index < chronicles.size(); index++) {
            ChronicleDto chronicle = chronicles.get(index);
            List<ChronicleDto.Point> points = new ArrayList<>();
            for (ChronicleDto.Point point : chronicle.points) if (mapId.equals(point.mapId)) points.add(point);
            points.sort((a, b) -> Integer.compare(a.order, b.order));
            if (showTeam) {
                List<float[]> line = new ArrayList<>();
                for (ChronicleDto.Point point : points) if (point.wholeTeam && !point.teleport) line.add(new float[]{(float) point.x, (float) point.y});
                if (line.size() > 1) routes.add(new MapCanvasView.Route(line, teamColor(), 8));
            }
            for (String heroId : selectedHeroes) {
                List<float[]> line = new ArrayList<>();
                String color = null;
                for (ChronicleDto.Point point : points) {
                    if (point.wholeTeam || heroId.equals(point.heroId)) {
                        if (!point.teleport) line.add(new float[]{(float) point.x, (float) point.y});
                        if (heroId.equals(point.heroId)) color = point.heroColor;
                    }
                }
                if (line.size() > 1) routes.add(new MapCanvasView.Route(line, parseColor(color, 0xFF2E7D32), 4));
            }
        }
        canvas.setRoutes(routes);
    }

    private int teamColor() {
        // Цвет команды задаётся в настройках кампании; на Android пока используем базовый.
        return 0xFF8A4B2A;
    }

    private int parseColor(String value, int fallback) {
        try { return value == null ? fallback : android.graphics.Color.parseColor(value); } catch (Exception ignored) { return fallback; }
    }

    private void applyFilters() {
        List<MapMarkerDto> filtered = new ArrayList<>();
        for (MapMarkerDto marker : allMarkers) {
            if (marker.objectType != null && visibleTypes.contains(marker.objectType)) filtered.add(marker);
        }
        canvas.setMarkers(filtered);
    }

    private void loadChronicles() {
        App.container(this).apiService().chronicles(campaignId).enqueue(new Callback<List<ChronicleDto>>() {
            @Override public void onResponse(@NonNull Call<List<ChronicleDto>> call, @NonNull Response<List<ChronicleDto>> response) {
                List<ChronicleDto> body = response.body();
                if (body != null) runOnUiThread(() -> applyChronicles(body));
            }
            @Override public void onFailure(@NonNull Call<List<ChronicleDto>> call, @NonNull Throwable t) { }
        });
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
                    loadChronicles();
                    if (map.imageFileId != null) {
                        android.graphics.Bitmap cached = MapImageCache.load(MapViewActivity.this, map.imageFileId);
                        if (cached != null) {
                            canvas.setBitmap(cached);
                        } else {
                            ImageLoader.loadFull(App.container(MapViewActivity.this).okHttpClient(),
                                    map.imageFileId, bitmap -> {
                                        MapImageCache.save(MapViewActivity.this, map.imageFileId, bitmap);
                                        runOnUiThread(() -> canvas.setBitmap(bitmap));
                                    });
                        }
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
