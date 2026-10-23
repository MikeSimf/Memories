<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { errorMessage } from '@/api'
import {
  OBJECT_CLASS_LABELS,
  OBJECT_CLASSES,
  type ObjectClass,
} from '@/api/types'
import type { LocalObject } from '@/db/schema'
import BaseModal from '@/components/BaseModal.vue'
import ConflictNotice from '@/components/ConflictNotice.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import EntityAvatar from '@/components/EntityAvatar.vue'
import { genderLabel, genderSymbol } from '@/types/gender'
import { classIconUrl } from '@/ui/classIcons'
import { typeIconPath } from '@/ui/typeIcons'
import EntityImage from '@/components/EntityImage.vue'
import MarkupText from '@/components/MarkupText.vue'
import { useCampaignStore } from '@/stores/campaign'
import { useUiStore } from '@/stores/ui'
import { formatDateTime } from '@/utils/datetime'

const route = useRoute()
const router = useRouter()
const campaign = useCampaignStore()
const ui = useUiStore()

const entityId = computed(() => String(route.params.entityId ?? ''))
const entity = computed(() => campaign.objectById(entityId.value))
const objectClass = computed<ObjectClass>(() => entity.value?.objectType ?? 'CHARACTER')
const canEdit = computed(() => (entity.value ? campaign.canEdit(entity.value) : false))
const canDelete = computed(() => (entity.value ? campaign.canDelete(entity.value) : false))

/** Поля своего класса (`api.md` §6): у локации своих полей нет. */
const details = computed<Array<{ label: string; value: string }>>(() => {
  const item = entity.value
  if (!item) return []
  if (item.objectType === 'HERO') {
    const rows: Array<{ label: string; value: string }> = []
    rows.push({ label: 'Пол', value: genderLabel(item.gender) })
    if (item.race) rows.push({ label: 'Раса', value: item.race })
    if (item.klass) rows.push({ label: 'Класс', value: item.klass })
    rows.push({ label: 'Игрок', value: item.playerName ?? 'не указан' })
    return rows
  }
  if (item.objectType === 'CHARACTER') {
    const rows: Array<{ label: string; value: string }> = []
    rows.push({ label: 'Пол', value: genderLabel(item.gender) })
    if (item.race) rows.push({ label: 'Раса', value: item.race })
    if (item.shortDescription) rows.push({ label: 'Краткое описание', value: item.shortDescription })
    return rows
  }
  if (item.objectType === 'ITEM') {
    return [
      { label: 'Тип предмета', value: item.itemType ?? 'Не заполнено' },
      { label: 'Владелец', value: item.ownerName ?? 'Не заполнено' },
      {
        label: 'Ссылка',
        value: item.ownerHeroName
          ? `герой «${item.ownerHeroName}»`
          : item.ownerCharacterName
            ? `персонаж «${item.ownerCharacterName}»`
            : 'не указана',
      },
    ]
  }
  if (item.objectType === 'LOCATION') {
    return [{ label: 'Тип локации', value: item.locationType ?? 'Не заполнено' }]
  }
  return []
})

/** Поля героя и персонажа: имя, раса, класс, пол, кратко — с значками. */
const personRows = computed(() => {
  const item = entity.value
  if (!item || (item.objectType !== 'HERO' && item.objectType !== 'CHARACTER')) return []
  const rows: Array<{ label: string; value: string; icon: 'race' | 'class' | 'gender' | null }> = [
    { label: 'Раса', value: item.race || 'Не заполнено', icon: 'race' },
    { label: 'Класс', value: item.klass || 'Не заполнено', icon: 'class' },
    { label: 'Пол', value: genderLabel(item.gender), icon: 'gender' },
  ]
  if (item.objectType === 'CHARACTER') {
    rows.push({ label: 'Кратко', value: item.shortDescription || 'Не заполнено', icon: null })
  }
  return rows
})

const personGender = computed(() => {
  const item = entity.value
  if (!item || (item.objectType !== 'HERO' && item.objectType !== 'CHARACTER')) return ''
  return genderSymbol(item.gender)
})

/** Обратные ссылки считаем по локальной базе — работает и без сети (`sync.md` §8). */
const backlinks = computed(() =>
  campaign.activeObjects.filter(
    (item) => item.id !== entityId.value && item.links.some((link) => link.targetId === entityId.value),
  ),
)

/**
 * Кто упоминает эту карточку. У локации — только герои и персонажи:
 * так ищется «кто здесь был», даже если имя забылось.
 */
