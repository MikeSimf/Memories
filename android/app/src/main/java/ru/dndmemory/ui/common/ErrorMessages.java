package ru.dndmemory.ui.common;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.material.snackbar.Snackbar;

import ru.dndmemory.R;
import ru.dndmemory.data.remote.ApiException;

/** Единый текст ошибок: коды из docs/api.md превращаются в понятные фразы. */
public final class ErrorMessages {

    private ErrorMessages() {
    }

    @StringRes
    public static int resFor(@Nullable ApiException e) {
        if (e == null) {
            return R.string.error_unknown;
        }
        if (e.isNetwork()) {
            return R.string.error_network;
        }
        String code = e.getCode();
        // Контакты для входа: сервер различает занятую почту и занятый телефон.
        if ("email_taken".equals(code)) {
            return R.string.error_email_taken;
        }
        if ("login_taken".equals(code)) {
            return R.string.error_login_taken;
        }
        if ("phone_taken".equals(code)) {
            return R.string.error_phone_taken;
        }
        if ("invite_invalid".equals(code)) {
            return R.string.error_invite_invalid;
        }
        if ("draft_not_synced".equals(code)) {
            return R.string.retelling_publish_hint;
        }
        if (e.isUnauthorized()) {
            // 401 на смене пароля — это не «войдите заново», а неверный текущий пароль.
            String message = e.getServerMessage();
            if (message != null && message.toLowerCase(java.util.Locale.ROOT).contains("парол")) {
                return R.string.error_password_wrong;
            }
            return R.string.error_unauthorized;
        }
        if (e.isForbidden()) {
            return R.string.error_forbidden;
        }
        if (e.isNotFound()) {
            return R.string.error_not_found;
        }
        if (e.isConflict()) {
            return R.string.error_conflict;
        }
        if (e.isRateLimited()) {
            return R.string.error_rate_limited;
        }
        if (e.isResyncRequired()) {
            return R.string.error_conflict;
        }
        if ("internal_error".equals(code)) {
            return R.string.error_server;
        }
        return R.string.error_unknown;
    }

    @NonNull
    public static CharSequence text(@NonNull Context context, @Nullable ApiException e) {
        int res = resFor(e);
        // Для ошибок валидации сервер присылает понятное сообщение — покажем его.
        if (e != null && res == R.string.error_unknown
                && e.getServerMessage() != null && !e.getServerMessage().isEmpty()) {
            return context.getString(R.string.error_api, e.getServerMessage());
        }
        return context.getString(res);
    }

    /** Показывает сообщение над ближайшим корневым View. */
    public static void show(@NonNull View anchor, @Nullable ApiException e) {
        Snackbar.make(anchor, text(anchor.getContext(), e), Snackbar.LENGTH_LONG).show();
    }

    /** Показывает произвольный текст (например, «Недостаточно прав»). */
    public static void showText(@NonNull View anchor, @StringRes int messageRes) {
        Snackbar.make(anchor, messageRes, Snackbar.LENGTH_LONG).show();
    }
}
