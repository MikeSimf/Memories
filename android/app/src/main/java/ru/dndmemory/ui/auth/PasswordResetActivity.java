package ru.dndmemory.ui.auth;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import ru.dndmemory.App;
import ru.dndmemory.R;
import ru.dndmemory.databinding.ActivityPasswordResetBinding;
import ru.dndmemory.ui.common.ErrorMessages;
import ru.dndmemory.ui.common.ViewModelFactory;
import ru.dndmemory.ui.common.ViewUtils;

/**
 * Сброс пароля: экран с полем почты. Почтовый сервер не настроен, поэтому сервер
 * всегда отвечает {@code 202}, а ссылку передают администратору — так и написано
 * в ответе экрана (docs/api.md, раздел 3).
 *
 * <p>Подтверждение по ссылке делает веб-версия, отдельного экрана в приложении нет.
 */
public class PasswordResetActivity extends AppCompatActivity {

    private ActivityPasswordResetBinding binding;
    private AuthViewModel viewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPasswordResetBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this, ViewModelFactory.of(
                () -> new AuthViewModel(App.container(this).authRepository())))
                .get(AuthViewModel.class);

        setSupportActionBar(binding.passwordResetToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        binding.passwordResetSubmit.setOnClickListener(v -> submit());
        observe();
    }

    private void submit() {
        CharSequence raw = binding.passwordResetEmail.getText();
        String email = raw == null ? "" : raw.toString().trim();
        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.passwordResetEmailInput.setError(getString(R.string.error_required));
            return;
        }
        binding.passwordResetEmailInput.setError(null);
        viewModel.requestPasswordReset(email);
    }

    private void observe() {
        viewModel.resetResult().observe(this, resource -> {
            if (resource == null) {
                return;
            }
            ViewUtils.show(binding.passwordResetProgress, resource.isLoading());
            binding.passwordResetSubmit.setEnabled(!resource.isLoading());
            if (resource.isError()) {
                ErrorMessages.show(binding.getRoot(), resource.error);
                return;
            }
            if (resource.isSuccess()) {
                binding.passwordResetResult.setText(R.string.password_reset_sent);
                ViewUtils.show(binding.passwordResetResult, true);
                ViewUtils.show(binding.passwordResetForm, false);
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
