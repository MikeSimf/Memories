package ru.dndmemory.ui.common;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

/**
 * Фабрика ViewModel без Hilt/Dagger: экран передаёт лямбду, которая берёт
 * зависимости из {@link ru.dndmemory.AppContainer}.
 */
public final class ViewModelFactory implements ViewModelProvider.Factory {

    public interface Creator<T extends ViewModel> {
        @NonNull
        T create();
    }

    private final Creator<? extends ViewModel> creator;

    private ViewModelFactory(Creator<? extends ViewModel> creator) {
        this.creator = creator;
    }

    public static <T extends ViewModel> ViewModelFactory of(@NonNull Creator<T> creator) {
        return new ViewModelFactory(creator);
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        return (T) creator.create();
    }
}
