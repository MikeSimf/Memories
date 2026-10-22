package ru.dndmemory.ui.common;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import ru.dndmemory.R;

/**
 * Экран внутри кампании с постоянным нижним меню.
 *
 * <p>Меню добавляется один раз поверх разметки экрана, а не в саму разметку:
 * поэтому оно есть и на списке, и в карточке объекта, и в редакторе, и не
 * зависит от того, какой экран открыт. Подсветка раздела задаётся
 * {@link #setActiveNav(String)}.</p>
 */
public abstract class BottomNavActivity extends AppCompatActivity {

    @Nullable
    private BottomNavView bottomNav;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void setContentView(@NonNull View view) {
        if (!(view instanceof ViewGroup)) {
            super.setContentView(view);
            return;
        }
        String campaignId = getIntent() == null ? null
                : getIntent().getStringExtra(Extras.CAMPAIGN_ID);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        view.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(view);

        bottomNav = new BottomNavView(this);
        root.addView(bottomNav, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        bottomNav.setVisibility(campaignId == null || campaignId.isEmpty()
                ? View.GONE : View.VISIBLE);
        if (campaignId != null && !campaignId.isEmpty()) {
            bottomNav.bind(campaignId, navActiveKey());
        }
        super.setContentView(root);
    }

    /** Какой раздел подсветить; экраны могут переопределить. */
    @NonNull
    protected String navActiveKey() {
        return "characters";
    }

    protected void setActiveNav(@Nullable String key) {
        if (bottomNav != null) {
            bottomNav.setActive(key);
        }
    }

    @Nullable
    protected BottomNavView bottomNav() {
        return bottomNav;
    }
}
