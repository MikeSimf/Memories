package ru.dndmemory.ui.common;

import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.util.ArrayList;
import java.util.List;

import ru.dndmemory.R;

/**
 * Выпадающие списки «выбери из готовых»: класс объекта, игрок, владелец предмета.
 *
 * <p>Список открывается целиком по нажатию. Материальное поле измеряет высоту
 * popup до того, как адаптер закончит фильтрацию, и первый показ выходит
 * высотой в одну сплющенную строку; прогрев сразу после установки адаптера
 * убирает этот эффект.</p>
 */
public final class Dropdowns {

    private Dropdowns() {
    }

    public static void bind(@NonNull MaterialAutoCompleteTextView field,
                            @NonNull List<String> labels) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(field.getContext(),
                R.layout.item_suggestion, new ArrayList<>(labels));
        field.setAdapter(adapter);
        field.post(() -> {
            if (field.getWindowToken() == null || field.isFocused() || !field.isShown()) {
                return;
            }
            field.showDropDown();
            field.dismissDropDown();
        });
    }
}
