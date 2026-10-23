<script setup lang="ts">
/**
 * Пароль меняется вручную: пользователь пишет администратору через обратную
 * связь или VK, тот выдаёт ссылку на сброс.
 */
</script>

<template>
  <div class="auth-page">
    <div class="auth-card stack">
      <div class="auth-card__title">
        <img src="/icons/icon-192.svg" alt="" width="40" height="40" />
        <h1 style="margin: 0">Сброс пароля</h1>
      </div>

      <p class="muted">
        Напишите администратору через форму обратной связи или в
        <a href="https://vk.ru/i.mikea" target="_blank" rel="noopener">VK</a> — он вышлет
        ссылку для смены пароля.
      </p>

      <RouterLink class="btn btn--primary btn--block" :to="{ name: 'login' }">
        Вернуться ко входу
      </RouterLink>
    </div>
  </div>
</template>
