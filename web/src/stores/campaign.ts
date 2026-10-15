import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import {
  ApiError,
  errorMessage,
  invitesApi,
  membersApi,
  type AcceptInviteResponse,
  type AddMemberRequest,
  type AddMemberResponse,
  type CreateInviteRequest,
  type EntityLinkDto,
  type EntityType,
  type InviteDto,
  type MemberRole,
  type PatchMemberRequest,
  type Permission,
  type RetellingStatus,
} from '@/api'
import {
  deleteDraft,
  deleteEntityRecord,
  deleteMemberRecord,
  deleteRetellingRecord,
  draftToRetelling,
  entityToPayload,
  getDraft,
  getEntity,
  getRetelling,
  listDrafts,
  listEntities,
  listMembers,
  listRetellings,
  memberFromDto,
  nowIso,
  pruneMembers,
  putDraft,
  putEntity,
  putMember,
  putMembers,
  putRetelling,
  retellingToPayload,
  type CampaignRecord,
  type LocalEntity,
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
import { normalizeAttrs } from '@/utils/attrs'
import { formatRelative } from '@/utils/datetime'
import {
  canCreateEntity,
  canCreateRetelling,
  canDeleteEntity,
  canDeleteRetelling,
  canEditEntity,
  canEditRetelling,
  hasPermission,
  type PermissionSubject,
} from '@/utils/permissions'
import { collectLinks, createLinkResolver, emptyLinkResolver } from '@/utils/wikilinks'
import { createUuid } from '@/utils/uuid'
import { useAuthStore } from './auth'
import { useCampaignsStore } from './campaigns'
import { useUiStore } from './ui'
import type { EntityFormValues, RetellingFormValues } from '@/types/forms'

export type { EntityFormValues, RetellingFormValues }

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
  const entities = ref<LocalEntity[]>([])
  const retellings = ref<LocalRetelling[]>([])
  const drafts = ref<LocalRetellingDraft[]>([])
  const members = ref<MemberRecord[]>([])
  const invites = ref<InviteDto[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  const userId = computed(() => auth.user?.id ?? null)
  const permissions = computed<Permission[]>(
    () => (campaign.value?.myPermissions ?? []) as Permission[],
  )
  const myMembership = computed<MemberRecord | null>(
    () => members.value.find((member) => member.userId === userId.value) ?? null,
  )
  const linkedEntityId = computed(() => myMembership.value?.linkedEntityId ?? null)
  const subject = computed<PermissionSubject>(() => ({
    userId: userId.value,
    permissions: permissions.value,
    linkedEntityId: linkedEntityId.value,
  }))

  const canManageCampaign = computed(() => hasPermission(permissions.value, 'campaign.manage'))
  const canManageMembers = computed(() => hasPermission(permissions.value, 'members.manage'))
  const canInvite = computed(() => hasPermission(permissions.value, 'invite.create'))
  const canWriteEntities = computed(() => hasPermission(permissions.value, 'entity.write'))
  const canWriteHeroes = computed(() => hasPermission(permissions.value, 'hero.write'))
  const canWriteRetellings = computed(() => canCreateRetelling(subject.value))

  const activeEntities = computed(() => entities.value.filter((entity) => !entity.deleted))
  const heroes = computed(() => activeEntities.value.filter((entity) => entity.type === 'HERO'))
  const npcs = computed(() => activeEntities.value.filter((entity) => entity.type === 'NPC'))
  const locations = computed(() =>
    activeEntities.value.filter((entity) => entity.type === 'LOCATION'),
  )
  const items = computed(() => activeEntities.value.filter((entity) => entity.type === 'ITEM'))

  /** Все объекты, на которые можно ссылаться из разметки `[[…]]`. */
  const linkResolver = computed(() =>
    activeEntities.value.length > 0
      ? createLinkResolver(activeEntities.value.map((entity) => ({ id: entity.id, name: entity.name })))
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

  const heroOptions = computed(() =>
    [...heroes.value, ...npcs.value].map((entity) => ({
      id: entity.id,
      name: entity.name,
      type: entity.type,
    })),
  )

  const entityOptions = computed(() =>
    activeEntities.value.map((entity) => ({
      id: entity.id,
      name: entity.name,
      type: entity.type,
    })),
  )

  const hasConflicts = computed(
    () =>
      entities.value.some((entity) => entity.conflict !== null) ||
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
    const [record, entityList, retellingList, draftList, memberList] = await Promise.all([
      campaigns.getRecord(id),
      listEntities(id),
      listRetellings(id),
      listDrafts(id),
      listMembers(id),
    ])
    campaign.value = record
    entities.value = entityList
    retellings.value = retellingList
    drafts.value = draftList
    members.value = memberList
  }

  /** Открытие кампании: локальные данные сразу, детали роли — при наличии сети. */
  async function open(id: string): Promise<void> {
    loading.value = true
    error.value = null
    campaignId.value = id
    try {
      await reload()
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
    campaignId.value = null
    campaign.value = null
    entities.value = []
    retellings.value = []
    drafts.value = []
    members.value = []
    invites.value = []
  }

  function entityById(id: string): LocalEntity | null {
    return entities.value.find((entity) => entity.id === id) ?? null
  }

  function entityName(id: string | null | undefined): string {
    if (!id) return ''
    return entityById(id)?.name ?? ''
  }

  function retellingById(id: string): LocalRetelling | null {
    return retellings.value.find((retelling) => retelling.id === id) ?? null
  }

  function draftById(id: string): LocalRetellingDraft | null {
    return drafts.value.find((draft) => draft.id === id) ?? null
  }

  /** Значения формы по объекту — для экрана правки. */
  function entityFormValues(entity: LocalEntity): EntityFormValues {
    return {
      type: entity.type,
      name: entity.name,
      summary: entity.summary,
      description: entity.description,
      attrs: { ...entity.attrs },
      tags: [...entity.tags],
      imageFileId: entity.imageFileId,
    }
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
      const name = entityName(id)
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

  function canCreate(type: EntityType): boolean {
    return canCreateEntity(subject.value, type)
  }

  function canEdit(entity: LocalEntity): boolean {
    return canEditEntity(subject.value, entity)
  }

  function canDelete(entity: LocalEntity): boolean {
    return canDeleteEntity(subject.value, entity)
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

  function canEditEntityId(id: string): boolean {
    const entity = entityById(id)
    return entity ? canEdit(entity) : false
  }

  /* --------------------------- Объекты ------------------------------- */

  function localLinks(values: { summary: string; description: string }): EntityLinkDto[] {
    return collectLinks([values.summary, values.description], linkResolver.value)
  }

  /** Новый объект: сразу локально + в очередь синхронизации (`sync.md` §4). */
  async function createEntity(values: EntityFormValues): Promise<LocalEntity> {
    const cid = requireCampaignId()
    const now = nowIso()
    const entityId = createUuid()
    const entity: LocalEntity = {
      id: entityId,
      campaignId: cid,
      clientId: entityId,
      type: values.type,
      name: values.name.trim(),
      summary: values.summary.trim(),
      description: values.description,
      attrs: normalizeAttrs(values.type, values.attrs),
      tags: values.tags,
      imageFileId: values.imageFileId,
      links: [],
      createdBy: userId.value ?? '',
      updatedBy: userId.value ?? '',
      revision: 0,
      createdAt: now,
      updatedAt: now,
      deleted: false,
      dirty: 1,
      conflict: null,
    }
    entity.links = localLinks(entity)
    await putEntity(entity)
    await enqueueChange({
      campaignId: cid,
      objectType: 'ENTITY',
      objectId: entity.id,
      op: 'UPSERT',
      baseRevision: null,
      payload: entityToPayload(entity),
    })
    await reload()
    notifyLocalChange(cid)
    return entity
  }

  async function updateEntity(id: string, values: EntityFormValues): Promise<LocalEntity | null> {
    const cid = requireCampaignId()
    const existing = await getEntity(id)
    if (!existing) return null
    const updated: LocalEntity = {
      ...existing,
      type: values.type,
      name: values.name.trim(),
      summary: values.summary.trim(),
      description: values.description,
      attrs: normalizeAttrs(values.type, values.attrs),
      tags: values.tags,
      imageFileId: values.imageFileId,
      updatedBy: userId.value ?? '',
      updatedAt: nowIso(),
      dirty: 1,
      conflict: null,
    }
    updated.links = localLinks(updated)
    await putEntity(updated)
    await enqueueChange({
      campaignId: cid,
      objectType: 'ENTITY',
      objectId: updated.id,
      op: 'UPSERT',
      baseRevision: existing.revision > 0 ? existing.revision : null,
      payload: entityToPayload(updated),
    })
    await reload()
    notifyLocalChange(cid)
    return updated
  }

  async function deleteEntity(id: string): Promise<void> {
    const cid = requireCampaignId()
    const existing = await getEntity(id)
    if (!existing) return
    const updated: LocalEntity = {
      ...existing,
      deleted: true,
      updatedBy: userId.value ?? '',
      updatedAt: nowIso(),
      dirty: 1,
    }
    await putEntity(updated)
    const result = await enqueueChange({
      campaignId: cid,
      objectType: 'ENTITY',
      objectId: id,
      op: 'DELETE',
      baseRevision: existing.revision > 0 ? existing.revision : null,
      payload: { id, deleted: true, updatedAt: updated.updatedAt },
    })
    if (result.dropped) {
      // Объект существовал только на устройстве.
      await deleteEntityRecord(id)
    }
    await reload()
    notifyLocalChange(cid)
  }

  /* --------------------------- Пересказы ----------------------------- */

  /**
   * Сохранение черновика. Черновик живёт только в `retelling_drafts` и никогда
   * не попадает ни в `pending_changes`, ни в push (`sync.md` §4).
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
      updatedBy: userId.value ?? '',
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

  async function linkMemberToHero(memberId: string, linkedEntityId: string | null): Promise<void> {
    await updateMember(memberId, { linkedEntityId })
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

  /** Принятие приглашения по токену (`/join/:token`). */
  async function acceptInvite(token: string): Promise<AcceptInviteResponse> {
    const response = await invitesApi.accept(token)
    await campaigns.load()
    return response
  }

  return {
    campaignId,
    campaign,
    entities,
    retellings,
    drafts,
    members,
    invites,
    loading,
    error,
    permissions,
    subject,
    myMembership,
    linkedEntityId,
    canManageCampaign,
    canManageMembers,
    canInvite,
    canWriteEntities,
    canWriteHeroes,
    canWriteRetellings,
    activeEntities,
    heroes,
    npcs,
    locations,
    items,
    heroOptions,
    entityOptions,
    retellingItems,
    linkResolver,
    hasConflicts,
    open,
    close,
    reload,
    entityById,
    entityName,
    retellingById,
    draftById,
    entityFormValues,
    retellingFormValues,
    userName,
    changeSummary,
    canCreate,
    canEdit,
    canDelete,
    canEditEntityId,
    canEditRetellingItem,
    canDeleteRetellingItem,
    createEntity,
    updateEntity,
    deleteEntity,
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
    acceptInvite,
  }
})
