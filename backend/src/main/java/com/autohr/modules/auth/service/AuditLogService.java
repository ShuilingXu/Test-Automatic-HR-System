package com.autohr.modules.auth.service;

import com.autohr.modules.auth.dto.AuditLogVO;

import java.util.List;

public interface AuditLogService {
    void log(Long operatorUserId, String operatorUsername, String operatorRoleCode, String moduleCode, String actionCode, String targetType, String targetId, String detail);
    List<AuditLogVO> list(String moduleCode, String actionCode, String keyword);
}
