package ru.dndmemory.ui.entity;

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
import ru.dndmemory.data.local.entity.EntityRoom;
import ru.dndmemory.databinding.ItemEntityBinding;
import ru.dndmemory.ui.common.Labels;
import ru.dndmemory.ui.common.ViewUtils;

/** Список объектов кампании. */
public class EntityAdapter extends RecyclerView.Adapter<EntityAdapter.EntityHolder> {

    public interface Listener {
        void onEntityClick(@NonNull EntityRoom entity);
    }

    private final List<EntityRoom> items = new ArrayList<>();
    private final Listener listener;

    public EntityAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<EntityRoom> list) {
        items.clear();
        if (list != null) {
            items.addAll(list);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public EntityHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemEntityBinding binding = ItemEntityBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new EntityHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull EntityHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class EntityHolder extends RecyclerView.ViewHolder {

        private final ItemEntityBinding binding;

        EntityHolder(@NonNull ItemEntityBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull final EntityRoom entity) {
            Context context = itemView.getContext();
            binding.itemEntityIcon.setImageResource(Labels.entityIcon(entity.type));
            binding.itemEntityName.setText(TextUtils.isEmpty(entity.name)
                    ? context.getString(R.string.attr_not_set) : entity.name);
            ViewUtils.setTextOrGone(binding.itemEntitySummary, entity.summary);
            if (entity.tags == null || entity.tags.isEmpty()) {
                ViewUtils.show(binding.itemEntityTags, false);
            } else {
                binding.itemEntityTags.setText(TextUtils.join(" · ", entity.tags));
                ViewUtils.show(binding.itemEntityTags, true);
            }

            if (entity.hasConflict()) {
                binding.itemEntityStatus.setText(R.string.entity_conflict);
                binding.itemEntityStatus.setTextColor(
                        ContextCompat.getColor(context, R.color.badge_conflict));
                ViewUtils.show(binding.itemEntityStatus, true);
            } else if (entity.dirty) {
                binding.itemEntityStatus.setText(R.string.entity_dirty);
                binding.itemEntityStatus.setTextColor(
                        ContextCompat.getColor(context, R.color.badge_dirty));
                ViewUtils.show(binding.itemEntityStatus, true);
            } else {
                ViewUtils.show(binding.itemEntityStatus, false);
            }
        }
    }
}
