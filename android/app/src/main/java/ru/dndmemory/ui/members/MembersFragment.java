package ru.dndmemory.ui.members;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import ru.dndmemory.App;
import ru.dndmemory.AppContainer;
import ru.dndmemory.R;
import ru.dndmemory.data.local.entity.CampaignRoom;
import ru.dndmemory.data.local.entity.EntityRoom;
import ru.dndmemory.data.local.entity.MemberRoom;
import ru.dndmemory.data.model.MemberRole;
import ru.dndmemory.databinding.DialogMemberEditBinding;
import ru.dndmemory.databinding.FragmentMembersBinding;
import ru.dndmemory.ui.campaign.CampaignViewModel;
import ru.dndmemory.ui.common.CampaignHost;
import ru.dndmemory.ui.common.ErrorMessages;
import ru.dndmemory.ui.common.Labels;
import ru.dndmemory.ui.common.NameIndex;
import ru.dndmemory.ui.common.Rights;
import ru.dndmemory.ui.common.ViewModelFactory;
import ru.dndmemory.ui.common.ViewUtils;
import ru.dndmemory.util.AppExecutors;

/** Вкладка «Участники»: роли, привязка к герою, исключение из кампании. */
public class MembersFragment extends Fragment implements MemberAdapter.Listener {

    private FragmentMembersBinding binding;
    private MembersViewModel viewModel;
    private MemberAdapter adapter;
    private CampaignViewModel campaignViewModel;

    private List<EntityRoom> heroes = new ArrayList<>();
    private List<MemberRoom> lastMembers = new ArrayList<>();
    private NameIndex nameIndex = NameIndex.of(null);
    private MemberRoom currentMember;

    private static final MemberRole[] ROLES = {
            MemberRole.OWNER, MemberRole.EDITOR_MASTER, MemberRole.EDITOR_HERO, MemberRole.VIEWER};

