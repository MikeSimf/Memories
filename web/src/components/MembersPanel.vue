<script setup lang="ts">
import { computed, ref } from 'vue'

import { errorMessage } from '@/api'
import {
  MEMBER_ROLE_LABELS,
  MEMBER_ROLES,
  MEMBER_STATUS_LABELS,
  type InviteDto,
  type MemberRole,
} from '@/api/types'
import BaseModal from '@/components/BaseModal.vue'
import EntityPicker from '@/components/EntityPicker.vue'
import { useCampaignStore } from '@/stores/campaign'
import { useUiStore } from '@/stores/ui'
import { formatDateTime } from '@/utils/datetime'
import { generateQrDataUrl } from '@/utils/qr'

const campaign = useCampaignStore()
const ui = useUiStore()

const addOpen = ref(false)
const email = ref('')
const role = ref<MemberRole>('EDITOR_HERO')
const linkedEntityId = ref<string | null>(null)
const busy = ref(false)
const createdInvite = ref<InviteDto | null>(null)
const qrDataUrl = ref<string | null>(null)

const canManage = computed(() => campaign.canManageMembers)
const members = computed(() => campaign.members.filter((member) => member.status !== 'REMOVED'))
const heroTypeList = ['HERO'] as const

async function submitAdd(): Promise<void> {
  if (email.value.trim() === '') return
  busy.value = true
  try {
    const response = await campaign.addMember({
      email: email.value.trim(),
      role: role.value,
      linkedEntityId: linkedEntityId.value,
    })
    addOpen.value = false
    email.value = ''
    linkedEntityId.value = null
    if (response.invite) {
      createdInvite.value = response.invite
      qrDataUrl.value = await generateQrDataUrl(response.invite.url)
      ui.pushToast('info', 'Пользователя ещё нет — создано приглашение')
    } else {
      ui.pushToast('success', 'Участник добавлен')
    }
  } catch (caught) {
    ui.pushToast('error', errorMessage(caught))
  } finally {
    busy.value = false
  }
}

async function changeRole(memberId: string, value: string): Promise<void> {
  busy.value = true
  try {
    await campaign.changeRole(memberId, value as MemberRole)
    ui.pushToast('success', 'Роль обновлена')
  } catch (caught) {
    ui.pushToast('error', errorMessage(caught))
  } finally {
    busy.value = false
  }
}

async function changeHero(memberId: string, value: string | null): Promise<void> {
  busy.value = true
  try {
    await campaign.linkMemberToHero(memberId, value)
  } catch (caught) {
    ui.pushToast('error', errorMessage(caught))
  } finally {
    busy.value = false
  }
}

async function remove(memberId: string, name: string): Promise<void> {
  if (!window.confirm(`Удалить участника «${name}» из кампании?`)) return
  busy.value = true
  try {
    await campaign.removeMember(memberId)
    ui.pushToast('success', 'Участник удалён')
  } catch (caught) {
    ui.pushToast('error', errorMessage(caught))
  } finally {
    busy.value = false
  }
}

async function copy(text: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(text)
    ui.pushToast('success', 'Скопировано')
  } catch {
    ui.pushToast('warning', 'Не удалось скопировать — выделите текст вручную')
  }
}
</script>

<template>
  <div class="stack">
    <div class="row row--between">
      <span class="muted">{{ members.length }} участников</span>
      <div class="row">
        <RouterLink
          v-if="campaign.canInvite || campaign.canManageMembers"
          class="btn btn--sm"
          :to="{ name: 'campaign-invites', params: { campaignId: campaign.campaignId } }"
        >
          Приглашения
        </RouterLink>
        <button v-if="canManage" class="btn btn--sm btn--primary" type="button" @click="addOpen = true">
          Добавить участника
        </button>
      </div>
    </div>

    <p v-if="!canManage" class="notice">
      Управление участниками доступно владельцу кампании (право <code>members.manage</code>).
    </p>

    <div class="list">
      <div v-for="member in members" :key="member.id" class="list-row">
        <div class="list-row__main">
          <div class="list-row__title">
            {{ member.displayName }}
            <span class="badge">{{ MEMBER_ROLE_LABELS[member.role] }}</span>
            <span v-if="member.status === 'INVITED'" class="badge badge--warning">
              {{ MEMBER_STATUS_LABELS[member.status] }}
            </span>
            <span v-if="member.userId === campaign.myMembership?.userId" class="badge badge--info">
              это вы
            </span>
          </div>
          <div class="faint">{{ member.email }}</div>
          <div v-if="member.linkedEntityName" class="faint">
            герой: {{ member.linkedEntityName }}
          </div>
        </div>

        <div v-if="canManage" class="stack stack--sm" style="min-width: 190px">
          <select
            class="select"
            :value="member.role"
            :disabled="busy"
            aria-label="Роль участника"
            @change="changeRole(member.id, ($event.target as HTMLSelectElement).value)"
          >
            <option v-for="value in MEMBER_ROLES" :key="value" :value="value">
              {{ MEMBER_ROLE_LABELS[value] }}
            </option>
          </select>
          <EntityPicker
            :model-value="member.linkedEntityId"
            :types="[...heroTypeList]"
            empty-label="— без героя —"
            @update:model-value="changeHero(member.id, $event)"
          />
        </div>

        <button
          v-if="canManage"
          class="btn btn--danger btn--sm"
          type="button"
          :disabled="busy"
          @click="remove(member.id, member.displayName)"
        >
          Удалить
        </button>
      </div>
    </div>

    <BaseModal :open="addOpen" title="Новый участник" @close="addOpen = false">
      <form class="stack" @submit.prevent="submitAdd">
        <div class="field">
          <label class="field__label" for="member-email">Почта</label>
          <input id="member-email" v-model="email" class="input" type="email" required />
          <span class="field__hint">
            Если пользователь уже зарегистрирован — он сразу станет участником, иначе получит приглашение.
          </span>
        </div>
        <div class="field">
          <label class="field__label" for="member-role">Роль</label>
          <select id="member-role" v-model="role" class="select">
            <option v-for="value in MEMBER_ROLES" :key="value" :value="value">
              {{ MEMBER_ROLE_LABELS[value] }}
            </option>
          </select>
        </div>
        <div class="field">
          <label class="field__label">Связь с героем</label>
          <EntityPicker v-model="linkedEntityId" :types="[...heroTypeList]" empty-label="— без героя —" />
          <span class="field__hint">Для роли «Игрок» даёт права на своего героя.</span>
        </div>
        <div class="modal__actions">
          <button class="btn" type="button" @click="addOpen = false">Отмена</button>
          <button class="btn btn--primary" type="submit" :disabled="busy">
            <span v-if="busy" class="spinner" aria-hidden="true" />
            Добавить
          </button>
        </div>
      </form>
    </BaseModal>

    <BaseModal
      :open="createdInvite !== null"
      title="Приглашение создано"
      @close="createdInvite = null"
    >
      <div v-if="createdInvite" class="stack">
        <div v-if="qrDataUrl" class="qr-code">
          <img :src="qrDataUrl" :alt="`QR-код приглашения ${createdInvite.token}`" />
        </div>
        <div class="token-code">{{ createdInvite.token }}</div>
        <input class="input" type="text" readonly :value="createdInvite.url" />
        <div class="faint">Действует до {{ formatDateTime(createdInvite.expiresAt) }}</div>
        <div class="modal__actions">
          <button class="btn" type="button" @click="copy(createdInvite.url)">Скопировать ссылку</button>
          <button class="btn btn--primary" type="button" @click="createdInvite = null">Готово</button>
        </div>
      </div>
    </BaseModal>
  </div>
</template>
