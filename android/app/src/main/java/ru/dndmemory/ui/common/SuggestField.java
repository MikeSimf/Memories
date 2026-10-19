package ru.dndmemory.ui.common;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListPopupWindow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Поле с подсказками из справочника: раса из {@code GET /api/races}.
 *
 * <p>Подсказки показываются по нажатию на поле и обновляются при вводе.
 * Свободный текст не запрещается — старые записи и домашние расы остаются
 * рабочими.
 */
public final class SuggestField {

    /** Откуда брать значения: обычно {@code () -> races.cachedNames()}. */
    public interface Values {
        @NonNull
        List<String> values();
    }

    private static final int MAX_HINTS = 12;

    private final EditText field;
    private final Values values;
    private final ListPopupWindow popup;
    private final List<String> shown = new ArrayList<>();
    private boolean applying;

    private SuggestField(@NonNull EditText field, @NonNull Values values) {
        this.field = field;
        this.values = values;
        this.popup = new ListPopupWindow(field.getContext());
        popup.setAnchorView(field);
        popup.setModal(false);
        popup.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < shown.size()) {
                applying = true;
                field.setText(shown.get(position));
                field.setSelection(field.getText().length());
                applying = false;
                field.clearFocus();
            }
            popup.dismiss();
        });
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!applying) {
                    refresh();
                }
            }
        });
        field.setOnClickListener(v -> refresh());
        field.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                refresh();
            } else {
                popup.dismiss();
            }
        });
    }

    /** Включает подсказки для поля. Вызывать один раз. */
    public static void attach(@NonNull EditText field, @NonNull Values values) {
        new SuggestField(field, values);
    }

    private void refresh() {
        String query = field.getText() == null ? "" : field.getText().toString().trim();
        List<String> source = values.values();
        if (source.isEmpty()) {
            popup.dismiss();
            return;
        }
        String needle = query.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String value : source) {
            if (needle.isEmpty() || value.toLowerCase(Locale.ROOT).contains(needle)) {
                matches.add(value);
            }
            if (matches.size() >= MAX_HINTS) {
                break;
            }
        }
        if (matches.isEmpty() || (matches.size() == 1 && matches.get(0).equalsIgnoreCase(query))) {
            popup.dismiss();
            return;
        }
        if (matches.equals(shown) && popup.isShowing()) {
            return;
        }
        shown.clear();
        shown.addAll(matches);
        popup.setAdapter(new ArrayAdapter<>(field.getContext(),
                android.R.layout.simple_list_item_1, shown));
        int width = field.getWidth();
        popup.setWidth(width > 0 ? width : android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        popup.show();
    }
}
