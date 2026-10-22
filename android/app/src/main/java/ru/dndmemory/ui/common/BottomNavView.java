package ru.dndmemory.ui.common;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import ru.dndmemory.R;

/**
 * Нижняя панель разделов: три рабочих вкладки и остальные разделы кампании.
 *
 * <p>Панель прокручивается по горизонтали: первыми видны персонажи, локации
 * и предметы, остальное — за краем экрана. Одна и та же панель стоит на экране
 * кампании, в карточке и в редакторе объекта, поэтому из глубины ссылок можно
 * вернуться одним нажатием.</p>
 */
public class BottomNavView extends HorizontalScrollView {

    private static final int TEXT_MUTED = R.color.text_muted;
    private static final int TEXT_ACTIVE = R.color.colorPrimary;

    private LinearLayout row;
    private String campaignId;
    private String active;

    public BottomNavView(@NonNull Context context) {
        super(context);
        init();
    }

    public BottomNavView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setHorizontalScrollBarEnabled(false);
        setFillViewport(false);
        row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(4), dp(8), dp(4));
        addView(row);
        setBackgroundColor(ContextCompat.getColor(getContext(), R.color.colorSurface));
        setElevation(dp(8));
    }

    /** @param active ключ текущего раздела: characters, locations, items, heroes… */
    public void bind(@NonNull String campaignId, @Nullable String active) {
        this.campaignId = campaignId;
        this.active = active;
        rebuild();
    }

    private void rebuild() {
        row.removeAllViews();
        addItem("characters", R.string.tab_characters, R.drawable.ic_npc);
        addItem("locations", R.string.tab_locations, R.drawable.ic_location);
        addItem("items", R.string.tab_items, R.drawable.ic_item);
        addItem("heroes", R.string.heroes_title, R.drawable.ic_heroes);
        addItem("retellings", R.string.tab_retellings, R.drawable.ic_retelling);
        addItem("materials", R.string.materials_title, R.drawable.ic_materials);
        addItem("members", R.string.tab_members, R.drawable.ic_members);
        addItem("home", R.string.nav_home, R.drawable.ic_home);
    }

    private void addItem(@NonNull String key, @StringRes int label, @DrawableRes int icon) {
        LinearLayout item = new LinearLayout(getContext());
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER_HORIZONTAL);
        item.setPadding(dp(12), dp(4), dp(12), dp(4));
        item.setMinimumWidth(dp(76));
        boolean selected = key.equals(active);

        ImageView image = new ImageView(getContext());
        image.setImageResource(icon);
        image.setColorFilter(ContextCompat.getColor(getContext(),
                selected ? TEXT_ACTIVE : TEXT_MUTED));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(24), dp(24));
        item.addView(image, iconParams);

        TextView text = new TextView(getContext());
        text.setText(label);
        text.setTextSize(10);
        text.setGravity(Gravity.CENTER);
        text.setMaxLines(1);
        text.setTextColor(ContextCompat.getColor(getContext(),
                selected ? TEXT_ACTIVE : TEXT_MUTED));
        item.addView(text);

        item.setOnClickListener(v -> open(key));
        row.addView(item);
    }

    private void open(@NonNull String key) {
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
}
