package com.autohr.modules.hr.service.impl;

import com.autohr.common.exception.BusinessException;
import com.autohr.modules.hr.dto.HrStatisticsVO;
import com.autohr.modules.hr.service.HrStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class HrStatisticsServiceImpl implements HrStatisticsService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private final JdbcTemplate jdbc;

    @Override
    public HrStatisticsVO statistics(String month) {
        YearMonth selected = parseMonth(month);
        YearMonth previousMonth = selected.minusMonths(1);
        HrStatisticsVO result = new HrStatisticsVO();
        result.setSalaryMonth(selected.toString());

        List<Map<String, Object>> employees = jdbc.queryForList(
                "SELECT id,employee_code,full_name,hire_date,base_salary,department_id,job_id "
                        + "FROM hr_employee WHERE employment_status=1 AND salary_confirmed=1");
        Map<Long, BigDecimal> currentGross = grossByEmployee(employees, selected);
        Map<Long, BigDecimal> previousGross = grossByEmployee(employees, previousMonth);
        BigDecimal currentTotal = sum(currentGross);
        BigDecimal previousTotal = sum(previousGross);

        for (Map<String, Object> employee : employees) {
            Long employeeId = number(employee.get("id"));
            BigDecimal gross = currentGross.get(employeeId);
            if (gross == null) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("employeeId", employeeId);
            item.put("employeeCode", employee.get("employee_code"));
            item.put("employeeName", employee.get("full_name"));
            item.put("grossIncome", gross);
            item.put("monthOverMonth", percentage(gross, previousGross.get(employeeId)));
            LocalDate hireDate = date(employee.get("hire_date"));
            BigDecimal entrySalary = entrySalary(employeeId, employee.get("base_salary"));
            BigDecimal currentBaseSalary = effectiveBaseSalary(employeeId, selected, employee.get("base_salary"));
            item.put("newEmployeeGrowth", hireDate != null && monthsSinceHire(hireDate, selected) >= 0
                    && monthsSinceHire(hireDate, selected) < 3 ? percentage(currentBaseSalary, entrySalary) : null);
            result.getSalary().getEmployees().add(item);
        }
        result.getSalary().setGrossTotal(money(currentTotal));
        result.getSalary().setAverageGross(currentGross.isEmpty() ? ZERO
                : money(currentTotal.divide(BigDecimal.valueOf(currentGross.size()), 8, RoundingMode.HALF_UP)));
        result.getSalary().setMonthOverMonth(percentage(currentTotal, previousTotal));

        populateRecruitment(result, selected, employees);
        populateDismissals(result, selected);
        populateDepartments(result, selected, employees, currentGross);
        return result;
    }

    private Map<Long, BigDecimal> grossByEmployee(List<Map<String, Object>> employees, YearMonth month) {
        Map<Long, BigDecimal> gross = new HashMap<>();
        for (Map<String, Object> employee : employees) {
            LocalDate hireDate = date(employee.get("hire_date"));
            if (hireDate != null && YearMonth.from(hireDate).isAfter(month)) continue;
            Long employeeId = number(employee.get("id"));
            BigDecimal base = effectiveBaseSalary(employeeId, month, employee.get("base_salary"));
            if (base == null) continue;
            BigDecimal performance = scalar(
                    "SELECT amount FROM hr_performance_month WHERE employee_id=? AND salary_month=?", employeeId, month.toString());
            BigDecimal overtime = scalar(
                    "SELECT overtime_pay FROM hr_overtime_month WHERE employee_id=? AND salary_month=?", employeeId, month.toString());
            gross.put(employeeId, money(base.add(performance).add(overtime)));
        }
        return gross;
    }

    private BigDecimal effectiveBaseSalary(Long employeeId, YearMonth month, Object fallback) {
        List<Map<String, Object>> history = jdbc.queryForList(
                "SELECT effective_month,base_salary_after FROM hr_salary_history WHERE employee_id=? ORDER BY effective_month DESC,id DESC",
                employeeId);
        for (Map<String, Object> row : history) {
            if (row.get("effective_month").toString().compareTo(month.toString()) <= 0) {
                return money(row.get("base_salary_after"));
            }
        }
        // Confirmed records created before salary history was introduced keep their current base as a compatibility fallback.
        return history.isEmpty() ? money(fallback) : null;
    }

    private BigDecimal entrySalary(Long employeeId, Object fallback) {
        List<Map<String, Object>> history = jdbc.queryForList(
                "SELECT base_salary_after FROM hr_salary_history WHERE employee_id=? ORDER BY effective_month,id", employeeId);
        return history.isEmpty() ? money(fallback) : money(history.get(0).get("base_salary_after"));
    }

    private void populateRecruitment(HrStatisticsVO result, YearMonth month, List<Map<String, Object>> employees) {
        LocalDate closeDateCutoff = month.equals(YearMonth.now(BUSINESS_ZONE)) ? LocalDate.now(BUSINESS_ZONE) : month.atEndOfMonth();
        result.getRecruitment().setOpenJobCount(jdbc.queryForObject(
                "SELECT COUNT(*) FROM recruitment_job WHERE status=1 AND (close_date IS NULL OR close_date>=?)",
                Long.class, closeDateCutoff));
        result.getRecruitment().setCandidateCount(jdbc.queryForObject("SELECT COUNT(*) FROM recruitment_candidate", Long.class));
        result.getRecruitment().setInterviewingCount(jdbc.queryForObject(
                "SELECT COUNT(*) FROM interview_process WHERE overall_status='IN_PROGRESS'", Long.class));
        result.getRecruitment().setPassedCount(jdbc.queryForObject(
                "SELECT COUNT(*) FROM interview_process WHERE overall_status='PASSED'", Long.class));

        List<Map<String, Object>> jobs = jdbc.queryForList("SELECT id,job_code,job_title FROM recruitment_job ORDER BY id");
        for (Map<String, Object> job : jobs) {
            Long jobId = number(job.get("id"));
            BigDecimal total = ZERO;
            long count = 0;
            for (Map<String, Object> employee : employees) {
                if (!Objects.equals(numberOrNull(employee.get("job_id")), jobId)) continue;
                BigDecimal salary = effectiveBaseSalary(number(employee.get("id")), month, employee.get("base_salary"));
                if (salary != null) {
                    total = total.add(salary);
                    count++;
                }
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("jobId", jobId);
            item.put("jobCode", job.get("job_code"));
            item.put("jobTitle", job.get("job_title"));
            item.put("averageBaseSalary", count == 0 ? ZERO
                    : money(total.divide(BigDecimal.valueOf(count), 8, RoundingMode.HALF_UP)));
            result.getRecruitment().getJobAverageSalaries().add(item);
        }
    }

    private void populateDismissals(HrStatisticsVO result, YearMonth month) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT e.dismissal_reason,p.gross_income FROM hr_employee e "
                        + "LEFT JOIN hr_payroll_month p ON p.employee_id=e.id AND p.salary_month=? "
                        + "WHERE e.employment_status=3 AND e.dismissal_date>=? AND e.dismissal_date<=?",
                month.toString(), month.atDay(1), month.atEndOfMonth());
        result.getDismissal().setCount(rows.size());
        BigDecimal total = ZERO;
        long payrollCount = 0;
        Map<String, Long> reasons = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            if (row.get("gross_income") != null) {
                total = total.add(money(row.get("gross_income")));
                payrollCount++;
            }
            reasons.merge(Objects.toString(row.get("dismissal_reason"), "其他"), 1L, Long::sum);
        }
        result.getDismissal().setAverageGross(payrollCount == 0 ? ZERO
                : money(total.divide(BigDecimal.valueOf(payrollCount), 8, RoundingMode.HALF_UP)));
        reasons.forEach((name, count) -> result.getDismissal().getReasons().add(
                new LinkedHashMap<>(Map.of("name", name, "value", count))));
    }

    private void populateDepartments(HrStatisticsVO result, YearMonth month,
                                     List<Map<String, Object>> employees, Map<Long, BigDecimal> currentGross) {
        List<Map<String, Object>> departments = jdbc.queryForList("SELECT id,department_name FROM hr_department");
        long employeeTotal = 0;
        long hireTotal = 0;
        long dismissalTotal = 0;
        for (Map<String, Object> department : departments) {
            Long departmentId = number(department.get("id"));
            long employeeCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM hr_employee WHERE department_id=? AND employment_status=1", Long.class, departmentId);
            long hireCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM hr_employee WHERE department_id=? AND hire_date>=? AND hire_date<=?",
                    Long.class, departmentId, month.atDay(1), month.atEndOfMonth());
            long dismissalCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM hr_employee WHERE department_id=? AND dismissal_date>=? AND dismissal_date<=?",
                    Long.class, departmentId, month.atDay(1), month.atEndOfMonth());
            employeeTotal += employeeCount;
            hireTotal += hireCount;
            dismissalTotal += dismissalCount;

            BigDecimal salaryTotal = ZERO;
            long salaryCount = 0;
            for (Map<String, Object> employee : employees) {
                if (!Objects.equals(numberOrNull(employee.get("department_id")), departmentId)) continue;
                BigDecimal gross = currentGross.get(number(employee.get("id")));
                if (gross != null) {
                    salaryTotal = salaryTotal.add(gross);
                    salaryCount++;
                }
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("departmentId", departmentId);
            item.put("departmentName", department.get("department_name"));
            item.put("averageGross", salaryCount == 0 ? ZERO
                    : money(salaryTotal.divide(BigDecimal.valueOf(salaryCount), 8, RoundingMode.HALF_UP)));
            result.getDepartment().getAverageSalaries().add(item);
        }
        int count = departments.size();
        result.getDepartment().setAverageEmployeeCount(average(employeeTotal, count));
        result.getDepartment().setAverageHireCount(average(hireTotal, count));
        result.getDepartment().setAverageDismissalCount(average(dismissalTotal, count));
    }

    private BigDecimal scalar(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        return rows.isEmpty() ? ZERO : money(rows.get(0).values().iterator().next());
    }

    private BigDecimal sum(Map<Long, BigDecimal> values) {
        return values.values().stream().reduce(ZERO, BigDecimal::add);
    }

    private BigDecimal average(long total, int count) {
        return count == 0 ? ZERO : money(BigDecimal.valueOf(total).divide(BigDecimal.valueOf(count), 8, RoundingMode.HALF_UP));
    }

    private BigDecimal percentage(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.signum() == 0) return null;
        return money(current.subtract(previous).multiply(BigDecimal.valueOf(100))
                .divide(previous, 8, RoundingMode.HALF_UP));
    }

    private BigDecimal money(Object value) {
        return value == null ? ZERO : new BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP);
    }

    private Long number(Object value) { return ((Number) value).longValue(); }
    private Long numberOrNull(Object value) { return value == null ? null : number(value); }
    private LocalDate date(Object value) {
        if (value instanceof java.sql.Date sqlDate) return sqlDate.toLocalDate();
        if (value instanceof LocalDate localDate) return localDate;
        return value == null ? null : LocalDate.parse(value.toString());
    }
    private int monthsSinceHire(LocalDate hireDate, YearMonth month) {
        return (month.getYear() - hireDate.getYear()) * 12 + month.getMonthValue() - hireDate.getMonthValue();
    }
    private YearMonth parseMonth(String value) {
        try { return YearMonth.parse(value); }
        catch (Exception ignored) { throw new BusinessException("Month must be yyyy-MM"); }
    }
}
