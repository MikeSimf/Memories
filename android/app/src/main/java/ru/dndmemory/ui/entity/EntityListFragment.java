package ru.dndmemory.ui.entity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
import ru.dndmemory.ui.common.Labels;
import ru.dndmemory.ui.common.Nav;
import ru.dndmemory.ui.common.Rights;
import ru.dndmemory.ui.common.ViewModelFactory;
import ru.dndmemory.ui.common.TypeColors;
import ru.dndmemory.ui.common.ViewUtils;
import ru.dndmemory.util.AppExecutors;

/**
 * Вкладка объектов. Список грузится по {@code kinds=HERO,CHARACTER} (или одного
 * класса), вверху — поле быстрого фильтра: оно фильтрует прямо во время набора
 * с небольшой задержкой, поэтому длинный список не подтормаживает.
 *
 * <p>На вкладке «Персонажи» кнопка «Добавить» спрашивает класс: герой или
 * персонаж. Героя заводит только владелец кампании — остальным вариант
 * не показываем (docs/roles.md).
 *
 * <p>Действия «Поиск», «Пересказы», «Участники», «Приглашения» и «Обновить»
 * живут в меню {@link ru.dndmemory.ui.campaign.CampaignActivity}.
 */
public class EntityListFragment extends Fragment implements EntityAdapter.Listener {

    private static final String ARG_KINDS = "argKinds";
    /** Задержка фильтра: достаточно, чтобы не считать на каждую букву. */
    private static final long FILTER_DELAY_MS = 200L;

    private FragmentEntityListBinding binding;
    private EntityListViewModel viewModel;
    private EntityAdapter adapter;
    private CampaignViewModel campaignViewModel;
    private String kinds = "";

    /** Режим выбора: id отмеченных объектов и панель действий над ними. */
    private final Set<String> selected = new LinkedHashSet<>();
    private androidx.appcompat.view.ActionMode selectionMode;

    private final Handler filterHandler = new Handler(Looper.getMainLooper());
    private final Runnable filterTask = () -> viewModel.setQuery(text(binding.entityListFilter.getText()));

    /** @param kindsCsv классы через запятую: {@code HERO,CHARACTER}, {@code LOCATION}, {@code ITEM}. */
    public static EntityListFragment newInstance(@NonNull String kindsCsv) {
        EntityListFragment fragment = new EntityListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_KINDS, kindsCsv);
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
        if (args != null && args.getString(ARG_KINDS) != null) {
            kinds = args.getString(ARG_KINDS);
        }

        CampaignHost host = (CampaignHost) requireActivity();
        campaignViewModel = host.campaignViewModel();
        String campaignId = host.campaignId();

        AppContainer container = App.container(requireContext());
        viewModel = new ViewModelProvider(this, ViewModelFactory.of(
                () -> new EntityListViewModel(container.entityRepository(),
                        container.syncScheduler(), AppExecutors.get())))
                .get(EntityListViewModel.class);
        viewModel.init(campaignId, kinds);

        adapter = new EntityAdapter(this);
        binding.entityListRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.entityListRecycler.setAdapter(adapter);

        setupSwipes();
        setupFilter();

        binding.entityListSwipe.setOnRefreshListener(() -> {
            viewModel.refresh();
            viewModel.syncNow();
        });
        binding.entityListFab.setOnClickListener(v -> onCreateClicked());

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

