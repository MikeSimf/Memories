<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { errorMessage, injuriesApi, type InjuryDto } from '@/api'
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
import { useAuthStore } from '@/stores/auth'
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
const auth = useAuthStore()
const canViewGmNote = computed(() => {
  if (auth.user?.admin) return false
  const role = campaign.campaign?.myRole
  if (role === 'EDITOR_MASTER') return true
  if (role === 'OWNER') return !campaign.members.some((member) => member.role === 'EDITOR_MASTER' && member.status !== 'REMOVED')
  return false
})
const injuries = ref<InjuryDto[]>([])
const newInjury = ref('')
const editingInjury = ref<string | null>(null)
const editingText = ref('')

async function loadInjuries(): Promise<void> {
  const current = entity.value
  if (!current || current.objectType !== 'HERO') { injuries.value = []; return }
  try { injuries.value = await injuriesApi.list(campaign.campaignId ?? '', current.id) } catch { injuries.value = [] }
}

async function addInjury(): Promise<void> {
  const current = entity.value
  if (!current || !newInjury.value.trim()) return
  await injuriesApi.add(campaign.campaignId ?? '', current.id, newInjury.value.trim())
  newInjury.value = ''
  await loadInjuries()
}

async function saveInjury(item: InjuryDto): Promise<void> {
  const current = entity.value
  if (!current || !editingText.value.trim()) return
  await injuriesApi.update(campaign.campaignId ?? '', current.id, item.id, editingText.value.trim())
  editingInjury.value = null
  await loadInjuries()
}

async function removeInjury(item: InjuryDto): Promise<void> {
  const current = entity.value
  if (!current || !confirm('Удалить травму?')) return
  await injuriesApi.remove(campaign.campaignId ?? '', current.id, item.id)
  await loadInjuries()
}

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
      { label: 'Магический', value: item.magic === true ? 'да' : item.magic === false ? 'нет' : 'неизвестно' },
      { label: 'Есть настройка', value: item.hasSetting ? 'да' : 'нет' },
      { label: 'Настроен на героя', value: campaign.objectName(item.configuredHeroId ?? '') ?? '—' },
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
    { label: 'Статус', value: item.alive === true ? 'жив' : item.alive === false ? 'мёртв' : 'неизвестно', icon: null },
  ]
  if (item.objectType === 'CHARACTER') {
    rows.push({ label: 'Кратко', value: item.shortDescription || 'Не заполнено', icon: null })
  }
  return rows
})

const backpack = computed(() => {
  const current = entity.value
  if (!current || (current.objectType !== 'HERO' && current.objectType !== 'CHARACTER')) return []
  return campaign.activeObjects
    .filter((item) => item.objectType === 'ITEM' && (item.ownerHeroId === current.id || item.ownerCharacterId === current.id))
    .sort((a, b) => a.name.localeCompare(b.name, 'ru'))
})

const itemOwner = computed(() => {
  const current = entity.value
  if (!current || current.objectType !== 'ITEM') return null
  return campaign.objectById(current.ownerHeroId ?? '') ?? campaign.objectById(current.ownerCharacterId ?? '') ?? null
})

const configuredArtifacts = computed(() => {
  const current = entity.value
  if (!current || current.objectType !== 'HERO') return []
  return campaign.activeObjects
    .filter((item) => item.objectType === 'ITEM' && item.configuredHeroId === current.id)
    .sort((a, b) => a.name.localeCompare(b.name, 'ru'))
})

const baseStats = computed(() => {
  const current = entity.value
  if (!current || current.objectType !== 'HERO') return []
  return [
    ['СИЛ', current.strength], ['ЛОВ', current.dexterity], ['ТЕЛ', current.constitution],
    ['ИНТ', current.intelligence], ['МУД', current.wisdom], ['ХАР', current.charisma],
  ] as Array<[string, number]>
})

