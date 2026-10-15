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

const HERO_TYPES: EntityType[] = ['HERO', 'NPC']

/** Два разных QR (`api.md` §3.1): приглашение человеку и регистрация устройства. */
type FormMode = 'MEMBER' | 'DEVICE'
const formMode = ref<FormMode>(campaign.canInvite ? 'MEMBER' : 'DEVICE')

// Общие поля приглашения и регистрации устройства.
const role = ref<MemberRole>('EDITOR_HERO')
const linkedEntityId = ref<string | null>(null)

// Приглашение участнику.
const expiresInHours = ref(168)
const maxUses = ref(1)

// Регистрация устройства: имя игрока обязательно, QR живёт сутки.
const deviceName = ref('')
const deviceLabel = ref('')
const deviceExpiresInHours = ref(24)

const busy = ref(false)
const creating = ref(false)
const created = ref<InviteDto | null>(null)
const activeInvite = ref<InviteDto | null>(null)
const qrCache = ref<Record<string, string>>({})
const scannerOpen = ref(false)

const canInvite = computed(() => campaign.canInvite)
const canManage = computed(() => campaign.canManageMembers)
const devices = computed(() => campaign.devices)

onMounted(async () => {
  if (canInvite.value) {
    try {
      await campaign.reloadInvites()
    } catch (caught) {
      ui.pushToast('error', errorMessage(caught))
    }
  }
  if (canManage.value) {
    try {
      await campaign.reloadDevices()
    } catch (caught) {
      ui.pushToast('error', errorMessage(caught))
    }
  }
})

