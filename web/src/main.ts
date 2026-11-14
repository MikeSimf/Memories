import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import { useAuthStore } from './stores/auth'
import { useSyncStore } from './stores/sync'
import { useUiStore } from './stores/ui'
import './styles/main.css'
import { registerSW } from 'virtual:pwa-register'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)

// Тема, подписки на сеть и уведомления синхронизации — до первого рендера.
useUiStore(pinia).initialize()
useAuthStore(pinia).initialize()
useSyncStore(pinia).initialize()

app.use(router)
app.mount('#app')

// PWA: после установки новой версии перезагружаем страницу один раз,
// чтобы пользователь не оставался на старом бандле из кэша service worker.
const updateSW = registerSW({
  immediate: true,
  onNeedRefresh() {
    void updateSW(true)
  },
})
if ('serviceWorker' in navigator) {
  navigator.serviceWorker.addEventListener('controllerchange', () => {
    const key = 'dnd-memory.sw-reloaded'
    if (sessionStorage.getItem(key) === '1') return
    sessionStorage.setItem(key, '1')
    window.location.reload()
  })
}
