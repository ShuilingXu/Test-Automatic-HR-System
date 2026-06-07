package com.autohr.modules.hr.dto;

import lombok.Data;

import java.util.List;

@Data
public class EmployeeDetailVO {

    private EmployeeVO employee;
    private DepartmentVO department;
    private EmployeeVO manager;
    private List<IntegrationBindingVO> bindings;
}
