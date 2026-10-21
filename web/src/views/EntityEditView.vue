<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { errorMessage } from '@/api'
import { OBJECT_CLASSES, type ObjectClass } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'
import EntityForm from '@/components/EntityForm.vue'
import { useCampaignStore } from '@/stores/campaign'
import { useUiStore } from '@/stores/ui'
import { emptyObjectForm, type ObjectFormValues } from '@/types/forms'

const route = useRoute()
const router = useRouter()
const campaign = useCampaignStore()
const ui = useUiStore()

const entityId = computed(() => (typeof route.params.entityId === 'string' ? route.params.entityId : ''))
const isEdit = computed(() => entityId.value !== '')
const existing = computed(() => (isEdit.value ? campaign.objectById(entityId.value) : null))

const saving = ref(false)
/** Класс объекта: при создании выбирается, при правке берётся у самой записи. */
const objectClass = ref<ObjectClass>(initialClass())
const form = ref<ObjectFormValues>(emptyObjectForm())

function initialClass(): ObjectClass {
  const fromQuery = route.query.class
  if (typeof fromQuery === 'string' && (OBJECT_CLASSES as readonly string[]).includes(fromQuery)) {
    return fromQuery as ObjectClass
  }
  return 'CHARACTER'
}

function tabForClass(cls: ObjectClass): string {
  if (cls === 'HERO') return 'heroes'
  if (cls === 'CHARACTER') return 'characters'
  if (cls === 'LOCATION') return 'locations'
  return 'items'
}

/** Заголовок называет класс: тип выбирается один раз при создании. */
const TITLES: Record<ObjectClass, { create: string; edit: string }> = {
  HERO: { create: 'Создание героя', edit: 'Правка героя' },
  CHARACTER: { create: 'Создание персонажа', edit: 'Правка персонажа' },
  LOCATION: { create: 'Создание локации', edit: 'Правка локации' },
  ITEM: { create: 'Создание предмета', edit: 'Правка предмета' },
}

const pageTitle = computed(() =>
  isEdit.value ? TITLES[objectClass.value].edit : TITLES[objectClass.value].create,
)

watch(
  existing,
  (object) => {
    if (!object) return
    objectClass.value = object.objectType
    form.value = campaign.objectFormValues(object)
  },
  { immediate: true },
)

/** Имя из ссылки `[[…]]` — предзаполняем новую запись (`missing-names`). */
watch(
  () => route.query.name,
  (value) => {
    if (!isEdit.value && typeof value === 'string' && form.value.name === '') {
      form.value = { ...form.value, name: value }
    }
  },
  { immediate: true },
)

const allowed = computed(() => {
  if (isEdit.value) {
    return existing.value ? campaign.canEdit(existing.value) : false
  }
  return campaign.canCreate(objectClass.value)
})

function back(): void {
  if (isEdit.value && existing.value) {
    void router.push({
      name: 'entity-detail',
      params: { campaignId: campaign.campaignId, entityId: existing.value.id },
    })
    return
  }
  const tab = tabForClass(objectClass.value)
  void router.push({
    name: tab === 'heroes' ? 'campaign-heroes' : 'campaign-tab',
    params: tab === 'heroes'
      ? { campaignId: campaign.campaignId }
      : { campaignId: campaign.campaignId, tab },
  })
}

async function submit(): Promise<void> {
  saving.value = true
  try {
    if (isEdit.value && existing.value) {
      const updated = await campaign.updateObject(existing.value.id, form.value)
      if (updated) {
        ui.pushToast('success', 'Изменения сохранены на устройстве')
        await router.push({
          name: 'entity-detail',
          params: { campaignId: campaign.campaignId, entityId: updated.id },
        })
      }
    } else {
      const created = await campaign.createObject(objectClass.value, form.value)
      ui.pushToast('success', 'Объект создан и уйдёт на сервер при синхронизации')
      await router.push({
        name: 'entity-detail',
        params: { campaignId: campaign.campaignId, entityId: created.id },
      })
    }
  } catch (caught) {
    ui.pushToast('error', errorMessage(caught))
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="stack">
    <div class="page-header">
      <div class="page-header__titles">
        <h1>{{ pageTitle }}</h1>
        <div class="page-header__subtitle">
          Правки сохраняются в локальную базу и отправляются на сервер при синхронизации.
        </div>
      </div>
    </div>

    <EmptyState
      v-if="!allowed"
      title="Недостаточно прав"
      :hint="`Ваша роль не позволяет ${isEdit ? 'править' : 'создавать'} запись этого класса. Героев партии ведёт только владелец кампании.`"
    >
      <button class="btn" type="button" @click="back">Назад</button>
    </EmptyState>

    <EmptyState
      v-else-if="isEdit && !existing"
      title="Объект не найден"
      hint="Он ещё не загружен на это устройство."
    >
      <button class="btn" type="button" @click="back">Назад</button>
    </EmptyState>

    <div v-else class="card">
      <EntityForm
        v-model="form"
        :object-class="objectClass"
        :mode="isEdit ? 'edit' : 'create'"
        :saving="saving"
        :submit-label="isEdit ? 'Сохранить изменения' : 'Создать'"
        @submit="submit"
        @cancel="back"
      />
    </div>
  </div>
</template>
