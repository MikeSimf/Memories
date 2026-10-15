package ru.dndmemory.ui.retelling;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import ru.dndmemory.App;
import ru.dndmemory.AppContainer;
import ru.dndmemory.R;
import ru.dndmemory.data.local.entity.CampaignRoom;
import ru.dndmemory.data.local.entity.RetellingDraftRoom;
import ru.dndmemory.data.local.entity.RetellingRoom;
import ru.dndmemory.databinding.FragmentRetellingListBinding;
import ru.dndmemory.ui.campaign.CampaignViewModel;
import ru.dndmemory.ui.common.CampaignHost;
import ru.dndmemory.ui.common.ErrorMessages;
import ru.dndmemory.ui.common.Nav;
import ru.dndmemory.ui.common.Rights;
import ru.dndmemory.ui.common.ViewModelFactory;
import ru.dndmemory.ui.common.ViewUtils;

/** Вкладка «Пересказы»: черновики сверху, опубликованные ниже. */
public class RetellingListFragment extends Fragment
        implements RetellingAdapter.Listener, RetellingAdapter.DraftAdapter.Listener, MenuProvider {

    private FragmentRetellingListBinding binding;
    private RetellingListViewModel viewModel;
    private RetellingAdapter adapter;
    private RetellingAdapter.DraftAdapter draftAdapter;
    private CampaignViewModel campaignViewModel;

    public static RetellingListFragment newInstance() {
        return new RetellingListFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentRetellingListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        CampaignHost host = (CampaignHost) requireActivity();
        campaignViewModel = host.campaignViewModel();
        String campaignId = host.campaignId();

        AppContainer container = App.container(requireContext());
        viewModel = new ViewModelProvider(this, ViewModelFactory.of(
                () -> new RetellingListViewModel(container.retellingRepository(),
                        container.syncScheduler())))
                .get(RetellingListViewModel.class);
        viewModel.init(campaignId);

        adapter = new RetellingAdapter(this);
        draftAdapter = new RetellingAdapter.DraftAdapter(this);
        binding.retellingListRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.retellingListRecycler.setAdapter(adapter);
        binding.retellingListDrafts.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.retellingListDrafts.setAdapter(draftAdapter);

        binding.retellingListSwipe.setOnRefreshListener(() -> {
            viewModel.refresh();
            viewModel.syncNow();
        });
        binding.retellingListFab.setOnClickListener(v -> {
            CampaignRoom campaign = campaignViewModel.campaign().getValue();
            if (!Rights.canCreateRetelling(campaign)) {
                ViewUtils.toast(requireContext(), R.string.error_forbidden);
                return;
            }
            Nav.createRetelling(requireContext(), campaignId);
        });

        requireActivity().addMenuProvider(this, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
        observe();
    }

    private void observe() {
        viewModel.published().observe(getViewLifecycleOwner(), items -> {
            adapter.submitList(items);
            boolean empty = items == null || items.isEmpty();
            ViewUtils.show(binding.retellingListEmpty, empty);
        });

        viewModel.drafts().observe(getViewLifecycleOwner(), items -> {
            draftAdapter.submitList(items);
            boolean hasDrafts = items != null && !items.isEmpty();
            ViewUtils.show(binding.retellingListDraftsTitle, hasDrafts);
            ViewUtils.show(binding.retellingListDrafts, hasDrafts);
        });

        viewModel.refreshState().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) {
                return;
            }
            binding.retellingListSwipe.setRefreshing(resource.isLoading());
            ViewUtils.show(binding.retellingListProgress, resource.isLoading());
            if (resource.isError()) {
                ErrorMessages.show(binding.getRoot(), resource.error);
                viewModel.clearRefreshState();
            }
        });

        campaignViewModel.campaign().observe(getViewLifecycleOwner(), campaign ->
                ViewUtils.show(binding.retellingListFab, Rights.canCreateRetelling(campaign)));
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_retelling_list, menu);
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_search) {
            Nav.openSearch(requireContext(), viewModel.campaignId(), "RETELLING");
            return true;
        }
        return false;
    }

    @Override
    public void onRetellingClick(@NonNull RetellingRoom retelling) {
        Nav.openRetelling(requireContext(), viewModel.campaignId(), retelling.id);
    }

    @Override
    public void onDraftClick(@NonNull RetellingDraftRoom draft) {
        Nav.editRetelling(requireContext(), viewModel.campaignId(), draft.id, null);
    }

    @Override
    public void onDraftDelete(@NonNull final RetellingDraftRoom draft) {
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(getString(R.string.retelling_delete_confirm,
                        draft.title == null ? "" : draft.title))
                .setPositiveButton(R.string.action_delete,
                        (dialog, which) -> viewModel.deleteDraft(draft.id))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