const extraStats = computed(() => {
  const current = entity.value
  if (!current || current.objectType !== 'HERO') return []
  return [
    ['ВНИМ', current.passivePerception], ['ИНИЦ', current.initiative], ['СКОР', current.speed],
    ['КД', current.armorClass], ['МАКС', current.maxHp], ['ВДОХ', current.inspiration],
  ].filter((row) => row[1] != null) as Array<[string, number | null]>
})

function modifier(value: number): number {
  return Math.floor((value - 10) / 2)
}

const statusKind = computed(() => {
  const current = entity.value
  if (!current || (current.objectType !== 'ITEM' && current.objectType !== 'LOCATION')) return null
  return current.intact ? 'alive' : 'dead'
})

const personGender = computed(() => {
  const item = entity.value
  if (!item || (item.objectType !== 'HERO' && item.objectType !== 'CHARACTER')) return ''
  return genderSymbol(item.gender)
})

/** Единый список ссылок: исходящие, обратные и владелец предмета. */
const LINK_ORDER: ObjectClass[] = ['CHARACTER', 'LOCATION', 'ITEM', 'HERO']
const linkGroups = computed(() => {
  const current = entity.value
  if (!current) return []
  const byId = new Map<string, LocalObject>()
  const add = (object: LocalObject | null | undefined) => {
    if (!object || object.id === current.id) return
    byId.set(object.id, object)
  }
  for (const link of current.links ?? []) {
    if (link.targetId) add(campaign.objectById(link.targetId))
  }
  for (const item of campaign.activeObjects) {
    if (item.links.some((link) => link.targetId === current.id)) add(item)
  }
  return LINK_ORDER.map((type) => ({
    type,
    label: OBJECT_CLASS_LABELS[type],
    items: [...byId.values()]
      .filter((item) => item.objectType === type)
      .sort((a, b) => a.name.localeCompare(b.name, 'ru')),
  })).filter((group) => group.items.length > 0)
})

/** Истории, которые ссылаются на объект. */
const retellingMentions = computed(() =>
  campaign.retellings.filter((item) =>
    (item.links ?? []).some((link) => link.targetId === entityId.value),
  ),
)

const avatarOpen = ref(false)
const brokenLinkName = ref<string | null>(null)
const brokenLinkClass = ref<ObjectClass>('CHARACTER')
const confirmDelete = ref(false)
const busy = ref(false)

