package com.autohr.modules.auth.service.impl;

import com.autohr.modules.auth.dto.AuditLogVO;
import com.autohr.modules.auth.entity.SysAuditLog;
import com.autohr.modules.auth.mapper.SysAuditLogMapper;
import com.autohr.modules.auth.service.AuditLogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final SysAuditLogMapper auditLogMapper;

    @Override
    public synchronized void log(Long operatorUserId, String operatorUsername, String operatorRoleCode, String moduleCode, String actionCode, String targetType, String targetId, String detail) {
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

    @Override
    public List<AuditLogVO> list(String moduleCode, String actionCode, String keyword) {
        boolean adminCategory = "ADMIN".equals(moduleCode);
        return auditLogMapper.selectList(new LambdaQueryWrapper<SysAuditLog>()
                .eq(moduleCode != null && !moduleCode.isBlank() && !adminCategory, SysAuditLog::getModuleCode, moduleCode)
                .notIn(adminCategory, SysAuditLog::getModuleCode, "INTERVIEW", "RECRUITMENT")
                .eq(actionCode != null && !actionCode.isBlank(), SysAuditLog::getActionCode, actionCode)
                .and(keyword != null && !keyword.isBlank(), q -> q.like(SysAuditLog::getOperatorUsername, keyword)
                        .or().like(SysAuditLog::getDetail, keyword)
                        .or().like(SysAuditLog::getTargetId, keyword))
                .orderByDesc(SysAuditLog::getId))
                .stream().map(this::toVO).toList();
    }

    private AuditLogVO toVO(SysAuditLog log) {
        AuditLogVO vo = new AuditLogVO();
        BeanUtils.copyProperties(log, vo);
        return vo;
    }

    private Long nextId(List<Long> ids) {
        return ids.stream().filter(Objects::nonNull).max(Long::compareTo).map(id -> id + 1).orElse(1L);
    }
}
