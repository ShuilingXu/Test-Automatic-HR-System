package com.autohr.modules.hr.dto;

import lombok.Data;

@Data
public class HrDashboardVO {

    private Long departmentCount;
    private Long employeeCount;
    private Long activeEmployeeCount;
    private Long pendingOnboardingCount;
    private Long resignedCount;
    private Long recruitmentBindingCount;
    private Long performanceBindingCount;
}
