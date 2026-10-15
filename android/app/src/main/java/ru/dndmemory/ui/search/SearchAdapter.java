package ru.dndmemory.ui.search;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import ru.dndmemory.R;
import ru.dndmemory.databinding.ItemSearchBinding;

/** Результаты поиска по локальной базе. */
public class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.SearchHolder> {

    public static final String KIND_ENTITY = "ENTITY";
    public static final String KIND_RETELLING = "RETELLING";

    /** Одна строка результата. */
    public static final class Item {

        public final String kind;
        public final String id;
        public final String type;
        public final CharSequence title;
        public final CharSequence snippet;

        public Item(String kind, String id, String type, CharSequence title, CharSequence snippet) {
            this.kind = kind;
            this.id = id;
            this.type = type;
            this.title = title;
            this.snippet = snippet;
        }
    }

    public interface Listener {
        void onSearchItemClick(@NonNull Item item);
    }

    private final List<Item> items = new ArrayList<>();
    private final Listener listener;

    public SearchAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<Item> list) {
        items.clear();
        if (list != null) {
            items.addAll(list);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SearchHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new SearchHolder(ItemSearchBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull SearchHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    final class SearchHolder extends RecyclerView.ViewHolder {

        private final ItemSearchBinding binding;

        SearchHolder(@NonNull ItemSearchBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull final Item item) {
            binding.itemSearchTitle.setText(item.title);
            binding.itemSearchSnippet.setText(item.snippet);
            binding.itemSearchKind.setText(KIND_RETELLING.equals(item.kind)
                    ? R.string.search_kind_retelling : R.string.search_kind_entity);
            itemView.setOnClickListener(v -> listener.onSearchItemClick(item));
        }
    }
}
