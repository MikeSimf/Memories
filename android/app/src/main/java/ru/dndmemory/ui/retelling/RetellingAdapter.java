package ru.dndmemory.ui.retelling;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import ru.dndmemory.R;
import ru.dndmemory.data.local.entity.RetellingDraftRoom;
import ru.dndmemory.data.local.entity.RetellingRoom;
import ru.dndmemory.databinding.ItemRetellingBinding;
import ru.dndmemory.databinding.ItemRetellingDraftBinding;
import ru.dndmemory.ui.common.ViewUtils;
import ru.dndmemory.util.DateFormats;
import ru.dndmemory.util.WikiMarkdown;

/** Список опубликованных пересказов и отдельный адаптер для локальных черновиков. */
public class RetellingAdapter extends RecyclerView.Adapter<RetellingAdapter.RetellingHolder> {

    public interface Listener {
        void onRetellingClick(@NonNull RetellingRoom retelling);
    }

    /** Черновики: нажатие открывает редактор, крестик удаляет черновик. */
    public static class DraftAdapter extends RecyclerView.Adapter<DraftAdapter.DraftHolder> {

        public interface Listener {
            void onDraftClick(@NonNull RetellingDraftRoom draft);

            void onDraftDelete(@NonNull RetellingDraftRoom draft);
        }

        private final List<RetellingDraftRoom> items = new ArrayList<>();
        private final Listener listener;

        public DraftAdapter(@NonNull Listener listener) {
            this.listener = listener;
        }

        public void submitList(List<RetellingDraftRoom> list) {
            items.clear();
            if (list != null) {
                items.addAll(list);
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public DraftHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new DraftHolder(ItemRetellingDraftBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull DraftHolder holder, int position) {
            holder.bind(items.get(position));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        final class DraftHolder extends RecyclerView.ViewHolder {

            private final ItemRetellingDraftBinding binding;

            DraftHolder(@NonNull ItemRetellingDraftBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }

            void bind(@NonNull final RetellingDraftRoom draft) {
                binding.itemDraftTitle.setText(TextUtils.isEmpty(draft.title)
                        ? itemView.getContext().getString(R.string.retelling_edit_title_new)
                        : draft.title);
                binding.itemDraftUpdated.setText(itemView.getContext().getString(
                        R.string.retelling_edited, DateFormats.formatIso(draft.updatedAt)));
                itemView.setOnClickListener(v -> listener.onDraftClick(draft));
                binding.itemDraftDelete.setOnClickListener(v -> listener.onDraftDelete(draft));
            }
        }
    }

    private final List<RetellingRoom> items = new ArrayList<>();
    private final Listener listener;

    public RetellingAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<RetellingRoom> list) {
        items.clear();
        if (list != null) {
            items.addAll(list);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RetellingHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new RetellingHolder(ItemRetellingBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RetellingHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    final class RetellingHolder extends RecyclerView.ViewHolder {

        private final ItemRetellingBinding binding;

        RetellingHolder(@NonNull ItemRetellingBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull final RetellingRoom retelling) {
            Context context = itemView.getContext();
            binding.itemRetellingTitle.setText(TextUtils.isEmpty(retelling.title)
                    ? context.getString(R.string.retelling_detail_title) : retelling.title);

            StringBuilder meta = new StringBuilder();
            if (!TextUtils.isEmpty(retelling.sessionDate)) {
                meta.append(DateFormats.formatDate(retelling.sessionDate));
            }
            if (!TextUtils.isEmpty(retelling.authorEntityName)) {
                if (meta.length() > 0) {
                    meta.append(" · ");
                }
                meta.append(context.getString(R.string.retelling_author, retelling.authorEntityName));
            }
            ViewUtils.setTextOrGone(binding.itemRetellingMeta, meta.toString());
            ViewUtils.setTextOrGone(binding.itemRetellingSnippet,
                    WikiMarkdown.plainText(retelling.body));

            if (retelling.hasConflict()) {
                binding.itemRetellingStatus.setText(R.string.retelling_conflict);
                binding.itemRetellingStatus.setTextColor(
                        ContextCompat.getColor(context, R.color.badge_conflict));
            } else if (retelling.dirty) {
                binding.itemRetellingStatus.setText(R.string.entity_dirty);
                binding.itemRetellingStatus.setTextColor(
                        ContextCompat.getColor(context, R.color.badge_dirty));
            } else {
                binding.itemRetellingStatus.setText(R.string.retelling_published);
                binding.itemRetellingStatus.setTextColor(
                        ContextCompat.getColor(context, R.color.badge_published));
            }

            itemView.setOnClickListener(v -> listener.onRetellingClick(retelling));
        }
    }
}
