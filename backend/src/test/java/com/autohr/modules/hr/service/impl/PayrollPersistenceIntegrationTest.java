package com.autohr.modules.hr.service.impl;

import com.autohr.config.database.ActiveDatabase;
import com.autohr.config.database.AppMigrationProperties;
import com.autohr.config.database.DatabaseMigrationRunner;
import com.autohr.config.database.DatabaseType;
import com.autohr.modules.hr.dto.PayrollGenerateRequest;
import com.autohr.modules.hr.dto.PayrollVO;
import com.autohr.common.exception.BusinessException;
import jakarta.validation.Validation;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PayrollPersistenceIntegrationTest {
    @TempDir Path tempDirectory;

    @Test
    void currentTaxSubtractsPersistedPriorWithholding() throws Exception {
        String url = "jdbc:sqlite:" + tempDirectory.resolve("payroll.db").toString().replace('\\', '/');
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url);
        new DatabaseMigrationRunner(dataSource, new ActiveDatabase(DatabaseType.SQLITE, url, "", "", false), new AppMigrationProperties()).run();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO hr_department (department_code,department_name,description) VALUES ('D1','研发部','研发')");
        jdbc.update("INSERT INTO recruitment_job (job_code,job_title,department_name,requirements,responsibilities,publish_date) VALUES ('J1','工程师','研发部','要求','职责','2026-01-01')");
        jdbc.update("INSERT INTO hr_employee (employee_code,full_name,id_card_no,mobile_phone,recruitment_major,position_name,department_id,bank_account_no,bank_name,hire_date,employment_status,job_id,base_salary,salary_confirmed) VALUES ('E001','测试员工','110101199001010011','13800000001','计算机','工程师',1,'6222000000000001','测试银行','2026-01-01',1,1,10000,1)");
        jdbc.update("INSERT INTO hr_salary_history (employee_id,effective_month,base_salary_before,base_salary_after) VALUES (1,'2026-01',0,10000)");
        jdbc.update("INSERT INTO hr_payroll_month (employee_id,salary_month,base_salary,performance,overtime_hours,overtime_pay,gross_income,social_insurance_total,special_deduction_total,taxable_income_month,cumulative_income,cumulative_deduction_base,cumulative_social_insurance,cumulative_special_deduction,cumulative_taxable_income,cumulative_tax_withheld,current_tax_withheld,net_pay,locked) VALUES (1,'2026-01',10000,0,0,0,10000,0,0,5000,10000,5000,0,0,5000,150,100,9900,0)");
        PayrollServiceImpl service = new PayrollServiceImpl(jdbc, Validation.buildDefaultValidatorFactory().getValidator());
        PayrollGenerateRequest request = new PayrollGenerateRequest();
        request.setEmployeeId(1L);
        request.setSalaryMonth("2026-02");

        List<PayrollVO> payrolls = service.generate(request);

        assertEquals(new BigDecimal("300.00"), payrolls.get(0).getCumulativeTaxWithheld());
        assertEquals(new BigDecimal("200.00"), payrolls.get(0).getCurrentTaxWithheld());
        assertEquals(new BigDecimal("9800.00"), payrolls.get(0).getNetPay());
        assertEquals(new BigDecimal("20000.00"), payrolls.get(0).getCumulativeIncome());
        assertEquals(new BigDecimal("10000.00"), payrolls.get(0).getCumulativeDeductionBase());
    }

    @Test
    void singleMonthPayrollMatchesTheManualGrossTaxAndNetCalculation() throws Exception {
        JdbcTemplate jdbc = migratedDatabase("single-month.db");
        insertDepartmentAndJob(jdbc);
        insertEmployee(jdbc, 1, "E001", "2026-08-01", 1, null, "20000.00");
        jdbc.update("INSERT INTO hr_salary_history (employee_id,effective_month,base_salary_before,base_salary_after) VALUES (1,'2026-08',0,20000)");
        jdbc.update("INSERT INTO hr_performance_month (employee_id,salary_month,amount) VALUES (1,'2026-08',1000)");
        jdbc.update("INSERT INTO hr_overtime_month (employee_id,salary_month,overtime_hours,unit_rate,overtime_pay) VALUES (1,'2026-08',2,50,100)");
        jdbc.update("INSERT INTO hr_social_insurance_month (employee_id,salary_month,pension,medical,unemployment,housing_fund) VALUES (1,'2026-08',1000,200,50,750)");
        jdbc.update("INSERT INTO hr_special_deduction_month (employee_id,salary_month,children_education,continuing_education,housing_loan_interest,housing_rent,elderly_support,infant_care,other_deduction) VALUES (1,'2026-08',1000,0,0,0,0,0,0)");

        PayrollVO payroll = service(jdbc).generate(request(1L, "2026-08")).get(0);

        assertEquals(new BigDecimal("21100.00"), payroll.getGrossIncome());
        assertEquals(new BigDecimal("13100.00"), payroll.getCumulativeTaxableIncome());
        assertEquals(new BigDecimal("393.00"), payroll.getCurrentTaxWithheld());
        assertEquals(new BigDecimal("18707.00"), payroll.getNetPay());
    }

    @Test
    void yearMidHireUsesOnlyEmployedMonthsInPersistedPayroll() throws Exception {
        JdbcTemplate jdbc = migratedDatabase("mid-year.db");
        insertDepartmentAndJob(jdbc);
        insertEmployee(jdbc, 1, "E001", "2026-07-10", 1, null, "10000.00");
        jdbc.update("INSERT INTO hr_salary_history (employee_id,effective_month,base_salary_before,base_salary_after) VALUES (1,'2026-07',0,10000)");

        PayrollVO payroll = service(jdbc).generate(request(1L, "2026-09")).get(0);

        assertEquals(new BigDecimal("30000.00"), payroll.getCumulativeIncome());
        assertEquals(new BigDecimal("15000.00"), payroll.getCumulativeDeductionBase());
        assertEquals(new BigDecimal("15000.00"), payroll.getCumulativeTaxableIncome());
        assertEquals(new BigDecimal("450.00"), payroll.getCurrentTaxWithheld());
    }

    @Test
    void fullMonthGenerationSkipsLockedPayrollAndGeneratesOtherEmployees() throws Exception {
        JdbcTemplate jdbc = migratedDatabase("locked-batch.db");
        insertDepartmentAndJob(jdbc);
        insertEmployee(jdbc, 1, "E001", "2026-01-01", 1, null, "10000.00");
        insertEmployee(jdbc, 2, "E002", "2026-01-01", 1, null, "12000.00");
        jdbc.update("INSERT INTO hr_salary_history (employee_id,effective_month,base_salary_before,base_salary_after) VALUES (1,'2026-01',0,10000),(2,'2026-01',0,12000)");
        insertPayroll(jdbc, 1, "2026-08", 1);

        List<PayrollVO> generated = service(jdbc).generate(request(null, "2026-08"));

        assertEquals(1, generated.size());
        assertEquals("E002", generated.get(0).getEmployeeCode());
        assertEquals(2L, jdbc.queryForObject("SELECT COUNT(*) FROM hr_payroll_month WHERE salary_month='2026-08'", Long.class));
    }

    @Test
    void resignedEmployeeCanGenerateDismissalMonthButNotLater() throws Exception {
        JdbcTemplate jdbc = migratedDatabase("dismissal-boundary.db");
        insertDepartmentAndJob(jdbc);
        insertEmployee(jdbc, 1, "E001", "2026-01-01", 3, "2026-08-15", "10000.00");
        jdbc.update("INSERT INTO hr_salary_history (employee_id,effective_month,base_salary_before,base_salary_after) VALUES (1,'2026-01',0,10000)");

        assertEquals(1, service(jdbc).generate(request(1L, "2026-08")).size());
        assertThrows(BusinessException.class, () -> service(jdbc).generate(request(1L, "2026-09")));
    }

    @Test
    void fullMonthGenerationIncludesResignedEmployeeInDismissalMonth() throws Exception {
        JdbcTemplate jdbc = migratedDatabase("dismissal-batch.db");
        insertDepartmentAndJob(jdbc);
        insertEmployee(jdbc, 1, "E001", "2026-01-01", 3, "2026-08-15", "10000.00");
        jdbc.update("INSERT INTO hr_salary_history (employee_id,effective_month,base_salary_before,base_salary_after) VALUES (1,'2026-01',0,10000)");

        List<PayrollVO> generated = service(jdbc).generate(request(null, "2026-08"));

        assertEquals(1, generated.size());
        assertEquals("E001", generated.get(0).getEmployeeCode());
    }

    @Test
    void payrollExportWorkbookUsesItsHeaderOrder() throws Exception {
        JdbcTemplate jdbc = migratedDatabase("export.db");
        insertDepartmentAndJob(jdbc);
        insertEmployee(jdbc, 1, "E001", "2026-01-01", 1, null, "10000.00");
        jdbc.update("INSERT INTO hr_salary_history (employee_id,effective_month,base_salary_before,base_salary_after) VALUES (1,'2026-01',0,10000)");
        service(jdbc).generate(request(1L, "2026-08"));

        byte[] bytes = service(jdbc).exportPayroll("2026-08", null);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            String[] actual = new String[workbook.getSheetAt(0).getRow(0).getLastCellNum()];
            for (int index = 0; index < actual.length; index++) {
                actual[index] = workbook.getSheetAt(0).getRow(0).getCell(index).getStringCellValue();
            }
            assertArrayEquals(com.autohr.modules.hr.service.PayrollExportTemplate.HEADERS, actual);
            assertEquals("E001", workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue());
            assertEquals("员工1", workbook.getSheetAt(0).getRow(1).getCell(1).getStringCellValue());
            assertEquals("110101199001010001", workbook.getSheetAt(0).getRow(1).getCell(3).getStringCellValue());
            assertEquals(10000D, workbook.getSheetAt(0).getRow(1).getCell(4).getNumericCellValue());
            assertEquals(0D, workbook.getSheetAt(0).getRow(1).getCell(5).getNumericCellValue());
        }
    }

    @Test
    void monthlyInputListsExposeTheEmployeeNameExpectedByTheFrontend() throws Exception {
        JdbcTemplate jdbc = migratedDatabase("input-list.db");
        insertDepartmentAndJob(jdbc);
        insertEmployee(jdbc, 1, "E001", "2026-01-01", 1, null, "10000.00");
        jdbc.update("INSERT INTO hr_performance_month (employee_id,salary_month,amount) VALUES (1,'2026-08',1200)");

        List<java.util.Map<String, Object>> rows = service(jdbc).listInputs("performance", "2026-08", null);

        assertEquals(1, rows.size());
        assertEquals("员工1", rows.get(0).get("employee_name"));
    }

    private JdbcTemplate migratedDatabase(String fileName) throws Exception {
        String url = "jdbc:sqlite:" + tempDirectory.resolve(fileName).toString().replace('\\', '/');
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url);
        new DatabaseMigrationRunner(dataSource, new ActiveDatabase(DatabaseType.SQLITE, url, "", "", false), new AppMigrationProperties()).run();
        return new JdbcTemplate(dataSource);
    }

    private PayrollServiceImpl service(JdbcTemplate jdbc) {
        return new PayrollServiceImpl(jdbc, Validation.buildDefaultValidatorFactory().getValidator());
    }

    private PayrollGenerateRequest request(Long employeeId, String month) {
        PayrollGenerateRequest request = new PayrollGenerateRequest();
        request.setEmployeeId(employeeId);
        request.setSalaryMonth(month);
        return request;
    }

    private void insertDepartmentAndJob(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO hr_department (department_code,department_name,description) VALUES ('D1','研发部','研发')");
        jdbc.update("INSERT INTO recruitment_job (job_code,job_title,department_name,requirements,responsibilities,publish_date) VALUES ('J1','工程师','研发部','要求','职责','2026-01-01')");
    }

    private void insertEmployee(JdbcTemplate jdbc, int sequence, String code, String hireDate,
                                int status, String dismissalDate, String salary) {
        jdbc.update("INSERT INTO hr_employee (employee_code,full_name,id_card_no,mobile_phone,recruitment_major,position_name,department_id,bank_account_no,bank_name,hire_date,employment_status,job_id,base_salary,salary_confirmed,dismissal_date) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                code, "员工" + sequence, "1101011990010100" + String.format("%02d", sequence),
                "138000000" + String.format("%02d", sequence), "计算机", "工程师", 1,
                "62220000000000" + String.format("%02d", sequence), "测试银行", hireDate, status, 1,
                new BigDecimal(salary), 1, dismissalDate);
    }

    private void insertPayroll(JdbcTemplate jdbc, long employeeId, String month, int locked) {
        jdbc.update("INSERT INTO hr_payroll_month (employee_id,salary_month,base_salary,performance,overtime_hours,overtime_pay,gross_income,social_insurance_total,special_deduction_total,taxable_income_month,cumulative_income,cumulative_deduction_base,cumulative_social_insurance,cumulative_special_deduction,cumulative_taxable_income,cumulative_tax_withheld,current_tax_withheld,net_pay,locked) VALUES (?,?,10000,0,0,0,10000,0,0,5000,80000,40000,0,0,40000,1480,150,9850,?)",
                employeeId, month, locked);
    }
}
