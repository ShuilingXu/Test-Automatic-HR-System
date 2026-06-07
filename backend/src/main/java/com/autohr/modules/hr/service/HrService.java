package com.autohr.modules.hr.service;

import com.autohr.modules.hr.dto.DepartmentDetailVO;
import com.autohr.modules.hr.dto.DepartmentSaveRequest;
import com.autohr.modules.hr.dto.DepartmentTreeNodeVO;
import com.autohr.modules.hr.dto.DepartmentVO;
import com.autohr.modules.hr.dto.EmployeeDetailVO;
import com.autohr.modules.hr.dto.EmployeeSaveRequest;
import com.autohr.modules.hr.dto.EmployeeVO;
import com.autohr.modules.hr.dto.HrDashboardVO;
import com.autohr.modules.hr.dto.IntegrationBindingSaveRequest;
import com.autohr.modules.hr.dto.IntegrationBindingVO;

import java.util.List;

public interface HrService {

    DepartmentVO saveDepartment(DepartmentSaveRequest request);

    List<DepartmentVO> listDepartments();

    DepartmentDetailVO getDepartmentDetail(Long id);

    List<DepartmentTreeNodeVO> getDepartmentTree();

    void deleteDepartment(Long id);

    EmployeeVO saveEmployee(EmployeeSaveRequest request);

    List<EmployeeVO> listEmployees(Long departmentId, Integer employmentStatus, String keyword);

    EmployeeDetailVO getEmployeeDetail(Long id);

    void deleteEmployee(Long id);

    IntegrationBindingVO saveBinding(IntegrationBindingSaveRequest request);

    List<IntegrationBindingVO> listBindings(String moduleCode, Long employeeId, Long departmentId);

    HrDashboardVO getDashboard();
}