const mentions = computed(() => {
  const all = backlinks.value
  const filtered =
    entity.value?.objectType === 'LOCATION'
      ? all.filter((item) => item.objectType === 'HERO' || item.objectType === 'CHARACTER')
      : all
  return [...filtered].sort((a, b) => a.name.localeCompare(b.name, 'ru'))
})

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
const brokenLinkClass = ref<ObjectClass>('CHARACTER')
const confirmDelete = ref(false)
const busy = ref(false)

watch(entityId, () => {
  confirmDelete.value = false
  brokenLinkName.value = null
})

function onBrokenLink(payload: { name: string }): void {
  brokenLinkName.value = payload.name
  const current = entity.value?.objectType
  // Ссылка в тексте героя чаще указывает на персонажа — так и предлагаем.
  brokenLinkClass.value = current === 'CHARACTER' ? 'LOCATION' : 'CHARACTER'
}

function createFromBrokenLink(): void {
  const name = brokenLinkName.value ?? ''
  brokenLinkName.value = null
  void router.push({
    name: 'entity-new',
    params: { campaignId: campaign.campaignId },
    query: { class: brokenLinkClass.value, name },
  })
}

async function remove(): Promise<void> {
  const item = entity.value
  if (!item) return
  busy.value = true
  try {
    await campaign.deleteObject(item.id)
    confirmDelete.value = false
    ui.pushToast('success', 'Объект удалён')
    await router.push({
      name: 'campaign-tab',
      params: { campaignId: campaign.campaignId, tab: tabForClass(item) },
    })
  } catch (caught) {
    ui.pushToast('error', errorMessage(caught))
  } finally {
    busy.value = false
  }
}

function tabForClass(item: LocalObject): string {
  // Герои и НПС живут на одной вкладке «Персонажи».
  if (item.objectType === 'HERO' || item.objectType === 'CHARACTER') return 'characters'
  if (item.objectType === 'LOCATION') return 'locations'
  return 'items'
}

function badgeClass(item: LocalObject): string {
  if (item.objectType === 'HERO') return 'badge--hero'
  if (item.objectType === 'CHARACTER') return 'badge--npc'
  if (item.objectType === 'LOCATION') return 'badge--location'
  return 'badge--item'
}

