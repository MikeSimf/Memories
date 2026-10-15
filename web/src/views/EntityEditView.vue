<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { errorMessage } from '@/api'
import { ENTITY_TYPE_PLURALS, ENTITY_TYPES, type EntityType } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'
import EntityForm from '@/components/EntityForm.vue'
import { useCampaignStore } from '@/stores/campaign'
import { useUiStore } from '@/stores/ui'
import { emptyEntityForm, type EntityFormValues } from '@/types/forms'

const route = useRoute()
const router = useRouter()
const campaign = useCampaignStore()
const ui = useUiStore()

const entityId = computed(() => (typeof route.params.entityId === 'string' ? route.params.entityId : ''))
const isEdit = computed(() => entityId.value !== '')
const existing = computed(() => (isEdit.value ? campaign.entityById(entityId.value) : null))

const saving = ref(false)
const form = ref<EntityFormValues>(initialForm())

function initialForm(): EntityFormValues {
  const typeParam = route.query.type
  const type =
    typeof typeParam === 'string' && (ENTITY_TYPES as readonly string[]).includes(typeParam)
      ? (typeParam as EntityType)
      : 'NPC'
  const name = typeof route.query.name === 'string' ? route.query.name : ''
  return emptyEntityForm(type, name)
}

function tabForType(type: EntityType): string {
  switch (type) {
    case 'HERO':
      return 'heroes'
    case 'NPC':
      return 'npcs'
    case 'LOCATION':
      return 'locations'
    default:
      return 'items'
  }
}

watch(
  existing,
  (entity) => {
    if (entity) {
      form.value = campaign.entityFormValues(entity)
    }
  },
  { immediate: true },
)

const allowed = computed(() => {
  if (isEdit.value) {
    return existing.value ? campaign.canEdit(existing.value) : false
  }
  return campaign.canCreate(form.value.type)
})

function back(): void {
  if (isEdit.value && existing.value) {
    void router.push({
      name: 'entity-detail',
      params: { campaignId: campaign.campaignId, entityId: existing.value.id },
    })
    return
  }
  void router.push({
    name: 'campaign-tab',
    params: { campaignId: campaign.campaignId, tab: tabForType(form.value.type) },
  })
}

async function submit(): Promise<void> {
  saving.value = true
  try {
    if (isEdit.value && existing.value) {
      const updated = await campaign.updateEntity(existing.value.id, form.value)
      if (updated) {
        ui.pushToast('success', 'Изменения сохранены на устройстве')
        await router.push({
          name: 'entity-detail',
          params: { campaignId: campaign.campaignId, entityId: updated.id },
        })
      }
    } else {
      const created = await campaign.createEntity(form.value)
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
        <h1>{{ isEdit ? 'Правка объекта' : 'Новый объект' }}</h1>
        <div class="page-header__subtitle">
          Правки сохраняются в локальную базу и отправляются на сервер при синхронизации.
        </div>
      </div>
    </div>

    <EmptyState
      v-if="!allowed"
      title="Недостаточно прав"
      :hint="`Для этого действия нужно право на изменение объектов. Ваша роль не позволяет ${
        isEdit ? 'править' : 'создавать'
      } запись этого типа.`"
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
        :mode="isEdit ? 'edit' : 'create'"
        :saving="saving"
        :submit-label="isEdit ? 'Сохранить изменения' : `Создать: ${ENTITY_TYPE_PLURALS[form.type].toLowerCase()}`"
        @submit="submit"
        @cancel="back"
      />
    </div>
  </div>
</template>
