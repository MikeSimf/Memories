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
        // В filename= по RFC 6266 разрешён только ASCII. С кириллической буквой внутри
        // Tomcat выбрасывает заголовок целиком — вместе с корректным filename*, — и
        // пользователь получает файл с именем из URL. Поэтому в filename= всегда идёт
        // транслит, а исходное имя остаётся percent-закодированным в filename*.
        boolean named = name != null && !name.isBlank();
        String ascii = FileNames.forDownload(named ? name : null, meta.getFileName());
        String original = named ? name : meta.getFileName();
        if (original == null || original.isBlank()) {
            original = ascii;
        }
        String encoded = java.net.URLEncoder.encode(original, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded)
                .body(resource);
    }
}
