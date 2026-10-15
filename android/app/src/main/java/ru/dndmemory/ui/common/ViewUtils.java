package ru.dndmemory.ui.common;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

/** Мелкие помощники для экранов. */
public final class ViewUtils {

    private ViewUtils() {
    }

    public static void toast(@NonNull Context context, @NonNull CharSequence text) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
    }

    public static void toast(@NonNull Context context, @StringRes int textRes) {
        Toast.makeText(context, textRes, Toast.LENGTH_SHORT).show();
    }

    /** Показать или скрыть элемент (GONE, а не INVISIBLE — не занимает место). */
    public static void show(@NonNull View view, boolean visible) {
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public static void setTextOrGone(@NonNull android.widget.TextView view, CharSequence text) {
        if (text == null || text.length() == 0) {
            view.setVisibility(View.GONE);
        } else {
            view.setText(text);
            view.setVisibility(View.VISIBLE);
        }
    }
}
