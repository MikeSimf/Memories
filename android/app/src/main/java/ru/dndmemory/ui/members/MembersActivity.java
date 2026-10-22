package ru.dndmemory.ui.members;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ru.dndmemory.databinding.ActivityMembersBinding;
import ru.dndmemory.ui.common.BaseCampaignActivity;

/**
 * Отдельный экран «Участники»: раньше это была вкладка кампании, теперь пункт
 * меню в шапке (docs/roles.md). Список и права те же — {@link MembersFragment}.
 */
public class MembersActivity extends BaseCampaignActivity {

    private ActivityMembersBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Кампанию и ViewModel готовим до setContentView: фрагмент из разметки
        // создаётся при её раздувании и берёт их у Activity.
        if (!initCampaign()) {
            return;
        }
        binding = ActivityMembersBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.membersToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        viewModel.refresh();
    }

    @NonNull
    @Override
    protected String navActiveKey() {
        return "members";
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
