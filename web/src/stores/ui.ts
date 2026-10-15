import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

export type ThemeMode = 'light' | 'dark' | 'system'

export type ToastKind = 'info' | 'success' | 'warning' | 'error'

export interface Toast {
  id: number
  kind: ToastKind
  message: string
  /** Показывать кнопку «Повторить» — используется в ошибках синхронизации. */
  action?: { label: string; run: () => void }
}

const THEME_KEY = 'dnd-memory.theme'
let toastCounter = 0

function readStoredTheme(): ThemeMode {
  try {
    const stored = window.localStorage.getItem(THEME_KEY)
    if (stored === 'light' || stored === 'dark' || stored === 'system') return stored
  } catch {
    /* приватный режим — работаем без сохранения */
  }
  return 'system'
}

export const useUiStore = defineStore('ui', () => {
  const themeMode = ref<ThemeMode>(readStoredTheme())
  const systemPrefersDark = ref(
    typeof window !== 'undefined' && typeof window.matchMedia === 'function'
      ? window.matchMedia('(prefers-color-scheme: dark)').matches
      : true,
  )
  const toasts = ref<Toast[]>([])
  const online = ref(typeof navigator === 'undefined' ? true : navigator.onLine)
  const sidebarOpen = ref(false)

  const effectiveTheme = computed<'light' | 'dark'>(() =>
    themeMode.value === 'system' ? (systemPrefersDark.value ? 'dark' : 'light') : themeMode.value,
  )

  function applyTheme(): void {
    const root = document.documentElement
    root.dataset.theme = effectiveTheme.value
    root.style.colorScheme = effectiveTheme.value
  }

  function setTheme(mode: ThemeMode): void {
    themeMode.value = mode
    try {
      window.localStorage.setItem(THEME_KEY, mode)
    } catch {
      /* игнорируем невозможность сохранить */
    }
    applyTheme()
  }

  function toggleTheme(): void {
    setTheme(effectiveTheme.value === 'dark' ? 'light' : 'dark')
  }

  function pushToast(
    kind: ToastKind,
    message: string,
    options: { timeoutMs?: number; action?: Toast['action'] } = {},
  ): number {
    toastCounter += 1
    const id = toastCounter
    const toast: Toast = { id, kind, message, action: options.action }
    toasts.value = [...toasts.value, toast]
    const timeout = options.timeoutMs ?? (kind === 'error' ? 8000 : 4000)
    if (timeout > 0) {
      setTimeout(() => dismissToast(id), timeout)
    }
    return id
  }

  function dismissToast(id: number): void {
    toasts.value = toasts.value.filter((toast) => toast.id !== id)
  }

  function toggleSidebar(value?: boolean): void {
    sidebarOpen.value = value ?? !sidebarOpen.value
  }

  let initialized = false

  /** Подписка на системную тему и состояние сети — один раз на приложение. */
  function initialize(): void {
    if (initialized || typeof window === 'undefined') return
    initialized = true
    applyTheme()

    if (typeof window.matchMedia === 'function') {
      const query = window.matchMedia('(prefers-color-scheme: dark)')
      query.addEventListener('change', (event) => {
        systemPrefersDark.value = event.matches
        applyTheme()
      })
    }

    window.addEventListener('online', () => {
      online.value = true
      pushToast('success', 'Связь восстановлена')
    })
    window.addEventListener('offline', () => {
      online.value = false
      pushToast('warning', 'Нет сети — правки сохраняются на устройстве')
    })
  }

  return {
    themeMode,
    effectiveTheme,
    toasts,
    online,
    sidebarOpen,
    setTheme,
    toggleTheme,
    applyTheme,
    pushToast,
    dismissToast,
    toggleSidebar,
    initialize,
  }
})
