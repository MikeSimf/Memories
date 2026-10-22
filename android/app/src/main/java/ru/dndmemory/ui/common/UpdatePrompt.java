package ru.dndmemory.ui.common;

import android.app.Activity;

import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import ru.dndmemory.R;
import ru.dndmemory.data.repo.UpdateRepository;

/** Единый диалог обновления: обязательный не закрывается, обычный можно отложить. */
public final class UpdatePrompt {

    private UpdatePrompt() {
    }

    public static void show(@NonNull final Activity activity,
                            @NonNull final UpdateRepository.UpdateInfo info) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle(info.required
                        ? R.string.update_required_title : R.string.update_available_title)
                .setMessage(activity.getString(info.required
                        ? R.string.update_required_text : R.string.update_available_text,
                        info.versionName == null ? "" : info.versionName))
                .setCancelable(!info.required)
                .setPositiveButton(R.string.update_download, (dialog, which) ->
                        ApkInstaller.downloadAndInstall(activity, info.url))
                .setNegativeButton(info.required ? null : activity.getString(R.string.update_later), null)
                .show();
    }
}
