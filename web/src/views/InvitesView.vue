<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { errorMessage } from '@/api'
import { MEMBER_ROLE_LABELS, MEMBER_ROLES, type EntityType, type InviteDto, type MemberRole } from '@/api/types'
import BaseModal from '@/components/BaseModal.vue'
import EmptyState from '@/components/EmptyState.vue'
import EntityPicker from '@/components/EntityPicker.vue'
import QrScannerDialog from '@/components/QrScannerDialog.vue'
import { useCampaignStore } from '@/stores/campaign'
import { useUiStore } from '@/stores/ui'
import { formatDateTime } from '@/utils/datetime'
import { generateQrDataUrl } from '@/utils/qr'

const router = useRouter()
const campaign = useCampaignStore()
const ui = useUiStore()

const HERO_TYPES: EntityType[] = ['HERO']

const role = ref<MemberRole>('EDITOR_HERO')
const linkedEntityId = ref<string | null>(null)
const expiresInHours = ref(168)
const maxUses = ref(1)

const busy = ref(false)
const creating = ref(false)
const created = ref<InviteDto | null>(null)
const activeInvite = ref<InviteDto | null>(null)
const qrCache = ref<Record<string, string>>({})
const scannerOpen = ref(false)

const canInvite = computed(() => campaign.canInvite)

onMounted(async () => {
  if (!canInvite.value) return
  try {
    await campaign.reloadInvites()
  } catch (caught) {
    ui.pushToast('error', errorMessage(caught))
  }
})

function absoluteUrl(invite: InviteDto): string {
  if (/^https?:\/\//i.test(invite.url)) return invite.url
  return `${window.location.origin}${invite.url.startsWith('/') ? '' : '/'}${invite.url}`
}

async function qrFor(invite: InviteDto): Promise<string> {
  const cached = qrCache.value[invite.id]
  if (cached) return cached
  const dataUrl = await generateQrDataUrl(absoluteUrl(invite))
  qrCache.value = { ...qrCache.value, [invite.id]: dataUrl }
  return dataUrl
}

async function openQr(invite: InviteDto): Promise<void> {
  activeInvite.value = invite
  await qrFor(invite)
}

async function create(): Promise<void> {
  creating.value = true
  try {
    const invite = await campaign.createInvite({
      role: role.value,
      linkedEntityId: linkedEntityId.value,
      expiresInHours: expiresInHours.value,
      maxUses: maxUses.value,
    })
    created.value = invite
    await qrFor(invite)
    ui.pushToast('success', 'Приглашение создано')
  } catch (caught) {
    ui.pushToast('error', errorMessage(caught))
  } finally {
    creating.value = false
  }
}

async function revoke(invite: InviteDto): Promise<void> {
  if (!window.confirm(`Отозвать приглашение ${invite.token}?`)) return
  busy.value = true
  try {
    await campaign.revokeInvite(invite.id)
    ui.pushToast('success', 'Приглашение отозвано')
  } catch (caught) {
    ui.pushToast('error', errorMessage(caught))
  } finally {
    busy.value = false
  }
}

async function copy(text: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(text)
    ui.pushToast('success', 'Ссылка скопирована')
  } catch {
    ui.pushToast('warning', 'Скопируйте ссылку вручную')
  }
}

function extractToken(value: string): string | null {
  const trimmed = value.trim()
  const byPath = /\/join\/([A-Za-z0-9_-]+)/.exec(trimmed)
  if (byPath) return byPath[1]
  const byQuery = /[?&]token=([A-Za-z0-9_-]+)/.exec(trimmed)
  if (byQuery) return byQuery[1]
  return /^[A-Za-z0-9_-]{4,64}$/.test(trimmed) ? trimmed : null
}

function onDetected(value: string): void {
  scannerOpen.value = false
  const token = extractToken(value)
  if (!token) {
    ui.pushToast('error', 'В QR-коде нет токена приглашения')
    return
  }
  void router.push({ name: 'join', params: { token } })
}
</script>

