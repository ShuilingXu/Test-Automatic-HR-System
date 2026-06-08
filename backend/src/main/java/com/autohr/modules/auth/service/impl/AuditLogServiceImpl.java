package com.autohr.modules.auth.service.impl;

import com.autohr.modules.auth.entity.SysAuditLog;
import com.autohr.modules.auth.mapper.SysAuditLogMapper;
import com.autohr.modules.auth.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final SysAuditLogMapper auditLogMapper;

    @Override
    public void log(Long operatorUserId, String operatorUsername, String operatorRoleCode, String moduleCode, String actionCode, String targetType, String targetId, String detail) {
        SysAuditLog log = new SysAuditLog();
        log.setId(nextId(auditLogMapper.selectList(null).stream().map(SysAuditLog::getId).toList()));
        log.setOperatorUserId(operatorUserId);
        log.setOperatorUsername(operatorUsername);
        log.setOperatorRoleCode(operatorRoleCode);
        log.setModuleCode(moduleCode);
        log.setActionCode(actionCode);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetail(detail);
        auditLogMapper.insert(log);
    }

    private Long nextId(List<Long> ids) {
        return ids.stream().filter(Objects::nonNull).max(Long::compareTo).map(id -> id + 1).orElse(1L);
    }
}
