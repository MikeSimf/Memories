package ru.dndmemory.ui.common;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import java.util.LinkedHashMap;
import java.util.Map;

import ru.dndmemory.R;

/**
 * Нижнее меню приложения: разделы кампании с иконками и мелкими подписями.
 *
 * <p>Панель строится один раз и потом только меняет подсветку — при переходах
 * между вкладками она не перерисовывается. Прокручивается по горизонтали:
 * первыми видны персонажи, локации и предметы, остальное — за краем экрана.</p>
 */
public class BottomNavView extends HorizontalScrollView {

    private final LinearLayout row;
    private final Map<String, Item> items = new LinkedHashMap<>();
    private String campaignId;
    private String active;
    private OnSectionClick listener;

    public BottomNavView(@NonNull Context context) {
        this(context, null);
    }

    public BottomNavView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setHorizontalScrollBarEnabled(false);
        setFillViewport(false);
        row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(4), dp(8), dp(4));
        addView(row);
        setBackgroundColor(ContextCompat.getColor(context, R.color.colorSurface));
        setElevation(dp(8));
        build();
    }

    private void build() {
        add("characters", R.string.tab_characters, R.drawable.ic_npc);
        add("locations", R.string.tab_locations, R.drawable.ic_location);
        add("items", R.string.tab_items, R.drawable.ic_item);
        add("heroes", R.string.heroes_title, R.drawable.ic_heroes);
        add("retellings", R.string.tab_retellings, R.drawable.ic_retelling);
        add("materials", R.string.materials_title, R.drawable.ic_materials);
        add("home", R.string.nav_home, R.drawable.ic_home);
    }

    private void add(@NonNull String key, @StringRes int label, @DrawableRes int icon) {
        LinearLayout item = new LinearLayout(getContext());
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER_HORIZONTAL);
        item.setPadding(dp(12), dp(4), dp(12), dp(4));
        item.setMinimumWidth(dp(76));

        ImageView image = new ImageView(getContext());
        image.setImageResource(icon);
        item.addView(image, new LinearLayout.LayoutParams(dp(24), dp(24)));

        TextView text = new TextView(getContext());
        text.setText(label);
        text.setTextSize(10);
        text.setGravity(Gravity.CENTER);
        text.setMaxLines(1);
        item.addView(text);

        item.setOnClickListener(v -> open(key));
        row.addView(item);
        items.put(key, new Item(image, text));
    }

    /** Перехват кликов: экран кампании переключает вкладки без пересоздания. */
    public interface OnSectionClick {
        boolean onSection(@NonNull String key);
    }

    public void setOnSectionClick(@Nullable OnSectionClick listener) {
        this.listener = listener;
    }

    /** Подключает меню к кампании и подсвечивает раздел. */
    public void bind(@NonNull String campaignId, @Nullable String active) {
        this.campaignId = campaignId;
        setActive(active);
    }

    /** Только подсветка: сама панель не пересоздаётся. */
    public void setActive(@Nullable String active) {
        this.active = active;
        for (Map.Entry<String, Item> entry : items.entrySet()) {
            boolean selected = entry.getKey().equals(active);
            int color = ContextCompat.getColor(getContext(),
                    selected ? R.color.colorPrimary : R.color.text_muted);
            entry.getValue().icon.setColorFilter(color);
            entry.getValue().label.setTextColor(color);
        }
    }

    private void open(@NonNull String key) {
        if (listener != null && listener.onSection(key)) {
            return;
        }
        if (campaignId == null) {
            return;
        }
        Context context = getContext();
        switch (key) {
            case "home":
                Nav.openCampaigns(context);
                return;
            case "heroes":
                Nav.openHeroes(context, campaignId);
                return;
            case "retellings":
                Nav.openRetellings(context, campaignId);
                return;
            case "materials":
                Nav.openMaterials(context, campaignId);
                return;
            case "members":
                Nav.openMembers(context, campaignId);
                return;
            default:
                Nav.openCampaignTab(context, campaignId, key);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class Item {
        final ImageView icon;
        final TextView label;

        Item(ImageView icon, TextView label) {
            this.icon = icon;
            this.label = label;
        }
    }
}
