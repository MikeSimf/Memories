package ru.dndmemory.ui.entity;

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

import java.util.List;

import ru.dndmemory.App;
import ru.dndmemory.AppContainer;
import ru.dndmemory.R;
import ru.dndmemory.data.local.entity.CampaignRoom;
import ru.dndmemory.data.local.entity.EntityRoom;
import ru.dndmemory.databinding.FragmentEntityListBinding;
import ru.dndmemory.ui.campaign.CampaignViewModel;
import ru.dndmemory.ui.common.CampaignHost;
import ru.dndmemory.ui.common.ErrorMessages;
import ru.dndmemory.ui.common.Nav;
import ru.dndmemory.ui.common.Rights;
import ru.dndmemory.ui.common.ViewModelFactory;
import ru.dndmemory.ui.common.ViewUtils;
import ru.dndmemory.util.AppExecutors;

/**
 * Вкладка объектов. Список грузится по {@code types=HERO,NPC} (или одного вида),
 * вверху — поле быстрого фильтра: оно фильтрует прямо во время набора с небольшой
 * задержкой, поэтому длинный список не подтормаживает.
 *
 * <p>Действия «Поиск», «Пересказы», «Участники», «Приглашения» и «Обновить»
 * живут в меню {@link ru.dndmemory.ui.campaign.CampaignActivity}.
 */
public class EntityListFragment extends Fragment implements EntityAdapter.Listener {

    private static final String ARG_TYPES = "argTypes";
    /** Задержка фильтра: достаточно, чтобы не считать на каждую букву. */
    private static final long FILTER_DELAY_MS = 200L;

    private FragmentEntityListBinding binding;
    private EntityListViewModel viewModel;
    private EntityAdapter adapter;
    private CampaignViewModel campaignViewModel;
    private String types = "";

    private final Handler filterHandler = new Handler(Looper.getMainLooper());
    private final Runnable filterTask = () -> viewModel.setQuery(text(binding.entityListFilter.getText()));

    /** @param typesCsv список видов через запятую: {@code HERO,NPC}, {@code LOCATION}, {@code ITEM}. */
    public static EntityListFragment newInstance(@NonNull String typesCsv) {
        EntityListFragment fragment = new EntityListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TYPES, typesCsv);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentEntityListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();
        if (args != null && args.getString(ARG_TYPES) != null) {
            types = args.getString(ARG_TYPES);
        }

        CampaignHost host = (CampaignHost) requireActivity();
        campaignViewModel = host.campaignViewModel();
        String campaignId = host.campaignId();

        AppContainer container = App.container(requireContext());
        viewModel = new ViewModelProvider(this, ViewModelFactory.of(
                () -> new EntityListViewModel(container.entityRepository(),
                        container.syncScheduler(), AppExecutors.get())))
                .get(EntityListViewModel.class);
        viewModel.init(campaignId, types);

        adapter = new EntityAdapter(this);
        binding.entityListRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.entityListRecycler.setAdapter(adapter);

        setupFilter();

        binding.entityListSwipe.setOnRefreshListener(() -> {
            viewModel.refresh();
            viewModel.syncNow();
        });
        binding.entityListFab.setOnClickListener(v -> {
            CampaignRoom campaign = campaignViewModel.campaign().getValue();
            if (!Rights.canCreateEntity(campaign)) {
                ViewUtils.toast(requireContext(), R.string.error_forbidden);
                return;
            }
            Nav.createEntity(requireContext(), campaignId, viewModel.createType());
        });

        observe();
    }

    /** Быстрый фильтр: задержка 200 мс, очистка отдельной кнопкой. */
    private void setupFilter() {
        binding.entityListFilter.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                filterHandler.removeCallbacks(filterTask);
                filterHandler.postDelayed(filterTask, FILTER_DELAY_MS);
            }
        });
        binding.entityListFilterClear.setOnClickListener(v -> {
            binding.entityListFilter.setText("");
            binding.entityListFilter.clearFocus();
            viewModel.setQuery("");
        });
    }

    private void observe() {
        viewModel.items().observe(getViewLifecycleOwner(), items -> {
            adapter.submitList(items);
            boolean empty = items == null || items.isEmpty();
            boolean filtering = !text(binding.entityListFilter.getText()).isEmpty();
            Integer total = viewModel.total().getValue();
            boolean tabHasItems = total != null && total > 0;

            // Пустое состояние разное: «здесь пусто» и «фильтр ничего не нашёл».
            binding.entityListEmpty.setText(filtering && tabHasItems
                    ? R.string.entity_filter_no_results : R.string.entity_list_empty);
            ViewUtils.show(binding.entityListEmpty, empty);
            ViewUtils.show(binding.entityListRecycler, !empty);

            ViewUtils.show(binding.entityListFilterBar, filtering);
            if (filtering) {
                binding.entityListFilterCount.setText(
                        getString(R.string.entity_filter_found, count(items)));
            }
        });

        viewModel.refreshState().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) {
                return;
            }
            binding.entityListSwipe.setRefreshing(resource.isLoading());
            ViewUtils.show(binding.entityListProgress, resource.isLoading());
            if (resource.isError()) {
                ErrorMessages.show(binding.getRoot(), resource.error);
                viewModel.clearRefreshState();
            }
        });

        campaignViewModel.campaign().observe(getViewLifecycleOwner(), campaign ->
                ViewUtils.show(binding.entityListFab, Rights.canCreateEntity(campaign)));
    }

    private static int count(@Nullable List<EntityRoom> items) {
        return items == null ? 0 : items.size();
    }

    @Override
    public void onEntityClick(@NonNull EntityRoom entity) {
        Nav.openEntity(requireContext(), viewModel.campaignId(), entity.id);
    }

    @Override
    public void onDestroyView() {
        filterHandler.removeCallbacks(filterTask);
        super.onDestroyView();
        binding = null;
    }

    private static String text(@Nullable CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }
}
