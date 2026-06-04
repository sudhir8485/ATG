-- Migrate old "Lecture" classroom type to "Classroom"
UPDATE classroom SET type = 'Classroom' WHERE type = 'Lecture';

-- If any password was accidentally BCrypt-hashed (e.g. from a dev experiment),
-- reset it to plain text = the username so logins are never locked out.
UPDATE users SET password = username WHERE password LIKE '$2a$%' OR password LIKE '$2b$%';

-- Seed only the admin account on first run.
-- All other data (departments, divisions, subjects, faculty, classrooms, timeslots)
-- is entered through the admin UI.
INSERT INTO users (name, username, password, role, department, deleted)
SELECT 'System Admin', 'apcoer', 'atg', 'ADMIN', NULL, false
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'apcoer');

-- Seed student count and roll number start on first run (only if null)
UPDATE division SET student_count = 75,  roll_number_start = 5201 WHERE name = 'SE-IT' AND student_count IS NULL;
UPDATE division SET student_count = 74,  roll_number_start = 5301 WHERE name = 'TE-IT' AND student_count IS NULL;
UPDATE division SET student_count = 77,  roll_number_start = 5401 WHERE name = 'BE-IT' AND student_count IS NULL;

-- Seed institution settings on first run (only if fields are null after Hibernate DDL adds columns)
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
