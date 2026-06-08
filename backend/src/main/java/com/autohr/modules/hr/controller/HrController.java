package com.autohr.modules.hr.controller;

import com.autohr.common.api.ApiResponse;
import com.autohr.modules.auth.dto.SessionUserVO;
import com.autohr.modules.auth.service.AuthService;
import com.autohr.modules.auth.service.AuditLogService;
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
import com.autohr.modules.hr.service.HrService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/hr")
@RequiredArgsConstructor
public class HrController {

    private final HrService hrService;
    private final AuthService authService;
    private final AuditLogService auditLogService;

    @GetMapping("/dashboard")
    public ApiResponse<HrDashboardVO> getDashboard() {
        return ApiResponse.success(hrService.getDashboard());
    }

    @PostMapping("/departments")
    public ApiResponse<DepartmentVO> saveDepartment(Authentication authentication,
                                                    @Valid @RequestBody DepartmentSaveRequest request) {
        DepartmentVO saved = hrService.saveDepartment(request);
        SessionUserVO current = currentUser(authentication);
        auditLogService.log(current.getId(), current.getDisplayName(), current.getRoleCode(), "ADMIN", request.getId() == null ? "CREATE_DEPARTMENT" : "UPDATE_DEPARTMENT", "HR_DEPARTMENT", String.valueOf(saved.getId()), saved.getDepartmentName());
        return ApiResponse.success(saved);
    }

    @GetMapping("/departments")
    public ApiResponse<List<DepartmentVO>> listDepartments(@RequestParam(required = false) Long parentDepartmentId,
                                                           @RequestParam(required = false) Integer status,
                                                           @RequestParam(required = false) String keyword) {
        return ApiResponse.success(hrService.listDepartments(parentDepartmentId, status, keyword));
    }

    @GetMapping("/departments/tree")
    public ApiResponse<List<DepartmentTreeNodeVO>> getDepartmentTree() {
        return ApiResponse.success(hrService.getDepartmentTree());
    }

    @GetMapping("/departments/{id}")
    public ApiResponse<DepartmentDetailVO> getDepartmentDetail(@PathVariable Long id) {
        return ApiResponse.success(hrService.getDepartmentDetail(id));
    }

    @DeleteMapping("/departments/{id}")
    public ApiResponse<Void> deleteDepartment(Authentication authentication,
                                             @PathVariable Long id) {
        hrService.deleteDepartment(id);
        SessionUserVO current = currentUser(authentication);
        auditLogService.log(current.getId(), current.getDisplayName(), current.getRoleCode(), "ADMIN", "DELETE_DEPARTMENT", "HR_DEPARTMENT", String.valueOf(id), "删除部门");
        return ApiResponse.success("deleted", null);
    }

    @PostMapping("/employees")
    public ApiResponse<EmployeeVO> saveEmployee(Authentication authentication,
                                                @Valid @RequestBody EmployeeSaveRequest request) {
        EmployeeVO saved = hrService.saveEmployee(request);
        SessionUserVO current = currentUser(authentication);
        auditLogService.log(current.getId(), current.getDisplayName(), current.getRoleCode(), "ADMIN", request.getId() == null ? "CREATE_EMPLOYEE" : "UPDATE_EMPLOYEE", "HR_EMPLOYEE", String.valueOf(saved.getId()), saved.getFullName());
        return ApiResponse.success(saved);
    }

    @GetMapping("/employees")
    public ApiResponse<List<EmployeeVO>> listEmployees(@RequestParam(required = false) Long departmentId,
                                                       @RequestParam(required = false) Integer employmentStatus,
                                                       @RequestParam(required = false) String keyword) {
        return ApiResponse.success(hrService.listEmployees(departmentId, employmentStatus, keyword));
    }

    @GetMapping("/employees/{id}")
    public ApiResponse<EmployeeDetailVO> getEmployeeDetail(@PathVariable Long id) {
        return ApiResponse.success(hrService.getEmployeeDetail(id));
    }

    @DeleteMapping("/employees/{id}")
    public ApiResponse<Void> deleteEmployee(Authentication authentication,
                                           @PathVariable Long id) {
        hrService.deleteEmployee(id);
        SessionUserVO current = currentUser(authentication);
        auditLogService.log(current.getId(), current.getDisplayName(), current.getRoleCode(), "ADMIN", "DELETE_EMPLOYEE", "HR_EMPLOYEE", String.valueOf(id), "删除员工");
        return ApiResponse.success("deleted", null);
    }

    @PostMapping("/bindings")
    public ApiResponse<IntegrationBindingVO> saveBinding(Authentication authentication,
                                                         @Valid @RequestBody IntegrationBindingSaveRequest request) {
        IntegrationBindingVO saved = hrService.saveBinding(request);
        SessionUserVO current = currentUser(authentication);
        auditLogService.log(current.getId(), current.getDisplayName(), current.getRoleCode(), "ADMIN", request.getId() == null ? "CREATE_BINDING" : "UPDATE_BINDING", "HR_INTEGRATION_BINDING", String.valueOf(saved.getId()), saved.getModuleCode());
        return ApiResponse.success(saved);
    }

    @GetMapping("/bindings")
    public ApiResponse<List<IntegrationBindingVO>> listBindings(@RequestParam(required = false) String moduleCode,
                                                                @RequestParam(required = false) Long employeeId,
                                                                @RequestParam(required = false) Long departmentId) {
        return ApiResponse.success(hrService.listBindings(moduleCode, employeeId, departmentId));
    }

    private SessionUserVO currentUser(Authentication authentication) {
        return authService.loadUserByUsername(authentication.getName());
    }
}
