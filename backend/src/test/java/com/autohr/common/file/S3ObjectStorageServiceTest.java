package com.autohr.common.file;

import com.autohr.modules.system.service.SystemConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3ObjectStorageServiceTest {

    @Mock
    SystemConfigService systemConfigService;

    @Mock
    ThreadPoolTaskExecutor s3ArchiveExecutor;

    @Test
    void queuesBestEffortDeletionInsteadOfBlockingTheRequestThread() {
        S3ObjectStorageService service = new S3ObjectStorageService(systemConfigService, s3ArchiveExecutor);

        service.deleteObjectIfEnabled("resumes/resume.pdf");

        verify(s3ArchiveExecutor).execute(any(Runnable.class));
    }

    @Test
    void queueRejectionStillAttemptsDeletionWithoutFailingTheCommittedRequest() {
        S3ObjectStorageService service = new S3ObjectStorageService(systemConfigService, s3ArchiveExecutor);
        doThrow(new TaskRejectedException("full")).when(s3ArchiveExecutor).execute(any(Runnable.class));
        when(systemConfigService.loadConfig(any(String[].class))).thenReturn(Map.of("S3_ENABLED", "false"));

        assertDoesNotThrow(() -> service.deleteObjectIfEnabled("resumes/resume.pdf"));

        verify(systemConfigService).loadConfig(any(String[].class));
    }
}
