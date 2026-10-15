package ru.dndmemory.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.dndmemory.domain.EntityType;
import ru.dndmemory.domain.Member;
import ru.dndmemory.domain.MemberRole;
import ru.dndmemory.repo.MemberRepository;
import ru.dndmemory.web.ApiException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private MemberRepository memberRepository;

    private PermissionService service;

    @BeforeEach
    void setUp() {
        service = new PermissionService(memberRepository);
    }

    private Member member(MemberRole role, UUID linkedEntityId) {
        Member member = new Member();
        member.setId(UUID.randomUUID());
        member.setRole(role);
        member.setLinkedEntityId(linkedEntityId);
        return member;
    }

    @Test
    @DisplayName("Владелец получает управление кампанией, участниками и правку объектов")
    void ownerHasAllRights() {
        var permissions = service.permissionSet(member(MemberRole.OWNER, null));
        assertTrue(permissions.contains(Permissions.CAMPAIGN_MANAGE));
        assertTrue(permissions.contains(Permissions.MEMBERS_MANAGE));
        assertTrue(permissions.contains(Permissions.HERO_WRITE));
        assertTrue(permissions.contains(Permissions.RETELLING_PUBLISH));
    }

    @Test
    @DisplayName("Гость только читает: ни правки, ни публикации, ни управления")
    void viewerIsReadOnly() {
        var permissions = service.permissionSet(member(MemberRole.VIEWER, null));
        assertTrue(permissions.contains(Permissions.ENTITY_READ));
        assertFalse(permissions.contains(Permissions.ENTITY_WRITE));
        assertFalse(permissions.contains(Permissions.RETELLING_WRITE));
        assertFalse(permissions.contains(Permissions.MEMBERS_MANAGE));
        assertFalse(service.canWriteEntity(member(MemberRole.VIEWER, null), UUID.randomUUID(), EntityType.ITEM));
    }

    @Test
    @DisplayName("Редактор-герой правит НПС и предметы, но из героев — только своего")
    void heroEditorWritesOnlyOwnHero() {
        UUID ownHero = UUID.randomUUID();
        Member hero = member(MemberRole.EDITOR_HERO, ownHero);

        assertTrue(service.canWriteEntity(hero, ownHero, EntityType.HERO));
        assertFalse(service.canWriteEntity(hero, UUID.randomUUID(), EntityType.HERO));
        assertTrue(service.canWriteEntity(hero, UUID.randomUUID(), EntityType.NPC));
        assertTrue(service.canWriteEntity(hero, UUID.randomUUID(), EntityType.LOCATION));
        assertFalse(permissionsHas(hero, Permissions.HERO_WRITE));
        assertFalse(permissionsHas(hero, Permissions.MEMBERS_MANAGE));
    }

    @Test
    @DisplayName("Мастер правит любого героя и чужие пересказы")
    void masterWritesEverything() {
        Member master = member(MemberRole.EDITOR_MASTER, null);
        assertTrue(service.canWriteEntity(master, UUID.randomUUID(), EntityType.HERO));
        assertTrue(permissionsHas(master, Permissions.RETELLING_PUBLISH));
        assertFalse(permissionsHas(master, Permissions.MEMBERS_MANAGE));
    }

    @Test
    @DisplayName("Редактор-герой со своим героем второго создать не может, но предметы — сколько угодно")
    void heroEditorCannotCreateSecondHero() {
        Member hero = member(MemberRole.EDITOR_HERO, UUID.randomUUID());
        assertThrows(ApiException.class, () -> service.requireEntityCreate(hero, EntityType.HERO));
        service.requireEntityCreate(hero, EntityType.ITEM);
        service.requireEntityCreate(hero, EntityType.NPC);
    }

    @Test
    @DisplayName("Пока своего героя нет, игрок может создать первого — он к нему привяжется")
    void heroEditorWithoutLinkedHeroCanCreateFirstHero() {
        service.requireEntityCreate(member(MemberRole.EDITOR_HERO, null), EntityType.HERO);
    }

    @Test
    @DisplayName("Гость не создаёт ничего")
    void viewerCannotCreateAnything() {
        Member viewer = member(MemberRole.VIEWER, null);
        assertThrows(ApiException.class, () -> service.requireEntityCreate(viewer, EntityType.ITEM));
        assertThrows(ApiException.class, () -> service.requireEntityCreate(viewer, EntityType.HERO));
    }

    private boolean permissionsHas(Member member, String permission) {
        return service.permissionSet(member).contains(permission);
    }
}
