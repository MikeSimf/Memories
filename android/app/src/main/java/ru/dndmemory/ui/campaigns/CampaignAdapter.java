package ru.dndmemory.ui.campaigns;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import ru.dndmemory.R;
import ru.dndmemory.data.local.entity.CampaignRoom;
import ru.dndmemory.databinding.ItemCampaignBinding;
import ru.dndmemory.ui.common.Labels;
import ru.dndmemory.ui.common.ViewUtils;
import ru.dndmemory.util.DateFormats;

/** Список кампаний. */
public class CampaignAdapter extends RecyclerView.Adapter<CampaignAdapter.CampaignHolder> {

    public interface Listener {
        void onCampaignClick(@NonNull CampaignRoom campaign);

        void onCampaignLongClick(@NonNull CampaignRoom campaign);
    }

    private final List<CampaignRoom> items = new ArrayList<>();
    private final Listener listener;

    public CampaignAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<CampaignRoom> list) {
        items.clear();
        if (list != null) {
            items.addAll(list);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CampaignHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCampaignBinding binding = ItemCampaignBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new CampaignHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull CampaignHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    final class CampaignHolder extends RecyclerView.ViewHolder {

        private final ItemCampaignBinding binding;

        CampaignHolder(@NonNull ItemCampaignBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull final CampaignRoom campaign) {
            binding.itemCampaignTitle.setText(campaign.title);
            ViewUtils.setTextOrGone(binding.itemCampaignDescription, campaign.description);
            binding.itemCampaignRole.setText(Labels.roleText(itemView.getContext(), campaign.myRole));
            binding.itemCampaignCounts.setText(itemView.getContext().getString(
                    R.string.campaign_counts, campaign.memberCount, campaign.entityCount,
                    campaign.retellingCount));
            binding.itemCampaignUpdated.setText(itemView.getContext().getString(
                    R.string.campaign_updated, DateFormats.formatIso(campaign.updatedAt)));

            itemView.setOnClickListener(v -> listener.onCampaignClick(campaign));
            itemView.setOnLongClickListener(v -> {
                listener.onCampaignLongClick(campaign);
                return true;
            });
        }
    }
}
