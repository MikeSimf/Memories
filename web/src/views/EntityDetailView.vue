<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { errorMessage } from '@/api'
import { ENTITY_TYPE_LABELS, ENTITY_TYPES, type EntityType } from '@/api/types'
import BaseModal from '@/components/BaseModal.vue'
import ConflictNotice from '@/components/ConflictNotice.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import EntityImage from '@/components/EntityImage.vue'
import MarkupText from '@/components/MarkupText.vue'
import { useCampaignStore } from '@/stores/campaign'
import { useUiStore } from '@/stores/ui'
import { filledAttrs } from '@/utils/attrs'
import { formatDateTime } from '@/utils/datetime'

const route = useRoute()
const router = useRouter()
const campaign = useCampaignStore()
const ui = useUiStore()

const entityId = computed(() => String(route.params.entityId ?? ''))
const entity = computed(() => campaign.entityById(entityId.value))
const attrs = computed(() =>
  entity.value ? filledAttrs(entity.value.type, entity.value.attrs) : [],
)
const canEdit = computed(() => (entity.value ? campaign.canEdit(entity.value) : false))
const canDelete = computed(() => (entity.value ? campaign.canDelete(entity.value) : false))

/** Обратные ссылки считаем по локальной базе — работает и без сети (`sync.md` §7). */
const backlinks = computed(() =>
  campaign.activeEntities.filter(
    (item) => item.id !== entityId.value && item.links.some((link) => link.targetId === entityId.value),
  ),
)

const outgoingLinks = computed(() => {
  const seen = new Set<string>()
  const result: Array<{ id: string | null; label: string; resolved: boolean }> = []
  for (const link of entity.value?.links ?? []) {
    const key = `${link.targetId ?? ''}|${link.label}`
    if (seen.has(key)) continue
    seen.add(key)
    result.push({ id: link.targetId, label: link.label, resolved: link.resolved && !!link.targetId })
  }
  return result
})

const brokenLinkName = ref<string | null>(null)
const brokenLinkType = ref<EntityType>('NPC')
const confirmDelete = ref(false)
const busy = ref(false)

watch(entityId, () => {
  confirmDelete.value = false
  brokenLinkName.value = null
})

function onBrokenLink(payload: { name: string }): void {
  brokenLinkName.value = payload.name
  brokenLinkType.value = entity.value?.type === 'HERO' ? 'NPC' : (entity.value?.type ?? 'NPC')
}

function createFromBrokenLink(): void {
  const name = brokenLinkName.value ?? ''
  brokenLinkName.value = null
  void router.push({
    name: 'entity-new',
    params: { campaignId: campaign.campaignId },
    query: { type: brokenLinkType.value, name },
  })
}

async function remove(): Promise<void> {
  if (!entity.value) return
  busy.value = true
  try {
    await campaign.deleteEntity(entity.value.id)
    confirmDelete.value = false
    ui.pushToast('success', 'Объект удалён')
    await router.push({
      name: 'campaign-tab',
      params: { campaignId: campaign.campaignId, tab: tabForType(entity.value.type) },
    })
  } catch (caught) {
    ui.pushToast('error', errorMessage(caught))
  } finally {
    busy.value = false
  }
}

function tabForType(type: EntityType): string {
  switch (type) {
    // Герои и НПС живут на одной вкладке «Персонажи».
    case 'HERO':
    case 'NPC':
      return 'characters'
    case 'LOCATION':
      return 'locations'
    default:
      return 'items'
  }
}

async function keepServer(): Promise<void> {
  if (!entity.value) return
  busy.value = true
  try {
    await campaign.resolveConflictUseServer(entity.value.id)
    ui.pushToast('success', 'Применена серверная версия')
  } catch (caught) {
    ui.pushToast('error', errorMessage(caught))
  } finally {
    busy.value = false
  }
}

