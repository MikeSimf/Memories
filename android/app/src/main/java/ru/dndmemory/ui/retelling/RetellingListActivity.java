package ru.dndmemory.ui.retelling;

import android.os.Bundle;

import androidx.annotation.Nullable;

import ru.dndmemory.R;
import ru.dndmemory.databinding.ActivityRetellingListBinding;
import ru.dndmemory.ui.common.BaseCampaignActivity;

/**
 * Отдельный экран «Пересказы»: раньше это была вкладка кампании, теперь пункт
 * меню в шапке (docs/api.md, раздел 7). Функциональность списка та же —
 * {@link RetellingListFragment} внутри.
 */
public class RetellingListActivity extends BaseCampaignActivity {

    private ActivityRetellingListBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Кампанию и ViewModel готовим до setContentView: фрагмент из разметки
        // создаётся при её раздувании и берёт их у Activity.
        if (!initCampaign()) {
            return;
        }
        binding = ActivityRetellingListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.retellingsToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        viewModel.refresh();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
