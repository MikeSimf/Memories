<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { errorMessage } from '@/api'
import { RETELLING_STATUS_LABELS } from '@/api/types'
import ConflictNotice from '@/components/ConflictNotice.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import EmptyState from '@/components/EmptyState.vue'
import MarkupText from '@/components/MarkupText.vue'
import { useCampaignStore } from '@/stores/campaign'
import { useUiStore } from '@/stores/ui'
import { formatDate, formatDateTime } from '@/utils/datetime'

const route = useRoute()
const router = useRouter()
const campaign = useCampaignStore()
const ui = useUiStore()

const retellingId = computed(() => String(route.params.retellingId ?? ''))
const item = computed(
  () => campaign.retellingItems.find((entry) => entry.id === retellingId.value) ?? null,
)
const published = computed(() => campaign.retellingById(retellingId.value))

const canEdit = computed(() =>
  item.value ? (item.value.isDraft || campaign.canEditRetellingItem(retellingId.value)) : false,
)
const canDelete = computed(() =>
  item.value ? campaign.canDeleteRetellingItem(retellingId.value) : false,
)

const busy = ref(false)
const confirmDelete = ref(false)
const brokenLink = ref<string | null>(null)

watch(retellingId, () => {
  confirmDelete.value = false
})

async function publish(): Promise<void> {
  if (!item.value?.isDraft) return
  busy.value = true
  try {
    const id = await campaign.publishDraft(item.value.id)
    ui.pushToast('success', 'Пересказ опубликован и уйдёт на сервер при синхронизации')
    await router.replace({
      name: 'retelling-detail',
      params: { campaignId: campaign.campaignId, retellingId: id },
    })
  } catch (caught) {
    ui.pushToast('error', errorMessage(caught))
  } finally {
    busy.value = false
  }
}

async function remove(): Promise<void> {
  if (!item.value) return
  busy.value = true
  try {
    await campaign.removeRetelling(item.value.id)
    confirmDelete.value = false
    ui.pushToast('success', item.value.isDraft ? 'Черновик удалён' : 'Пересказ удалён')
    await router.push({
      name: 'campaign-retellings',
      params: { campaignId: campaign.campaignId },
    })
  } catch (caught) {
    ui.pushToast('error', errorMessage(caught))
  } finally {
    busy.value = false
  }
}

async function keepServer(): Promise<void> {
  if (!item.value) return
  busy.value = true
  try {
    await campaign.resolveConflictUseServer(item.value.id)
    ui.pushToast('success', 'Применена серверная версия')
  } catch (caught) {
    ui.pushToast('error', errorMessage(caught))
  } finally {
    busy.value = false
  }
}

async function keepLocal(): Promise<void> {
  if (!item.value) return
  busy.value = true
  try {
    await campaign.resolveConflictUseLocal(item.value.id)
    ui.pushToast('success', 'Ваша версия отправлена на сервер')
  } catch (caught) {
    ui.pushToast('error', errorMessage(caught))
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="stack">
    <template v-if="item">
      <div class="page-header">
        <div class="page-header__titles">
          <h1>{{ item.title || 'Без названия' }}</h1>
          <div class="page-header__subtitle">
            <span class="badge" :class="item.status === 'DRAFT' ? 'badge--warning' : 'badge--success'">
              {{ RETELLING_STATUS_LABELS[item.status] }}
            </span>
            <span v-if="item.isDraft" class="faint"> · хранится только на этом устройстве</span>
            <span v-else-if="item.dirty" class="dirty-dot" title="Есть неотправленная правка" />
          </div>
        </div>
        <button
          v-if="item.isDraft && campaign.canWriteRetellings"
          class="btn btn--sm btn--primary"
          type="button"
          :disabled="busy"
          @click="publish"
        >
          Опубликовать
        </button>
        <RouterLink
          v-if="canEdit"
          class="btn btn--sm"
          :to="{ name: 'retelling-edit', params: { campaignId: campaign.campaignId, retellingId: item.id } }"
        >
          Редактировать
        </RouterLink>
        <button v-if="canDelete" class="btn btn--sm btn--danger" type="button" @click="confirmDelete = true">
          Удалить
        </button>
      </div>

      <ConflictNotice
        v-if="published"
        :conflict="published.conflict"
        :saving="busy"
        @keep-server="keepServer"
        @keep-local="keepLocal"
      />

      <div class="row faint">
        <span v-if="item.sessionDate">Сессия: {{ formatDate(item.sessionDate) }}</span>
        <span v-if="item.authorEntityName">
          От лица:
          <RouterLink
            v-if="item.authorEntityId"
            :to="{ name: 'entity-detail', params: { campaignId: campaign.campaignId, entityId: item.authorEntityId } }"
          >
            {{ item.authorEntityName }}
          </RouterLink>
          <template v-else>{{ item.authorEntityName }}</template>
        </span>
        <span v-if="!item.isDraft">
          изменён(а) {{ item.updatedBy }} · {{ formatDateTime(item.updatedAt) }} · ревизия
          {{ item.revision }}
        </span>
      </div>

      <div class="card">
        <MarkupText
          v-if="item.body.trim()"
          :text="item.body"
          @broken-link="brokenLink = $event.name"
        />
        <p v-else class="faint">Текст пересказа пуст.</p>
      </div>

      <div v-if="brokenLink" class="notice notice--warning">
        Ссылка «{{ brokenLink }}» не найдена.
        <div class="notice__actions">
          <RouterLink
            class="btn btn--sm"
            :to="{
              name: 'entity-new',
              params: { campaignId: campaign.campaignId },
              query: { type: 'NPC', name: brokenLink },
            }"
          >
            Создать объект «{{ brokenLink }}»
          </RouterLink>
          <button class="btn btn--sm" type="button" @click="brokenLink = null">Скрыть</button>
        </div>
      </div>

      <ConfirmDialog
        :open="confirmDelete"
        :busy="busy"
        title="Удалить пересказ?"
        :message="
          item.isDraft
            ? 'Черновик будет удалён с устройства безвозвратно.'
            : 'Пересказ будет удалён из кампании после синхронизации.'
        "
        @cancel="confirmDelete = false"
        @confirm="remove"
      />
    </template>

    <EmptyState
      v-else
      title="Пересказ не найден"
      hint="Возможно, он ещё не загружен на это устройство или был удалён."
    >
      <RouterLink
        class="btn"
        :to="{ name: 'campaign-retellings', params: { campaignId: campaign.campaignId } }"
      >
        К списку пересказов
      </RouterLink>
    </EmptyState>
  </div>
</template>