function absoluteUrl(invite: InviteDto): string {
  if (/^https?:\/\//i.test(invite.url)) return invite.url
  return `${window.location.origin}${invite.url.startsWith('/') ? '' : '/'}${invite.url}`
}

/** Сервер отдаёт QR готовым PNG в base64 — используем его как есть. */
function qrFromBase64(invite: InviteDto): string | null {
  if (!invite.qrPngBase64) return null
  return invite.qrPngBase64.startsWith('data:')
    ? invite.qrPngBase64
    : `data:image/png;base64,${invite.qrPngBase64}`
}

async function qrFor(invite: InviteDto): Promise<string> {
  const cached = qrCache.value[invite.id]
  if (cached) return cached
  const dataUrl = qrFromBase64(invite) ?? (await generateQrDataUrl(absoluteUrl(invite)))
  qrCache.value = { ...qrCache.value, [invite.id]: dataUrl }
  return dataUrl
}

async function openQr(invite: InviteDto): Promise<void> {
  activeInvite.value = invite
  await qrFor(invite)
}

async function createInvite(): Promise<void> {
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

async function createEnrollment(): Promise<void> {
  if (deviceName.value.trim() === '') {
    ui.pushToast('warning', 'Укажите имя игрока')
    return
  }
  creating.value = true
  try {
    const enrollment = await campaign.createDeviceEnrollment({
      displayName: deviceName.value.trim(),
      role: role.value,
      linkedEntityId: linkedEntityId.value,
      expiresInHours: deviceExpiresInHours.value,
      maxUses: 1,
      label: deviceLabel.value.trim() || undefined,
    })
    created.value = enrollment
    await qrFor(enrollment)
    deviceName.value = ''
    deviceLabel.value = ''
    ui.pushToast('success', 'QR-код устройства создан')
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

async function revokeDevice(deviceId: string, name: string): Promise<void> {
  if (!window.confirm(`Отозвать устройство «${name}»? Его сессии перестанут работать.`)) return
  busy.value = true
  try {
    await campaign.revokeDevice(deviceId)
    ui.pushToast('success', 'Устройство отозвано')
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
        <h1>Приглашения и устройства</h1>
        <div class="page-header__subtitle">
          Приглашение участнику живёт 7 дней, QR устройства — сутки.
        </div>
      </div>
      <button class="btn btn--sm" type="button" @click="scannerOpen = true">
        Проверить QR камерой
      </button>
      <RouterLink
        class="btn btn--sm"
        :to="{ name: 'campaign-members', params: { campaignId: campaign.campaignId } }"
      >
        Участники
      </RouterLink>
    </div>

    <EmptyState
      v-if="!canInvite && !canManage"
      title="Недостаточно прав"
      hint="Приглашения создаёт владелец кампании (право invite.create), устройства — участник с правом members.manage."
    />

    <template v-else>
      <div class="row mode-switch" role="tablist" aria-label="Что выпускаем">
        <button
          v-if="canInvite"
          class="btn btn--sm"
          :class="{ 'btn--primary': formMode === 'MEMBER' }"
          type="button"
          role="tab"
          :aria-selected="formMode === 'MEMBER'"
          @click="formMode = 'MEMBER'"
        >
          Приглашение участнику
        </button>
        <button
          v-if="canManage"
          class="btn btn--sm"
          :class="{ 'btn--primary': formMode === 'DEVICE' }"
          type="button"
          role="tab"
          :aria-selected="formMode === 'DEVICE'"
          @click="formMode = 'DEVICE'"
        >
          Регистрация устройства
        </button>
      </div>

      <!-- Приглашение участнику -->
      <form v-if="formMode === 'MEMBER' && canInvite" class="card stack" @submit.prevent="createInvite">
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

      <!-- Регистрация устройства по QR -->
      <form v-else-if="canManage" class="card stack" @submit.prevent="createEnrollment">
        <h2>Регистрация устройства</h2>
        <p class="faint" style="margin: 0">
          Телефон сканирует QR и сразу получает доступ — пароль и почта не нужны.
          Аккаунт создаст сервер с указанным именем.
        </p>
        <div class="row" style="align-items: flex-end">
          <div class="field" style="flex: 1; min-width: 200px">
            <label class="field__label" for="device-name">Имя игрока</label>
            <input id="device-name" v-model="deviceName" class="input" type="text" required placeholder="Аня" />
          </div>
          <div class="field" style="flex: 0 0 200px">
            <label class="field__label" for="device-role">Роль</label>
            <select id="device-role" v-model="role" class="select">
              <option v-for="value in MEMBER_ROLES" :key="value" :value="value">
                {{ MEMBER_ROLE_LABELS[value] }}
              </option>
            </select>
          </div>
        </div>
        <div class="row" style="align-items: flex-end">
          <div class="field" style="flex: 1; min-width: 220px">
            <label class="field__label">Привязка к герою</label>
            <EntityPicker v-model="linkedEntityId" :types="HERO_TYPES" empty-label="— без героя —" />
          </div>
          <div class="field" style="flex: 0 0 200px">
            <label class="field__label" for="device-expires">Срок действия</label>
            <select id="device-expires" v-model.number="deviceExpiresInHours" class="select">
              <option :value="1">1 час</option>
              <option :value="24">24 часа</option>
              <option :value="72">3 дня</option>
              <option :value="168">7 дней</option>
            </select>
          </div>
        </div>
        <div class="row" style="align-items: flex-end">
          <div class="field" style="flex: 1; min-width: 220px">
            <label class="field__label" for="device-label">Подпись устройства</label>
            <input
              id="device-label"
              v-model="deviceLabel"
              class="input"
              type="text"
              placeholder="Телефон Ани"
            />
          </div>
          <button class="btn btn--primary" type="submit" :disabled="creating || deviceName.trim() === ''">
            <span v-if="creating" class="spinner" aria-hidden="true" />
            Создать QR устройства
          </button>
        </div>
      </form>

      <template v-if="canInvite">
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
                <span class="badge">{{ invite.mode === 'DEVICE' ? 'устройство' : 'участник' }}</span>
                <span v-if="invite.revoked" class="badge badge--danger">отозвано</span>
              </div>
              <div class="faint">
                <template v-if="invite.displayName">игрок: {{ invite.displayName }} · </template>
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

      <!-- Устройства кампании: только при праве members.manage -->
      <template v-if="canManage">
        <h2>Устройства кампании</h2>
        <EmptyState
          v-if="devices.length === 0"
          title="Устройств пока нет"
          hint="Отсканируйте QR регистрации устройства в приложении на телефоне — оно появится здесь."
        />
        <div v-else class="list">
          <div v-for="device in devices" :key="device.id" class="list-row">
            <div class="list-row__main">
              <div class="list-row__title">
                {{ device.displayName }}
                <span v-if="device.revoked" class="badge badge--danger">отозвано</span>
              </div>
              <div class="faint">
                <template v-if="device.label">{{ device.label }} · </template>
                <template v-if="device.platform">{{ device.platform }} · </template>
                последний вход:
                {{ device.lastSeenAt ? formatDateTime(device.lastSeenAt) : 'ещё не входило' }}
              </div>
            </div>
            <button
              class="btn btn--danger btn--sm"
              type="button"
              :disabled="busy || device.revoked"
              @click="revokeDevice(device.id, device.displayName)"
            >
              Отозвать
            </button>
          </div>
        </div>
      </template>
    </template>

    <BaseModal
      :open="created !== null"
      :title="created?.mode === 'DEVICE' ? 'Регистрация устройства' : 'Приглашение создано'"
      @close="created = null"
    >
      <div v-if="created" class="stack">
        <div v-if="qrCache[created.id]" class="qr-code">
          <img :src="qrCache[created.id]" :alt="`QR-код ${created.token}`" />
        </div>
        <div class="token-code">{{ created.token }}</div>
        <div v-if="created.mode === 'DEVICE'" class="faint center">
          Откройте DnD Memory на телефоне и отсканируйте QR. Игрок:
          <strong>{{ created.displayName }}</strong>
        </div>
        <input v-else class="input" type="text" readonly :value="absoluteUrl(created)" />
        <div class="faint">
          Для Android — диплинк <code>{{ created.deepLink }}</code>
        </div>
        <div class="modal__actions">
          <button
            v-if="created.mode !== 'DEVICE'"
            class="btn"
            type="button"
            @click="copy(absoluteUrl(created))"
          >
            Скопировать
          </button>
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
