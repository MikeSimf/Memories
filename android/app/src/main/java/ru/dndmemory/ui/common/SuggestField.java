package ru.dndmemory.ui.common;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListPopupWindow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ru.dndmemory.R;

/**
 * Поле со списком значений из справочника: раса, класс, тип локации, тип предмета.
 *
 * <p>По нажатию показывается <b>весь</b> список, а не только совпадения с уже
 * введённым текстом: человек сразу видит, из чего выбирать. Ввод при этом
 * фильтрует список, как обычно. Свободный текст не запрещается — старые записи
 * остаются рабочими, окончательную проверку делает редактор.</p>
 *
 * <p>Список рисует собственный {@link ListPopupWindow}, а не встроенный в
 * {@code AutoCompleteTextView}: встроенный измеряет высоту по результатам
 * асинхронной фильтрации и на первом показе открывается высотой в одну
 * сплющенную строку. Здесь адаптер заполняется синхронно, поэтому первый же
 * показ выглядит правильно.</p>
 */
public final class SuggestField {

    /** Откуда брать значения: обычно {@code () -> races.cachedNames()}. */
    public interface Values {
        @NonNull
        List<String> values();
    }

    /** Сколько строк видно без прокрутки; остальное листается. */
    private static final int MAX_VISIBLE = 5;
    private static final int ROW_DP = 48;

    private final EditText field;
    private final Values values;
    private final ListPopupWindow popup;
    private final List<String> shown = new ArrayList<>();
    private final ArrayAdapter<String> adapter;
    private boolean applying;
    private long openedAt;

    private SuggestField(@NonNull EditText field, @NonNull Values values) {
        this.field = field;
        this.values = values;
        Context context = field.getContext();

        adapter = new ArrayAdapter<>(context, R.layout.item_suggestion, shown);

        popup = new ListPopupWindow(context);
        popup.setAnchorView(field);
        popup.setModal(false);
        popup.setInputMethodMode(ListPopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setBackgroundDrawable(ContextCompat.getDrawable(context, R.drawable.bg_suggest_popup));
        popup.setAdapter(adapter);
        popup.setOnItemClickListener((parent, view, position, id) -> choose(position));

        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!applying && field.hasFocus()) {
                    open(false, false);
                }
            }
        });

        // Нажатие по уже сфокусированному полю: список закрыт — открываем весь,
        // открыт — закрываем. Проверка по времени не даёт «открыть и тут же
        // закрыть» на первом касании, когда фокус и клик приходят вместе.
        field.setOnClickListener(v -> {
            if (System.currentTimeMillis() - openedAt < 350) {
                return;
            }
            if (popup.isShowing()) {
                popup.dismiss();
            } else {
                open(true, true);
            }
        });
        field.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                open(true, true);
            } else {
                popup.dismiss();
            }
        });
        field.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP
                    && popup.isShowing()) {
                popup.dismiss();
                return true;
            }
            return false;
        });
        field.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                popup.dismiss();
            }
        });
    }

    /** Включает список значений для поля. Повторный вызов ничего не меняет. */
    public static void attach(@NonNull EditText field, @NonNull Values values) {
        if (field.getTag(R.id.tag_suggest_field) != null) {
            return;
        }
        field.setTag(R.id.tag_suggest_field, Boolean.TRUE);
        new SuggestField(field, values);
    }

    private void choose(int position) {
        if (position < 0 || position >= shown.size()) {
            return;
        }
        applying = true;
        field.setText(shown.get(position));
        field.setSelection(field.getText().length());
        applying = false;
        popup.dismiss();
        field.clearFocus();
    }

    /**
     * @param all   показать весь список, без фильтра по текущему тексту
     * @param focus открытие по касанию: текст выделяется, чтобы ввод сразу
     *              заменял выбранное значение
     */
    private void open(boolean all, boolean focus) {
        if (!field.isShown() || field.getWindowToken() == null) {
            return;
        }
        List<String> source = values.values();
        if (source.isEmpty()) {
            popup.dismiss();
            return;
        }
        String query = all ? "" : text();
        List<String> matches = filter(source, query);
        if (matches.isEmpty()) {
            popup.dismiss();
            return;
        }
        if (all && focus && !query.isEmpty() && contains(source, query)) {
            // Старое значение подсвечивается после обработки касания: иначе
            // TextView на ACTION_UP снова ставит курсор и снимает выделение.
            field.post(() -> {
                if (popup.isShowing()) {
                    field.setSelection(0, field.getText().length());
                }
            });
        }
        shown.clear();
        shown.addAll(matches);
        adapter.notifyDataSetChanged();

        int width = field.getWidth();
        popup.setWidth(width > 0 ? width : ViewGroup.LayoutParams.WRAP_CONTENT);
        popup.setHeight(dp(field.getContext(), ROW_DP * Math.min(shown.size(), MAX_VISIBLE) + 8));
        openedAt = System.currentTimeMillis();
        if (popup.isShowing()) {
            popup.dismiss();
        }
        popup.show();
    }

    @NonNull
    private String text() {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }

    @NonNull
    private static List<String> filter(@NonNull List<String> source, @NonNull String query) {
        String needle = normalize(query);
        List<String> out = new ArrayList<>();
        for (String value : source) {
            if (value == null || value.isEmpty()) {
                continue;
            }
            if (needle.isEmpty() || normalize(value).contains(needle)) {
                out.add(value);
            }
        }
        return out;
    }

    private static boolean contains(@NonNull List<String> source, @NonNull String query) {
        String needle = normalize(query);
        for (String value : source) {
            if (value != null && normalize(value).equals(needle)) {
                return true;
            }
        }
        return false;
    }

    /** «Драконорождённый» и «драконорожденный» — одно и то же. */
    @NonNull
    private static String normalize(@Nullable String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('ё', 'е');
    }

    private static int dp(@NonNull Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
