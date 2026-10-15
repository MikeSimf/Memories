package ru.dndmemory.ui.invite;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import ru.dndmemory.R;
import ru.dndmemory.data.remote.dto.InviteDto;
import ru.dndmemory.databinding.ItemInviteBinding;
import ru.dndmemory.ui.common.Labels;
import ru.dndmemory.util.DateFormats;

/** Список выданных приглашений. */
public class InviteAdapter extends RecyclerView.Adapter<InviteAdapter.InviteHolder> {

    public interface Listener {
        void onInviteClick(@NonNull InviteDto invite);

        void onInviteRevoke(@NonNull InviteDto invite);
    }

    private final List<InviteDto> items = new ArrayList<>();
    private final Listener listener;

    public InviteAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<InviteDto> list) {
        items.clear();
        if (list != null) {
            items.addAll(list);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public InviteHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new InviteHolder(ItemInviteBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull InviteHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    final class InviteHolder extends RecyclerView.ViewHolder {

        private final ItemInviteBinding binding;

        InviteHolder(@NonNull ItemInviteBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull final InviteDto invite) {
            Context context = itemView.getContext();
            binding.itemInviteToken.setText(context.getString(R.string.invite_token,
                    invite.token == null ? "" : invite.token));
            binding.itemInviteRole.setText(Labels.roleText(context, invite.role));
            binding.itemInviteExpires.setText(context.getString(R.string.invite_expires,
                    DateFormats.formatIso(invite.expiresAt)));
            binding.itemInviteUses.setText(context.getString(R.string.invite_uses,
                    invite.uses, invite.maxUses));

            itemView.setOnClickListener(v -> listener.onInviteClick(invite));
            binding.itemInviteRevoke.setOnClickListener(v -> listener.onInviteRevoke(invite));
        }
    }
}
