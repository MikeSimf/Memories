import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import {
  ApiError,
  errorMessage,
  invitesApi,
  membersApi,
  objectsApi,
  type AcceptInviteResponse,
  type AddMemberRequest,
  type AddMemberResponse,
  type CampaignObjectDto,
  type CreateInviteRequest,
  type CharacterDto,
  type EntityLinkDto,
  type HeroDto,
  type ItemDto,
  type LocationDto,
  type MemberRole,
  type ObjectClass,
  type ObjectListQuery,
  type PageDto,
  type PatchMemberRequest,
  type InviteDto,
  type Permission,
  type RetellingBriefDto,
  type RetellingStatus,
} from '@/api'
import {
  deleteDraft,
  deleteMemberRecord,
  deleteObjectRecord,
  deleteRetellingRecord,
  draftToRetelling,
  getDraft,
  getObject,
  getRetelling,
  isLocalObjectOf,
  listDrafts,
  listMembers,
  listObjects,
  listRetellings,
  memberFromDto,
  nowIso,
  objectFromDto,
  objectToPayload,
  objectTombstone,
  onDatabaseChanged,
  pruneMembers,
  putDraft,
  putMember,
  putMembers,
  putObject,
  putObjects,
  putRetelling,
  retellingToPayload,
  type CampaignRecord,
  type LocalCharacter,
  type LocalHero,
  type LocalItem,
  type LocalLocation,
  type LocalObject,
  type LocalRetelling,
  type LocalRetellingDraft,
  type MemberRecord,
} from '@/db'
import {
  notifyLocalChange,
  resolveConflictUseLocal as applyConflictUseLocal,
  resolveConflictUseServer as applyConflictUseServer,
} from '@/sync/engine'
import { enqueueChange } from '@/sync/queue'
import { formatRelative } from '@/utils/datetime'
import {
  canCreateObject,
  canCreateRetelling,
  canDeleteObject,
  canDeleteRetelling,
  canEditObject,
  canEditRetelling,
  hasPermission,
  type PermissionSubject,
} from '@/utils/permissions'
import { collectLinks, createLinkResolver, emptyLinkResolver } from '@/utils/wikilinks'
import { createUuid } from '@/utils/uuid'
import { useAuthStore } from './auth'
import { useCampaignsStore } from './campaigns'
import { useUiStore } from './ui'
import type { HeroItemFormValue, ObjectFormValues, RetellingFormValues } from '@/types/forms'

export type { ObjectFormValues, RetellingFormValues }

/** Строка объединённого списка пересказов: черновики и публикации вместе. */
export interface RetellingListItem {
  id: string
  title: string
  body: string
  sessionDate: string | null
  status: RetellingStatus
  authorEntityId: string | null
  authorEntityName: string | null
  updatedAt: string
  updatedBy: string
  revision: number
  /** Есть неотправленная правка. */
  dirty: boolean
  /** Есть расхождение с сервером. */
  conflicted: boolean
  isDraft: boolean
}

