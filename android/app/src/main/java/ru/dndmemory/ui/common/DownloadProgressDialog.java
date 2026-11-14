package ru.dndmemory.ui.common;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;

/** Небольшой диалог с горизонтальным прогрессом загрузки. */
public final class DownloadProgressDialog {

    private final AlertDialog dialog;
    private final ProgressBar bar;
    private final TextView label;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private DownloadProgressDialog(@NonNull Activity activity, @NonNull String title) {
        int pad = (int) (20 * activity.getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);
        label = new TextView(activity);
        label.setText(title);
        bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = pad / 2;
        box.addView(label);
        box.addView(bar, params);
        dialog = new AlertDialog.Builder(activity)
                .setView(box)
                .setCancelable(false)
                .create();
    }

    public static DownloadProgressDialog show(@NonNull Context context, @NonNull String title) {
        if (!(context instanceof Activity)) {
            return null;
        }
        DownloadProgressDialog result = new DownloadProgressDialog((Activity) context, title);
        result.dialog.show();
        return result;
    }

    public void progress(int percent) {
        handler.post(() -> {
            if (dialog.isShowing()) {
                bar.setProgress(Math.max(0, Math.min(100, percent)));
                label.setText(percent + "%");
            }
        });
    }

    public void dismiss() {
        handler.post(() -> {
            if (dialog.isShowing()) {
                dialog.dismiss();
            }
        });
    }
}
