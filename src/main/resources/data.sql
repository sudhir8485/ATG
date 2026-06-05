-- Seed admin account on first run only.
INSERT INTO users (name, username, password, role, department, deleted)
SELECT 'System Admin', 'apcoer', 'atg', 'ADMIN', NULL, false
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'apcoer');

-- Seed institution settings on first run (only when fields are null).
UPDATE academic_setting
SET
    institution_name        = 'Akhil Bharatiya Maratha Shikshan Parishad''s' || chr(10) || 'Anantrao Pawar college of Engineering & Research, Parvati, Pune',
    institution_name_short  = 'Akhil Bharatiya Maratha Shikshan Parishad''s' || chr(10) || 'Anantrao Pawar college of Engineering & Research',
    department_name         = 'Information Technology',
    academic_year           = '2025-26',
    semester_display        = 'II',
    wef_date                = '01/01/2026',
    doi_date                = '01/02/2025',
    revision_number         = '00',
    record_no_master        = 'ACA/R/003A',
    record_no_class         = 'ACA/R/003B',
    record_no_lab           = 'ACA/R/003D',
    record_no_faculty       = 'ACA/R/003E',
    tt_coordinator_name     = 'PROF. R. A. NIKAM',
    hod_signature_name      = 'DR. A. A. KADAM',
    principal_name          = 'DR. S. B. THAKARE',
    college_logo            = '',
    owner_logo              = ''
WHERE id = 1 AND institution_name IS NULL;
