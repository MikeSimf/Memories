package ru.dndmemory.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.dndmemory.domain.Member;
import ru.dndmemory.domain.MemberRole;
import ru.dndmemory.repo.MemberRepository;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Матрица прав: героев ведёт только владелец, остальные объекты — все редакторы. */
@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ru.dndmemory.repo.UserRepository userRepository;

    private PermissionService service;

    @BeforeEach
    void setUp() {
        service = new PermissionService(memberRepository, userRepository);
    }

    private Member member(MemberRole role) {
        Member member = new Member();
        member.setId(UUID.randomUUID());
        member.setRole(role);
        return member;
    }

    @Test
    @DisplayName("Владелец получает управление кампанией, участниками, героями и объектами")
    void ownerHasAllRights() {
        Member owner = member(MemberRole.OWNER);
        var permissions = service.permissionSet(owner);
        assertTrue(permissions.contains(Permissions.CAMPAIGN_MANAGE));
        assertTrue(permissions.contains(Permissions.MEMBERS_MANAGE));
        assertTrue(permissions.contains(Permissions.INVITE_CREATE));
        assertTrue(permissions.contains(Permissions.HERO_WRITE));
        assertTrue(permissions.contains(Permissions.ENTITY_WRITE));
        assertTrue(permissions.contains(Permissions.RETELLING_PUBLISH));
        assertTrue(service.canWriteClass(owner, true));
    }

    @Test
    @DisplayName("Мастер правит персонажей, локации и предметы, но не героев")
    void masterWritesEverythingButHeroes() {
        Member master = member(MemberRole.EDITOR_MASTER);
        assertTrue(service.canWriteClass(master, false));
        assertFalse(service.canWriteClass(master, true));
        assertFalse(service.permissionSet(master).contains(Permissions.HERO_WRITE));
        assertTrue(service.permissionSet(master).contains(Permissions.RETELLING_PUBLISH));
        assertFalse(service.permissionSet(master).contains(Permissions.MEMBERS_MANAGE));
    }

    @Test
    @DisplayName("Редактор-герой правит персонажей, локации и предметы, но не героев")
    void heroEditorWritesObjectsOnly() {
        Member hero = member(MemberRole.EDITOR_HERO);
        assertTrue(service.canWriteClass(hero, false));
        assertFalse(service.canWriteClass(hero, true));
        assertFalse(service.permissionSet(hero).contains(Permissions.HERO_WRITE));
        assertFalse(service.permissionSet(hero).contains(Permissions.MEMBERS_MANAGE));
    }

    @Test
    @DisplayName("Гость только читает: ни правки, ни публикации, ни управления")
    void viewerIsReadOnly() {
        Member viewer = member(MemberRole.VIEWER);
        assertTrue(service.permissionSet(viewer).contains(Permissions.ENTITY_READ));
        assertFalse(service.permissionSet(viewer).contains(Permissions.ENTITY_WRITE));
        assertFalse(service.permissionSet(viewer).contains(Permissions.HERO_WRITE));
        assertFalse(service.permissionSet(viewer).contains(Permissions.RETELLING_WRITE));
        assertFalse(service.permissionSet(viewer).contains(Permissions.MEMBERS_MANAGE));
        assertFalse(service.canWriteClass(viewer, false));
        assertFalse(service.canWriteClass(viewer, true));
    }
}