export const useCampaignStore = defineStore('campaign', () => {
  const auth = useAuthStore()
  const ui = useUiStore()
  const campaigns = useCampaignsStore()

  const campaignId = ref<string | null>(null)
  const campaign = ref<CampaignRecord | null>(null)
  /** Все четыре класса объектов кампании в одной коллекции — с полем `objectType`. */
  const objects = ref<LocalObject[]>([])
  const retellings = ref<LocalRetelling[]>([])
  const drafts = ref<LocalRetellingDraft[]>([])
  const members = ref<MemberRecord[]>([])
  const invites = ref<InviteDto[]>([])
  /**
   * Объекты активной вкладки: читаются из IndexedDB с фильтром классов
   * (`listObjects(id, { classes })`) — поэтому список работает и без сети.
   */
  const tabObjects = ref<LocalObject[]>([])
  const tabClasses = ref<ObjectClass[]>([])
  /** Идёт постраничная подгрузка вкладки с сервера (локальный список уже показан). */
  const tabLoading = ref(false)
  /** Сколько объектов вкладки уже получено с сервера (для индикатора). */
  const tabLoaded = ref(0)
  /** Сколько объектов обещает сервер (`total`); 0 — размер неизвестен. */
  const tabTotal = ref(0)
  const loading = ref(false)
  const error = ref<string | null>(null)
  let unsubscribeDatabase: (() => void) | null = null
  /** Метка последней загрузки вкладки: гасит ответы устаревших вкладок. */
  let tabLoadToken = 0

  const userId = computed(() => auth.user?.id ?? null)
  const permissions = computed<Permission[]>(
    () => (campaign.value?.myPermissions ?? []) as Permission[],
  )
  const myMembership = computed<MemberRecord | null>(
    () => members.value.find((member) => member.userId === userId.value) ?? null,
  )
  const linkedHeroId = computed(() => myMembership.value?.linkedHeroId ?? null)
  const subject = computed<PermissionSubject>(() => ({
    userId: userId.value,
    permissions: permissions.value,
    linkedHeroId: linkedHeroId.value,
    role: campaign.value?.myRole ?? null,
  }))

  const canManageCampaign = computed(() => hasPermission(permissions.value, 'campaign.manage'))
  const canManageMembers = computed(() => hasPermission(permissions.value, 'members.manage'))
  const canInvite = computed(() => hasPermission(permissions.value, 'invite.create'))
  const canWriteEntities = computed(() => hasPermission(permissions.value, 'entity.write'))
  const canWriteHeroes = computed(() => hasPermission(permissions.value, 'hero.write'))
  const canWriteRetellings = computed(() => canCreateRetelling(subject.value))

  const activeObjects = computed(() => objects.value.filter((object) => !object.deleted))
  const heroes = computed(() => activeObjects.value.filter((object) => isLocalObjectOf(object, 'HERO')))
  const characters = computed(() =>
    activeObjects.value.filter((object) => isLocalObjectOf(object, 'CHARACTER')),
  )
  const locations = computed(() =>
    activeObjects.value.filter((object) => isLocalObjectOf(object, 'LOCATION')),
  )
  const items = computed(() => activeObjects.value.filter((object) => isLocalObjectOf(object, 'ITEM')))

  /** Краткие пересказы из карточки кампании (`api.md` §4) — без отдельного запроса. */
  const retellingBriefs = computed<RetellingBriefDto[]>(() => campaign.value?.retellingBriefs ?? [])

  /** Все объекты, на которые можно ссылаться из разметки `[[…]]`. */
  const linkResolver = computed(() =>
    activeObjects.value.length > 0
      ? createLinkResolver(activeObjects.value.map((object) => ({ id: object.id, name: object.name })))
      : emptyLinkResolver,
  )

  /** Черновики и публикации одним списком, свежие — выше. */
  const retellingItems = computed<RetellingListItem[]>(() => {
    const published: RetellingListItem[] = retellings.value
      .filter((retelling) => !retelling.deleted)
      .map((retelling) => ({
        id: retelling.id,
        title: retelling.title,
        body: retelling.body,
        sessionDate: retelling.sessionDate,
        status: retelling.status,
        authorEntityId: retelling.authorEntityId,
        authorEntityName: authorEntityName(retelling.authorEntityId, retelling.authorEntityName),
        updatedAt: retelling.updatedAt,
        updatedBy: userName(retelling.updatedBy),
        revision: retelling.revision,
        dirty: retelling.dirty === 1,
        conflicted: retelling.conflict !== null,
        isDraft: false,
      }))
    const localDrafts: RetellingListItem[] = drafts.value.map((draft) => ({
      id: draft.id,
      title: draft.title,
      body: draft.body,
      sessionDate: draft.sessionDate,
      status: 'DRAFT' as RetellingStatus,
      authorEntityId: draft.authorEntityId,
      authorEntityName: authorEntityName(draft.authorEntityId, null),
      updatedAt: draft.updatedAt,
      updatedBy: 'черновик на устройстве',
      revision: 0,
      dirty: true,
      conflicted: false,
      isDraft: true,
    }))
    return [...localDrafts, ...published].sort((a, b) => {
      const dateA = a.sessionDate ?? a.updatedAt
      const dateB = b.sessionDate ?? b.updatedAt
      if (dateA === dateB) return b.updatedAt.localeCompare(a.updatedAt)
      return dateB.localeCompare(dateA)
    })
  })

  /** Герои партии — для привязки участника и приглашения (`members.linkedHeroId`). */
  const heroOptions = computed(() =>
    heroes.value.map((hero) => ({ id: hero.id, name: hero.name, objectClass: 'HERO' as ObjectClass })),
  )

  /** Все объекты — для «от лица кого» и ссылок в предметах. */
  const objectOptions = computed(() =>
    activeObjects.value.map((object) => ({
      id: object.id,
      name: object.name,
      objectClass: object.objectType,
    })),
  )

  const hasConflicts = computed(
    () =>
      objects.value.some((object) => object.conflict !== null) ||
      retellings.value.some((retelling) => retelling.conflict !== null),
  )

  /* ------------------------------ Чтение ------------------------------ */

  function requireCampaignId(): string {
    if (!campaignId.value) {
      throw new Error('Кампания не открыта')
    }
    return campaignId.value
  }

  async function reload(): Promise<void> {
    const id = campaignId.value
    if (!id) return
    const [record, objectList, retellingList, draftList, memberList] = await Promise.all([
      campaigns.getRecord(id),
      listObjects(id),
      listRetellings(id),
      listDrafts(id),
      listMembers(id),
    ])
    campaign.value = record
    objects.value = objectList
    retellings.value = retellingList
    drafts.value = draftList
    members.value = memberList
    await readTabObjects()
  }

  /** Локальное чтение вкладки: тот же фильтр классов, что уходит на сервер. */
  async function readTabObjects(): Promise<void> {
    const id = campaignId.value
    if (!id || tabClasses.value.length === 0) {
      tabObjects.value = []
      return
    }
    tabObjects.value = await listObjects(id, { classes: tabClasses.value })
  }

  /** Сливаем свежие страницы в общий список: ссылки `[[…]]` видят их сразу. */
  function mergeObjects(incoming: LocalObject[]): void {
    if (incoming.length === 0) return
    const byId = new Map(objects.value.map((object) => [object.id, object]))
    for (const object of incoming) byId.set(object.id, object)
    objects.value = [...byId.values()]
  }

  /** Предел страниц вкладки: сервер отдаёт максимум 500 за раз (`api.md` §1). */
  const TAB_PAGE_LIMIT = 500
  const TAB_MAX_PAGES = 20

  /** Запрос страницы одного класса: тип ответа сужается по классу. */
  function listClassPage(
    id: string,
    objectClass: ObjectClass,
    query: ObjectListQuery,
  ): Promise<PageDto<CampaignObjectDto>> {
    switch (objectClass) {
      case 'HERO':
        return objectsApi.list(id, 'HERO', query)
      case 'CHARACTER':
        return objectsApi.list(id, 'CHARACTER', query)
      case 'ITEM':
        return objectsApi.list(id, 'ITEM', query)
      default:
        return objectsApi.list(id, 'LOCATION', query)
    }
  }

  /** DTO класса → локальная запись: класс известен из цикла загрузки. */
  function localFromDto(objectClass: ObjectClass, dto: CampaignObjectDto): LocalObject {
    switch (objectClass) {
      case 'HERO':
        return objectFromDto('HERO', dto as HeroDto, { conflict: null })
      case 'CHARACTER':
        return objectFromDto('CHARACTER', dto as CharacterDto, { conflict: null })
      case 'ITEM':
        return objectFromDto('ITEM', dto as ItemDto, { conflict: null })
      default:
        return objectFromDto('LOCATION', dto as LocationDto, { conflict: null })
    }
  }

  /**
   * Постраничная загрузка вкладки: `GET .../heroes?limit=500&offset=…` и аналоги
   * (`api.md` §6). Вкладка «Персонажи» делает два запроса — герои и персонажи.
   * Записи с неотправленными правками не перетираем — их версия уйдёт push'ем.
   * Без сети цикл не стартует: вкладка остаётся на данных IndexedDB.
   */
  async function refreshObjectsFromServer(classes: ObjectClass[], token: number): Promise<void> {
    const id = campaignId.value
    if (!id || !ui.online) return
    tabLoading.value = true
    tabLoaded.value = 0
    tabTotal.value = 0
    try {
      for (const objectClass of classes) {
        let offset = 0
        for (let page = 0; page < TAB_MAX_PAGES; page += 1) {
          if (token !== tabLoadToken) return
          const response = await listClassPage(id, objectClass, {
            limit: TAB_PAGE_LIMIT,
            offset,
          })
          if (token !== tabLoadToken) return
          const items = response.items
          const total = typeof response.total === 'number' ? response.total : null

          // Не перетираем «грязные» записи: у них есть неотправленная правка.
          const local = await listObjects(id, { classes: [objectClass], includeDeleted: true })
          const dirtyIds = new Set(local.filter((object) => object.dirty === 1).map((object) => object.id))
          const incoming = items
            .filter((dto) => !dirtyIds.has(dto.id))
            .map((dto) => localFromDto(objectClass, dto))
          await putObjects(incoming)
          if (token !== tabLoadToken) return

          // Обновляем то, что видит пользователь, сразу после каждой страницы.
          mergeObjects(incoming)
          tabLoaded.value += items.length
          if (total !== null) tabTotal.value += total
          await readTabObjects()

          if (items.length === 0) break
          offset += items.length
          if (total !== null && offset >= total) break
          // Сервер не сообщил `total` — последняя страница короче лимита.
          if (items.length < TAB_PAGE_LIMIT) break
        }
      }
    } catch (caught) {
      if (caught instanceof ApiError && caught.isOffline) return
      console.warn('[dnd-memory] не удалось обновить объекты вкладки', caught)
    } finally {
      if (token === tabLoadToken) tabLoading.value = false
    }
  }

  /**
   * Открытие вкладки: сначала локальная база с фильтром классов (работает офлайн),
   * затем постраничная подгрузка с сервера и перечитывание стора на финал.
   */
  async function loadTabObjects(classes: ObjectClass[]): Promise<void> {
    const token = tabLoadToken + 1
    tabLoadToken = token
    tabClasses.value = [...classes]
    await readTabObjects()
    await refreshObjectsFromServer(classes, token)
    if (token !== tabLoadToken) return
    // Перечитываем стор целиком: свежие записи нужны и общим ссылкам `[[…]]`.
    await reload()
  }

  /** Открытие кампании: локальные данные сразу, детали роли — при наличии сети. */
  async function open(id: string): Promise<void> {
    loading.value = true
    error.value = null
    campaignId.value = id
    // Синхронизация пишет в локальную базу отдельно от сторов, поэтому
    // подписываемся на её изменения и перечитываем данные.
    if (unsubscribeDatabase) unsubscribeDatabase()
    unsubscribeDatabase = onDatabaseChanged((changedId) => {
      if (changedId === campaignId.value) void reload()
    })
    try {
      await reload()
      // `GET /api/campaigns/{id}` отдаёт `members` и `retellings` — сохраняем их,
      // чтобы не делать отдельные запросы за участниками и пересказами.
      const details = await campaigns.loadDetails(id)
      if (details) campaign.value = details
      await reload()
    } catch (caught) {
      error.value = errorMessage(caught)
    } finally {
      loading.value = false
    }
  }

  function close(): void {
    if (unsubscribeDatabase) {
      unsubscribeDatabase()
      unsubscribeDatabase = null
    }
    campaignId.value = null
    campaign.value = null
    objects.value = []
    retellings.value = []
    drafts.value = []
    members.value = []
    invites.value = []
    tabObjects.value = []
    tabClasses.value = []
    // Гасим возможную незавершённую подгрузку вкладки.
    tabLoadToken += 1
    tabLoading.value = false
    tabLoaded.value = 0
    tabTotal.value = 0
  }

  function objectById(id: string): LocalObject | null {
    return objects.value.find((object) => object.id === id) ?? null
  }

  function objectName(id: string | null | undefined): string {
    if (!id) return ''
    return objectById(id)?.name ?? ''
  }

  function retellingById(id: string): LocalRetelling | null {
    return retellings.value.find((retelling) => retelling.id === id) ?? null
  }

  function draftById(id: string): LocalRetellingDraft | null {
    return drafts.value.find((draft) => draft.id === id) ?? null
  }

  /** Значения формы по объекту — для экрана правки: только поля своего класса. */
  function objectFormValues(object: LocalObject): ObjectFormValues {
    const base: ObjectFormValues = {
      name: object.name,
      description: object.description,
      imageFileId: object.imageFileId,
      avatarFileId: object.avatarFileId,
      gender: 'unknown',
      race: '',
      klass: '',
      locationType: '',
      itemType: '',
      playerUserId: null,
      items: [],
      shortDescription: '',
      ownerName: '',
      ownerHeroId: null,
      ownerCharacterId: null,
    }
    if (object.objectType === 'HERO') {
      return {
        ...base,
        race: object.race ?? '',
        klass: object.klass ?? '',
        gender: object.gender ?? 'unknown',
        playerUserId: object.playerUserId,
        items: object.items.map((item) => ({
          id: item.id,
          name: item.name,
          itemId: item.itemId,
          quantity: item.quantity,
          notes: item.notes ?? '',
        })),
      }
    }
    if (object.objectType === 'LOCATION') {
      base.locationType = object.locationType ?? ''
    }
    if (object.objectType === 'ITEM') {
      base.itemType = object.itemType ?? ''
    }
    if (object.objectType === 'CHARACTER') {
      return {
        ...base,
        race: object.race ?? '',
        klass: object.klass ?? '',
        gender: object.gender ?? 'unknown',
        shortDescription: object.shortDescription ?? '',
      }
    }
    if (object.objectType === 'ITEM') {
      return {
        ...base,
        ownerName: object.ownerName ?? '',
        ownerHeroId: object.ownerHeroId,
        ownerCharacterId: object.ownerCharacterId,
      }
    }
    return base
  }

  /** Значения формы по пересказу или черновику. */
  function retellingFormValues(source: LocalRetelling | LocalRetellingDraft): RetellingFormValues {
    return {
      title: source.title,
      body: source.body,
      authorEntityId: source.authorEntityId,
      sessionDate: source.sessionDate,
    }
  }

  function userName(id: string): string {
    if (!id) return 'неизвестно'
    if (id === userId.value) return 'вы'
    const member = members.value.find((item) => item.userId === id)
    return member?.displayName ?? `участник ${id.slice(0, 8)}`
  }

  function authorEntityName(id: string | null, fallback: string | null): string | null {
    if (id) {
      const name = objectName(id)
      if (name) return name
    }
    return fallback
  }

  /** «кто изменил и когда» для карточки объекта. */
  function changeSummary(item: { updatedBy: string; updatedAt: string }): string {
    const who = userName(item.updatedBy)
    const when = formatRelative(item.updatedAt)
    return who === 'вы' ? `Вы изменили ${when}` : `${who} изменил(а) ${when}`
  }

  /* ----------------------------- Права ------------------------------- */

  function canCreate(objectClass: ObjectClass): boolean {
    return canCreateObject(subject.value, objectClass)
  }

  function canEdit(object: LocalObject): boolean {
    return canEditObject(subject.value, object.objectType)
  }

  function canDelete(object: LocalObject): boolean {
    return canDeleteObject(subject.value, object.objectType)
  }

  function canEditObjectId(id: string): boolean {
    const object = objectById(id)
    return object ? canEdit(object) : false
  }

  function canEditRetellingItem(id: string): boolean {
    const retelling = retellings.value.find((entry) => entry.id === id)
    if (!retelling) {
      // Черновик: править может автор, то есть тот, у кого есть право писать.
      return canCreateRetelling(subject.value)
    }
    return canEditRetelling(subject.value, retelling)
  }

  function canDeleteRetellingItem(id: string): boolean {
    const draft = drafts.value.find((entry) => entry.id === id)
    if (draft) return true
    const retelling = retellings.value.find((entry) => entry.id === id)
    if (!retelling) return false
    return canDeleteRetelling(subject.value, retelling)
  }

  /* --------------------------- Объекты ------------------------------- */

  function linksFor(texts: Array<string | null | undefined>): EntityLinkDto[] {
    return collectLinks(texts, linkResolver.value)
  }

  /** Предмет котомки: `itemId` подставляем, если имя нашлось среди предметов кампании. */
  function resolveItemId(name: string): string | null {
    const normalized = name.trim().toLowerCase()
    if (normalized === '') return null
    const match = items.value.find((item) => item.name.trim().toLowerCase() === normalized)
    return match?.id ?? null
  }

  function buildHeroItems(values: HeroItemFormValue[]) {
    return values
      .filter((item) => item.name.trim() !== '')
      .map((item, index) => ({
        id: item.id ?? `${index + 1}`,
        name: item.name.trim(),
        itemId: item.itemId ?? resolveItemId(item.name),
        quantity: item.quantity,
        notes: item.notes.trim() === '' ? null : item.notes.trim(),
      }))
  }

  function optional(value: string): string | null {
    const trimmed = value.trim()
    return trimmed === '' ? null : trimmed
  }

  function playerNameFor(id: string | null): string | null {
    if (!id) return null
    return members.value.find((member) => member.userId === id)?.displayName ?? null
  }

  /**
   * Собирает локальную запись нужного класса из формы (`api.md` §6). Класс объекта
   * при правке не меняется: у каждого класса свои поля и свой эндпоинт.
   */
  function buildLocalObject(
    objectClass: ObjectClass,
    id: string,
    values: ObjectFormValues,
    existing: LocalObject | null,
    now: string,
  ): LocalObject {
    const actor = userId.value ?? ''
    const actorName = auth.user?.displayName ?? ''
    const base = {
      id,
      campaignId: requireCampaignId(),
      name: values.name.trim(),
      description: values.description,
      imageFileId: values.imageFileId,
      avatarFileId: values.avatarFileId,
      links: [] as EntityLinkDto[],
      createdBy: existing?.createdBy ?? actor,
      createdByName: existing?.createdByName ?? actorName,
      updatedBy: actor,
      updatedByName: actorName,
      revision: existing?.revision ?? 0,
      createdAt: existing?.createdAt ?? now,
      updatedAt: now,
      deleted: false,
      dirty: 1 as const,
      conflict: null,
    }
    if (objectClass === 'HERO') {
      const hero: LocalHero = {
        ...base,
        objectType: 'HERO',
        race: optional(values.race),
        gender: optional(values.gender),
        klass: optional(values.klass),
        playerUserId: values.playerUserId,
        playerName: playerNameFor(values.playerUserId),
        items: buildHeroItems(values.items),
      }
      hero.links = linksFor([hero.description])
      return hero
    }
    if (objectClass === 'CHARACTER') {
      const character: LocalCharacter = {
        ...base,
        objectType: 'CHARACTER',
        race: optional(values.race),
        gender: optional(values.gender),
        klass: optional(values.klass),
        shortDescription: optional(values.shortDescription),
      }
      character.links = linksFor([character.description, character.shortDescription])
      return character
    }
    if (objectClass === 'ITEM') {
      // Ссылка не более чем на одного владельца (`roles.md` §2, правило 5).
      const ownerHeroId = values.ownerHeroId
      const ownerCharacterId = ownerHeroId ? null : values.ownerCharacterId
      const item: LocalItem = {
        ...base,
        objectType: 'ITEM',
        itemType: optional(values.itemType),
        ownerName: optional(values.ownerName),
        ownerHeroId,
        ownerHeroName: ownerHeroId ? (objectById(ownerHeroId)?.name ?? null) : null,
        ownerCharacterId,
        ownerCharacterName: ownerCharacterId
          ? (objectById(ownerCharacterId)?.name ?? null)
          : null,
      }
      item.links = linksFor([item.description])
      return item
    }
    const location: LocalLocation = {
      ...base,
      objectType: 'LOCATION',
      locationType: optional(values.locationType),
    }
    location.links = linksFor([location.description])
    return location
  }

  /** Новый объект: сразу локально + в очередь синхронизации (`sync.md` §5). */
  async function createObject(
    objectClass: ObjectClass,
    values: ObjectFormValues,
  ): Promise<LocalObject> {
    const cid = requireCampaignId()
    const now = nowIso()
    const object = buildLocalObject(objectClass, createUuid(), values, null, now)
    await putObject(object)
    await enqueueChange({
      campaignId: cid,
      objectType: object.objectType,
      objectId: object.id,
      op: 'UPSERT',
      baseRevision: null,
      payload: objectToPayload(object),
    })
    await reload()
    notifyLocalChange(cid)
    return object
  }

  async function updateObject(id: string, values: ObjectFormValues): Promise<LocalObject | null> {
    const cid = requireCampaignId()
    const existing = await getObject(id)
    if (!existing) return null
    const updated = buildLocalObject(existing.objectType, id, values, existing, nowIso())
    await putObject(updated)
    await enqueueChange({
      campaignId: cid,
      objectType: updated.objectType,
      objectId: updated.id,
      op: 'UPSERT',
      baseRevision: existing.revision > 0 ? existing.revision : null,
      payload: objectToPayload(updated),
    })
    await reload()
    notifyLocalChange(cid)
    return updated
  }

  async function deleteObject(id: string): Promise<void> {
    const cid = requireCampaignId()
    const existing = await getObject(id)
    if (!existing) return
    const removed: LocalObject = {
      ...existing,
      deleted: true,
      updatedBy: userId.value ?? '',
      updatedAt: nowIso(),
      dirty: 1,
    }
    await putObject(removed)
    const result = await enqueueChange({
      campaignId: cid,
      objectType: existing.objectType,
      objectId: id,
      op: 'DELETE',
      baseRevision: existing.revision > 0 ? existing.revision : null,
      payload: objectTombstone(removed),
    })
    if (result.dropped) {
      // Объект существовал только на устройстве.
      await deleteObjectRecord(id)
    }
    await reload()
    notifyLocalChange(cid)
  }

  /* --------------------------- Пересказы ----------------------------- */

  /**
   * Сохранение черновика. Черновик живёт только в `retelling_drafts` и никогда
   * не попадает ни в `pending_changes`, ни в push (`sync.md` §5).
   */
  async function saveDraft(
    draftId: string | null,
    values: RetellingFormValues,
  ): Promise<string> {
    const cid = requireCampaignId()
    const now = nowIso()
    if (draftId) {
      const existing = await getDraft(draftId)
      const draft: LocalRetellingDraft = {
        id: draftId,
        campaignId: cid,
        title: values.title.trim(),
        body: values.body,
        authorEntityId: values.authorEntityId,
        sessionDate: values.sessionDate,
        createdAt: existing?.createdAt ?? now,
        updatedAt: now,
        status: 'DRAFT',
      }
      await putDraft(draft)
      await reload()
      return draftId
    }
    const id = createUuid()
    await putDraft({
      id,
      campaignId: cid,
      title: values.title.trim(),
      body: values.body,
      authorEntityId: values.authorEntityId,
      sessionDate: values.sessionDate,
      createdAt: now,
      updatedAt: now,
      status: 'DRAFT',
    })
    await reload()
    return id
  }

  /** «Опубликовать»: черновик становится `PUBLISHED` и уходит в очередь синхронизации. */
  async function publishDraft(
    draftId: string,
    values?: RetellingFormValues,
  ): Promise<string> {
    const cid = requireCampaignId()
    const draft = await getDraft(draftId)
    const now = nowIso()
    const base: LocalRetellingDraft = {
      id: draftId,
      campaignId: cid,
      title: values?.title.trim() ?? draft?.title ?? '',
      body: values?.body ?? draft?.body ?? '',
      authorEntityId: values?.authorEntityId ?? draft?.authorEntityId ?? null,
      sessionDate: values?.sessionDate ?? draft?.sessionDate ?? null,
      createdAt: draft?.createdAt ?? now,
      updatedAt: now,
      status: 'DRAFT',
    }
    const retelling = draftToRetelling(base, {
      createdBy: userId.value ?? '',
      updatedBy: userId.value ?? '',
      createdByName: auth.user?.displayName ?? '',
      updatedByName: auth.user?.displayName ?? '',
    })
    retelling.links = collectLinks([retelling.title, retelling.body], linkResolver.value)
    await putRetelling(retelling)
    await deleteDraft(draftId)
    await enqueueChange({
      campaignId: cid,
      objectType: 'RETELLING',
      objectId: retelling.id,
      op: 'UPSERT',
      baseRevision: null,
      payload: retellingToPayload(retelling),
    })
    await reload()
    notifyLocalChange(cid)
    return retelling.id
  }

  /** Новый пересказ сразу опубликованным (без черновика). */
  async function createPublishedRetelling(values: RetellingFormValues): Promise<string> {
    const cid = requireCampaignId()
    const now = nowIso()
    const retellingId = createUuid()
    const retelling: LocalRetelling = {
      id: retellingId,
      campaignId: cid,
      clientId: retellingId,
      title: values.title.trim(),
      body: values.body,
      authorEntityId: values.authorEntityId,
      authorEntityName: authorEntityName(values.authorEntityId, null),
      sessionDate: values.sessionDate,
      status: 'PUBLISHED',
      links: [],
      revision: 0,
      createdBy: userId.value ?? '',
      createdByName: auth.user?.displayName ?? '',
      updatedBy: userId.value ?? '',
      updatedByName: auth.user?.displayName ?? '',
      createdAt: now,
      updatedAt: now,
      deleted: false,
      dirty: 1,
      conflict: null,
    }
    retelling.links = collectLinks([retelling.title, retelling.body], linkResolver.value)
    await putRetelling(retelling)
    await enqueueChange({
      campaignId: cid,
      objectType: 'RETELLING',
      objectId: retelling.id,
      op: 'UPSERT',
      baseRevision: null,
      payload: retellingToPayload(retelling),
    })
    await reload()
    notifyLocalChange(cid)
    return retelling.id
  }

  async function updatePublishedRetelling(
    id: string,
    values: RetellingFormValues,
  ): Promise<LocalRetelling | null> {
    const cid = requireCampaignId()
    const existing = await getRetelling(id)
    if (!existing) return null
    const updated: LocalRetelling = {
      ...existing,
      title: values.title.trim(),
      body: values.body,
      authorEntityId: values.authorEntityId,
      authorEntityName: authorEntityName(values.authorEntityId, existing.authorEntityName),
      sessionDate: values.sessionDate,
      status: 'PUBLISHED',
      updatedBy: userId.value ?? '',
      updatedByName: auth.user?.displayName ?? '',
      updatedAt: nowIso(),
      dirty: 1,
      conflict: null,
    }
    updated.links = collectLinks([updated.title, updated.body], linkResolver.value)
    await putRetelling(updated)
    await enqueueChange({
      campaignId: cid,
      objectType: 'RETELLING',
      objectId: updated.id,
      op: 'UPSERT',
      baseRevision: existing.revision > 0 ? existing.revision : null,
      payload: retellingToPayload(updated),
    })
    await reload()
    notifyLocalChange(cid)
    return updated
  }

  async function removeRetelling(id: string): Promise<void> {
    const cid = requireCampaignId()
    const draft = await getDraft(id)
    if (draft) {
      await deleteDraft(id)
      await reload()
      return
    }
    const existing = await getRetelling(id)
    if (!existing) return
    const updated: LocalRetelling = {
      ...existing,
      deleted: true,
      updatedBy: userId.value ?? '',
      updatedAt: nowIso(),
      dirty: 1,
    }
    await putRetelling(updated)
    const result = await enqueueChange({
      campaignId: cid,
      objectType: 'RETELLING',
      objectId: id,
      op: 'DELETE',
      baseRevision: existing.revision > 0 ? existing.revision : null,
      payload: { id, deleted: true, updatedAt: updated.updatedAt },
    })
    if (result.dropped) {
      await deleteRetellingRecord(id)
    }
    await reload()
    notifyLocalChange(cid)
  }

  /* --------------------------- Конфликты ----------------------------- */

  async function resolveConflictUseServer(objectId: string): Promise<void> {
    const cid = requireCampaignId()
    await applyConflictUseServer(cid, objectId)
    await reload()
  }

  async function resolveConflictUseLocal(objectId: string): Promise<void> {
    const cid = requireCampaignId()
    await applyConflictUseLocal(cid, objectId)
    await reload()
  }

  /* --------------------------- Участники ----------------------------- */

  async function reloadMembers(): Promise<void> {
    const id = requireCampaignId()
    try {
      const list = await membersApi.list(id)
      await putMembers(list.map((member) => memberFromDto(id, member)))
      await pruneMembers(id, new Set(list.map((member) => member.id)))
      members.value = await listMembers(id)
    } catch (caught) {
      if (caught instanceof ApiError && caught.isOffline) {
        members.value = await listMembers(id)
        ui.pushToast('warning', 'Участники показаны из локальной копии')
        return
      }
      throw caught
    }
  }

  async function addMember(body: AddMemberRequest): Promise<AddMemberResponse> {
    const id = requireCampaignId()
    const response = await membersApi.add(id, body)
    if (response.member) {
      await putMember(memberFromDto(id, response.member))
    }
    if (response.invite) {
      invites.value = [response.invite, ...invites.value]
    }
    await reloadMembers()
    return response
  }

  async function updateMember(memberId: string, patch: PatchMemberRequest): Promise<void> {
    const id = requireCampaignId()
    const updated = await membersApi.patch(id, memberId, patch)
    await putMember(memberFromDto(id, updated))
    await reloadMembers()
  }

  async function removeMember(memberId: string): Promise<void> {
    const id = requireCampaignId()
    await membersApi.remove(id, memberId)
    await deleteMemberRecord(memberId)
    await reloadMembers()
  }

  async function changeRole(memberId: string, role: MemberRole): Promise<void> {
    await updateMember(memberId, { role })
  }

  /** Привязка участника к герою: `linkedHeroId` вместо прежнего `linkedEntityId`. */
  async function linkMemberToHero(memberId: string, linkedHeroId: string | null): Promise<void> {
    await updateMember(memberId, { linkedHeroId })
  }

  /* ---------------------------- Приглашения -------------------------- */

  async function reloadInvites(): Promise<void> {
    const id = requireCampaignId()
    try {
      invites.value = await invitesApi.list(id)
    } catch (caught) {
      if (caught instanceof ApiError && caught.isOffline) {
        ui.pushToast('warning', 'Приглашения доступны только при наличии сети')
        invites.value = []
        return
      }
      throw caught
    }
  }

  async function createInvite(body: CreateInviteRequest): Promise<InviteDto> {
    const id = requireCampaignId()
    const invite = await invitesApi.create(id, body)
    invites.value = [invite, ...invites.value]
    return invite
  }

  async function revokeInvite(inviteId: string): Promise<void> {
    const id = requireCampaignId()
    await invitesApi.revoke(id, inviteId)
    invites.value = invites.value.filter((invite) => invite.id !== inviteId)
  }

  /** Принятие приглашения по токену (`/join/:token`); приглашение одноразовое. */
  async function acceptInvite(token: string): Promise<AcceptInviteResponse> {
    const response = await invitesApi.accept(token)
    await campaigns.load()
    return response
  }

  return {
    campaignId,
    campaign,
    objects,
    retellings,
    drafts,
    members,
    invites,
    tabObjects,
    tabClasses,
    tabLoading,
    tabLoaded,
    tabTotal,
    loading,
    error,
    permissions,
    subject,
    myMembership,
    linkedHeroId,
    canManageCampaign,
    canManageMembers,
    canInvite,
    canWriteEntities,
    canWriteHeroes,
    canWriteRetellings,
    activeObjects,
    heroes,
    characters,
    locations,
    items,
    heroOptions,
    objectOptions,
    retellingBriefs,
    retellingItems,
    linkResolver,
    hasConflicts,
    open,
    close,
    reload,
    objectById,
    objectName,
    retellingById,
    draftById,
    objectFormValues,
    retellingFormValues,
    userName,
    changeSummary,
    canCreate,
    canEdit,
    canDelete,
    canEditObjectId,
    canEditRetellingItem,
    canDeleteRetellingItem,
    createObject,
    updateObject,
    deleteObject,
    saveDraft,
    publishDraft,
    createPublishedRetelling,
    updatePublishedRetelling,
    removeRetelling,
    resolveConflictUseServer,
    resolveConflictUseLocal,
    reloadMembers,
    addMember,
    updateMember,
    removeMember,
    changeRole,
    linkMemberToHero,
    reloadInvites,
    createInvite,
    revokeInvite,
    loadTabObjects,
    acceptInvite,
  }
})
