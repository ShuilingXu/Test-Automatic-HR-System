package com.autohr.common.file;

import com.autohr.modules.system.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class S3ObjectStorageService {

    private static final Logger log = LoggerFactory.getLogger(S3ObjectStorageService.class);
    private static final String[] CONFIG_KEYS = {
            "S3_ENABLED", "S3_ENDPOINT", "S3_REGION", "S3_BUCKET", "S3_ACCESS_KEY_ID",
            "S3_SECRET_ACCESS_KEY", "S3_SESSION_TOKEN", "S3_PREFIX", "S3_PATH_STYLE_ACCESS",
            "S3_INTERNAL_ENDPOINT_ENABLED", "S3_INTERNAL_ENDPOINT"
    };

    private final SystemConfigService systemConfigService;
    private final ThreadPoolTaskExecutor s3ArchiveExecutor;

    /**
     * Archives a local file to an S3-compatible object store. Local storage remains
     * the source of truth so a storage outage never interrupts interview processing.
     */
    public void archiveIfEnabled(Path localFile, String objectName, String contentType) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submitArchive(localFile, objectName, contentType);
                }
            });
            return;
        }
        submitArchive(localFile, objectName, contentType);
    }

    private void submitArchive(Path localFile, String objectName, String contentType) {
        try {
            s3ArchiveExecutor.execute(() -> archive(localFile, objectName, contentType));
        } catch (TaskRejectedException ex) {
            log.warn("S3 archive queue is full for {}. Keeping the local file.", localFile.getFileName());
        }
    }

    private void archive(Path localFile, String objectName, String contentType) {
        Map<String, String> config = systemConfigService.loadConfig(CONFIG_KEYS);
        if (!Boolean.parseBoolean(config.get("S3_ENABLED"))) {
            return;
        }
        if (!Files.isRegularFile(localFile)) {
            log.warn("Skipping S3 archive because the local file is unavailable: {}", localFile);
            return;
        }

        String endpoint = selectUploadEndpoint(config);
        if (endpoint.isEmpty()) {
            log.warn("Skipping S3 archive because the upload endpoint is not configured");
            return;
        }
        String region = valueOrDefault(config.get("S3_REGION"), "us-east-1");
        String bucket = trim(config.get("S3_BUCKET"));
        String accessKeyId = trim(config.get("S3_ACCESS_KEY_ID"));
        String secretAccessKey = trim(config.get("S3_SECRET_ACCESS_KEY"));
        if (bucket.isEmpty() || accessKeyId.isEmpty() || secretAccessKey.isEmpty()) {
            log.warn("Skipping S3 archive because S3_BUCKET, S3_ACCESS_KEY_ID, or S3_SECRET_ACCESS_KEY is not configured");
            return;
        }

        String objectKey;
        try {
            objectKey = buildObjectKey(config.get("S3_PREFIX"), objectName);
        } catch (IllegalArgumentException ex) {
            log.warn("Skipping S3 archive because the object key is invalid: {}", ex.getMessage());
            return;
        }

        try (S3Client client = createClient(endpoint, region, accessKeyId, secretAccessKey,
                trim(config.get("S3_SESSION_TOKEN")), Boolean.parseBoolean(config.get("S3_PATH_STYLE_ACCESS")))) {
            PutObjectRequest.Builder request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey);
            if (contentType != null && !contentType.isBlank()) {
                request.contentType(contentType);
            }
            client.putObject(request.build(), RequestBody.fromFile(localFile));
            log.info("Archived {} to S3 bucket {} as {}", localFile.getFileName(), bucket, objectKey);
        } catch (Exception ex) {
            // S3 storage is an optional archive. The caller keeps its local file on every failure.
            log.warn("S3 archive failed for {}. Keeping the local file: {}", localFile.getFileName(), ex.toString());
        }
    }

    /**
     * Returns a short-lived URL signed for the browser-facing endpoint. A failed
     * archive leaves no readable object, so callers retain their local response.
     */
    public Optional<URI> presignExternalDownloadIfAvailable(String objectName) {
        Map<String, String> config = systemConfigService.loadConfig(CONFIG_KEYS);
        if (!Boolean.parseBoolean(config.get("S3_ENABLED"))) {
            return Optional.empty();
        }
        String endpoint = trim(config.get("S3_ENDPOINT"));
        String region = valueOrDefault(config.get("S3_REGION"), "us-east-1");
        String bucket = trim(config.get("S3_BUCKET"));
        String accessKeyId = trim(config.get("S3_ACCESS_KEY_ID"));
        String secretAccessKey = trim(config.get("S3_SECRET_ACCESS_KEY"));
        if (endpoint.isEmpty() || bucket.isEmpty() || accessKeyId.isEmpty() || secretAccessKey.isEmpty()) {
            return Optional.empty();
        }
        try {
            String objectKey = buildObjectKey(config.get("S3_PREFIX"), objectName);
            String sessionToken = trim(config.get("S3_SESSION_TOKEN"));
            boolean pathStyleAccess = Boolean.parseBoolean(config.get("S3_PATH_STYLE_ACCESS"));
            try (S3Client client = createClient(endpoint, region, accessKeyId, secretAccessKey, sessionToken, pathStyleAccess);
                 S3Presigner presigner = createPresigner(endpoint, region, accessKeyId, secretAccessKey, sessionToken, pathStyleAccess)) {
                client.headObject(HeadObjectRequest.builder().bucket(bucket).key(objectKey).build());
                GetObjectRequest getObjectRequest = GetObjectRequest.builder().bucket(bucket).key(objectKey).build();
                URI url = presigner.presignGetObject(GetObjectPresignRequest.builder()
                                .signatureDuration(Duration.ofMinutes(5))
                                .getObjectRequest(getObjectRequest)
                                .build())
                        .url()
                        .toURI();
                return Optional.of(url);
            }
        } catch (Exception ex) {
            log.debug("S3 object {} is not available through the browser endpoint: {}", objectName, ex.toString());
            return Optional.empty();
        }
    }

    private S3Client createClient(String endpoint, String region, String accessKeyId, String secretAccessKey,
                                  String sessionToken, boolean pathStyleAccess) {
        var credentials = sessionToken.isEmpty()
                ? AwsBasicCredentials.create(accessKeyId, secretAccessKey)
                : AwsSessionCredentials.create(accessKeyId, secretAccessKey, sessionToken);
        var builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .forcePathStyle(pathStyleAccess)
                .overrideConfiguration(configuration -> configuration
                        .apiCallAttemptTimeout(Duration.ofSeconds(15))
                        .apiCallTimeout(Duration.ofSeconds(30)));
        if (!endpoint.isEmpty()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    private S3Presigner createPresigner(String endpoint, String region, String accessKeyId, String secretAccessKey,
                                        String sessionToken, boolean pathStyleAccess) {
        var credentials = sessionToken.isEmpty()
                ? AwsBasicCredentials.create(accessKeyId, secretAccessKey)
                : AwsSessionCredentials.create(accessKeyId, secretAccessKey, sessionToken);
        var builder = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(pathStyleAccess).build());
        builder.endpointOverride(URI.create(endpoint));
        return builder.build();
    }

    private String selectUploadEndpoint(Map<String, String> config) {
        if (!Boolean.parseBoolean(config.get("S3_INTERNAL_ENDPOINT_ENABLED"))) {
            return trim(config.get("S3_ENDPOINT"));
        }
        return trim(config.get("S3_INTERNAL_ENDPOINT"));
    }

    private String buildObjectKey(String prefix, String objectName) {
        String normalizedName = normalizePath(objectName);
        String normalizedPrefix = normalizePath(prefix == null ? "" : prefix);
        return normalizedPrefix.isEmpty() ? normalizedName : normalizedPrefix + "/" + normalizedName;
    }

    private String normalizePath(String value) {
        String normalized = trim(value).replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isEmpty()) {
            return "";
        }
        for (String segment : normalized.split("/")) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("object path must not contain empty, dot, or parent segments");
            }
        }
        return normalized;
    }

    private String valueOrDefault(String value, String fallback) {
        String trimmed = trim(value);
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
