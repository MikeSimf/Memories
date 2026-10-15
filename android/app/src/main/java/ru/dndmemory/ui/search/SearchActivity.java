package ru.dndmemory.ui.search;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;

import ru.dndmemory.R;
import ru.dndmemory.databinding.ActivitySearchBinding;
import ru.dndmemory.ui.common.Extras;

/** Общий поиск по кампании (открывается из шапки) или поиск внутри вкладки. */
public class SearchActivity extends AppCompatActivity {

    private ActivitySearchBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySearchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String campaignId = getIntent() == null ? null : getIntent().getStringExtra(Extras.CAMPAIGN_ID);
        String typeFilter = getIntent() == null ? null : getIntent().getStringExtra(Extras.SEARCH_TYPE);
        if (campaignId == null) {
            finish();
            return;
        }

        setSupportActionBar(binding.searchToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        if (savedInstanceState == null) {
            SearchFragment fragment = SearchFragment.newInstance(campaignId, typeFilter);
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.search_container, fragment);
            transaction.commit();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
