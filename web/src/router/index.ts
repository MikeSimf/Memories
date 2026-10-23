import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

import { useAuthStore } from '@/stores/auth'

/**
 * Маршруты приложения.
 *
 * Кампания — вложенный раздел: `CampaignLayout` держит шапку кампании и вкладки,
 * а вложенные маршруты показывают список, карточку объекта, редактор истории
 * и служебные экраны.
 */
const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true, bare: true, title: 'Вход' },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/RegisterView.vue'),
    meta: { public: true, bare: true, title: 'Регистрация' },
  },
  {
    path: '/forgot-password',
    name: 'password-reset-request',
    component: () => import('@/views/PasswordResetRequestView.vue'),
    meta: { public: true, bare: true, title: 'Сброс пароля' },
  },
  {
    // Ссылку на сброс сервер пишет в лог в виде `…/#/reset/<token>` (`api.md` §3),
    // поэтому маршрут есть и в обычном виде, и с хешем (см. `beforeEach`).
    path: '/reset/:token',
    name: 'password-reset-confirm',
    component: () => import('@/views/PasswordResetConfirmView.vue'),
    meta: { public: true, bare: true, title: 'Новый пароль' },
  },
  {
    path: '/verify-email/:token',
    name: 'verify-email',
    component: () => import('@/views/VerifyEmailView.vue'),
    meta: { public: true, bare: true, title: 'Подтверждение почты' },
  },
  {
    path: '/join/:token',
    name: 'join',
    component: () => import('@/views/JoinView.vue'),
    meta: { public: true, title: 'Приглашение' },
  },
  {
    path: '/',
    name: 'campaigns',
    component: () => import('@/views/CampaignsView.vue'),
    meta: { title: 'Кампании' },
  },
  {
    path: '/feedback',
    name: 'admin-feedback',
    component: () => import('@/views/AdminFeedbackView.vue'),
    meta: { title: 'Обращения', narrow: true },
  },
  {
    path: '/settings',
    name: 'settings',
    component: () => import('@/views/SettingsView.vue'),
    meta: { title: 'Настройки', narrow: true },
  },
  {
    path: '/campaigns/:campaignId',
    component: () => import('@/views/CampaignLayout.vue'),
    children: [
      {
        path: '',
        redirect: (to) => ({ name: 'campaign-tab', params: { campaignId: to.params.campaignId, tab: 'characters' } }),
      },
      {
        // Три вкладки объектов: персонажи (герои и НПС вместе), локации, предметы.
        path: ':tab(characters|locations|items)',
        name: 'campaign-tab',
        component: () => import('@/views/CampaignTabView.vue'),
      },
      {
        // Герои — отдельный раздел кампании, рядом с историими и участниками.
        path: 'heroes',
        name: 'campaign-heroes',
        component: () => import('@/views/CampaignTabView.vue'),
        props: { tab: 'heroes' },
        meta: { title: 'Герои' },
      },
      {
        path: 'npcs',
        redirect: (to) => ({ name: 'campaign-tab', params: { campaignId: to.params.campaignId, tab: 'characters' } }),
      },
      {
        path: 'materials',
        name: 'campaign-materials',
        component: () => import('@/views/MaterialsView.vue'),
        meta: { title: 'Материалы' },
      },
      {
        path: 'materials/new',
        name: 'material-new',
        component: () => import('@/views/MaterialEditView.vue'),
        meta: { title: 'Новый материал' },
      },
      {
        path: 'materials/:materialId',
        name: 'material-detail',
        component: () => import('@/views/MaterialDetailView.vue'),
        meta: { title: 'Материал' },
      },
      {
        path: 'materials/:materialId/edit',
        name: 'material-edit',
        component: () => import('@/views/MaterialEditView.vue'),
        meta: { title: 'Правка материала' },
      },
      {
        // Истории и участники — отдельные разделы кампании, не вкладки.
        path: 'retellings',
        name: 'campaign-retellings',
        component: () => import('@/views/CampaignTabView.vue'),
        props: { tab: 'retellings' },
        meta: { title: 'Истории' },
      },
      {
        path: 'members',
        name: 'campaign-members',
        component: () => import('@/views/CampaignTabView.vue'),
        props: { tab: 'members' },
        meta: { title: 'Участники' },
      },
      {
        path: 'search',
        name: 'campaign-search',
        component: () => import('@/views/SearchView.vue'),
        meta: { title: 'Поиск по кампании' },
      },
      {
        path: 'invites',
        name: 'campaign-invites',
        component: () => import('@/views/InvitesView.vue'),
        meta: { title: 'Приглашения', narrow: true },
      },
      {
        path: 'settings',
        name: 'campaign-settings',
        component: () => import('@/views/CampaignSettingsView.vue'),
        meta: { title: 'Настройки кампании', narrow: true },
      },
      {
        path: 'entities/new',
        name: 'entity-new',
        component: () => import('@/views/EntityEditView.vue'),
        meta: { title: 'Новый объект' },
      },
      {
        path: 'entities/:entityId',
        name: 'entity-detail',
        component: () => import('@/views/EntityDetailView.vue'),
      },
      {
        path: 'entities/:entityId/edit',
        name: 'entity-edit',
        component: () => import('@/views/EntityEditView.vue'),
        meta: { title: 'Правка объекта' },
      },
      {
        path: 'retellings/new',
        name: 'retelling-new',
        component: () => import('@/views/RetellingEditView.vue'),
        meta: { title: 'Новый история' },
      },
      {
        path: 'retellings/:retellingId',
        name: 'retelling-detail',
        component: () => import('@/views/RetellingDetailView.vue'),
      },
      {
        path: 'retellings/:retellingId/edit',
        name: 'retelling-edit',
        component: () => import('@/views/RetellingEditView.vue'),
        meta: { title: 'Правка истории' },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { public: true, title: 'Страница не найдена' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(_to, _from, savedPosition) {
    return savedPosition ?? { top: 0 }
  },
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  auth.initialize()

  // Ссылка сброса пароля приходит в виде `…/#/reset/<token>` (`api.md` §3):
  // перенаправляем её на обычный маршрут `/reset/:token`.
  const hash = window.location.hash
  if (hash.startsWith('#/reset/')) {
    const token = decodeURIComponent(hash.slice('#/reset/'.length))
    if (token) return { name: 'password-reset-confirm', params: { token }, replace: true }
  }

  const isAuthScreen = to.name === 'login' || to.name === 'register'
  if (to.meta.public !== true && !auth.isAuthenticated) {
    return { name: 'login', query: to.fullPath === '/' ? {} : { redirect: to.fullPath } }
  }
  if (isAuthScreen && auth.isAuthenticated) {
    return { name: 'campaigns' }
  }
  return true
})

router.afterEach((to) => {
  const title = typeof to.meta.title === 'string' ? to.meta.title : ''
  document.title = title ? `${title} — DnD Memory` : 'DnD Memory'
})

export default router
