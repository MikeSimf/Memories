package ru.dndmemory.ui.material;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import ru.dndmemory.App;
import ru.dndmemory.data.model.Attachment;
import ru.dndmemory.databinding.ItemMaterialImageBinding;
import ru.dndmemory.ui.common.ImageLoader;

/** Листалка картинок материала. */
public class MaterialImagesAdapter extends RecyclerView.Adapter<MaterialImagesAdapter.Holder> {

    private final List<Attachment> images = new ArrayList<>();

    public void submit(@NonNull List<Attachment> next) {
        images.clear();
        for (Attachment file : next) {
            if (file != null && file.isImage()) {
                images.add(file);
            }
        }
        notifyDataSetChanged();
    }

    public int count() {
        return images.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemMaterialImageBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        ImageLoader.load(App.container(holder.itemView.getContext()).okHttpClient(),
                images.get(position).id, holder.image);
    }

    @Override
    public int getItemCount() {
        return images.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView image;

        Holder(ItemMaterialImageBinding binding) {
            super(binding.getRoot());
            image = binding.materialImage;
        }
    }
}
