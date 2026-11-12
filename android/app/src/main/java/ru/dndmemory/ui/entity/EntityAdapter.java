package ru.dndmemory.ui.entity;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import ru.dndmemory.R;
import ru.dndmemory.data.local.entity.EntityRoom;
import ru.dndmemory.data.model.EntityType;
import ru.dndmemory.databinding.ItemCustomCardBinding;
import ru.dndmemory.databinding.ItemEntityBinding;
import ru.dndmemory.data.remote.dto.CardTemplateDto;
import ru.dndmemory.data.remote.dto.CardBlockDto;
import ru.dndmemory.ui.common.Labels;
import ru.dndmemory.ui.common.TypeColors;
import ru.dndmemory.ui.common.ViewUtils;

/**
 * Список объектов кампании. Наследник {@link ListAdapter}: список обновляется
 * через DiffUtil, поэтому быстрый фильтр не пересоздаёт адаптер целиком и не
 * мигает на каждой введённой букве.
 */
public class EntityAdapter extends ListAdapter<EntityRoom, EntityAdapter.EntityHolder> {

    public interface Listener {
        void onEntityClick(@NonNull EntityRoom entity);

        /** Долгое нажатие включает режим выбора — как и свайп вправо. */
        void onEntityLongClick(@NonNull EntityRoom entity);
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
                            && TextUtils.equals(oldItem.description, newItem.description)
                            && TextUtils.equals(oldItem.kind, newItem.kind)
                            && TextUtils.equals(oldItem.race, newItem.race)
                            && TextUtils.equals(oldItem.klass, newItem.klass)
                            && TextUtils.equals(oldItem.playerName, newItem.playerName)
                            && TextUtils.equals(oldItem.shortDescription, newItem.shortDescription)
                            && TextUtils.equals(oldItem.ownerName, newItem.ownerName)
                            && oldItem.dirty == newItem.dirty
                            && oldItem.deleted == newItem.deleted
                            && oldItem.hasConflict() == newItem.hasConflict();
                }
            };

    private final Listener listener;
    private final java.util.Set<String> selected = new java.util.HashSet<>();
    private boolean selectionMode;
    private java.util.Map<String, CardTemplateDto> templates = java.util.Collections.emptyMap();

    public void setTemplates(@NonNull java.util.Map<String, CardTemplateDto> value) {
        templates = value;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return templates.containsKey(getItem(position).cardTemplateId) ? 1 : 0;
    }

    public EntityAdapter(@NonNull Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    /** Режим выбора: подсвечиваем отмеченные и не даём свайпам мешать. */
    public void setSelection(@NonNull java.util.Collection<String> ids, boolean mode) {
        selected.clear();
        selected.addAll(ids);
        selectionMode = mode;
        notifyDataSetChanged();
    }

    public boolean isSelected(@Nullable String id) {
        return id != null && selected.contains(id);
    }

    public boolean isSelectionMode() {
        return selectionMode;
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
        EntityRoom entity = getCurrentList().get(position);
        holder.bind(entity, selectionMode && isSelected(entity.id));
        holder.itemView.setOnClickListener(v -> listener.onEntityClick(entity));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onEntityLongClick(entity);
            return true;
        });
    }

    static final class EntityHolder extends RecyclerView.ViewHolder {

        private final ItemEntityBinding binding;

        EntityHolder(@NonNull ItemEntityBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull final EntityRoom entity, boolean picked) {
            Context context = itemView.getContext();
            EntityType type = EntityType.fromOrCharacter(entity.kind);
            binding.itemEntityRoot.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(
                    context, picked ? R.color.colorPrimaryContainer : R.color.colorSurface));
            ru.dndmemory.ui.common.AvatarBinder.bind(context, binding.itemEntityIcon, entity,
                    ru.dndmemory.App.container(context).okHttpClient());
            ru.dndmemory.ui.common.AvatarBinder.bindClassIcon(context,
                    binding.itemEntityClassIcon, entity);
            // Тип виден по цвету полоски и подписи — не нужно читать строку класса.
            binding.itemEntityStripe.setBackgroundColor(TypeColors.of(context, type));
            binding.itemEntityType.setTextColor(TypeColors.of(context, type));
            binding.itemEntityName.setText(TextUtils.isEmpty(entity.name)
                    ? context.getString(R.string.attr_not_set) : entity.name);
            // Герой и персонаж живут в одной вкладке — различаем подписью класса.
            binding.itemEntityType.setText(Labels.entityTypeText(context, entity.kind));
            ViewUtils.setTextOrGone(binding.itemEntitySubtitle,
                    Labels.entitySubtitle(context, entity));
            // Пол — в конце строки: символ, а не отдельное слово.
            String gender = Labels.genderSymbol(context, entity.gender);
            ViewUtils.setTextOrGone(binding.itemEntityGender, gender);
            if ("alive".equalsIgnoreCase(entity.lifeStatus)) {
                binding.itemEntityLife.setImageResource(R.drawable.ic_life_alive);
                binding.itemEntityLife.setColorFilter(ContextCompat.getColor(context, R.color.badge_published));
                ViewUtils.show(binding.itemEntityLife, true);
            } else if ("dead".equalsIgnoreCase(entity.lifeStatus)) {
                binding.itemEntityLife.setImageResource(R.drawable.ic_life_dead);
                binding.itemEntityLife.setColorFilter(ContextCompat.getColor(context, R.color.badge_conflict));
                ViewUtils.show(binding.itemEntityLife, true);
            } else {
                ViewUtils.show(binding.itemEntityLife, false);
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

    /** Карточка, нарисованная по шаблону: блоки в абсолютных координатах. */
    static final class CustomHolder extends RecyclerView.ViewHolder {
        private final ItemCustomCardBinding binding;

        CustomHolder(ItemCustomCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull EntityRoom entity, @Nullable CardTemplateDto template) {
            binding.customCardRoot.removeAllViews();
            if (template == null || template.blocks == null) return;
            for (CardBlockDto block : template.blocks) {
                if (block == null) continue;
                if ("avatar".equals(block.field)) {
                    android.widget.ImageView image = new android.widget.ImageView(itemView.getContext());
                    image.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                    android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                            Math.max(1, (int) block.width), Math.max(1, (int) block.height));
                    params.leftMargin = (int) block.x;
                    params.topMargin = (int) block.y;
                    image.setLayoutParams(params);
                    ru.dndmemory.ui.common.AvatarBinder.bind(itemView.getContext(), image, entity,
                            ru.dndmemory.App.container(itemView.getContext()).okHttpClient());
                    binding.customCardRoot.addView(image);
                    continue;
                }
                android.widget.TextView text = new android.widget.TextView(itemView.getContext());
                text.setTextSize(block.fontSize > 0 ? block.fontSize : 14);
                text.setMaxLines(1);
                text.setEllipsize(android.text.TextUtils.TruncateAt.END);
                android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                        Math.max(1, (int) block.width), Math.max(1, (int) block.height));
                params.leftMargin = (int) block.x;
                params.topMargin = (int) block.y;
                text.setLayoutParams(params);
                text.setText(cardValue(entity, block.field));
                binding.customCardRoot.addView(text);
            }
        }

        private String cardValue(@NonNull EntityRoom entity, @Nullable String field) {
            if (field == null) return "";
            Context context = itemView.getContext();
            switch (field) {
                case "name": return entity.name == null ? "" : entity.name;
                case "type": return Labels.entityTypeText(context, entity.kind).toString();
                case "race": return entity.race == null ? "" : entity.race;
                case "klass": return entity.klass == null ? "" : entity.klass;
                case "gender": return Labels.gender(context, entity.gender).toString();
                case "alive": return Labels.lifeStatus(context, entity.lifeStatus).toString();
                case "shortDescription": return entity.shortDescription == null ? "" : entity.shortDescription;
                case "itemType": return entity.itemType == null ? "" : entity.itemType;
                case "locationType": return entity.locationType == null ? "" : entity.locationType;
                case "ownerName": return entity.ownerName == null ? "" : entity.ownerName;
                default: return "";
            }
        }
    }
}