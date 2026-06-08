package com.autohr.common.file;

import com.autohr.common.exception.BusinessException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class FileDownloadSupport {

    private FileDownloadSupport() {
    }

    public static ResponseEntity<Resource> buildInlineResponse(String filePath, Path baseDir, String fileName, String contentType, String missingMessage) {
        return buildResponse(filePath, baseDir, fileName, contentType, "inline", missingMessage);
    }

    public static ResponseEntity<Resource> buildAttachmentResponse(String filePath, Path baseDir, String fileName, String contentType, String missingMessage) {
        return buildResponse(filePath, baseDir, fileName, contentType, "attachment", missingMessage);
    }

    private static ResponseEntity<Resource> buildResponse(String filePath, Path baseDir, String fileName, String contentType, String disposition, String missingMessage) {
        Path path = Paths.get(filePath).toAbsolutePath().normalize();
        if (!path.startsWith(baseDir) || !Files.isRegularFile(path)) {
            throw new BusinessException(missingMessage);
        }
        Resource resource = new FileSystemResource(path);
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        String safeContentType = contentType == null ? "application/octet-stream" : contentType;
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(safeContentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename*=UTF-8''" + encodedFileName)
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
    }
}
