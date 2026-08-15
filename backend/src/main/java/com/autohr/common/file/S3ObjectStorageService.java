package com.autohr.common.file;

import com.autohr.modules.system.service.SystemConfigService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class S3ObjectStorageService {

    private static final Logger log = LoggerFactory.getLogger(S3ObjectStorageService.class);
    private static final String[] CONFIG_KEYS = {
            "S3_ENABLED", "S3_ENDPOINT", "S3_REGION", "S3_BUCKET", "S3_ACCESS_KEY_ID",
            "S3_SECRET_ACCESS_KEY", "S3_SESSION_TOKEN", "S3_PREFIX", "S3_PATH_STYLE_ACCESS",
            "S3_INTERNAL_ENDPOINT_ENABLED", "S3_INTERNAL_ENDPOINT", "S3_ALLOW_HTTP_ENDPOINTS",
            "S3_ALLOW_PRIVATE_ENDPOINTS"
    };

    private final SystemConfigService systemConfigService;
    private final ThreadPoolTaskExecutor s3ArchiveExecutor;
    private final ReentrantReadWriteLock clientLock = new ReentrantReadWriteLock();
    private volatile UploadClientBundle uploadClientBundle;
    private volatile ExternalClientBundle externalClientBundle;
    private volatile boolean closed;

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
        StorageSettings settings = loadSettings();
        if (!settings.enabled()) {
            return;
        }
        if (!Files.isRegularFile(localFile)) {
            log.warn("Skipping S3 archive because the local file is unavailable: {}", localFile);
            return;
        }
        if (!settings.isUploadUsable()) {
            log.warn("Skipping S3 archive because the object storage configuration is incomplete or unsafe");
            return;
        }

        String objectKey;
        try {
            objectKey = buildObjectKey(settings.prefix(), objectName);
        } catch (IllegalArgumentException ex) {
            log.warn("Skipping S3 archive because the object key is invalid: {}", ex.getMessage());
            return;
        }

        try {
            withUploadClient(settings, client -> {
                PutObjectRequest.Builder request = PutObjectRequest.builder()
                        .bucket(settings.bucket())
                        .key(objectKey);
                if (contentType != null && !contentType.isBlank()) {
                    request.contentType(contentType);
                }
                client.putObject(request.build(), RequestBody.fromFile(localFile));
                return null;
            });
            log.info("Archived {} to S3 bucket {} as {}", localFile.getFileName(), settings.bucket(), objectKey);
        } catch (Exception ex) {
            // S3 storage is optional. The caller keeps its local file on every failure.
            log.warn("S3 archive failed for {}. Keeping the local file: {}", localFile.getFileName(), ex.toString());
        }
    }

    /**
     * Deletes an archived object without affecting the caller when storage is unavailable.
     * Callers should invoke this only after the owning database transaction commits.
     */
    public void deleteObjectIfEnabled(String objectName) {
        deleteObjectsIfEnabled(objectName == null ? List.of() : List.of(objectName));
    }

    public void deleteObjectsIfEnabled(Collection<String> objectNames) {
        if (objectNames == null || objectNames.isEmpty()) {
            return;
        }
        List<String> distinctObjectNames = new LinkedHashSet<>(objectNames).stream()
                .filter(objectName -> objectName != null && !objectName.isBlank())
                .toList();
        if (distinctObjectNames.isEmpty()) {
            return;
        }
        try {
            s3ArchiveExecutor.execute(() -> deleteObjects(distinctObjectNames));
        } catch (TaskRejectedException ex) {
            log.warn("S3 deletion queue is full for {} object(s). Retrying synchronously.", distinctObjectNames.size());
            deleteObjects(distinctObjectNames);
        }
    }

    private void deleteObjects(List<String> objectNames) {
        StorageSettings settings = loadSettings();
        if (!settings.enabled() || !settings.isUploadUsable()) {
            return;
        }
        try {
            withUploadClient(settings, client -> {
                for (String objectName : objectNames) {
                    try {
                        String objectKey = buildObjectKey(settings.prefix(), objectName);
                        client.deleteObject(DeleteObjectRequest.builder()
                                .bucket(settings.bucket())
                                .key(objectKey)
                                .build());
                        log.info("Deleted S3 object {} from bucket {}", objectKey, settings.bucket());
                    } catch (Exception ex) {
                        log.warn("Best-effort S3 deletion failed for {}: {}", objectName, ex.toString());
                    }
                }
                return null;
            });
        } catch (Exception ex) {
            log.warn("Best-effort S3 deletion could not start for {} object(s): {}",
                    objectNames.size(), ex.toString());
        }
    }

    /**
     * Returns a short-lived URL signed for the browser-facing endpoint. A failed
     * archive leaves no readable object, so callers retain their local response.
     */
    public Optional<URI> presignExternalDownloadIfAvailable(String objectName) {
        StorageSettings settings = loadSettings();
        if (!settings.enabled() || !settings.isExternalUsable()) {
            return Optional.empty();
        }
        try {
            String objectKey = buildObjectKey(settings.prefix(), objectName);
            return withExternalClients(settings, bundle -> {
                bundle.externalClient().headObject(HeadObjectRequest.builder()
                        .bucket(settings.bucket())
                        .key(objectKey)
                        .build());
                GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                        .bucket(settings.bucket())
                        .key(objectKey)
                        .build();
                URI url = URI.create(bundle.presigner().presignGetObject(GetObjectPresignRequest.builder()
                                .signatureDuration(Duration.ofMinutes(5))
                                .getObjectRequest(getObjectRequest)
                                .build())
                        .url()
                        .toExternalForm());
                return Optional.of(url);
            });
        } catch (Exception ex) {
            log.debug("S3 object {} is not available through the browser endpoint: {}", objectName, ex.toString());
            return Optional.empty();
        }
    }

    private StorageSettings loadSettings() {
        Map<String, String> config = systemConfigService.loadConfig(CONFIG_KEYS);
        String externalEndpoint = trim(config.get("S3_ENDPOINT"));
        boolean internalEndpointEnabled = Boolean.parseBoolean(config.get("S3_INTERNAL_ENDPOINT_ENABLED"));
        String uploadEndpoint = internalEndpointEnabled
                ? trim(config.get("S3_INTERNAL_ENDPOINT"))
                : externalEndpoint;
        return new StorageSettings(
                Boolean.parseBoolean(config.get("S3_ENABLED")),
                uploadEndpoint,
                externalEndpoint,
                valueOrDefault(config.get("S3_REGION"), "us-east-1"),
                trim(config.get("S3_BUCKET")),
                trim(config.get("S3_ACCESS_KEY_ID")),
                trim(config.get("S3_SECRET_ACCESS_KEY")),
                trim(config.get("S3_SESSION_TOKEN")),
                trim(config.get("S3_PREFIX")),
                Boolean.parseBoolean(config.get("S3_PATH_STYLE_ACCESS")),
                Boolean.parseBoolean(config.get("S3_ALLOW_HTTP_ENDPOINTS")),
                Boolean.parseBoolean(config.get("S3_ALLOW_PRIVATE_ENDPOINTS")));
    }

    private <T> T withUploadClient(StorageSettings settings, Function<S3Client, T> action) {
        ClientKey requiredKey = settings.uploadClientKey();
        while (true) {
            clientLock.readLock().lock();
            try {
                if (closed) {
                    throw new IllegalStateException("S3 object storage service is shutting down");
                }
                UploadClientBundle current = uploadClientBundle;
                if (current != null && current.key().equals(requiredKey)) {
                    return action.apply(current.client());
                }
            } finally {
                clientLock.readLock().unlock();
            }
            refreshUploadClient(requiredKey);
        }
    }

    private <T> T withExternalClients(StorageSettings settings, Function<ExternalClientBundle, T> action) {
        ClientKey requiredKey = settings.externalClientKey();
        while (true) {
            clientLock.readLock().lock();
            try {
                if (closed) {
                    throw new IllegalStateException("S3 object storage service is shutting down");
                }
                ExternalClientBundle current = externalClientBundle;
                if (current != null && current.key().equals(requiredKey)) {
                    return action.apply(current);
                }
            } finally {
                clientLock.readLock().unlock();
            }
            refreshExternalClients(requiredKey);
        }
    }

    private void refreshUploadClient(ClientKey requiredKey) {
        clientLock.writeLock().lock();
        try {
            if (closed) {
                throw new IllegalStateException("S3 object storage service is shutting down");
            }
            if (uploadClientBundle != null && uploadClientBundle.key().equals(requiredKey)) {
                return;
            }
            UploadClientBundle replacement = new UploadClientBundle(requiredKey, createClient(requiredKey));
            UploadClientBundle previous = uploadClientBundle;
            uploadClientBundle = replacement;
            if (previous != null) {
                previous.close();
            }
        } finally {
            clientLock.writeLock().unlock();
        }
    }

    private void refreshExternalClients(ClientKey requiredKey) {
        clientLock.writeLock().lock();
        try {
            if (closed) {
                throw new IllegalStateException("S3 object storage service is shutting down");
            }
            if (externalClientBundle != null && externalClientBundle.key().equals(requiredKey)) {
                return;
            }
            ExternalClientBundle replacement = createExternalClientBundle(requiredKey);
            ExternalClientBundle previous = externalClientBundle;
            externalClientBundle = replacement;
            if (previous != null) {
                previous.close();
            }
        } finally {
            clientLock.writeLock().unlock();
        }
    }

    private ExternalClientBundle createExternalClientBundle(ClientKey key) {
        S3Client externalClient = null;
        S3Presigner presigner = null;
        try {
            externalClient = createClient(key);
            presigner = createPresigner(key.endpoint(), key.region(), key.accessKeyId(),
                    key.secretAccessKey(), key.sessionToken(), key.pathStyleAccess());
            return new ExternalClientBundle(key, externalClient, presigner);
        } catch (RuntimeException ex) {
            closeQuietly(presigner);
            closeQuietly(externalClient);
            throw ex;
        }
    }

    private S3Client createClient(ClientKey key) {
        return createClient(key.endpoint(), key.region(), key.accessKeyId(), key.secretAccessKey(),
                key.sessionToken(), key.pathStyleAccess());
    }

    private S3Client createClient(String endpoint, String region, String accessKeyId, String secretAccessKey,
                                  String sessionToken, boolean pathStyleAccess) {
        var credentials = sessionToken.isEmpty()
                ? AwsBasicCredentials.create(accessKeyId, secretAccessKey)
                : AwsSessionCredentials.create(accessKeyId, secretAccessKey, sessionToken);
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .forcePathStyle(pathStyleAccess)
                .endpointOverride(URI.create(endpoint))
                .overrideConfiguration(configuration -> configuration
                        .apiCallAttemptTimeout(Duration.ofSeconds(15))
                        .apiCallTimeout(Duration.ofSeconds(30)))
                .build();
    }

    private S3Presigner createPresigner(String endpoint, String region, String accessKeyId, String secretAccessKey,
                                        String sessionToken, boolean pathStyleAccess) {
        var credentials = sessionToken.isEmpty()
                ? AwsBasicCredentials.create(accessKeyId, secretAccessKey)
                : AwsSessionCredentials.create(accessKeyId, secretAccessKey, sessionToken);
        return S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(pathStyleAccess).build())
                .endpointOverride(URI.create(endpoint))
                .build();
    }

    @PreDestroy
    void closeClients() {
        clientLock.writeLock().lock();
        try {
            closed = true;
            if (uploadClientBundle != null) {
                uploadClientBundle.close();
                uploadClientBundle = null;
            }
            if (externalClientBundle != null) {
                externalClientBundle.close();
                externalClientBundle = null;
            }
        } finally {
            clientLock.writeLock().unlock();
        }
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

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }

    private record StorageSettings(boolean enabled, String uploadEndpoint, String externalEndpoint,
                                   String region, String bucket, String accessKeyId, String secretAccessKey,
                                   String sessionToken, String prefix, boolean pathStyleAccess,
                                   boolean allowHttpEndpoints, boolean allowPrivateEndpoints) {

        private boolean hasCredentials() {
            return !bucket.isEmpty()
                    && !accessKeyId.isEmpty()
                    && !secretAccessKey.isEmpty();
        }

        private boolean isUploadUsable() {
            return hasCredentials()
                    && !uploadEndpoint.isEmpty()
                    && S3EndpointValidator.isAllowed(uploadEndpoint, allowHttpEndpoints, allowPrivateEndpoints);
        }

        private boolean isExternalUsable() {
            return hasCredentials()
                    && !externalEndpoint.isEmpty()
                    && !bucket.isEmpty()
                    && S3EndpointValidator.isAllowed(externalEndpoint, allowHttpEndpoints, allowPrivateEndpoints);
        }

        private ClientKey uploadClientKey() {
            return new ClientKey(uploadEndpoint, region, accessKeyId, secretAccessKey,
                    sessionToken, pathStyleAccess);
        }

        private ClientKey externalClientKey() {
            return new ClientKey(externalEndpoint, region, accessKeyId, secretAccessKey,
                    sessionToken, pathStyleAccess);
        }
    }

    private record ClientKey(String endpoint, String region,
                             String accessKeyId, String secretAccessKey, String sessionToken,
                             boolean pathStyleAccess) {
    }

    private record UploadClientBundle(ClientKey key, S3Client client) implements AutoCloseable {
        @Override
        public void close() {
            closeQuietly(client);
        }
    }

    private record ExternalClientBundle(ClientKey key, S3Client externalClient,
                                        S3Presigner presigner) implements AutoCloseable {
        @Override
        public void close() {
            closeQuietly(presigner);
            closeQuietly(externalClient);
        }
    }
}