<template>
  <div class="stack">
    <div class="page-header">
      <div class="page-header__titles">
        <h1>Приглашения</h1>
        <div class="page-header__subtitle">
          QR-код и ссылка для новых участников. Приглашение одноразовое по умолчанию: 7 дней, 1 использование.
        </div>
      </div>
      <button class="btn btn--sm" type="button" @click="scannerOpen = true">
        Проверить QR камерой
      </button>
      <RouterLink
        class="btn btn--sm"
        :to="{ name: 'campaign-tab', params: { campaignId: campaign.campaignId, tab: 'members' } }"
      >
        Участники
      </RouterLink>
    </div>

    <EmptyState
      v-if="!canInvite"
      title="Недостаточно прав"
      hint="Создавать приглашения может владелец кампании (право invite.create)."
    />

    <template v-else>
      <form class="card stack" @submit.prevent="create">
        <h2>Новое приглашение</h2>
        <div class="row" style="align-items: flex-end">
          <div class="field" style="flex: 0 0 200px">
            <label class="field__label" for="invite-role">Роль</label>
            <select id="invite-role" v-model="role" class="select">
              <option v-for="value in MEMBER_ROLES" :key="value" :value="value">
                {{ MEMBER_ROLE_LABELS[value] }}
              </option>
            </select>
          </div>
          <div class="field" style="flex: 1; min-width: 220px">
            <label class="field__label">Связь с героем</label>
            <EntityPicker v-model="linkedEntityId" :types="HERO_TYPES" empty-label="— без героя —" />
          </div>
        </div>
        <div class="row" style="align-items: flex-end">
          <div class="field" style="flex: 0 0 200px">
            <label class="field__label" for="invite-expires">Срок действия</label>
            <select id="invite-expires" v-model.number="expiresInHours" class="select">
              <option :value="24">24 часа</option>
              <option :value="168">7 дней</option>
              <option :value="720">30 дней</option>
            </select>
          </div>
          <div class="field" style="flex: 0 0 200px">
            <label class="field__label" for="invite-uses">Использований</label>
            <select id="invite-uses" v-model.number="maxUses" class="select">
              <option :value="1">1</option>
              <option :value="3">3</option>
              <option :value="5">5</option>
              <option :value="10">10</option>
            </select>
          </div>
          <button class="btn btn--primary" type="submit" :disabled="creating">
            <span v-if="creating" class="spinner" aria-hidden="true" />
            Создать приглашение
          </button>
        </div>
      </form>

      <h2>Активные приглашения</h2>
      <EmptyState
        v-if="campaign.invites.length === 0"
        title="Активных приглашений нет"
        hint="Создайте приглашение и покажите QR-код игроку."
      />
      <div v-else class="list">
        <div v-for="invite in campaign.invites" :key="invite.id" class="list-row">
          <div class="list-row__main">
            <div class="list-row__title">
              <span class="token-code" style="font-size: 0.95rem">{{ invite.token }}</span>
              <span class="badge badge--info">{{ MEMBER_ROLE_LABELS[invite.role] }}</span>
              <span v-if="invite.revoked" class="badge badge--danger">отозвано</span>
            </div>
            <div class="faint">
              <template v-if="invite.linkedEntityId">
                герой: {{ invite.linkedEntityName ?? campaign.entityName(invite.linkedEntityId) }} ·
              </template>
              до {{ formatDateTime(invite.expiresAt) }} · использований {{ invite.uses }}/{{ invite.maxUses }}
            </div>
          </div>
          <button class="btn btn--sm" type="button" @click="openQr(invite)">QR-код</button>
          <button class="btn btn--sm" type="button" @click="copy(absoluteUrl(invite))">Ссылка</button>
          <button class="btn btn--sm btn--danger" type="button" :disabled="busy" @click="revoke(invite)">
            Отозвать
          </button>
        </div>
      </div>
    </template>

    <BaseModal :open="created !== null" title="Приглашение создано" @close="created = null">
      <div v-if="created" class="stack">
        <div v-if="qrCache[created.id]" class="qr-code">
          <img :src="qrCache[created.id]" :alt="`QR-код ${created.token}`" />
        </div>
        <div class="token-code">{{ created.token }}</div>
        <input class="input" type="text" readonly :value="absoluteUrl(created)" />
        <div class="faint">
          Ссылка для веба, для Android — диплинк <code>{{ created.deepLink }}</code>
        </div>
        <div class="modal__actions">
          <button class="btn" type="button" @click="copy(absoluteUrl(created))">Скопировать</button>
          <button class="btn btn--primary" type="button" @click="created = null">Готово</button>
        </div>
      </div>
    </BaseModal>

    <BaseModal
      :open="activeInvite !== null"
      :title="`QR-код ${activeInvite?.token ?? ''}`"
      @close="activeInvite = null"
    >
      <div v-if="activeInvite" class="stack">
        <div v-if="qrCache[activeInvite.id]" class="qr-code">
          <img :src="qrCache[activeInvite.id]" :alt="`QR-код ${activeInvite.token}`" />
        </div>
        <input class="input" type="text" readonly :value="absoluteUrl(activeInvite)" />
        <div class="modal__actions">
          <button class="btn" type="button" @click="copy(absoluteUrl(activeInvite))">Скопировать</button>
          <button class="btn btn--primary" type="button" @click="activeInvite = null">Закрыть</button>
        </div>
      </div>
    </BaseModal>

    <QrScannerDialog :open="scannerOpen" @close="scannerOpen = false" @detected="onDetected" />
  </div>
</template>
