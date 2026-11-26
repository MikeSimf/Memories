<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import { errorMessage, questsApi, type QuestDto } from '@/api'
import EntityImage from '@/components/EntityImage.vue'
import MarkupText from '@/components/MarkupText.vue'
import { useCampaignStore } from '@/stores/campaign'

const route = useRoute()
const campaign = useCampaignStore()
const campaignId = String(route.params.campaignId ?? '')
const questId = String(route.params.questId ?? '')
const quest = ref<QuestDto | null>(null)
const error = ref<string | null>(null)
const newComment = ref('')
const editingId = ref<string | null>(null)
const editingText = ref('')
const canWrite = ref(false)

async function load(): Promise<void> {
  try {
    quest.value = await questsApi.get(campaignId, questId)
    canWrite.value = campaign.canCreate('LOCATION')
  } catch (caught) { error.value = errorMessage(caught) }
}
async function addComment(): Promise<void> {
  if (!newComment.value.trim()) return
  await questsApi.addComment(campaignId, questId, newComment.value.trim())
  newComment.value = ''
  await load()
}
async function saveComment(id: string): Promise<void> {
  if (!editingText.value.trim()) return
  await questsApi.updateComment(campaignId, questId, id, editingText.value.trim())
  editingId.value = null
  await load()
}
async function removeComment(id: string): Promise<void> {
  if (!confirm('Удалить комментарий?')) return
  await questsApi.removeComment(campaignId, questId, id)
  await load()
}
onMounted(load)
</script>

<template>
  <div class="stack">
    <div class="page-header">
      <div class="page-header__titles"><h1>{{ quest?.name ?? 'Квест' }}</h1></div>
      <RouterLink class="btn btn--sm" :to="{ name: 'campaign-quests', params: { campaignId } }">К квестам</RouterLink>
      <RouterLink v-if="canWrite && quest" class="btn btn--sm" :to="{ name: 'quest-edit', params: { campaignId, questId } }">Изменить</RouterLink>
    </div>
    <p v-if="error" class="notice notice--danger">{{ error }}</p>
    <template v-if="quest">
      <div v-if="quest.avatarFileId" style="max-width: 180px"><EntityImage :file-id="quest.avatarFileId" alt="" /></div>
      <div class="card"><MarkupText :text="quest.description || 'Описание не заполнено.'" /></div>
      <p class="faint">
        Автор: {{ quest.createdByName || 'неизвестно' }} · создан {{ quest.createdAt }}
        <template v-if="quest.updatedByName && quest.updatedByName !== quest.createdByName"> · изменил(а): {{ quest.updatedByName }}, {{ quest.updatedAt }}</template>
      </p>
      <div class="card stack">
        <h2>Комментарии</h2>
        <p v-if="quest.comments.length === 0" class="faint">Комментариев пока нет.</p>
        <div v-for="comment in quest.comments" :key="comment.id" class="comment-row">
          <div class="row row--between">
            <strong>{{ comment.updatedByName || comment.createdByName || 'Участник' }}</strong>
            <span class="faint">{{ comment.updatedAt || comment.createdAt }}</span>
          </div>
          <div v-if="comment.updatedByName" class="faint">изменён</div>
          <template v-if="editingId === comment.id">
            <textarea v-model="editingText" class="input" rows="3" />
            <div class="row"><button class="btn btn--sm btn--primary" type="button" @click="saveComment(comment.id)">Сохранить</button><button class="btn btn--sm" type="button" @click="editingId = null">Отмена</button></div>
          </template>
          <template v-else>
            <p>{{ comment.text }}</p>
            <div v-if="canWrite" class="row"><button class="btn btn--ghost btn--sm" type="button" @click="editingId = comment.id; editingText = comment.text">Изменить</button><button class="btn btn--danger btn--sm" type="button" @click="removeComment(comment.id)">Удалить</button></div>
          </template>
        </div>
        <div v-if="canWrite" class="row" style="align-items: flex-start">
          <textarea v-model="newComment" class="input" rows="3" placeholder="Добавить комментарий" />
          <button class="btn btn--primary btn--sm" type="button" @click="addComment">Добавить</button>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.comment-row { display: flex; flex-direction: column; gap: 4px; border-bottom: 1px solid var(--border); padding: 8px 0; }
</style>
