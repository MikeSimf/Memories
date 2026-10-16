package ru.dndmemory.ui.splash;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;

import ru.dndmemory.App;
import ru.dndmemory.databinding.ActivitySplashBinding;
import ru.dndmemory.ui.common.Nav;

/**
 * Точка входа. Показывает логотип и сразу решает: показать экран входа или
 * список кампаний. Решение принимается по данным сессии, а не по сети, поэтому
 * приложение открывается и без интернета.
 *
 * <p>Если сессию прервали до запуска, сохраняем причину и передаём её на экран
 * входа.
 */
public class SplashActivity extends AppCompatActivity {

    private ActivitySplashBinding binding;
    private boolean navigated;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        LiveData<Boolean> loggedIn = App.container(this).authRepository().loggedIn();
        loggedIn.observe(this, this::route);
    }

    private void route(Boolean logged) {
        if (navigated) {
            return;
        }
        navigated = true;
        Intent next;
        if (Boolean.TRUE.equals(logged)) {
            next = new Intent(this, ru.dndmemory.ui.campaigns.CampaignListActivity.class);
        } else {
            // Пояснение показываем только если сессию действительно прервали.
            String reason = App.container(this).sessionStore().takeRevokeReason();
            next = Nav.auth(this, null, reason);
            App.container(this).sessionStore().clearRevokedEvent();
        }
        startActivity(next);
        finish();
    }
}
