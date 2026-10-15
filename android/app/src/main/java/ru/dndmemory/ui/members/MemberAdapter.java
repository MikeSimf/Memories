package ru.dndmemory.ui.members;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import ru.dndmemory.R;
import ru.dndmemory.data.local.entity.MemberRoom;
import ru.dndmemory.databinding.ItemMemberBinding;
import ru.dndmemory.ui.common.Labels;
import ru.dndmemory.ui.common.ViewUtils;

/** Список участников кампании. */
public class MemberAdapter extends RecyclerView.Adapter<MemberAdapter.MemberHolder> {

    public interface Listener {
        void onMemberClick(@NonNull MemberRoom member);

        void onMemberMore(@NonNull MemberRoom member);
    }

    private final List<MemberRoom> items = new ArrayList<>();
    private final Listener listener;
    private boolean manageAllowed;

    public MemberAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    public void setManageAllowed(boolean allowed) {
        this.manageAllowed = allowed;
        notifyDataSetChanged();
    }

    public void submitList(List<MemberRoom> list) {
        items.clear();
        if (list != null) {
            items.addAll(list);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MemberHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new MemberHolder(ItemMemberBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull MemberHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    final class MemberHolder extends RecyclerView.ViewHolder {

        private final ItemMemberBinding binding;

        MemberHolder(@NonNull ItemMemberBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull final MemberRoom member) {
            Context context = itemView.getContext();
            binding.itemMemberName.setText(TextUtils.isEmpty(member.displayName)
                    ? context.getString(R.string.attr_not_set) : member.displayName);
            ViewUtils.setTextOrGone(binding.itemMemberEmail, member.email);
            binding.itemMemberRole.setText(Labels.roleText(context, member.role));

            String hero = member.linkedEntityName;
            if (hero == null || hero.isEmpty()) {
                nameIndexOrNothing();
            } else {
                binding.itemMemberLinked.setText(context.getString(
                        R.string.members_linked_hero) + ": " + hero);
                ViewUtils.show(binding.itemMemberLinked, true);
            }

            binding.itemMemberStatus.setText(context.getString(Labels.memberStatus(member.status)));

            itemView.setOnClickListener(v -> listener.onMemberClick(member));
            binding.itemMemberMore.setVisibility(manageAllowed ? android.view.View.VISIBLE
                    : android.view.View.GONE);
            binding.itemMemberMore.setOnClickListener(v -> listener.onMemberMore(member));
        }

        private void nameIndexOrNothing() {
            ViewUtils.show(binding.itemMemberLinked, false);
        }
    }
}