async function keepLocal(): Promise<void> {
  if (!entity.value) return
  busy.value = true
  try {
    await campaign.resolveConflictUseLocal(entity.value.id)
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
    <template v-if="entity">
      <div class="page-header">
        <div class="page-header__titles">
          <h1>
            {{ entity.name }}
            <span v-if="entity.dirty === 1" class="dirty-dot" title="Есть неотправленная правка" />
          </h1>
          <div class="page-header__subtitle">
            <span class="badge" :class="`badge--${entity.type.toLowerCase()}`">
              {{ ENTITY_TYPE_LABELS[entity.type] }}
            </span>
            <span v-if="entity.deleted" class="badge badge--danger">удалён</span>
            <span class="faint"> · {{ campaign.changeSummary(entity) }}</span>
          </div>
        </div>
        <RouterLink
          v-if="canEdit"
          class="btn btn--sm"
          :to="{ name: 'entity-edit', params: { campaignId: campaign.campaignId, entityId: entity.id } }"
        >
          Редактировать
        </RouterLink>
        <button v-if="canDelete" class="btn btn--sm btn--danger" type="button" @click="confirmDelete = true">
          Удалить
        </button>
      </div>

      <ConflictNotice
        :conflict="entity.conflict"
        :saving="busy"
        @keep-server="keepServer"
        @keep-local="keepLocal"
      />

      <p v-if="entity.summary" class="muted">{{ entity.summary }}</p>

      <EntityImage
        v-if="entity.imageFileId"
        :file-id="entity.imageFileId"
        :alt="`Изображение: ${entity.name}`"
      />

      <div v-if="entity.tags.length > 0" class="chips">
        <span v-for="tag in entity.tags" :key="tag" class="chip">{{ tag }}</span>
      </div>

      <div v-if="attrs.length > 0" class="card">
        <h2>Поля</h2>
        <dl class="attr-grid" style="margin: 0">
          <div v-for="row in attrs" :key="row.key">
            <dt class="field__label">{{ row.label }}</dt>
            <dd style="margin: 0">{{ row.value }}</dd>
          </div>
        </dl>
      </div>

      <div class="card">
        <h2>Описание</h2>
        <MarkupText
          v-if="entity.description.trim()"
          :text="entity.description"
          @broken-link="onBrokenLink"
        />
        <p v-else class="faint">Описание не заполнено.</p>
      </div>

      <div v-if="outgoingLinks.length > 0" class="card">
        <h2>Упоминания в тексте</h2>
        <div class="row">
          <template v-for="link in outgoingLinks" :key="`${link.id}-${link.label}`">
            <RouterLink
              v-if="link.resolved && link.id"
              class="btn btn--sm"
              :to="{ name: 'entity-detail', params: { campaignId: campaign.campaignId, entityId: link.id } }"
            >
              {{ link.label }}
            </RouterLink>
            <button v-else class="btn btn--sm" type="button" @click="brokenLinkName = link.label">
              {{ link.label }} (создать)
            </button>
          </template>
        </div>
      </div>

      <div v-if="backlinks.length > 0" class="card">
        <h2>Ссылаются сюда</h2>
        <div class="row">
          <RouterLink
            v-for="item in backlinks"
            :key="item.id"
            class="btn btn--sm"
            :to="{ name: 'entity-detail', params: { campaignId: campaign.campaignId, entityId: item.id } }"
          >
            {{ item.name }}
          </RouterLink>
        </div>
      </div>

      <div class="faint">
        Создан(а) {{ campaign.userName(entity.createdBy) }}, изменён(а)
        {{ campaign.userName(entity.updatedBy) }} · ревизия {{ entity.revision }} ·
        {{ formatDateTime(entity.updatedAt) }}
      </div>

      <BaseModal
        :open="brokenLinkName !== null"
        title="Создать объект по ссылке"
        @close="brokenLinkName = null"
      >
        <div class="stack">
          <p>
            В тексте есть ссылка на объект
            <strong>«{{ brokenLinkName }}»</strong>, которого пока нет в кампании.
          </p>
          <div class="field">
            <label class="field__label" for="broken-type">Тип нового объекта</label>
            <select id="broken-type" v-model="brokenLinkType" class="select">
              <option v-for="type in ENTITY_TYPES" :key="type" :value="type">
                {{ ENTITY_TYPE_LABELS[type] }}
              </option>
            </select>
          </div>
        </div>
        <template #actions>
          <button class="btn" type="button" @click="brokenLinkName = null">Отмена</button>
          <button class="btn btn--primary" type="button" @click="createFromBrokenLink">
            Создать объект
          </button>
        </template>
      </BaseModal>

      <ConfirmDialog
        :open="confirmDelete"
        :busy="busy"
        title="Удалить объект?"
        :message="`«${entity.name}» будет удалён из кампании на всех устройствах после синхронизации.`"
        @cancel="confirmDelete = false"
        @confirm="remove"
      />
    </template>

    <EmptyState
      v-else
      title="Объект не найден"
      hint="Возможно, он ещё не загружен на это устройство или был удалён."
    >
      <RouterLink
        class="btn"
        :to="{ name: 'campaign-tab', params: { campaignId: campaign.campaignId, tab: 'characters' } }"
      >
        К списку
      </RouterLink>
    </EmptyState>
  </div>
</template>
