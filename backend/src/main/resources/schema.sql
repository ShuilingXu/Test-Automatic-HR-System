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
    source_candidate_id INTEGER,
    interview_stage_status VARCHAR(64),
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
    interview_stage_status VARCHAR(64) NOT NULL DEFAULT '简历待查',
    interviewee_user_id INTEGER,
    interview_process_id INTEGER,
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

CREATE TABLE IF NOT EXISTS sys_audit_log (
    id INTEGER PRIMARY KEY,
    operator_user_id INTEGER,
    operator_username VARCHAR(64),
    operator_role_code VARCHAR(32),
    module_code VARCHAR(64) NOT NULL,
    action_code VARCHAR(64) NOT NULL,
    target_type VARCHAR(64),
    target_id VARCHAR(128),
    detail VARCHAR(4000),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sys_audit_log_module_code ON sys_audit_log(module_code);

CREATE TABLE IF NOT EXISTS interview_knowledge_base (
    id INTEGER PRIMARY KEY,
    knowledge_base_name VARCHAR(128) NOT NULL,
    tech_category VARCHAR(128),
    job_category VARCHAR(128),
    status INTEGER NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS interview_knowledge_item (
    id INTEGER PRIMARY KEY,
    knowledge_base_id INTEGER NOT NULL,
    knowledge_point VARCHAR(255) NOT NULL,
    knowledge_content VARCHAR(5000) NOT NULL,
    status INTEGER NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (knowledge_base_id) REFERENCES interview_knowledge_base(id)
);

CREATE TABLE IF NOT EXISTS interview_job_knowledge_weight (
    id INTEGER PRIMARY KEY,
    job_id INTEGER NOT NULL,
    knowledge_base_id INTEGER NOT NULL,
    weight INTEGER NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS interview_llm_config (
    id INTEGER PRIMARY KEY,
    config_name VARCHAR(128) NOT NULL,
    model_role VARCHAR(32) NOT NULL,
    base_url VARCHAR(255) NOT NULL,
    api_key VARCHAR(255),
    api_key_masked VARCHAR(255),
    model_name VARCHAR(128) NOT NULL,
    prompt_template VARCHAR(5000),
    scoring_rule_prompt VARCHAR(5000),
    status INTEGER NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS interview_process (
    id INTEGER PRIMARY KEY,
    recruitment_candidate_id INTEGER NOT NULL,
    interviewee_user_id INTEGER,
    job_id INTEGER NOT NULL,
    current_stage VARCHAR(32) NOT NULL,
    stage_status VARCHAR(32) NOT NULL,
    overall_status VARCHAR(32) NOT NULL,
    ai_threshold_score INTEGER NOT NULL DEFAULT 7,
    ai_average_score INTEGER,
    video_approved INTEGER NOT NULL DEFAULT 0,
    onsite_approved INTEGER NOT NULL DEFAULT 0,
    approved_hr_user_id INTEGER,
    approved_hr_name VARCHAR(64),
    process_status_view VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS interview_ai_record (
    id INTEGER PRIMARY KEY,
    process_id INTEGER NOT NULL,
    knowledge_base_id INTEGER,
    knowledge_point VARCHAR(255),
    question_content VARCHAR(5000) NOT NULL,
    answer_content VARCHAR(5000),
    interviewer_score INTEGER,
    scorer_score INTEGER,
    average_score INTEGER,
    sequence_no INTEGER NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS interview_video_session (
    id INTEGER PRIMARY KEY,
    process_id INTEGER NOT NULL,
    video_serial_no VARCHAR(128) NOT NULL,
    video_join_link VARCHAR(500) NOT NULL,
    approver_user_id INTEGER,
    approver_name VARCHAR(64),
    interviewee_join_time DATETIME,
    hr_join_time DATETIME,
    start_time DATETIME,
    end_time DATETIME,
    recording_path VARCHAR(500),
    hr_offer_sdp TEXT,
    interviewee_answer_sdp TEXT,
    hr_ice_candidates TEXT,
    interviewee_ice_candidates TEXT,
    recording_file_name VARCHAR(255),
    session_status VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_interview_process_candidate_id ON interview_process(recruitment_candidate_id);
CREATE INDEX IF NOT EXISTS idx_interview_process_stage ON interview_process(current_stage);
CREATE INDEX IF NOT EXISTS idx_interview_ai_record_process_id ON interview_ai_record(process_id);
CREATE INDEX IF NOT EXISTS idx_interview_video_session_process_id ON interview_video_session(process_id);
