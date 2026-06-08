package com.autohr.modules.hr.service.impl;

import cn.hutool.core.util.StrUtil;
import com.autohr.common.exception.BusinessException;
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
import com.autohr.modules.hr.entity.Department;
import com.autohr.modules.hr.entity.Employee;
import com.autohr.modules.hr.entity.IntegrationBinding;
import com.autohr.modules.hr.enums.EmploymentStatus;
import com.autohr.modules.hr.mapper.DepartmentMapper;
import com.autohr.modules.hr.mapper.EmployeeMapper;
import com.autohr.modules.hr.mapper.IntegrationBindingMapper;
import com.autohr.modules.hr.service.HrService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HrServiceImpl implements HrService {

    private final DepartmentMapper departmentMapper;
    private final EmployeeMapper employeeMapper;
    private final IntegrationBindingMapper integrationBindingMapper;

    @Override
    @Transactional
    public DepartmentVO saveDepartment(DepartmentSaveRequest request) {
        validateDepartment(request.getParentDepartmentId(), request.getManagerEmployeeId(), request.getId());
        Department department = request.getId() == null ? new Department() : requireDepartment(request.getId());
        BeanUtils.copyProperties(request, department);
        if (StrUtil.isBlank(department.getDepartmentCode())) {
            department.setDepartmentCode(buildDepartmentCode(request.getDepartmentName()));
        }
        department.setSortOrder(Objects.requireNonNullElse(request.getSortOrder(), 0));
        department.setStatus(Objects.requireNonNullElse(request.getStatus(), 1));
        if (request.getId() == null) {
            department.setId(nextId(departmentMapper.selectList(null).stream().map(Department::getId).toList()));
            departmentMapper.insert(department);
        } else {
            departmentMapper.updateById(department);
        }
        return toDepartmentVO(requireDepartment(department.getId()), loadDepartmentMap(), loadEmployeeMap());
    }

    @Override
    public List<DepartmentVO> listDepartments(Long parentDepartmentId, Integer status, String keyword) {
        List<Department> departments = departmentMapper.selectList(new LambdaQueryWrapper<Department>()
                .eq(parentDepartmentId != null, Department::getParentDepartmentId, parentDepartmentId)
                .eq(status != null, Department::getStatus, status)
                .and(StrUtil.isNotBlank(keyword), q -> q.like(Department::getDepartmentName, keyword)
                        .or().like(Department::getDepartmentCode, keyword)
                        .or().like(Department::getDescription, keyword))
                .orderByAsc(Department::getSortOrder)
                .orderByAsc(Department::getId));
        Map<Long, Department> departmentMap = loadDepartmentMap();
        Map<Long, Employee> employeeMap = loadEmployeeMap();
        return departments.stream().map(item -> toDepartmentVO(item, departmentMap, employeeMap)).toList();
    }

    @Override
    public DepartmentDetailVO getDepartmentDetail(Long id) {
        Department department = requireDepartment(id);
        Map<Long, Department> departmentMap = loadDepartmentMap();
        Map<Long, Employee> employeeMap = loadEmployeeMap();
        List<Employee> directEmployees = employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getDepartmentId, id)
                .orderByAsc(Employee::getId));
        DepartmentDetailVO detailVO = new DepartmentDetailVO();
        detailVO.setDepartment(toDepartmentVO(department, departmentMap, employeeMap));
        detailVO.setEmployeeCount(directEmployees.size());
        detailVO.setDirectEmployees(directEmployees.stream().map(item -> toEmployeeVO(item, departmentMap, employeeMap)).toList());
        return detailVO;
    }

    @Override
    public List<DepartmentTreeNodeVO> getDepartmentTree() {
        List<Department> departments = listDepartmentEntities();
        Map<Long, Employee> employeeMap = loadEmployeeMap();
        Map<Long, Long> employeeCountMap = employeeMapper.selectList(null).stream()
                .collect(Collectors.groupingBy(Employee::getDepartmentId, Collectors.counting()));
        Map<Long, DepartmentTreeNodeVO> nodeMap = new LinkedHashMap<>();
        for (Department department : departments) {
            DepartmentTreeNodeVO node = new DepartmentTreeNodeVO();
            BeanUtils.copyProperties(department, node);
            Employee manager = employeeMap.get(department.getManagerEmployeeId());
            if (manager != null) {
                node.setManagerEmployeeName(manager.getFullName());
            }
            node.setEmployeeCount(employeeCountMap.getOrDefault(department.getId(), 0L).intValue());
            nodeMap.put(node.getId(), node);
        }
        List<DepartmentTreeNodeVO> roots = new ArrayList<>();
        for (DepartmentTreeNodeVO node : nodeMap.values()) {
            if (node.getParentDepartmentId() == null) {
                roots.add(node);
                continue;
            }
            DepartmentTreeNodeVO parent = nodeMap.get(node.getParentDepartmentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    @Override
    @Transactional
    public void deleteDepartment(Long id) {
        requireDepartment(id);
        Long employeeCount = employeeMapper.selectCount(new LambdaQueryWrapper<Employee>().eq(Employee::getDepartmentId, id));
        if (employeeCount > 0) {
            throw new BusinessException("该部门下仍有员工，不能删除");
        }
        Long childCount = departmentMapper.selectCount(new LambdaQueryWrapper<Department>().eq(Department::getParentDepartmentId, id));
        if (childCount > 0) {
            throw new BusinessException("该部门下仍有子部门，不能删除");
        }
        integrationBindingMapper.delete(new LambdaQueryWrapper<IntegrationBinding>().eq(IntegrationBinding::getDepartmentId, id));
        departmentMapper.deleteById(id);
    }

    @Override
    @Transactional
    public EmployeeVO saveEmployee(EmployeeSaveRequest request) {
        Long resolvedId = resolveEmployeeId(request);
        validateEmployee(request.getDepartmentId(), request.getManagerEmployeeId(), resolvedId);
        validateEmployeeUnique(request, resolvedId);
        Employee employee = resolvedId == null ? new Employee() : requireEmployee(resolvedId);
        BeanUtils.copyProperties(request, employee);
        if (StrUtil.isBlank(employee.getEmployeeCode())) {
            employee.setEmployeeCode(buildEmployeeCode());
        }
        employee.setHireDate(Objects.requireNonNullElse(request.getHireDate(), LocalDate.now()));
        employee.setEmploymentStatus(Objects.requireNonNullElse(request.getEmploymentStatus(), EmploymentStatus.ACTIVE.getCode()));
        if (resolvedId == null) {
            employee.setId(nextId(employeeMapper.selectList(null).stream().map(Employee::getId).toList()));
            employeeMapper.insert(employee);
        } else {
            employeeMapper.updateById(employee);
        }
        return toEmployeeVO(requireEmployee(employee.getId()), loadDepartmentMap(), loadEmployeeMap());
    }

    @Override
    public List<EmployeeVO> listEmployees(Long departmentId, Integer employmentStatus, String keyword) {
        List<Employee> employees = employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                .eq(departmentId != null, Employee::getDepartmentId, departmentId)
                .eq(employmentStatus != null, Employee::getEmploymentStatus, employmentStatus)
                .and(StrUtil.isNotBlank(keyword), q -> q.like(Employee::getFullName, keyword)
                        .or().like(Employee::getEmployeeCode, keyword)
                        .or().like(Employee::getMobilePhone, keyword)
                        .or().like(Employee::getPositionName, keyword))
                .orderByDesc(Employee::getId));
        Map<Long, Department> departmentMap = loadDepartmentMap();
        Map<Long, Employee> employeeMap = loadEmployeeMap();
        return employees.stream().map(item -> toEmployeeVO(item, departmentMap, employeeMap)).toList();
    }

    @Override
    public EmployeeDetailVO getEmployeeDetail(Long id) {
        Employee employee = requireEmployee(id);
        Map<Long, Department> departmentMap = loadDepartmentMap();
        Map<Long, Employee> employeeMap = loadEmployeeMap();
        EmployeeDetailVO detailVO = new EmployeeDetailVO();
        detailVO.setEmployee(toEmployeeVO(employee, departmentMap, employeeMap));
        Department department = departmentMap.get(employee.getDepartmentId());
        if (department != null) {
            detailVO.setDepartment(toDepartmentVO(department, departmentMap, employeeMap));
        }
        Employee manager = employeeMap.get(employee.getManagerEmployeeId());
        if (manager != null) {
            detailVO.setManager(toEmployeeVO(manager, departmentMap, employeeMap));
        }
        detailVO.setBindings(listBindings(null, id, null));
        return detailVO;
    }

    @Override
    @Transactional
    public void deleteEmployee(Long id) {
        requireEmployee(id);
        Long managerCount = employeeMapper.selectCount(new LambdaQueryWrapper<Employee>().eq(Employee::getManagerEmployeeId, id));
        if (managerCount > 0) {
            throw new BusinessException("该员工仍被作为直属上级引用，不能删除");
        }
        integrationBindingMapper.delete(new LambdaQueryWrapper<IntegrationBinding>().eq(IntegrationBinding::getEmployeeId, id));
        employeeMapper.deleteById(id);
    }

    @Override
    @Transactional
    public IntegrationBindingVO saveBinding(IntegrationBindingSaveRequest request) {
        if (request.getEmployeeId() == null && request.getDepartmentId() == null) {
            throw new BusinessException("employeeId 与 departmentId 不能同时为空");
        }
        if (request.getEmployeeId() != null) {
            requireEmployee(request.getEmployeeId());
        }
        if (request.getDepartmentId() != null) {
            requireDepartment(request.getDepartmentId());
        }
        IntegrationBinding binding = request.getId() == null ? new IntegrationBinding() : requireBinding(request.getId());
        BeanUtils.copyProperties(request, binding);
        binding.setBindingStatus(StrUtil.blankToDefault(request.getBindingStatus(), "ACTIVE"));
        if (request.getId() == null) {
            binding.setId(nextId(integrationBindingMapper.selectList(null).stream().map(IntegrationBinding::getId).toList()));
            integrationBindingMapper.insert(binding);
        } else {
            integrationBindingMapper.updateById(binding);
        }
        return toBindingVO(requireBinding(binding.getId()), loadDepartmentMap(), loadEmployeeMap());
    }

    @Override
    public List<IntegrationBindingVO> listBindings(String moduleCode, Long employeeId, Long departmentId) {
        List<IntegrationBinding> bindings = integrationBindingMapper.selectList(new LambdaQueryWrapper<IntegrationBinding>()
                .eq(StrUtil.isNotBlank(moduleCode), IntegrationBinding::getModuleCode, moduleCode)
                .eq(employeeId != null, IntegrationBinding::getEmployeeId, employeeId)
                .eq(departmentId != null, IntegrationBinding::getDepartmentId, departmentId)
                .orderByDesc(IntegrationBinding::getId));
        Map<Long, Department> departmentMap = loadDepartmentMap();
        Map<Long, Employee> employeeMap = loadEmployeeMap();
        return bindings.stream().map(item -> toBindingVO(item, departmentMap, employeeMap)).toList();
    }

    @Override
    public HrDashboardVO getDashboard() {
        HrDashboardVO dashboard = new HrDashboardVO();
        dashboard.setDepartmentCount(departmentMapper.selectCount(null));
        dashboard.setEmployeeCount(employeeMapper.selectCount(null));
        dashboard.setActiveEmployeeCount(employeeMapper.selectCount(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getEmploymentStatus, EmploymentStatus.ACTIVE.getCode())));
        dashboard.setPendingOnboardingCount(employeeMapper.selectCount(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getEmploymentStatus, EmploymentStatus.PENDING_ONBOARDING.getCode())));
        dashboard.setResignedCount(employeeMapper.selectCount(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getEmploymentStatus, EmploymentStatus.RESIGNED.getCode())));
        dashboard.setRecruitmentBindingCount(integrationBindingMapper.selectCount(new LambdaQueryWrapper<IntegrationBinding>()
                .eq(IntegrationBinding::getModuleCode, "RECRUITMENT")));
        dashboard.setPerformanceBindingCount(integrationBindingMapper.selectCount(new LambdaQueryWrapper<IntegrationBinding>()
                .eq(IntegrationBinding::getModuleCode, "PERFORMANCE")));
        return dashboard;
    }

    private void validateDepartment(Long parentDepartmentId, Long managerEmployeeId, Long currentId) {
        if (parentDepartmentId != null) {
            Department parent = requireDepartment(parentDepartmentId);
            if (currentId != null && parent.getId().equals(currentId)) {
                throw new BusinessException("部门不能将自己设为上级部门");
            }
        }
        if (managerEmployeeId != null) {
            requireEmployee(managerEmployeeId);
        }
    }

    private void validateEmployee(Long departmentId, Long managerEmployeeId, Long currentId) {
        requireDepartment(departmentId);
        if (managerEmployeeId != null) {
            Employee manager = requireEmployee(managerEmployeeId);
            if (currentId != null && manager.getId().equals(currentId)) {
                throw new BusinessException("员工不能将自己设为直属上级");
            }
        }
    }

    private void validateEmployeeUnique(EmployeeSaveRequest request, Long currentId) {
        ensureUnique(Employee::getIdCardNo, request.getIdCardNo(), currentId, "身份证号已存在");
        ensureUnique(Employee::getMobilePhone, request.getMobilePhone(), currentId, "手机号已存在");
        if (StrUtil.isNotBlank(request.getEmployeeCode())) {
            ensureUnique(Employee::getEmployeeCode, request.getEmployeeCode(), currentId, "员工编码已存在");
        }
    }

    private Long resolveEmployeeId(EmployeeSaveRequest request) {
        if (request.getId() != null) {
            return request.getId();
        }
        if (StrUtil.isBlank(request.getEmployeeCode())) {
            return null;
        }
        Employee existing = employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getEmployeeCode, request.getEmployeeCode())
                .last("LIMIT 1"));
        return existing == null ? null : existing.getId();
    }

    private void ensureUnique(SFunction<Employee, String> column, String value, Long currentId, String message) {
        LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<Employee>().eq(column, value);
        if (currentId != null) {
            wrapper.ne(Employee::getId, currentId);
        }
        if (employeeMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(message);
        }
    }

    private Department requireDepartment(Long id) {
        Department department = departmentMapper.selectById(id);
        if (department == null) {
            throw new BusinessException("部门不存在: " + id);
        }
        return department;
    }

    private Employee requireEmployee(Long id) {
        Employee employee = employeeMapper.selectById(id);
        if (employee == null) {
            throw new BusinessException("员工不存在: " + id);
        }
        return employee;
    }

    private IntegrationBinding requireBinding(Long id) {
        IntegrationBinding binding = integrationBindingMapper.selectById(id);
        if (binding == null) {
            throw new BusinessException("绑定记录不存在: " + id);
        }
        return binding;
    }

    private List<Department> listDepartmentEntities() {
        return departmentMapper.selectList(new LambdaQueryWrapper<Department>()
                .orderByAsc(Department::getSortOrder)
                .orderByAsc(Department::getId));
    }

    private Map<Long, Department> loadDepartmentMap() {
        return departmentMapper.selectList(null).stream().collect(Collectors.toMap(Department::getId, Function.identity(), (a, b) -> a));
    }

    private Map<Long, Employee> loadEmployeeMap() {
        return employeeMapper.selectList(null).stream().collect(Collectors.toMap(Employee::getId, Function.identity(), (a, b) -> a));
    }

    private DepartmentVO toDepartmentVO(Department department, Map<Long, Department> departmentMap, Map<Long, Employee> employeeMap) {
        DepartmentVO vo = new DepartmentVO();
        BeanUtils.copyProperties(department, vo);
        Department parent = departmentMap.get(department.getParentDepartmentId());
        if (parent != null) {
            vo.setParentDepartmentName(parent.getDepartmentName());
        }
        Employee manager = employeeMap.get(department.getManagerEmployeeId());
        if (manager != null) {
            vo.setManagerEmployeeName(manager.getFullName());
        }
        return vo;
    }

    private EmployeeVO toEmployeeVO(Employee employee, Map<Long, Department> departmentMap, Map<Long, Employee> employeeMap) {
        EmployeeVO vo = new EmployeeVO();
        BeanUtils.copyProperties(employee, vo);
        Department department = departmentMap.get(employee.getDepartmentId());
        if (department != null) {
            vo.setDepartmentName(department.getDepartmentName());
        }
        Employee manager = employeeMap.get(employee.getManagerEmployeeId());
        if (manager != null) {
            vo.setManagerEmployeeName(manager.getFullName());
        }
        return vo;
    }

    private IntegrationBindingVO toBindingVO(IntegrationBinding binding, Map<Long, Department> departmentMap, Map<Long, Employee> employeeMap) {
        IntegrationBindingVO vo = new IntegrationBindingVO();
        BeanUtils.copyProperties(binding, vo);
        Department department = departmentMap.get(binding.getDepartmentId());
        if (department != null) {
            vo.setDepartmentName(department.getDepartmentName());
        }
        Employee employee = employeeMap.get(binding.getEmployeeId());
        if (employee != null) {
            vo.setEmployeeName(employee.getFullName());
        }
        return vo;
    }

    private String buildDepartmentCode(String departmentName) {
        return "DEPT-" + Math.abs(departmentName.hashCode());
    }

    private String buildEmployeeCode() {
        long next = employeeMapper.selectCount(null) + 1;
        return String.format("EMP%05d", next);
    }

    private Long nextId(List<Long> ids) {
        return ids.stream()
                .filter(Objects::nonNull)
                .max(Long::compareTo)
                .map(id -> id + 1)
                .orElse(1L);
    }
}
