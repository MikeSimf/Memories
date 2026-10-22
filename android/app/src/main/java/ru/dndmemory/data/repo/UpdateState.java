package ru.dndmemory.data.repo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import ru.dndmemory.data.remote.ApiCall;
import ru.dndmemory.data.remote.ApiService;
import ru.dndmemory.data.remote.dto.AppUpdateDto;

/**
 * Общее состояние проверки обновлений: одно на всё приложение.
 *
 * <p>Проверка запускается и при входе, и после каждой синхронизации. Экраны
 * подписаны на {@link #info()} и сами решают, показать диалог, баннер или
 * метку «доступно обновление». Здесь же хранится история версий.</p>
 */
public final class UpdateState {

    private static final MutableLiveData<UpdateRepository.UpdateInfo> INFO = new MutableLiveData<>();
    /** Какую версию уже предлагали в этом запуске: не показываем диалог повторно. */
    private static int promptedCode = -1;
    private static final MutableLiveData<java.util.List<ru.dndmemory.data.remote.dto.AppVersionDto>> VERSIONS =
            new MutableLiveData<>();

    private UpdateState() {
    }

    @NonNull
    public static LiveData<UpdateRepository.UpdateInfo> info() {
        return INFO;
    }

    @NonNull
    public static LiveData<java.util.List<ru.dndmemory.data.remote.dto.AppVersionDto>> versions() {
        return VERSIONS;
    }

    /** Проверка версии из фонового потока: результат придёт в {@link #info()}. */
    public static void refresh(@NonNull ApiService api) {
        AppUpdateDto dto;
        try {
            dto = ApiCall.execute(api.appLatest());
        } catch (ru.dndmemory.data.remote.ApiException e) {
            return;
        }
        if (dto == null) {
            return;
        }
        int current = ru.dndmemory.BuildConfig.VERSION_CODE;
        if (dto.versionCode <= current) {
            INFO.postValue(null);
            return;
        }
        INFO.postValue(new UpdateRepository.UpdateInfo(
                current < dto.minVersionCode, dto.versionCode, dto.versionName, dto.url, dto.notes));
    }

    /** История версий из {@code GET /api/app/versions}. */
    public static void loadVersions(@NonNull ApiService api) {
        java.util.List<ru.dndmemory.data.remote.dto.AppVersionDto> list;
        try {
            list = ApiCall.execute(api.appVersions());
        } catch (ru.dndmemory.data.remote.ApiException e) {
            return;
        }
        if (list != null) {
            VERSIONS.postValue(list);
        }
    }

    /** Пора ли показать диалог: один раз на версию за запуск. */
    public static boolean shouldPrompt(@Nullable UpdateRepository.UpdateInfo info) {
        if (info == null || promptedCode == info.versionCode) {
            return false;
        }
        promptedCode = info.versionCode;
        return true;
    }

    /** Ручная публикация из {@link UpdateRepository#check}. */
    public static void publish(@Nullable UpdateRepository.UpdateInfo info) {
        INFO.postValue(info);
    }
}
