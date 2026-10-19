package ru.dndmemory.ui.material;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import ru.dndmemory.App;
import ru.dndmemory.R;
import ru.dndmemory.data.local.entity.MaterialRoom;
import ru.dndmemory.databinding.FragmentMaterialsBinding;
import ru.dndmemory.ui.common.CampaignHost;
import ru.dndmemory.ui.common.Nav;
import ru.dndmemory.ui.common.ViewUtils;

/**
 * Вкладка «Материалы»: карты, портреты и письма кампании.
 * Список читается из локальной базы, поэтому доступен и без сети;
 * обновление с сервера идёт в фоне и ошибок не показывает.
 */
public class MaterialsFragment extends Fragment implements MaterialAdapter.Listener {

    private FragmentMaterialsBinding binding;
    private MaterialAdapter adapter;
    private String campaignId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentMaterialsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        CampaignHost host = (CampaignHost) requireActivity();
        campaignId = host.campaignId();

        adapter = new MaterialAdapter(this);
        binding.materialsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.materialsRecycler.setAdapter(adapter);

        binding.materialsFab.setOnClickListener(v -> Nav.createMaterial(requireContext(), campaignId));
        binding.materialsSwipe.setOnRefreshListener(() -> {
            binding.materialsSwipe.setRefreshing(false);
            App.container(requireContext()).materialRepository().refreshQuietly(campaignId);
        });

        App.container(requireContext()).materialRepository().observe(campaignId)
                .observe(getViewLifecycleOwner(), materials -> {
                    adapter.submitList(materials);
                    boolean empty = materials == null || materials.isEmpty();
                    ViewUtils.show(binding.materialsEmpty, empty);
                    ViewUtils.show(binding.materialsRecycler, !empty);
                });

        // Тихо тянем список: если сети нет, остаёмся на локальном кэше.
        App.container(requireContext()).materialRepository().refreshQuietly(campaignId);
    }

    @Override
    public void onMaterialClick(@NonNull MaterialRoom material) {
        Nav.editMaterial(requireContext(), campaignId, material.id);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
