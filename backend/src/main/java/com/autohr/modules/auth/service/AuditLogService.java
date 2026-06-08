package com.autohr.modules.auth.service;

public interface AuditLogService {
    void log(Long operatorUserId, String operatorUsername, String operatorRoleCode, String moduleCode, String actionCode, String targetType, String targetId, String detail);
}
