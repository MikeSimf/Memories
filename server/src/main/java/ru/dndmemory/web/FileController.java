package ru.dndmemory.web;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.dndmemory.domain.StoredFile;
import ru.dndmemory.security.UserPrincipal;
import ru.dndmemory.service.FileNames;
import ru.dndmemory.service.FileStorageService;
import ru.dndmemory.web.dto.FileDtos;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    @PostMapping("/api/files")
    @ResponseStatus(HttpStatus.CREATED)
    public FileDtos.FileDto upload(@AuthenticationPrincipal UserPrincipal principal,
                                   @RequestParam("file") MultipartFile file) {
        StoredFile stored = fileStorageService.store(principal.getId(), file);
        return FileDtos.FileDto.builder()
                .id(stored.getId())
                .url("/api/files/" + stored.getId())
                .contentType(stored.getContentType())
                .size(stored.getSizeBytes())
                .build();
    }

    /**
     * Отдаёт файл. Необязательный {@code name} — название материала или объекта:
     * из него делается понятное имя для скачивания (транслит и обрезка),
     * расширение берётся у исходного файла.
     */
    @GetMapping("/api/files/{fileId}")
    public ResponseEntity<Resource> download(@PathVariable UUID fileId,
                                             @RequestParam(name = "name", required = false) String name) {
        StoredFile meta = fileStorageService.requireMeta(fileId);
        Resource resource = fileStorageService.load(fileId);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(meta.getContentType());
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        String fileName = name == null || name.isBlank()
                ? meta.getFileName()
                : FileNames.forDownload(name, meta.getFileName());
        // Русские буквы в заголовке недопустимы без кодирования: отдаём ASCII-имя
        // и дублируем исходное в filename*, как требует RFC 6266.
        String encoded = java.net.URLEncoder.encode(fileName, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + fileName + "\"; filename*=UTF-8''" + encoded)
                .body(resource);
    }
}
