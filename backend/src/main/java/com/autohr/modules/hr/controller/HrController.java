package com.autohr.modules.hr.controller;

import com.autohr.common.api.ApiResponse;
import com.autohr.modules.hr.dto.DepartmentSaveRequest;
import com.autohr.modules.hr.dto.DepartmentVO;
import com.autohr.modules.hr.dto.EmployeeSaveRequest;
import com.autohr.modules.hr.dto.EmployeeVO;
import com.autohr.modules.hr.dto.IntegrationBindingSaveRequest;
import com.autohr.modules.hr.dto.IntegrationBindingVO;
import com.autohr.modules.hr.service.HrService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

    @PostMapping("/departments")
    public ApiResponse<DepartmentVO> saveDepartment(@Valid @RequestBody DepartmentSaveRequest request) {
        return ApiResponse.success(hrService.saveDepartment(request));
    }

    @GetMapping("/departments")
    public ApiResponse<List<DepartmentVO>> listDepartments() {
        return ApiResponse.success(hrService.listDepartments());
    }

    @DeleteMapping("/departments/{id}")
    public ApiResponse<Void> deleteDepartment(@PathVariable Long id) {
        hrService.deleteDepartment(id);
        return ApiResponse.success("deleted", null);
    }

    @PostMapping("/employees")
    public ApiResponse<EmployeeVO> saveEmployee(@Valid @RequestBody EmployeeSaveRequest request) {
        return ApiResponse.success(hrService.saveEmployee(request));
    }

    @GetMapping("/employees")
    public ApiResponse<List<EmployeeVO>> listEmployees(@RequestParam(required = false) Long departmentId,
                                                       @RequestParam(required = false) Integer employmentStatus,
                                                       @RequestParam(required = false) String keyword) {
        return ApiResponse.success(hrService.listEmployees(departmentId, employmentStatus, keyword));
    }

    @DeleteMapping("/employees/{id}")
    public ApiResponse<Void> deleteEmployee(@PathVariable Long id) {
        hrService.deleteEmployee(id);
        return ApiResponse.success("deleted", null);
    }

    @PostMapping("/bindings")
    public ApiResponse<IntegrationBindingVO> saveBinding(@Valid @RequestBody IntegrationBindingSaveRequest request) {
        return ApiResponse.success(hrService.saveBinding(request));
    }

    @GetMapping("/bindings")
    public ApiResponse<List<IntegrationBindingVO>> listBindings(@RequestParam(required = false) String moduleCode,
                                                                @RequestParam(required = false) Long employeeId,
                                                                @RequestParam(required = false) Long departmentId) {
        return ApiResponse.success(hrService.listBindings(moduleCode, employeeId, departmentId));
    }
}
