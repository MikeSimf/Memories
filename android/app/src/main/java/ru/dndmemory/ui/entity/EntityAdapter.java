package ru.dndmemory.ui.entity;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import ru.dndmemory.R;
import ru.dndmemory.data.local.entity.EntityRoom;
import ru.dndmemory.databinding.ItemEntityBinding;
import ru.dndmemory.ui.common.Labels;
import ru.dndmemory.ui.common.ViewUtils;

/**
 * Список объектов кампании. Наследник {@link ListAdapter}: список обновляется
 * через DiffUtil, поэтому быстрый фильтр не пересоздаёт адаптер целиком и не
 * мигает на каждой введённой букве.
 */
public class EntityAdapter extends ListAdapter<EntityRoom, EntityAdapter.EntityHolder> {

    public interface Listener {
        void onEntityClick(@NonNull EntityRoom entity);
    }

    private static final DiffUtil.ItemCallback<EntityRoom> DIFF =
            new DiffUtil.ItemCallback<EntityRoom>() {
                @Override
                public boolean areItemsTheSame(@NonNull EntityRoom oldItem, @NonNull EntityRoom newItem) {
                    return oldItem.id != null && oldItem.id.equals(newItem.id);
                }

                @Override
                public boolean areContentsTheSame(@NonNull EntityRoom oldItem, @NonNull EntityRoom newItem) {
                    return TextUtils.equals(oldItem.name, newItem.name)
                            && TextUtils.equals(oldItem.summary, newItem.summary)
                            && TextUtils.equals(oldItem.type, newItem.type)
                            && oldItem.dirty == newItem.dirty
                            && oldItem.deleted == newItem.deleted
                            && oldItem.hasConflict() == newItem.hasConflict()
                            && TextUtils.equals(tagsOf(oldItem), tagsOf(newItem));
                }
            };

    private final Listener listener;

    public EntityAdapter(@NonNull Listener listener) {
        super(DIFF);
        this.listener = listener;
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
        EntityRoom entity = getItem(position);
        holder.bind(entity);
        holder.itemView.setOnClickListener(v -> listener.onEntityClick(entity));
    }

    @NonNull
    private static String tagsOf(@NonNull EntityRoom room) {
        return room.tags == null ? "" : TextUtils.join("|", room.tags);
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
            // Герой и НПС живут в одной вкладке — различаем подписью, а не разделением.
            binding.itemEntityType.setText(Labels.entityTypeText(context, entity.type));
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
