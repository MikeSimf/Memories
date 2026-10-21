package ru.dndmemory.ui.material;

import android.os.Bundle;

import androidx.annotation.Nullable;

import ru.dndmemory.R;
import ru.dndmemory.databinding.ActivitySectionListBinding;
import ru.dndmemory.ui.common.BaseCampaignActivity;

/**
 * Отдельный экран «Материалы»: раньше это была четвёртая вкладка кампании.
 * Список и загрузка файлов те же — {@link MaterialsFragment}.
 */
public class MaterialsActivity extends BaseCampaignActivity {

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
            getSupportActionBar().setTitle(R.string.materials_title);
        }
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.section_container, new MaterialsFragment())
                .commit();
        viewModel.refresh();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