/** Ссылка на предмет кампании из котомки героя. */
function heroItemTargetId(itemId: string | null): string | null {
  if (!itemId) return null
  return campaign.objectById(itemId) ? itemId : null
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
          <div class="row entity-head">
            <div>
          <h1>
            {{ entity.name }}
            <span v-if="entity.dirty === 1" class="dirty-dot" title="Есть неотправленная правка" />
          </h1>
          <span v-if="personGender" class="entity-gender" title="Пол">{{ personGender }}</span>
          <div class="page-header__subtitle">
            <span class="badge" :class="badgeClass(entity)">
              {{ OBJECT_CLASS_LABELS[objectClass] }}
            </span>
            <span v-if="entity.deleted" class="badge badge--danger">удалён</span>
            <span class="faint"> · {{ campaign.changeSummary(entity) }}</span>
          </div>
            </div>
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

      <p v-if="!canEdit && objectClass === 'HERO'" class="notice">
        Героя партии ведёт только владелец кампании — эта карточка доступна вам для чтения.
      </p>

      <EntityImage
        v-if="entity.imageFileId"
        :file-id="entity.imageFileId"
        :alt="`Изображение: ${entity.name}`"
      />

      <div v-if="personRows.length > 0" class="card">
        <dl class="attr-grid attr-grid--person" style="margin: 0">
          <div v-for="row in personRows" :key="row.label" class="person-row">
            <dt class="field__label">{{ row.label }}</dt>
            <dd class="person-row__value">
              <span>{{ row.value }}</span>
              <svg
                v-if="row.icon === 'race'"
                class="person-row__icon"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.6"
                stroke-linecap="round"
                stroke-linejoin="round"
                aria-hidden="true"
              >
                <path :d="typeIconPath('RACE', row.value)" />
              </svg>
              <span
                v-else-if="row.icon === 'class' && classIconUrl(row.value)"
                class="person-row__mask"
                :style="{
                  maskImage: `url(${classIconUrl(row.value)})`,
                  WebkitMaskImage: `url(${classIconUrl(row.value)})`,
                }"
                aria-hidden="true"
              />
              <span v-else-if="row.icon === 'gender'" class="person-row__symbol">
                {{ personGender }}
              </span>
            </dd>
          </div>
        </dl>
      </div>

      <div v-else-if="details.length > 0" class="card">
        <dl class="attr-grid" style="margin: 0">
          <div v-for="row in details" :key="row.label">
            <dt class="field__label">{{ row.label }}</dt>
            <dd style="margin: 0">{{ row.value }}</dd>
          </div>
        </dl>
      </div>

      <!-- Котомка героя: name, quantity, notes и ссылка на предмет кампании -->
      <div v-if="entity.objectType === 'HERO'" class="card">
        <h2>Предметы в котомке</h2>
        <p v-if="entity.items.length === 0" class="faint">Список пуст.</p>
        <div v-else class="list">
          <div v-for="item in entity.items" :key="item.id" class="list-row">
            <div class="list-row__main">
              <div class="list-row__title">
                {{ item.name }}
                <span v-if="item.quantity && item.quantity > 1" class="badge">×{{ item.quantity }}</span>
              </div>
              <div v-if="item.notes" class="faint">{{ item.notes }}</div>
            </div>
            <RouterLink
              v-if="heroItemTargetId(item.itemId)"
              class="btn btn--sm"
              :to="{
                name: 'entity-detail',
                params: { campaignId: campaign.campaignId, entityId: heroItemTargetId(item.itemId) },
              }"
            >
              Открыть предмет
            </RouterLink>
          </div>
        </div>
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

      <div class="faint">
        Создан(а) {{ campaign.userName(entity.createdBy) }}, изменён(а)
        {{ campaign.userName(entity.updatedBy) }} · ревизия {{ entity.revision }} ·
        {{ formatDateTime(entity.updatedAt) }}
      </div>

      <div v-if="outgoingLinks.length > 0" class="card">
        <h2>Упоминания в тексте</h2>
        <div class="mention-list">
          <template v-for="link in outgoingLinks" :key="`${link.id}-${link.label}`">
            <RouterLink
              v-if="link.resolved && link.id && campaign.objectById(link.id)"
              class="mention-row"
              :to="{ name: 'entity-detail', params: { campaignId: campaign.campaignId, entityId: link.id } }"
            >
              <EntityAvatar :object="campaign.objectById(link.id)!" :size="36" />
              <span class="mention-row__main">
                <span class="mention-row__name">{{ campaign.objectById(link.id)!.name }}</span>
                <span class="faint">{{ OBJECT_CLASS_LABELS[campaign.objectById(link.id)!.objectType] }}</span>
              </span>
            </RouterLink>
            <button v-else class="btn btn--sm" type="button" @click="brokenLinkName = link.label">
              {{ link.label }} (создать)
            </button>
          </template>
        </div>
      </div>

      <div v-if="mentions.length > 0" class="card">
        <h2>{{ entity.objectType === 'LOCATION' ? 'Здесь встречались' : 'Упоминают' }}</h2>
        <p v-if="entity.objectType === 'LOCATION'" class="faint" style="margin-top: 0">
          Герои и персонажи, в описании которых есть эта локация.
        </p>
        <div class="mention-list">
          <RouterLink
            v-for="item in mentions"
            :key="item.id"
            class="mention-row"
            :to="{ name: 'entity-detail', params: { campaignId: campaign.campaignId, entityId: item.id } }"
          >
            <EntityAvatar :object="item" :size="36" />
            <span class="mention-row__main">
              <span class="mention-row__name">{{ item.name }}</span>
              <span class="faint">{{ OBJECT_CLASS_LABELS[item.objectType] }}</span>
            </span>
          </RouterLink>
        </div>
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
            <label class="field__label" for="broken-class">Класс нового объекта</label>
            <select id="broken-class" v-model="brokenLinkClass" class="select">
              <option v-for="cls in OBJECT_CLASSES" :key="cls" :value="cls">
                {{ OBJECT_CLASS_LABELS[cls] }}
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

<style scoped>
.entity-gender {
  position: absolute;
  top: 0;
  right: 0;
  color: var(--text-muted);
  font-size: 1.2rem;
}
.person-row__value {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin: 0;
}
.person-row__icon {
  width: 20px;
  height: 20px;
  flex: none;
  color: var(--text-muted);
}
.person-row__mask {
  width: 18px;
  height: 18px;
  flex: none;
  background: var(--text-muted);
  -webkit-mask: center / contain no-repeat;
  mask: center / contain no-repeat;
}
.person-row__symbol {
  color: var(--text-muted);
}
</style>