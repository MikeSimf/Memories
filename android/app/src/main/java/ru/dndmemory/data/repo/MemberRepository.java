package ru.dndmemory.data.repo;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import java.util.ArrayList;
import java.util.List;

import ru.dndmemory.data.Mappers;
import ru.dndmemory.data.local.AppDatabase;
import ru.dndmemory.data.local.entity.MemberRoom;
import ru.dndmemory.data.remote.ApiCall;
import ru.dndmemory.data.remote.ApiService;
import ru.dndmemory.data.remote.dto.AddMemberRequest;
import ru.dndmemory.data.remote.dto.AddMemberResponse;
import ru.dndmemory.data.remote.dto.MemberDto;
import ru.dndmemory.data.remote.dto.PatchMemberRequest;
import ru.dndmemory.util.AppExecutors;
import ru.dndmemory.util.RepoCallback;

/**
 * Участники кампании. Роли меняет только {@code OWNER}, поэтому операции идут
 * напрямую в сеть, а результат сразу попадает в Room.
 */
public class MemberRepository {

    private final AppDatabase db;
    private final ApiService api;
    private final AppExecutors exec;

    public MemberRepository(AppDatabase db, ApiService api, AppExecutors exec) {
        this.db = db;
        this.api = api;
        this.exec = exec;
    }

    public LiveData<List<MemberRoom>> observe(String campaignId) {
        return db.memberDao().observeByCampaign(campaignId);
    }

    public List<MemberRoom> findAllNow(String campaignId) {
        return db.memberDao().findAllNow(campaignId);
    }

    @Nullable
    public MemberRoom find(String memberId) {
        return db.memberDao().find(memberId);
    }

    /** Участник, соответствующий текущему пользователю. */
    @Nullable
    public MemberRoom currentMember(String campaignId, @Nullable String userId) {
        if (userId == null) {
            return null;
        }
        for (MemberRoom member : db.memberDao().findAllNow(campaignId)) {
            if (userId.equals(member.userId)) {
                return member;
            }
        }
        return null;
    }

    public void refresh(String campaignId, RepoCallback<List<MemberRoom>> callback) {
        exec.io(() -> {
            List<MemberDto> remote = ApiCall.execute(api.members(campaignId));
            List<MemberRoom> rooms = new ArrayList<>();
            if (remote != null) {
                for (MemberDto dto : remote) {
                    if (dto == null || dto.id == null) {
                        continue;
                    }
                    MemberRoom room = Mappers.toRoom(dto, campaignId);
                    db.memberDao().upsert(room);
                    rooms.add(room);
                }
            }
            return rooms;
        }, callback);
    }

    /**
     * Добавление участника по почте. Если пользователя с такой почтой нет,
     * сервер вернёт приглашение — интерфейс покажет его QR-код.
     */
    public void add(String campaignId, String email, String role,
                    @Nullable String linkedEntityId, RepoCallback<AddMemberResponse> callback) {
        exec.io(() -> {
            AddMemberResponse response = ApiCall.execute(api.addMember(campaignId,
                    new AddMemberRequest(email, role, linkedEntityId)));
            if (response != null && response.member != null) {
                db.memberDao().upsert(Mappers.toRoom(response.member, campaignId));
            }
            return response;
        }, callback);
    }

    public void changeRole(String campaignId, String memberId, String role,
                           @Nullable String linkedEntityId, RepoCallback<MemberRoom> callback) {
        exec.io(() -> {
            MemberDto dto = ApiCall.execute(api.patchMember(campaignId, memberId,
                    new PatchMemberRequest(role, linkedEntityId)));
            MemberRoom room = Mappers.toRoom(dto, campaignId);
            if (room.id == null || room.id.isEmpty()) {
                room.id = memberId;
            }
            db.memberDao().upsert(room);
            return room;
        }, callback);
    }

    public void remove(String campaignId, String memberId, RepoCallback<Void> callback) {
        exec.io(() -> {
            ApiCall.execute(api.removeMember(campaignId, memberId));
            db.memberDao().delete(memberId);
            return null;
        }, callback);
    }

    /** Локальная замена списка участников (используется при snapshot/синхронизации). */
    public void replaceAll(String campaignId, List<MemberRoom> members) {
        db.memberDao().clearForCampaign(campaignId);
        db.memberDao().upsertAll(members);
    }
}
