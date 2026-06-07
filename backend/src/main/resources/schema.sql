CREATE TABLE IF NOT EXISTS hr_department (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    department_code VARCHAR(64) NOT NULL UNIQUE,
    department_name VARCHAR(128) NOT NULL,
    parent_department_id INTEGER,
    manager_employee_id INTEGER,
    description VARCHAR(1000) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    status INTEGER NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS hr_employee (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    employee_code VARCHAR(64) NOT NULL UNIQUE,
    full_name VARCHAR(64) NOT NULL,
    id_card_no VARCHAR(32) NOT NULL UNIQUE,
    mobile_phone VARCHAR(32) NOT NULL UNIQUE,
    email VARCHAR(128),
    recruitment_major VARCHAR(128) NOT NULL,
    position_name VARCHAR(128) NOT NULL,
    manager_employee_id INTEGER,
    department_id INTEGER NOT NULL,
    bank_account_no VARCHAR(64) NOT NULL,
    bank_name VARCHAR(128) NOT NULL,
    hire_date DATE NOT NULL,
    employment_status INTEGER NOT NULL DEFAULT 0,
    source_channel VARCHAR(64),
    notes VARCHAR(1000),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (department_id) REFERENCES hr_department(id)
);

CREATE TABLE IF NOT EXISTS hr_integration_binding (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    module_code VARCHAR(64) NOT NULL,
    business_type VARCHAR(64) NOT NULL,
    business_id INTEGER,
    employee_id INTEGER,
    department_id INTEGER,
    external_ref VARCHAR(128),
    binding_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    payload TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (employee_id) REFERENCES hr_employee(id),
    FOREIGN KEY (department_id) REFERENCES hr_department(id)
);

CREATE INDEX IF NOT EXISTS idx_hr_department_parent_department_id ON hr_department(parent_department_id);
CREATE INDEX IF NOT EXISTS idx_hr_employee_department_id ON hr_employee(department_id);
CREATE INDEX IF NOT EXISTS idx_hr_employee_manager_employee_id ON hr_employee(manager_employee_id);
CREATE INDEX IF NOT EXISTS idx_hr_integration_binding_module_code ON hr_integration_binding(module_code);
CREATE INDEX IF NOT EXISTS idx_hr_integration_binding_employee_id ON hr_integration_binding(employee_id);