watch(entityId, () => {
  confirmDelete.value = false
  brokenLinkName.value = null
  void loadInjuries()
}, { immediate: true })

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
            <button v-if="entity.avatarFileId" class="avatar-open" type="button" @click="avatarOpen = true">
              <EntityImage :file-id="entity.avatarFileId" :alt="entity.name" />
            </button>
            <div>
          <h1>
            <span
              v-if="entity.objectType === 'CHARACTER' || entity.objectType === 'ITEM' || entity.objectType === 'LOCATION'"
              class="life-dot"
              :class="entity.objectType === 'CHARACTER' ? (entity.alive === true ? 'life-dot--alive' : entity.alive === false ? 'life-dot--dead' : 'life-dot--unknown') : (statusKind === 'alive' ? 'life-dot--alive' : 'life-dot--dead')"
            />
            {{ entity.name }}
            <span v-if="entity.dirty === 1" class="dirty-dot" title="Есть неотправленная правка" />
            <span v-if="personGender" class="entity-gender" title="Пол">{{ personGender }}</span>
          </h1>
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
          v-if="entity.objectType === 'LOCATION' && canEdit"
          class="btn btn--sm"
          :to="{
            name: 'entity-new',
            params: { campaignId: campaign.campaignId },
            query: { class: 'CHARACTER', description: `[[${entity.name}]].` },
          }"
        >
          Добавить персонажа
        </RouterLink>
        <RouterLink
          v-if="(entity.objectType === 'CHARACTER' || entity.objectType === 'HERO') && canEdit"
          class="btn btn--sm"
          :to="{
            name: 'entity-new',
            params: { campaignId: campaign.campaignId },
            query: {
              class: 'ITEM',
              name: '',
              owner: entity.id,
              ownerClass: entity.objectType,
            },
          }"
        >
          Добавить предмет
        </RouterLink>
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

      <div v-if="entity.objectType === 'HERO'" class="card">
        <h2>Характеристики</h2>
        <div class="stats-row">
          <div v-for="stat in baseStats" :key="stat[0]" class="stat-box">
            <span class="stat-box__label">{{ stat[0] }}</span>
            <strong class="stat-box__mod">{{ modifier(stat[1]) >= 0 ? '+' : '' }}{{ modifier(stat[1]) }}</strong>
            <span class="stat-box__value">{{ stat[1] }}</span>
          </div>
        </div>
        <div v-if="extraStats.length" class="stats-row">
          <div v-for="stat in extraStats" :key="stat[0]" class="stat-box stat-box--small">
            <span class="stat-box__label">{{ stat[0] }}</span>
            <span class="stat-box__value">{{ stat[1] }}</span>
          </div>
        </div>
      </div>

      <div v-if="entity.objectType === 'HERO'" class="card">
        <h2>Настроен на артефакты</h2>
        <p v-if="configuredArtifacts.length === 0" class="faint">Нет артефактов, настроенных на героя.</p>
        <div v-else class="list">
          <RouterLink v-for="item in configuredArtifacts" :key="item.id" class="list-row" :to="{ name: 'entity-detail', params: { campaignId: campaign.campaignId, entityId: item.id } }">
            <span class="list-row__title">{{ item.name }}</span>
          </RouterLink>
        </div>
      </div>

      <div v-else-if="details.length > 0" class="card">
        <dl class="attr-grid" style="margin: 0">
          <div v-for="row in details" :key="row.label">
            <dt class="field__label">{{ row.label }}</dt>
            <dd style="margin: 0">{{ row.value }}</dd>
          </div>
        </dl>
      </div>

      <div v-if="itemOwner" class="card">
        <h2>Владелец</h2>
        <RouterLink :to="{ name: 'entity-detail', params: { campaignId: campaign.campaignId, entityId: itemOwner.id } }">{{ itemOwner.name }}</RouterLink>
      </div>

      <div v-if="entity.objectType === 'ITEM' && entity.configuredHeroId" class="card">
        <h2>Настроен на героя</h2>
        <RouterLink :to="{ name: 'entity-detail', params: { campaignId: campaign.campaignId, entityId: entity.configuredHeroId } }">
          {{ campaign.objectName(entity.configuredHeroId) }}
        </RouterLink>
      </div>

      <!-- Рюкзак: предметы, у которых владелец — этот герой или персонаж -->
      <div v-if="entity.objectType === 'HERO' || (entity.objectType === 'CHARACTER' && backpack.length > 0)" class="card">
        <h2>Рюкзак</h2>
        <p v-if="backpack.length === 0" class="faint">Пусто.</p>
        <div v-else class="list">
          <RouterLink v-for="item in backpack" :key="item.id" class="list-row" :to="{ name: 'entity-detail', params: { campaignId: campaign.campaignId, entityId: item.id } }">
            <span class="list-row__title">{{ item.name }}</span>
          </RouterLink>
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
        Создал(а) {{ entity.createdByName || campaign.userName(entity.createdBy) }}, изменил(а)
        {{ entity.updatedByName || campaign.userName(entity.updatedBy) }} · ревизия {{ entity.revision }} ·
        {{ formatDateTime(entity.updatedAt) }}
      </div>

      <div v-if="entity.objectType === 'HERO'" class="card stack">
        <h2>Травмы и увечья</h2>
        <p v-if="injuries.length === 0" class="faint">Пока нет записей.</p>
        <div v-for="item in injuries" :key="item.id" class="injury-row">
          <template v-if="editingInjury === item.id">
            <textarea v-model="editingText" class="input" rows="2" />
            <div class="row"><button class="btn btn--sm btn--primary" type="button" @click="saveInjury(item)">Сохранить</button><button class="btn btn--sm" type="button" @click="editingInjury = null">Отмена</button></div>
          </template>
          <template v-else>
            <span>{{ item.text }}</span>
            <div v-if="canEdit" class="row"><button class="btn btn--ghost btn--sm" type="button" @click="editingInjury = item.id; editingText = item.text">Изменить</button><button class="btn btn--danger btn--sm" type="button" @click="removeInjury(item)">Удалить</button></div>
          </template>
        </div>
        <div v-if="canEdit" class="row" style="align-items: flex-start">
          <textarea v-model="newInjury" class="input" rows="2" placeholder="Новая травма или увечье" />
          <button class="btn btn--primary btn--sm" type="button" @click="addInjury">Добавить</button>
        </div>
      </div>

      <div v-if="entity.objectType !== 'HERO' && entity.hasGmNote" class="card stack">
        <h2>Пометка мастера</h2>
        <p v-if="canViewGmNote && entity.gmNote" style="white-space: pre-wrap">{{ entity.gmNote }}</p>
        <p v-else class="faint">Вы не можете видеть пометки мастера.</p>
      </div>

      <div v-if="linkGroups.length > 0" class="card">
        <h2>Ссылки</h2>
        <div class="link-groups">
          <details v-for="group in linkGroups" :key="group.type" class="link-group" open>
            <summary>{{ group.label }} <span class="faint">{{ group.items.length }}</span></summary>
            <div class="mention-list">
              <RouterLink
                v-for="item in group.items"
                :key="item.id"
                class="mention-row"
                :to="{ name: 'entity-detail', params: { campaignId: campaign.campaignId, entityId: item.id } }"
              >
                <EntityAvatar :object="item" :size="36" />
                <span class="mention-row__main">
                  <span class="mention-row__name">{{ item.name }}</span>
                </span>
              </RouterLink>
            </div>
          </details>
        </div>
      </div>

      <div v-if="retellingMentions.length > 0" class="card">
        <h2>Истории</h2>
        <div class="mention-list">
          <RouterLink
            v-for="item in retellingMentions"
            :key="item.id"
            class="mention-row"
            :to="{ name: 'retelling-detail', params: { campaignId: campaign.campaignId, retellingId: item.id } }"
          >
            <span class="mention-row__name">{{ item.title }}</span>
          </RouterLink>
        </div>
      </div>

      <BaseModal
        :open="avatarOpen"
        title="Аватар"
        @close="avatarOpen = false"
      >
        <EntityImage v-if="entity.avatarFileId" :file-id="entity.avatarFileId" :alt="entity.name" />
      </BaseModal>

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
  margin-left: 0.5rem;
  color: var(--text-muted);
  font-size: 1.2rem;
}
.mention-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.mention-row {
  display: flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
  color: inherit;
}
.mention-row__main {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.mention-row__name {
  font-weight: 600;
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
.avatar-open { padding: 0; border: 0; background: none; cursor: zoom-in; width: 64px; height: 64px; flex: none; }
.life-dot { display: inline-block; width: 10px; height: 10px; border-radius: 50%; margin-right: 8px; vertical-align: middle; }
.life-dot--alive { background: #2e7d32; box-shadow: 0 0 0 2px rgba(46,125,50,.22); }
.life-dot--dead { background: #c62828; box-shadow: 0 0 0 2px rgba(198,40,40,.22); }
.life-dot--unknown { background: #8a8a8a; box-shadow: 0 0 0 2px rgba(138,138,138,.22); }
.stats-row { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 6px; margin-bottom: 8px; }
.stat-box { display: flex; flex-direction: column; align-items: center; gap: 2px; border: 1px solid var(--border); border-radius: 8px; padding: 6px 2px; background: var(--bg-elev); }
.stat-box__label { font-size: .68rem; font-weight: 700; color: var(--text-muted); }
.stat-box__mod { font-size: 1.15rem; line-height: 1.1; }
.stat-box__value { font-size: .85rem; }
.stat-box--small .stat-box__value { font-size: 1rem; font-weight: 700; }
@media (max-width: 640px) { .stats-row { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
.injury-row { display: flex; flex-direction: column; gap: 6px; padding: 8px 0; border-bottom: 1px solid var(--border); }
</style>