    public static MembersFragment newInstance() {
        return new MembersFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentMembersBinding.inflate(inflater, container, false);
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
                () -> new MembersViewModel(container.memberRepository(),
                        container.entityRepository(), AppExecutors.get())))
                .get(MembersViewModel.class);
        viewModel.init(campaignId);

        adapter = new MemberAdapter(this);
        binding.membersRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.membersRecycler.setAdapter(adapter);

        binding.membersSwipe.setOnRefreshListener(() -> {
            viewModel.refresh();
            campaignViewModel.refresh();
        });
        binding.membersAdd.setOnClickListener(v -> showMemberDialog(null));

        observe();
    }

    private void observe() {
        viewModel.items().observe(getViewLifecycleOwner(), items -> {
            adapter.submitList(items);
            lastMembers = items == null ? new ArrayList<>() : items;
            ViewUtils.show(binding.membersEmpty, items == null || items.isEmpty());
            currentMember = findCurrent(items);
            updateCurrentLabel();
        });

        viewModel.heroes().observe(getViewLifecycleOwner(), list -> {
            heroes = list == null ? new ArrayList<>() : list;
        });

        viewModel.nameIndex().observe(getViewLifecycleOwner(), index -> {
            if (index != null) {
                nameIndex = index;
            }
        });

        viewModel.actionState().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) {
                return;
            }
            ViewUtils.show(binding.membersProgress, resource.isLoading());
            binding.membersSwipe.setRefreshing(resource.isLoading());
            if (resource.isError()) {
                ErrorMessages.show(binding.getRoot(), resource.error);
            }
            if (!resource.isLoading()) {
                viewModel.clearActionState();
            }
        });

        viewModel.messageRes().observe(getViewLifecycleOwner(), res -> {
            if (res != null && res != 0) {
                ViewUtils.toast(requireContext(), res);
                viewModel.clearMessage();
            }
        });

        campaignViewModel.campaign().observe(getViewLifecycleOwner(), campaign -> {
            boolean canManage = Rights.canManageMembers(campaign);
            adapter.setManageAllowed(canManage);
            ViewUtils.show(binding.membersAdd, canManage);
            updateCurrentLabel();
        });
    }

    private void updateCurrentLabel() {
        CampaignRoom campaign = campaignViewModel.campaign().getValue();
        String role = currentMember != null
                ? Labels.roleText(requireContext(), currentMember.role).toString()
                : Labels.roleText(requireContext(), campaign == null ? null : campaign.myRole).toString();
        binding.membersCurrent.setText(getString(R.string.members_my_role, role));
    }

    @Nullable
    private MemberRoom findCurrent(@Nullable List<MemberRoom> items) {
        String userId = campaignViewModel.currentUserId();
        if (items == null || userId == null) {
            return null;
        }
        for (MemberRoom member : items) {
            if (userId.equals(member.userId)) {
                return member;
            }
        }
        return null;
    }

    /** Диалог добавления нового участника или смены роли существующего. */
    private void showMemberDialog(@Nullable final MemberRoom member) {
        CampaignRoom campaign = campaignViewModel.campaign().getValue();
        if (!Rights.canManageMembers(campaign)) {
            ViewUtils.toast(requireContext(), R.string.members_manage_forbidden);
            return;
        }
        if (isLastOwner(member)) {
            ViewUtils.toast(requireContext(), R.string.members_last_owner_error);
            return;
        }
        final DialogMemberEditBinding dialog = DialogMemberEditBinding.inflate(getLayoutInflater());
        final boolean editing = member != null;

        dialog.memberEditHeader.setText(editing ? R.string.members_role : R.string.members_add);
        dialog.memberEditEmailInput.setEnabled(!editing);
        if (editing) {
            dialog.memberEditEmailInput.setText(member.email);
        }

        String[] roleLabels = new String[ROLES.length];
        for (int i = 0; i < ROLES.length; i++) {
            roleLabels[i] = getString(Labels.role(ROLES[i]));
        }
        dialog.memberEditRoleInput.setSimpleItems(roleLabels);
        dialog.memberEditRoleInput.setText(roleLabels[2], false);

        final List<String> heroLabels = new ArrayList<>();
        heroLabels.add(getString(R.string.retelling_author_none));
        for (EntityRoom hero : heroes) {
            if (!TextUtils.isEmpty(hero.name)) {
                heroLabels.add(hero.name);
            }
        }
        dialog.memberEditLinkedInput.setSimpleItems(heroLabels.toArray(new String[0]));
        dialog.memberEditLinkedInput.setText(heroLabels.get(0), false);

        if (editing) {
            if (member.role != null) {
                MemberRole role = MemberRole.from(member.role);
                if (role != null) {
                    dialog.memberEditRoleInput.setText(getString(Labels.role(role)), false);
                }
            }
            String heroName = nameIndex.nameFor(member.linkedHeroId);
            if (heroName != null) {
                dialog.memberEditLinkedInput.setText(heroName, false);
            }
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setView(dialog.getRoot())
                .setPositiveButton(editing ? R.string.action_save : R.string.action_add,
                        (d, which) -> {
                            String role = selectedRole(dialog.memberEditRoleInput.getText()).name();
                            String linkedHeroId = selectedHeroId(dialog.memberEditLinkedInput.getText());
                            if (editing) {
                                viewModel.changeRole(member.id, role, linkedHeroId);
                            } else {
                                String email = text(dialog.memberEditEmailInput.getText());
                                if (email.isEmpty()) {
                                    ViewUtils.toast(requireContext(), R.string.error_required);
                                    return;
                                }
                                viewModel.add(email, role, linkedHeroId);
                            }
                        })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    /** Последний владелец: его нельзя ни понизить, ни убрать (docs/roles.md). */
    private boolean isLastOwner(@Nullable MemberRoom member) {
        if (member == null || !MemberRole.OWNER.name().equals(member.role)) {
            return false;
        }
        int owners = 0;
        for (MemberRoom item : lastMembers) {
            if (MemberRole.OWNER.name().equals(item.role)
                    && !"REMOVED".equalsIgnoreCase(item.status)) {
                owners++;
            }
        }
        return owners <= 1;
    }

    @NonNull
    private MemberRole selectedRole(@Nullable CharSequence shown) {
        if (shown != null) {
            for (MemberRole role : ROLES) {
                if (getString(Labels.role(role)).contentEquals(shown)) {
                    return role;
                }
            }
        }
        return MemberRole.EDITOR_HERO;
    }

    @Nullable
    private String selectedHeroId(@Nullable CharSequence shown) {
        if (shown == null) {
            return null;
        }
        String value = shown.toString().trim();
        if (value.isEmpty() || value.equals(getString(R.string.retelling_author_none))) {
            return null;
        }
        return nameIndex.idFor(value);
    }

    private static String text(@Nullable CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }

    @Override
    public void onMemberClick(@NonNull MemberRoom member) {
        showMemberDialog(member);
    }

    @Override
    public void onMemberMore(@NonNull final MemberRoom member) {
        CampaignRoom campaign = campaignViewModel.campaign().getValue();
        if (!Rights.canManageMembers(campaign)) {
            return;
        }
        if (isLastOwner(member)) {
            // Последнего владельца понизить нельзя (docs/roles.md, правило 1).
            ViewUtils.toast(requireContext(), R.string.members_last_owner_error);
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(getString(R.string.members_remove_confirm,
                        TextUtils.isEmpty(member.displayName) ? "" : member.displayName))
                .setPositiveButton(R.string.action_delete,
                        (dialog, which) -> viewModel.remove(member.id))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
