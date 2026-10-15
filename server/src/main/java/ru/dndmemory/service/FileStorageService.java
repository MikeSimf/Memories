package ru.dndmemory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.dndmemory.config.AppProperties;
import ru.dndmemory.domain.StoredFile;
import ru.dndmemory.repo.StoredFileRepository;
import ru.dndmemory.web.ApiException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/** Файлы лежат на томе Docker, в базе — только метаданные. */
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private static final long MAX_SIZE = 10L * 1024 * 1024;

    private final AppProperties properties;
    private final StoredFileRepository repository;

    @Transactional
    public StoredFile store(UUID ownerUserId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.validation("Файл не передан");
        }
        if (file.getSize() > MAX_SIZE) {
            throw ApiException.validation("Файл больше 10 МБ");
        }
        UUID id = UUID.randomUUID();
        Path directory = Paths.get(properties.getFilesDir()).toAbsolutePath().normalize();
        Path target = directory.resolve(id.toString());
        try {
            Files.createDirectories(directory);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось сохранить файл", e);
        }

        StoredFile stored = new StoredFile();
        stored.setId(id);
        stored.setOwnerUserId(ownerUserId);
        stored.setFileName(safeName(file.getOriginalFilename()));
        stored.setContentType(file.getContentType() == null ? "application/octet-stream" : file.getContentType());
        stored.setSizeBytes(file.getSize());
        stored.setStoragePath(target.toString());
        return repository.save(stored);
    }

    @Transactional(readOnly = true)
    public StoredFile requireMeta(UUID id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Файл"));
    }

    @Transactional(readOnly = true)
    public Resource load(UUID id) {
        StoredFile meta = requireMeta(id);
        Path path = Paths.get(meta.getStoragePath());
        if (!Files.exists(path)) {
            throw ApiException.notFound("Файл");
        }
        return new FileSystemResource(path);
    }

    private String safeName(String original) {
        if (original == null || original.trim().isEmpty()) {
            return "file";
        }
        String name = original.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return name.length() > 255 ? name.substring(0, 255) : name;
    }
}
