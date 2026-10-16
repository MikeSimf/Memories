import { openDatabase, nowIso, STORE, type MemberRecord } from './schema'
import type { MemberDto } from '@/api/types'

export function memberFromDto(campaignId: string, dto: MemberDto): MemberRecord {
  return {
    ...dto,
    linkedHeroId: dto.linkedHeroId ?? null,
    linkedHeroName: dto.linkedHeroName ?? null,
    campaignId,
    cachedAt: nowIso(),
  }
}

export async function listMembers(campaignId: string): Promise<MemberRecord[]> {
  const db = await openDatabase()
  const items = await db.getAllFromIndex(STORE.members, 'by-campaign', campaignId)
  const order = ['OWNER', 'EDITOR_MASTER', 'EDITOR_HERO', 'VIEWER']
  return items.sort((a, b) => {
    const roleDiff = order.indexOf(a.role) - order.indexOf(b.role)
    if (roleDiff !== 0) return roleDiff
    return a.displayName.localeCompare(b.displayName, 'ru')
  })
}

export async function putMember(member: MemberRecord): Promise<void> {
  const db = await openDatabase()
  await db.put(STORE.members, member)
}

export async function putMembers(members: MemberRecord[]): Promise<void> {
  if (members.length === 0) return
  const db = await openDatabase()
  const tx = db.transaction(STORE.members, 'readwrite')
  await Promise.all(members.map((member) => tx.store.put(member)))
  await tx.done
}

export async function deleteMemberRecord(memberId: string): Promise<void> {
  const db = await openDatabase()
  await db.delete(STORE.members, memberId)
}

export async function countMembers(campaignId: string): Promise<number> {
  const db = await openDatabase()
  return db.countFromIndex(STORE.members, 'by-campaign', campaignId)
}

export async function pruneMembers(campaignId: string, keepIds: Set<string>): Promise<number> {
  const local = await listMembers(campaignId)
  const stale = local.filter((member) => !keepIds.has(member.id))
  if (stale.length === 0) return 0
  const db = await openDatabase()
  const tx = db.transaction(STORE.members, 'readwrite')
  await Promise.all(stale.map((member) => tx.store.delete(member.id)))
  await tx.done
  return stale.length
}
