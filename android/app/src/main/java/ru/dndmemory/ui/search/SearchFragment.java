package ru.dndmemory.ui.search;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import ru.dndmemory.App;
import ru.dndmemory.AppContainer;
import ru.dndmemory.R;
import ru.dndmemory.data.model.EntityType;
import ru.dndmemory.databinding.FragmentSearchBinding;
import ru.dndmemory.ui.common.Labels;
import ru.dndmemory.ui.common.Nav;
import ru.dndmemory.ui.common.ViewModelFactory;
import ru.dndmemory.ui.common.ViewUtils;

/**
 * Поиск по кампании: общий (из шапки) или внутри вкладки — тогда заранее выбран
 * её класс. Локальная база отвечает сразу, серверный поиск дополняет выдачу,
 * когда есть сеть; запрос к серверу отправляется с небольшой задержкой.
 */
public class SearchFragment extends Fragment implements SearchAdapter.Listener {

    private static final String ARG_CAMPAIGN_ID = "argCampaignId";
    private static final String ARG_KIND = "argKind";
    private static final String FILTER_ALL = "";
    private static final String FILTER_RETELLINGS = EntityType.RETELLING;
    /** Задержка перед запросом к серверу: не дёргаем его на каждую букву. */
    private static final long SEARCH_DELAY_MS = 250L;

    private FragmentSearchBinding binding;
    private SearchViewModel viewModel;
    private SearchAdapter adapter;
    private String campaignId;
    private String initialFilter;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private final Runnable searchTask =
            () -> viewModel.setQuery(text(binding.searchInput.getText()));

    public static SearchFragment newInstance(@NonNull String campaignId, @Nullable String kindFilter) {
        SearchFragment fragment = new SearchFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CAMPAIGN_ID, campaignId);
        if (kindFilter != null) {
            args.putString(ARG_KIND, kindFilter);
        }
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSearchBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = requireArguments();
        campaignId = args.getString(ARG_CAMPAIGN_ID);
        initialFilter = args.getString(ARG_KIND);

        AppContainer container = App.container(requireContext());
        viewModel = new ViewModelProvider(this, ViewModelFactory.of(
                () -> new SearchViewModel(container.entityRepository(),
                        container.retellingRepository())))
                .get(SearchViewModel.class);
        viewModel.init(campaignId, initialFilter);

        adapter = new SearchAdapter(this);
        binding.searchRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.searchRecycler.setAdapter(adapter);

        buildChips();

        binding.searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                searchHandler.removeCallbacks(searchTask);
                searchHandler.postDelayed(searchTask, SEARCH_DELAY_MS);
            }
        });

        observe();
    }

    private void buildChips() {
        ChipGroup group = binding.searchChips;
        group.removeAllViews();
        addChip(group, getString(R.string.search_all), FILTER_ALL,
                initialFilter == null || initialFilter.isEmpty());
        // Та же группировка, что во вкладках: герои и персонажи ищутся вместе.
        addChip(group, getString(R.string.tab_characters), EntityType.CHARACTERS,
                EntityType.CHARACTERS.equalsIgnoreCase(initialFilter));
        for (EntityType kind : EntityType.ALL) {
            String value = kind.name();
            addChip(group, getString(Labels.entityType(kind)), value, value.equals(initialFilter));
        }
        addChip(group, getString(R.string.tab_retellings), FILTER_RETELLINGS,
                FILTER_RETELLINGS.equals(initialFilter));
    }

    private void addChip(@NonNull ChipGroup group, @NonNull String label,
                         @NonNull final String value, boolean checked) {
        Chip chip = new Chip(requireContext());
        chip.setText(label);
        chip.setCheckable(true);
        chip.setClickable(true);
        chip.setChecked(checked);
        chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                viewModel.setKindFilter(value);
            }
        });
        group.addView(chip);
    }

    private void observe() {
        viewModel.results().observe(getViewLifecycleOwner(), items -> {
            adapter.submitList(items);
            ViewUtils.show(binding.searchEmpty, items == null || items.isEmpty());
        });

        viewModel.loading().observe(getViewLifecycleOwner(), loading ->
                ViewUtils.show(binding.searchProgress, Boolean.TRUE.equals(loading)));
    }

    @Override
    public void onSearchItemClick(@NonNull SearchAdapter.Item item) {
        if (item.id == null) {
            return;
        }
        if (SearchAdapter.KIND_RETELLING.equals(item.kind)) {
            Nav.openRetelling(requireContext(), campaignId, item.id);
        } else {
            Nav.openEntity(requireContext(), campaignId, item.id);
        }
    }

    @Override
    public void onDestroyView() {
        searchHandler.removeCallbacks(searchTask);
        super.onDestroyView();
        binding = null;
    }

    private static String text(@Nullable CharSequence value) {
        return value == null ? "" : value.toString();
    }
}
