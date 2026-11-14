package ru.dndmemory.ui.map;

import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import ru.dndmemory.App;
import ru.dndmemory.R;
import ru.dndmemory.data.remote.dto.MapDto;
import ru.dndmemory.ui.common.BaseCampaignActivity;
import ru.dndmemory.ui.common.ImageLoader;
import ru.dndmemory.ui.common.Nav;
import ru.dndmemory.ui.common.ViewUtils;

/** Просмотр интерактивной карты: зум, перетаскивание и метки. */
public class MapViewActivity extends BaseCampaignActivity {

    private MapCanvasView canvas;
    private Toolbar toolbar;
    private String mapId;

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
        canvas = new MapCanvasView(this, null);
        root.addView(canvas, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        canvas.setListener(marker -> {
            Toast.makeText(this, marker.name, Toast.LENGTH_SHORT).show();
            Nav.openEntity(this, campaignId, marker.entityId);
        });
        load();
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
                    canvas.setMarkers(map.markers);
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
