package ru.dndmemory.ui.entity;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import ru.dndmemory.App;
import ru.dndmemory.AppContainer;
import ru.dndmemory.R;
import ru.dndmemory.data.local.entity.CampaignRoom;
import ru.dndmemory.data.local.entity.EntityRoom;
import ru.dndmemory.data.model.EntityType;
import ru.dndmemory.databinding.FragmentEntityListBinding;
import ru.dndmemory.ui.campaign.CampaignViewModel;
import ru.dndmemory.ui.common.CampaignHost;
import ru.dndmemory.ui.common.ErrorMessages;
import ru.dndmemory.ui.common.Nav;
import ru.dndmemory.ui.common.Rights;
import ru.dndmemory.ui.common.ViewModelFactory;
import ru.dndmemory.ui.common.ViewUtils;

/**
 * Вкладка с объектами одного типа. Действия «Поиск» (с фильтром по вкладке) и
 * «Обновить» живут в меню {@link ru.dndmemory.ui.campaign.CampaignActivity}.
 */
public class EntityListFragment extends Fragment implements EntityAdapter.Listener {

    private static final String ARG_TYPE = "argType";

    private FragmentEntityListBinding binding;
    private EntityListViewModel viewModel;
    private EntityAdapter adapter;
    private CampaignViewModel campaignViewModel;
    private EntityType type = EntityType.NPC;

    public static EntityListFragment newInstance(@NonNull EntityType type) {
        EntityListFragment fragment = new EntityListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TYPE, type.name());
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
        if (args != null) {
            EntityType parsed = EntityType.from(args.getString(ARG_TYPE));
            if (parsed != null) {
                type = parsed;
            }
        }

        CampaignHost host = (CampaignHost) requireActivity();
        campaignViewModel = host.campaignViewModel();
        String campaignId = host.campaignId();

        AppContainer container = App.container(requireContext());
        viewModel = new ViewModelProvider(this, ViewModelFactory.of(
                () -> new EntityListViewModel(container.entityRepository(),
                        container.syncScheduler())))
                .get(EntityListViewModel.class);
        viewModel.init(campaignId, type);

        adapter = new EntityAdapter(this);
        binding.entityListRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.entityListRecycler.setAdapter(adapter);

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
            Nav.createEntity(requireContext(), campaignId, type.name());
        });

        observe();
    }

    private void observe() {
        viewModel.items().observe(getViewLifecycleOwner(), items -> {
            adapter.submitList(items);
            boolean empty = items == null || items.isEmpty();
            ViewUtils.show(binding.entityListEmpty, empty);
            ViewUtils.show(binding.entityListRecycler, !empty);
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

    @Override
    public void onEntityClick(@NonNull EntityRoom entity) {
        Nav.openEntity(requireContext(), viewModel.campaignId(), entity.id);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
