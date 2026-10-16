package ru.dndmemory.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.dndmemory.config.AppProperties;
import ru.dndmemory.domain.Campaign;
import ru.dndmemory.domain.ChangeLog;
import ru.dndmemory.repo.CampaignRepository;
import ru.dndmemory.repo.ChangeLogRepository;
import ru.dndmemory.repo.InviteRepository;
import ru.dndmemory.repo.PasswordResetRepository;
import ru.dndmemory.repo.RefreshTokenRepository;
import ru.dndmemory.repo.SyncConflictRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Ночное обслуживание: компактация журнала синхронизации и уборка просроченного.
 * После компактации клиент, долго не выходивший на связь, получит resync_required —
 * это штатный сценарий (docs/sync.md, раздел 6).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaintenanceJob {

    private final CampaignRepository campaignRepository;
    private final ChangeLogRepository changeLogRepository;
    private final InviteRepository inviteRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetRepository passwordResetRepository;
    private final SyncConflictRepository conflictRepository;
    private final AppProperties properties;

    @Scheduled(cron = "0 20 4 * * *")
    @Transactional
    public void run() {
        Instant now = Instant.now();
        int revokedInvites = inviteRepository.revokeExpired(now);
        int expiredTokens = refreshTokenRepository.deleteExpired(now);
        int expiredResets = passwordResetRepository.deleteExpired(now);
        int oldConflicts = conflictRepository.deleteOlderThan(now.minus(180, ChronoUnit.DAYS));

        Instant logThreshold = now.minus(properties.getRetention().getChangeLogDays(), ChronoUnit.DAYS);
        int deletedOldChanges = changeLogRepository.deleteOlderThan(logThreshold);

        int compacted = 0;
        for (Campaign campaign : campaignRepository.findAll()) {
            compacted += compactByRows(campaign.getId(), properties.getRetention().getChangeLogMaxRows());
        }
        log.info("Обслуживание завершено: приглашений отозвано {}, refresh-токенов удалено {}, "
                        + "запросов сброса удалено {}, старых записей журнала {}, записей компактизовано {}, "
                        + "старых конфликтов {}",
                revokedInvites, expiredTokens, expiredResets, deletedOldChanges, compacted, oldConflicts);
    }

    private int compactByRows(java.util.UUID campaignId, int maxRows) {
        List<ChangeLog> newest = changeLogRepository.findAllByCampaignIdOrderByRevisionDesc(
                campaignId, PageRequest.of(0, maxRows + 1));
        if (newest.size() <= maxRows) {
            return 0;
        }
        long threshold = newest.get(maxRows).getRevision();
        return changeLogRepository.deleteBelowRevision(campaignId, threshold);
    }
}
