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
