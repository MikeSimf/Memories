package ru.dndmemory.ui.material;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import ru.dndmemory.App;
import ru.dndmemory.R;
import ru.dndmemory.data.local.entity.MaterialRoom;
import ru.dndmemory.databinding.ItemMaterialBinding;
import ru.dndmemory.ui.common.ImageLoader;
import ru.dndmemory.ui.common.ViewUtils;

/** Список фото-материалов: миниатюра, название, пара строк описания. */
public class MaterialAdapter extends ListAdapter<MaterialRoom, MaterialAdapter.Holder> {

    public interface Listener {
        void onMaterialClick(@NonNull MaterialRoom material);
    }

    private static final DiffUtil.ItemCallback<MaterialRoom> DIFF =
            new DiffUtil.ItemCallback<MaterialRoom>() {
                @Override
                public boolean areItemsTheSame(@NonNull MaterialRoom oldItem, @NonNull MaterialRoom newItem) {
                    return oldItem.id.equals(newItem.id);
                }

                @Override
                public boolean areContentsTheSame(@NonNull MaterialRoom oldItem, @NonNull MaterialRoom newItem) {
                    return TextUtils.equals(oldItem.name, newItem.name)
                            && TextUtils.equals(oldItem.description, newItem.description)
                            && TextUtils.equals(oldItem.imageFileId, newItem.imageFileId)
                            && oldItem.dirty == newItem.dirty;
                }
            };

    private final Listener listener;

    public MaterialAdapter(@NonNull Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemMaterialBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        MaterialRoom material = getCurrentList().get(position);
        holder.bind(material);
        holder.itemView.setOnClickListener(v -> listener.onMaterialClick(material));
    }

    static final class Holder extends RecyclerView.ViewHolder {

        private final ItemMaterialBinding binding;

        Holder(@NonNull ItemMaterialBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull MaterialRoom material) {
            boolean hasName = !TextUtils.isEmpty(material.name);
            binding.itemMaterialName.setText(hasName
                    ? material.name : itemView.getContext().getString(R.string.material_no_photo));
            ViewUtils.setTextOrGone(binding.itemMaterialDescription, material.description);
            ViewUtils.show(binding.itemMaterialStatus, material.dirty);
            ImageLoader.load(App.container(itemView.getContext()).okHttpClient(),
                    material.imageFileId, binding.itemMaterialPhoto);
        }
    }
}
