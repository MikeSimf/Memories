import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

import { useAuthStore } from '@/stores/auth'

/**
 * Маршруты приложения.
 *
 * Кампания — вложенный раздел: `CampaignLayout` держит шапку кампании и вкладки,
 * а вложенные маршруты показывают список, карточку объекта, редактор пересказа
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
    path: '/oauth/callback',
    name: 'oauth-callback',
    component: () => import('@/views/OAuthCallbackView.vue'),
    meta: { public: true, bare: true, title: 'Вход через провайдера' },
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
        redirect: (to) => ({ name: 'campaign-tab', params: { campaignId: to.params.campaignId, tab: 'heroes' } }),
      },
      {
        path: ':tab(heroes|npcs|locations|items|retellings|members)',
        name: 'campaign-tab',
        component: () => import('@/views/CampaignTabView.vue'),
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
        meta: { title: 'Новый пересказ' },
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
        meta: { title: 'Правка пересказа' },
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

  if (to.meta.public !== true && !auth.isAuthenticated) {
    return { name: 'login', query: to.fullPath === '/' ? {} : { redirect: to.fullPath } }
  }
  if ((to.name === 'login' || to.name === 'register') && auth.isAuthenticated) {
    return { name: 'campaigns' }
  }
  return true
})

router.afterEach((to) => {
  const title = typeof to.meta.title === 'string' ? to.meta.title : ''
  document.title = title ? `${title} — DnD Memory` : 'DnD Memory'
})

export default router
