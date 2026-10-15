import 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    /** Доступно без авторизации. */
    public?: boolean
    /** Без шапки и общей рамки (экраны входа, OAuth, приглашения). */
    bare?: boolean
    /** Узкая колонка контента. */
    narrow?: boolean
    /** Заголовок вкладки браузера. */
    title?: string
  }
}

export {}
