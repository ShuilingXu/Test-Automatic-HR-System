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

CREATE TABLE IF NOT EXISTS recruitment_job (
    id INTEGER PRIMARY KEY,
    job_code VARCHAR(64) NOT NULL UNIQUE,
    job_title VARCHAR(128) NOT NULL,
    department_name VARCHAR(128) NOT NULL,
    work_location VARCHAR(128),
    job_type VARCHAR(64),
    headcount INTEGER NOT NULL DEFAULT 1,
    requirements VARCHAR(2000) NOT NULL,
    responsibilities VARCHAR(2000) NOT NULL,
    salary_range VARCHAR(128),
    publish_date DATE NOT NULL,
    close_date DATE,
    status INTEGER NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS recruitment_candidate (
    id INTEGER PRIMARY KEY,
    job_id INTEGER NOT NULL,
    full_name VARCHAR(64) NOT NULL,
    mobile_phone VARCHAR(32) NOT NULL,
    email VARCHAR(128),
    id_card_no VARCHAR(32),
    major VARCHAR(128) NOT NULL,
    education_level VARCHAR(64),
    graduation_school VARCHAR(128),
    years_of_experience INTEGER,
    expected_salary VARCHAR(128),
    self_introduction VARCHAR(2000),
    application_status VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED',
    resume_file_id INTEGER,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (job_id) REFERENCES recruitment_job(id)
);

CREATE TABLE IF NOT EXISTS recruitment_resume_file (
    id INTEGER PRIMARY KEY,
    candidate_id INTEGER NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    stored_file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    content_type VARCHAR(128),
    file_size INTEGER,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (candidate_id) REFERENCES recruitment_candidate(id)
);

CREATE INDEX IF NOT EXISTS idx_recruitment_job_status ON recruitment_job(status);
CREATE INDEX IF NOT EXISTS idx_recruitment_candidate_job_id ON recruitment_candidate(job_id);
CREATE INDEX IF NOT EXISTS idx_recruitment_candidate_status ON recruitment_candidate(application_status);
CREATE INDEX IF NOT EXISTS idx_recruitment_resume_candidate_id ON recruitment_resume_file(candidate_id);

CREATE TABLE IF NOT EXISTS interview_batch (
    id INTEGER PRIMARY KEY,
    batch_code VARCHAR(64) NOT NULL UNIQUE,
    batch_name VARCHAR(128) NOT NULL,
    job_id INTEGER,
    start_time DATETIME,
    end_time DATETIME,
    description VARCHAR(1000),
    status INTEGER NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS interview_question (
    id INTEGER PRIMARY KEY,
    question_title VARCHAR(128) NOT NULL,
    question_type VARCHAR(32) NOT NULL DEFAULT 'TEXT',
    difficulty VARCHAR(32),
    tags VARCHAR(255),
    content VARCHAR(3000) NOT NULL,
    reference_answer VARCHAR(3000),
    score INTEGER NOT NULL DEFAULT 10,
    status INTEGER NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS interview_candidate (
    id INTEGER PRIMARY KEY,
    batch_id INTEGER NOT NULL,
    recruitment_candidate_id INTEGER NOT NULL,
    candidate_name VARCHAR(64) NOT NULL,
    mobile_phone VARCHAR(32) NOT NULL,
    interview_status VARCHAR(32) NOT NULL DEFAULT 'ASSIGNED',
    total_score INTEGER NOT NULL DEFAULT 0,
    interviewer_comment VARCHAR(1000),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (batch_id) REFERENCES interview_batch(id)
);

CREATE TABLE IF NOT EXISTS interview_submission (
    id INTEGER PRIMARY KEY,
    interview_candidate_id INTEGER NOT NULL,
    question_id INTEGER NOT NULL,
    answer_content VARCHAR(5000) NOT NULL,
    score INTEGER,
    reviewer_comment VARCHAR(1000),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (interview_candidate_id) REFERENCES interview_candidate(id),
    FOREIGN KEY (question_id) REFERENCES interview_question(id)
);

CREATE INDEX IF NOT EXISTS idx_interview_batch_status ON interview_batch(status);
CREATE INDEX IF NOT EXISTS idx_interview_question_status ON interview_question(status);
CREATE INDEX IF NOT EXISTS idx_interview_candidate_batch_id ON interview_candidate(batch_id);
CREATE INDEX IF NOT EXISTS idx_interview_submission_candidate_id ON interview_submission(interview_candidate_id);

CREATE TABLE IF NOT EXISTS sys_user (
    id INTEGER PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role_code VARCHAR(32) NOT NULL,
    display_name VARCHAR(64),
    mobile_phone VARCHAR(32),
    email VARCHAR(128),
    status INTEGER NOT NULL DEFAULT 1,
    profile_completed INTEGER NOT NULL DEFAULT 0,
    token_version INTEGER NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sys_user_username ON sys_user(username);
