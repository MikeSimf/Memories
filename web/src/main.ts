import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import { useAuthStore } from './stores/auth'
import { useSyncStore } from './stores/sync'
import { useUiStore } from './stores/ui'
import './styles/main.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)

// Тема, подписки на сеть и уведомления синхронизации — до первого рендера.
useUiStore(pinia).initialize()
useAuthStore(pinia).initialize()
useSyncStore(pinia).initialize()

app.use(router)
app.mount('#app')

// Веб всегда работает онлайн: старый service worker мог годами отдавать
// закэшированный бандл. Снимаем регистрацию и чистим кэши один раз.
if ('serviceWorker' in navigator) {
  void navigator.serviceWorker.getRegistrations().then((registrations) => {
    for (const registration of registrations) void registration.unregister()
  })
}
if ('caches' in window) {
  void caches.keys().then((keys) => Promise.all(keys.map((key) => caches.delete(key))))
}
