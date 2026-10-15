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
 * Точка входа. Показывает логотип и сразу решает: показать вход или список
 * кампаний. Решение принимается по данным сессии, а не по сети, поэтому
 * приложение открывается и без интернета.
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
        Intent next = Boolean.TRUE.equals(logged)
                ? new Intent(this, ru.dndmemory.ui.campaigns.CampaignListActivity.class)
                : Nav.auth(this, null);
        startActivity(next);
        finish();
    }
}