    /**
     * Создание объекта: на вкладке с несколькими классами сначала спрашиваем
     * класс, и предлагаем только те, на которые есть права.
     */
    private void onCreateClicked() {
        CampaignRoom campaign = campaignViewModel.campaign().getValue();
        if (!Rights.canCreateEntity(campaign)) {
            ViewUtils.toast(requireContext(), R.string.error_forbidden);
            return;
        }
        final String campaignId = viewModel.campaignId();
        if (!viewModel.hasSeveralKinds()) {
            EntityType kind = EntityType.fromOrCharacter(viewModel.createKind());
            if (!Rights.canWrite(campaign, kind)) {
                ViewUtils.toast(requireContext(), R.string.entity_hero_owner_only);
                return;
            }
            Nav.createEntity(requireContext(), campaignId, kind.name());
            return;
        }

        final List<EntityType> allowed = new ArrayList<>();
        for (String rawKind : EntityType.parseList(viewModel.kindsCsv())) {
            EntityType kind = EntityType.from(rawKind);
            if (kind != null && Rights.canWrite(campaign, kind)) {
                allowed.add(kind);
            }
        }
        if (allowed.isEmpty()) {
            ViewUtils.toast(requireContext(), R.string.error_forbidden);
            return;
        }
        if (allowed.size() == 1) {
            Nav.createEntity(requireContext(), campaignId, allowed.get(0).name());
            return;
        }
        String[] labels = new String[allowed.size()];
        for (int i = 0; i < allowed.size(); i++) {
            labels[i] = getString(Labels.entityType(allowed.get(i)));
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.entity_create_choose)
                .setItems(labels, (dialog, which) ->
                        Nav.createEntity(requireContext(), campaignId, allowed.get(which).name()))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
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
        if (selectionMode != null) {
            toggleSelection(entity);
            return;
        }
        Nav.openEntity(requireContext(), viewModel.campaignId(), entity.id);
    }

    @Override
    public void onEntityLongClick(@NonNull EntityRoom entity) {
        // Долгое нажатие — тот же режим выбора, что и свайп вправо.
        if (selectionMode == null) {
            startSelection(entity);
        } else {
            toggleSelection(entity);
        }
    }

    // --- Свайпы и режим выбора --------------------------------------------

