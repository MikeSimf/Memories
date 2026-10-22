package ru.dndmemory.ui.entity;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ru.dndmemory.R;
import ru.dndmemory.data.model.EntityType;
import ru.dndmemory.databinding.ActivitySectionListBinding;
import ru.dndmemory.ui.common.BaseCampaignActivity;

/**
 * Отдельный экран «Герои»: раньше герои жили на одной вкладке с НПС, хотя
 * заводят их только мастера. Список и права те же — {@link EntityListFragment}.
 */
public class HeroListActivity extends BaseCampaignActivity {

    private ActivitySectionListBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!initCampaign()) {
            return;
        }
        binding = ActivitySectionListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.sectionToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.heroes_title);
        }
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.section_container,
                        EntityListFragment.newInstance(EntityType.HERO.name()))
                .commit();
        viewModel.refresh();
    }

    @NonNull
    @Override
    protected String navActiveKey() {
        return "heroes";
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
