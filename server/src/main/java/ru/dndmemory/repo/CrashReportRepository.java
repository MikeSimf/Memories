package ru.dndmemory.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.dndmemory.domain.CrashReport;

import java.util.List;
import java.util.UUID;

public interface CrashReportRepository extends JpaRepository<CrashReport, UUID> {
    List<CrashReport> findAllByOrderByCreatedAtDesc();
}