    /**
     * Влево — меню действий над одной записью, вправо — режим выбора.
     * Свайпы отключаются, пока идёт выбор: иначе жест конфликтует с нажатиями.
     */
    private void setupSwipes() {
        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

            @Override
            public int getSwipeDirs(@NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder) {
                return selectionMode == null ? super.getSwipeDirs(recyclerView, viewHolder) : 0;
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) {
                    return;
                }
                EntityRoom entity = adapter.getCurrentList().get(position);
                // Карточка возвращается на место: свайп — это жест, а не удаление.
                // Через post: во время onSwiped RecyclerView ещё считает раскладку.
                binding.entityListRecycler.post(() -> adapter.notifyItemChanged(position));
                if (direction == ItemTouchHelper.LEFT) {
                    showActions(entity);
                } else {
                    startSelection(entity);
                }
            }
        };
        new ItemTouchHelper(callback).attachToRecyclerView(binding.entityListRecycler);
    }

    /** Меню действий над одной записью (свайп влево). */
    private void showActions(@NonNull final EntityRoom entity) {
        final CampaignRoom campaign = campaignViewModel.campaign().getValue();
        final boolean canWrite = Rights.canWriteEntity(campaign, entity, campaignViewModel.currentUserId());

        List<String> labels = new ArrayList<>();
        final List<Integer> actions = new ArrayList<>();
        labels.add(getString(R.string.action_open));
        actions.add(ACTION_OPEN);
        if (canWrite) {
            labels.add(getString(R.string.action_edit));
            actions.add(ACTION_EDIT);
            labels.add(getString(R.string.action_sync_one));
            actions.add(ACTION_REFRESH);
            labels.add(getString(R.string.action_delete));
            actions.add(ACTION_DELETE);
            labels.add(getString(R.string.action_select));
            actions.add(ACTION_SELECT);
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(TextUtils.isEmpty(entity.name) ? getString(R.string.entity_actions_title) : entity.name)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    int action = actions.get(which);
                    if (action == ACTION_OPEN) {
                        Nav.openEntity(requireContext(), viewModel.campaignId(), entity.id);
                    } else if (action == ACTION_EDIT) {
                        Nav.editEntity(requireContext(), viewModel.campaignId(), entity.id);
                    } else if (action == ACTION_REFRESH) {
                        viewModel.refreshOne(entity);
                    } else if (action == ACTION_DELETE) {
                        confirmDelete(entity);
                    } else if (action == ACTION_SELECT) {
                        startSelection(entity);
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void confirmDelete(@NonNull final EntityRoom entity) {
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(getString(R.string.entity_edit_delete_confirm,
                        TextUtils.isEmpty(entity.name) ? getString(R.string.attr_not_set) : entity.name))
                .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                    List<EntityRoom> one = new ArrayList<>();
                    one.add(entity);
                    viewModel.deleteMany(one);
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void startSelection(@NonNull EntityRoom entity) {
        if (selectionMode != null) {
            toggleSelection(entity);
            return;
        }
        if (!(requireActivity() instanceof androidx.appcompat.app.AppCompatActivity)) {
            return;
        }
        androidx.appcompat.app.AppCompatActivity activity =
                (androidx.appcompat.app.AppCompatActivity) requireActivity();
        selectionMode = activity.startSupportActionMode(new SelectionCallback());
        selected.clear();
        selected.add(entity.id);
        applySelection();
    }

    private void toggleSelection(@NonNull EntityRoom entity) {
        if (entity.id == null) {
            return;
        }
        if (!selected.remove(entity.id)) {
            selected.add(entity.id);
        }
        if (selected.isEmpty()) {
            if (selectionMode != null) {
                selectionMode.finish();
            }
            return;
        }
        applySelection();
    }

    private void applySelection() {
        adapter.setSelection(selected, selectionMode != null);
        if (selectionMode != null) {
            selectionMode.setTitle(getString(R.string.entity_selected_title, selected.size()));
        }
    }

    @NonNull
    private List<EntityRoom> selectedEntities() {
        List<EntityRoom> out = new ArrayList<>();
        List<EntityRoom> shown = adapter.getCurrentList();
        for (EntityRoom room : shown) {
            if (room != null && room.id != null && selected.contains(room.id)) {
                out.add(room);
            }
        }
        return out;
    }

    private void deleteSelected() {
        final List<EntityRoom> entities = selectedEntities();
        if (entities.isEmpty()) {
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(getString(R.string.entity_delete_many_confirm, entities.size()))
                .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                    viewModel.deleteMany(entities);
                    ViewUtils.toast(requireContext(),
                            getString(R.string.entity_selected_deleted, entities.size()));
                    if (selectionMode != null) {
                        selectionMode.finish();
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void refreshSelected() {
        List<EntityRoom> entities = selectedEntities();
        for (EntityRoom entity : entities) {
            viewModel.refreshOne(entity);
        }
        if (selectionMode != null) {
            selectionMode.finish();
        }
    }

    private static final int ACTION_OPEN = 0;
    private static final int ACTION_EDIT = 1;
    private static final int ACTION_REFRESH = 2;
    private static final int ACTION_DELETE = 3;
    private static final int ACTION_SELECT = 4;

    /** Панель действий над выбранными объектами. */
    private final class SelectionCallback implements androidx.appcompat.view.ActionMode.Callback {

        @Override
        public boolean onCreateActionMode(androidx.appcompat.view.ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.menu_entity_select, menu);
            mode.setTitle(getString(R.string.entity_selected_title, selected.size()));
            return true;
        }

        @Override
        public boolean onPrepareActionMode(androidx.appcompat.view.ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(androidx.appcompat.view.ActionMode mode, MenuItem item) {
            int id = item.getItemId();
            if (id == R.id.action_selected_refresh) {
                refreshSelected();
                return true;
            }
            if (id == R.id.action_selected_delete) {
                deleteSelected();
                return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(androidx.appcompat.view.ActionMode mode) {
            selectionMode = null;
            selected.clear();
            adapter.setSelection(selected, false);
        }
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
