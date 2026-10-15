<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { errorMessage } from '@/api'
import type { EntityType } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'
import EntityPicker from '@/components/EntityPicker.vue'
import MarkupText from '@/components/MarkupText.vue'
import { useCampaignStore } from '@/stores/campaign'
import { useUiStore } from '@/stores/ui'
import { todayIso } from '@/utils/datetime'
import { emptyRetellingForm, type RetellingFormValues } from '@/types/forms'

const route = useRoute()
const router = useRouter()
const campaign = useCampaignStore()
const ui = useUiStore()

const HERO_TYPES: EntityType[] = ['HERO', 'NPC']

const retellingId = computed(() =>
  typeof route.params.retellingId === 'string' ? route.params.retellingId : '',
)

/** Режим экрана: новый пересказ, черновик или публикация. */
const mode = computed<'new' | 'draft' | 'published'>(() => {
  if (!retellingId.value) return 'new'
  if (campaign.draftById(retellingId.value)) return 'draft'
  if (campaign.retellingById(retellingId.value)) return 'published'
  return 'new'
})

const form = ref<RetellingFormValues>(emptyRetellingForm())
const saving = ref(false)
const showPreview = ref(false)

function fillForm(): void {
  if (mode.value === 'new') {
    form.value = { ...emptyRetellingForm(), sessionDate: todayIso() }
    return
  }
  const source =
    mode.value === 'draft'
      ? campaign.draftById(retellingId.value)
      : campaign.retellingById(retellingId.value)
  if (source) {
    form.value = campaign.retellingFormValues(source)
  }
}

watch([mode, retellingId], fillForm, { immediate: true })

const canWrite = computed(() => campaign.canWriteRetellings)

function back(): void {
  if (retellingId.value) {
    void router.push({
      name: 'retelling-detail',
      params: { campaignId: campaign.campaignId, retellingId: retellingId.value },
    })
    return
  }
  void router.push({
    name: 'campaign-tab',
    params: { campaignId: campaign.campaignId, tab: 'retellings' },
  })
}

async function saveDraft(): Promise<void> {
  saving.value = true
  try {
    const id = await campaign.saveDraft(retellingId.value || null, form.value)
    ui.pushToast('info', 'Черновик сохранён только на устройстве')
    await router.replace({
      name: 'retelling-detail',
      params: { campaignId: campaign.campaignId, retellingId: id },
    })
  } catch (caught) {
    ui.pushToast('error', errorMessage(caught))
  } finally {
    saving.value = false
  }
}

async function publish(): Promise<void> {
  if (form.value.title.trim() === '') {
    ui.pushToast('warning', 'Укажите название пересказа')
    return
  }
  saving.value = true
  try {
    let id: string
    if (mode.value === 'draft' && retellingId.value) {
      id = await campaign.publishDraft(retellingId.value, form.value)
    } else if (mode.value === 'published' && retellingId.value) {
      const updated = await campaign.updatePublishedRetelling(retellingId.value, form.value)
      id = updated?.id ?? retellingId.value
    } else {
      id = await campaign.createPublishedRetelling(form.value)
    }
    ui.pushToast('success', 'Пересказ опубликован и уйдёт на сервер при синхронизации')
    await router.replace({
      name: 'retelling-detail',
      params: { campaignId: campaign.campaignId, retellingId: id },
    })
  } catch (caught) {
    ui.pushToast('error', errorMessage(caught))
  } finally {
    saving.value = false
  }
}

async function savePublished(): Promise<void> {
  if (!retellingId.value) return
  saving.value = true
  try {
    await campaign.updatePublishedRetelling(retellingId.value, form.value)
    ui.pushToast('success', 'Изменения сохранены на устройстве')
    await router.push({
      name: 'retelling-detail',
      params: { campaignId: campaign.campaignId, retellingId: retellingId.value },
    })
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
        <h1>
          {{
            mode === 'new'
              ? 'Новый пересказ'
              : mode === 'draft'
                ? 'Черновик пересказа'
                : 'Правка пересказа'
          }}
        </h1>
        <div class="page-header__subtitle">
          <template v-if="mode === 'published'">
            Пересказ опубликован: правки уйдут на сервер при синхронизации.
          </template>
          <template v-else>
            Черновик хранится только на этом устройстве и не отправляется на сервер, пока вы не
            нажмёте «Опубликовать».
          </template>
        </div>
      </div>
    </div>

    <EmptyState
      v-if="!canWrite"
      title="Недостаточно прав"
      hint="Создавать и править пересказы могут участники с правом retelling.write."
    >
      <button class="btn" type="button" @click="back">Назад</button>
    </EmptyState>

    <form v-else class="card stack" @submit.prevent="mode === 'published' ? savePublished() : publish()">
      <div class="field">
        <label class="field__label" for="retelling-title">Название</label>
        <input
          id="retelling-title"
          v-model="form.title"
          class="input"
          type="text"
          required
          placeholder="Сессия 12: подземелье"
        />
      </div>

      <div class="row" style="align-items: flex-end">
        <div class="field" style="flex: 0 0 200px">
          <label class="field__label" for="retelling-date">Дата сессии</label>
          <input id="retelling-date" v-model="form.sessionDate" class="input" type="date" />
        </div>
        <div class="field" style="flex: 1; min-width: 220px">
          <label class="field__label" for="retelling-author">От лица кого</label>
          <EntityPicker
            :model-value="form.authorEntityId"
            :types="HERO_TYPES"
            empty-label="— от лица мастера —"
            @update:model-value="form.authorEntityId = $event"
          />
          <span class="field__hint">Выбор из героев и персонажей кампании (authorEntityId).</span>
        </div>
      </div>

      <div class="field">
        <div class="row row--between">
          <label class="field__label" for="retelling-body">Текст</label>
          <button class="btn btn--ghost btn--sm" type="button" @click="showPreview = !showPreview">
            {{ showPreview ? 'Скрыть предпросмотр' : 'Предпросмотр разметки' }}
          </button>
        </div>
        <textarea
          id="retelling-body"
          v-model="form.body"
          class="textarea textarea--tall"
          placeholder="Мы вошли в [[Подземелье волн]]…"
        />
        <span class="field__hint">
          Markdown и ссылки <code>[[Имя]]</code>, <code>[[Имя|подпись]]</code>, <code>[[#uuid|подпись]]</code>.
        </span>
        <div v-if="showPreview" class="preview-box">
          <MarkupText v-if="form.body.trim()" :text="form.body" />
          <span v-else class="faint">Пусто</span>
        </div>
      </div>

      <div class="row row--end">
        <button class="btn" type="button" :disabled="saving" @click="back">Отмена</button>
        <button
          v-if="mode !== 'published'"
          class="btn"
          type="button"
          :disabled="saving || form.title.trim() === ''"
          @click="saveDraft"
        >
          Сохранить черновик
        </button>
        <button
          v-if="mode === 'published'"
          class="btn btn--primary"
          type="submit"
          :disabled="saving || form.title.trim() === ''"
        >
          <span v-if="saving" class="spinner" aria-hidden="true" />
          Сохранить
        </button>
        <button
          v-else
          class="btn btn--primary"
          type="submit"
          :disabled="saving || form.title.trim() === ''"
        >
          <span v-if="saving" class="spinner" aria-hidden="true" />
          Опубликовать
        </button>
      </div>
    </form>
  </div>
</template>
