package com.autohr.modules.hr.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class DepartmentTreeNodeVO {

    private Long id;
    private String departmentName;
    private String departmentCode;
    private Long parentDepartmentId;
    private Long managerEmployeeId;
    private String managerEmployeeName;
    private Integer employeeCount;
    private Integer sortOrder;
    private Integer status;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<DepartmentTreeNodeVO> children = new ArrayList<>();
}
