package ru.dndmemory.ui.invite;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import ru.dndmemory.R;
import ru.dndmemory.data.remote.dto.DeviceDto;
import ru.dndmemory.databinding.ItemDeviceBinding;
import ru.dndmemory.ui.common.ViewUtils;
import ru.dndmemory.util.DateFormats;

/** Устройства кампании: имя, подпись, платформа, последний вход и кнопка «Отозвать». */
public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.DeviceHolder> {

    public interface Listener {
        void onDeviceRevoke(@NonNull DeviceDto device);
    }

    private final List<DeviceDto> items = new ArrayList<>();
    private final Listener listener;

    public DeviceAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<DeviceDto> list) {
        items.clear();
        if (list != null) {
            items.addAll(list);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DeviceHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemDeviceBinding binding = ItemDeviceBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new DeviceHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    final class DeviceHolder extends RecyclerView.ViewHolder {

        private final ItemDeviceBinding binding;

        DeviceHolder(@NonNull ItemDeviceBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull final DeviceDto device) {
            Context context = itemView.getContext();
            binding.itemDeviceName.setText(context.getString(R.string.device_name,
                    TextUtils.isEmpty(device.displayName)
                            ? context.getString(R.string.attr_not_set) : device.displayName));
            ViewUtils.setTextOrGone(binding.itemDeviceLabel, TextUtils.isEmpty(device.label)
                    ? null : context.getString(R.string.device_label, device.label));
            ViewUtils.setTextOrGone(binding.itemDevicePlatform, TextUtils.isEmpty(device.platform)
                    ? null : context.getString(R.string.device_platform, device.platform));
            binding.itemDeviceLastSeen.setText(context.getString(R.string.device_last_seen,
                    TextUtils.isEmpty(device.lastSeenAt)
                            ? context.getString(R.string.sync_never)
                            : DateFormats.formatIso(device.lastSeenAt)));

            // Отозванное устройство остаётся в списке, но повторно отзывать нечего.
            ViewUtils.show(binding.itemDeviceRevoked, device.revoked);
            ViewUtils.show(binding.itemDeviceRevoke, !device.revoked);
            binding.itemDeviceRevoke.setOnClickListener(v -> listener.onDeviceRevoke(device));
        }
    }
}
