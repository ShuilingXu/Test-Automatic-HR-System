package com.autohr.modules.hr.dto;

import lombok.Data;

import java.util.List;

@Data
public class DepartmentDetailVO {

    private DepartmentVO department;
    private Integer employeeCount;
    private List<EmployeeVO> directEmployees;
}
