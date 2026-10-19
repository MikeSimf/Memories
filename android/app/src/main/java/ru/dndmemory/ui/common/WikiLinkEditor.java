package ru.dndmemory.ui.common;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListPopupWindow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ввод ссылок {@code [[Имя]]} в описаниях и пересказах.
 *
 * <p>Две вещи, которых не хватало:
 * <ul>
 *   <li>после набранных {@code [[} сразу подставляется закрывающее {@code ]]},
 *       а курсор встаёт между ними — не нужно искать квадратные скобки на клавиатуре;</li>
 *   <li>после трёх введённых символов над полем появляется список подходящих
 *       названий объектов кампании; выбор подставляет имя целиком.</li>
 * </ul>
 *
 * <p>Подсказки берутся из {@link NameIndex} — он уже построен для подсветки ссылок.
 */
public final class WikiLinkEditor {

    /** Откуда брать названия: обычно {@code () -> nameIndex.names()}. */
    public interface Names {
        @NonNull
        List<String> names();
    }

    private static final String OPEN = "[[";
    private static final String CLOSE = "]]";
    private static final int MIN_QUERY = 3;
    private static final int MAX_HINTS = 6;

    private final EditText field;
    private final Names names;
    private final ListPopupWindow popup;
    private final List<String> shown = new ArrayList<>();

    /** Защита от рекурсии: правки из самого редактора не должны снова открывать подсказки. */
    private boolean applying;

    private WikiLinkEditor(@NonNull EditText field, @NonNull Names names) {
        this.field = field;
        this.names = names;
        this.popup = new ListPopupWindow(field.getContext());
        popup.setAnchorView(field);
        popup.setModal(false);
        popup.setOnItemClickListener((parent, view, position, id) -> applyHint(position));
        field.addTextChangedListener(new Watcher());
    }

    /** Включает подсказки для поля. Вызывать один раз на поле. */
    public static void attach(@NonNull EditText field, @NonNull Names names) {
        new WikiLinkEditor(field, names);
    }

    private final class Watcher implements TextWatcher {

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (applying || count != OPEN.length() || s.length() < start + count) {
                return;
            }
            // Пользователь только что набрал «[[» — закрываем скобки и встаём внутрь.
            if (!OPEN.contentEquals(s.subSequence(start, start + count))) {
                return;
            }
            Editable text = field.getText();
            int caret = start + count;
            if (caret + CLOSE.length() > text.length()
                    || !CLOSE.contentEquals(text.subSequence(caret, caret + CLOSE.length()))) {
                applying = true;
                text.insert(caret, CLOSE);
                field.setSelection(caret);
                applying = false;
            }
        }

        @Override
        public void afterTextChanged(Editable s) {
            if (applying) {
                return;
            }
            refreshHints();
        }
    }

    /** Показывает или прячет список подсказок по тексту до курсора. */
    private void refreshHints() {
        String query = currentQuery();
        if (query == null || query.length() < MIN_QUERY) {
            popup.dismiss();
            return;
        }
        List<String> matches = matching(query);
        if (matches.isEmpty()) {
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

    /**
     * Текст, набранный внутри незакрытой ссылки, или {@code null}, если курсор
     * не внутри {@code [[ … ]]}.
     */
    @Nullable
    private String currentQuery() {
        int caret = field.getSelectionEnd();
        if (caret < 0 || caret > field.length()) {
            return null;
        }
        String text = field.getText().toString();
        int open = text.lastIndexOf(OPEN, caret - OPEN.length());
        if (open < 0) {
            return null;
        }
        String inside = text.substring(open + OPEN.length(), caret);
        if (inside.contains(CLOSE)) {
            return null;
        }
        return inside.trim();
    }

    @NonNull
    private List<String> matching(@NonNull String query) {
        String needle = query.toLowerCase(Locale.ROOT);
        List<String> starts = new ArrayList<>();
        List<String> contains = new ArrayList<>();
        for (String name : names.names()) {
            if (name == null || name.isEmpty()) {
                continue;
            }
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.startsWith(needle)) {
                starts.add(name);
            } else if (lower.contains(needle)) {
                contains.add(name);
            }
        }
        starts.addAll(contains);
        return starts.size() > MAX_HINTS ? new ArrayList<>(starts.subList(0, MAX_HINTS)) : starts;
    }

    /** Подставляет выбранное имя вместо набранных символов, закрывающие скобки остаются. */
    private void applyHint(int position) {
        if (position < 0 || position >= shown.size()) {
            return;
        }
        String name = shown.get(position);
        int caret = field.getSelectionEnd();
        String text = field.getText().toString();
        int open = text.lastIndexOf(OPEN, caret - OPEN.length());
        if (open < 0) {
            popup.dismiss();
            return;
        }
        int from = open + OPEN.length();
        applying = true;
        Editable editable = field.getText();
        editable.replace(from, caret, name);
        // Автозакрытие: после имени всегда стоят «]]», даже если пользователь
        // стёр их или пришёл из списка без закрывающих скобок.
        int after = from + name.length();
        if (after + CLOSE.length() > editable.length()
                || !CLOSE.contentEquals(editable.subSequence(after, after + CLOSE.length()))) {
            editable.insert(after, CLOSE);
        }
        field.setSelection(after);
        applying = false;
        shown.clear();
        popup.dismiss();
        // Возвращаем фокус: после выбора обычно продолжают печатать.
        field.requestFocus();
        View anchor = field;
        anchor.post(field::invalidate);
    }
}
