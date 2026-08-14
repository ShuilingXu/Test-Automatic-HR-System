package com.autohr.modules.hr.service.impl;

import com.autohr.common.exception.BusinessException;
import com.autohr.modules.hr.dto.EmployeeSaveRequest;
import com.autohr.modules.hr.dto.EmployeeVO;
import com.autohr.modules.hr.entity.Department;
import com.autohr.modules.hr.entity.Employee;
import com.autohr.modules.hr.mapper.DepartmentMapper;
import com.autohr.modules.hr.mapper.EmployeeMapper;
import com.autohr.modules.hr.mapper.IntegrationBindingMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HrServiceImplTest {

    @Mock
    private DepartmentMapper departmentMapper;

    @Mock
    private EmployeeMapper employeeMapper;

    @Mock
    private IntegrationBindingMapper integrationBindingMapper;

    @InjectMocks
    private HrServiceImpl hrService;

    @Test
    void createsAnEmployeeWithoutLookingUpEmployeeCode() {
        Department department = department(7L);
        Employee inserted = new Employee();
        inserted.setId(42L);
        inserted.setDepartmentId(department.getId());
        inserted.setEmployeeCode("EMP-42");
        inserted.setFullName("Test User");
        when(departmentMapper.selectById(department.getId())).thenReturn(department);
        when(employeeMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            Employee employee = invocation.getArgument(0);
            employee.setId(inserted.getId());
            return 1;
        }).when(employeeMapper).insert(any(Employee.class));
        when(employeeMapper.selectById(inserted.getId())).thenReturn(inserted);
        when(departmentMapper.selectList(ArgumentMatchers.any())).thenReturn(List.of(department));
        when(employeeMapper.selectList(ArgumentMatchers.any())).thenReturn(List.of(inserted));

        EmployeeSaveRequest request = employeeRequest(null);
        request.setEmployeeCode("EMP-42");
        EmployeeVO saved = hrService.saveEmployee(request);

        assertEquals(inserted.getId(), saved.getId());
        verify(employeeMapper).insert(any(Employee.class));
        verify(employeeMapper, never()).selectOne(any());
        verify(employeeMapper, never()).updateById(any(Employee.class));
    }

    @Test
    void rejectsAnUpdateForAnUnknownExplicitId() {
        Department department = department(7L);
        when(departmentMapper.selectById(department.getId())).thenReturn(department);
        when(employeeMapper.selectCount(any())).thenReturn(0L);

        assertThrows(BusinessException.class, () -> hrService.saveEmployee(employeeRequest(99L)));

        verify(employeeMapper, never()).insert(any(Employee.class));
        verify(employeeMapper, never()).updateById(any(Employee.class));
    }

    private Department department(Long id) {
        Department department = new Department();
        department.setId(id);
        department.setDepartmentCode("TEST");
        return department;
    }

    private EmployeeSaveRequest employeeRequest(Long id) {
        EmployeeSaveRequest request = new EmployeeSaveRequest();
        request.setId(id);
        request.setFullName("Test User");
        request.setIdCardNo("110101199001011234");
        request.setMobilePhone("13800138000");
        request.setRecruitmentMajor("Engineering");
        request.setPositionName("Engineer");
        request.setDepartmentId(7L);
        request.setBankAccountNo("6222020000000000");
        request.setBankName("Test Bank");
        return request;
    }
}
