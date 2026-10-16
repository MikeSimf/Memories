import { defineStore } from 'pinia'
import { ref } from 'vue'

import {
  ApiError,
  campaignsApi,
  errorMessage,
  type CampaignDto,
  type CampaignListItem,
} from '@/api'
import {
  campaignRecordFromDto,
  clearCampaignData,
  deleteCampaignRecord,
  getCampaignRecord,
  getRetelling,
  listCampaignRecords,
  memberFromDto,
  putCampaignRecord,
  putMembers,
  putRetelling,
  type CampaignRecord,
} from '@/db'
import { isOnline } from '@/sync/engine'
import { useUiStore } from './ui'

/** Список кампаний пользователя: из сети, а без неё — из локальной базы. */
export const useCampaignsStore = defineStore('campaigns', () => {
  const ui = useUiStore()

  const items = ref<CampaignRecord[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)
  const loadedFromCache = ref(false)

  async function load(): Promise<void> {
    loading.value = true
    error.value = null
    if (!isOnline()) {
      items.value = await listCampaignRecords()
      loadedFromCache.value = true
      loading.value = false
      ui.pushToast('info', 'Нет сети — показан список кампаний с устройства')
      return
    }
    try {
      const list: CampaignListItem[] = await campaignsApi.list()
      const records: CampaignRecord[] = []
      for (const item of list) {
        const previous = await getCampaignRecord(item.id)
        records.push(
          campaignRecordFromDto(item, {
            permissions: previous?.myPermissions ?? [],
            counts: {
              memberCount: item.memberCount,
              entityCount: item.entityCount,
              retellingCount: item.retellingCount,
              latestRevision: item.latestRevision,
            },
          }),
        )
      }
      // Список кампаний краткий: краткие пересказы и участники сохраняем из кэша.
      await Promise.all(
        records.map(async (record) => {
          const previous = await getCampaignRecord(record.id)
          await putCampaignRecord({
            ...record,
            members: previous?.members ?? [],
            retellingBriefs: previous?.retellingBriefs ?? [],
          })
        }),
      )
      items.value = records.sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
      loadedFromCache.value = false
    } catch (caught) {
      error.value = errorMessage(caught)
      items.value = await listCampaignRecords()
      loadedFromCache.value = true
    } finally {
      loading.value = false
    }
  }

  /**
   * Детали кампании (`api.md` §4): роль, права, счётчики, а также краткие списки
   * участников и пересказов. Участников сразу кладём в локальное хранилище —
   * интерфейсу не нужен отдельный запрос за ними.
   */
  async function loadDetails(campaignId: string): Promise<CampaignRecord | null> {
    try {
      const dto: CampaignDto = await campaignsApi.get(campaignId)
      const record = campaignRecordFromDto(dto, {
        permissions: dto.myPermissions ?? [],
        counts: {
          memberCount: dto.memberCount ?? 0,
          entityCount: dto.entityCount ?? 0,
          retellingCount: dto.retellingCount ?? 0,
          latestRevision: dto.latestRevision ?? 0,
        },
      })
      // Участников обновляем точечно: список в карточке кампании краткий, поэтому
      // удалять по нему локальные записи нельзя (удаление приходит журналом sync).
      if (record.members.length > 0) {
        await putMembers(record.members.map((member) => memberFromDto(campaignId, member)))
      }
      // Краткие пересказы из карточки кампании становятся локальными «заглушками»:
      // список открывается без отдельного запроса, а полный текст придёт синхронизацией.
      for (const brief of record.retellingBriefs) {
        const existing = await getRetelling(brief.id)
        if (existing) continue
        await putRetelling({
          id: brief.id,
          campaignId,
          clientId: brief.id,
          title: brief.title,
          body: '',
          authorEntityId: null,
          authorEntityName: null,
          sessionDate: brief.sessionDate,
          status: 'PUBLISHED',
          links: [],
          revision: brief.revision,
          createdBy: '',
          createdByName: '',
          updatedBy: '',
          updatedByName: '',
          createdAt: brief.updatedAt,
          updatedAt: brief.updatedAt,
          deleted: false,
          dirty: 0,
          conflict: null,
        })
      }
      await putCampaignRecord(record)
      return record
    } catch (caught) {
      if (!(caught instanceof ApiError) || !caught.isOffline) {
        error.value = errorMessage(caught)
      }
      return getCampaignRecord(campaignId)
    }
  }

  async function create(title: string, description: string): Promise<CampaignRecord> {
    const dto = await campaignsApi.create({ title, description })
    const record = campaignRecordFromDto(dto, {
      permissions: dto.myPermissions ?? [],
      counts: {
        memberCount: 1,
        entityCount: 0,
        retellingCount: 0,
        latestRevision: dto.latestRevision ?? 0,
      },
    })
    await putCampaignRecord(record)
    items.value = [record, ...items.value.filter((item) => item.id !== record.id)]
    return record
  }

  async function update(campaignId: string, title: string, description: string): Promise<void> {
    const dto = await campaignsApi.patch(campaignId, { title, description })
    const previous = await getCampaignRecord(campaignId)
    const record = campaignRecordFromDto(dto, {
      permissions: dto.myPermissions ?? previous?.myPermissions ?? [],
      counts: {
        memberCount: previous?.memberCount ?? 0,
        entityCount: previous?.entityCount ?? 0,
        retellingCount: previous?.retellingCount ?? 0,
        latestRevision: dto.latestRevision ?? previous?.latestRevision ?? 0,
      },
    })
    await putCampaignRecord({ ...record, members: previous?.members ?? [], retellingBriefs: previous?.retellingBriefs ?? [] })
    items.value = items.value.map((item) => (item.id === record.id ? record : item))
  }

  async function remove(campaignId: string): Promise<void> {
    await campaignsApi.remove(campaignId)
    await clearCampaignData(campaignId)
    await deleteCampaignRecord(campaignId)
    items.value = items.value.filter((item) => item.id !== campaignId)
  }

  /** Локальная копия кампании — для открытия карточки без сети. */
  async function getRecord(campaignId: string): Promise<CampaignRecord | null> {
    const local = await getCampaignRecord(campaignId)
    if (local) return local
    return loadDetails(campaignId)
  }

  return {
    items,
    loading,
    error,
    loadedFromCache,
    load,
    loadDetails,
    create,
    update,
    remove,
    getRecord,
  }
})

export type { CampaignRecord }
