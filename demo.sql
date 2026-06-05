-- ============================================================
--  ATG — Fresh Demo Data for H2 Console
--  APCOER IT Department, AY 2025-26, Sem II
--
--  HOW TO USE:
--  1. Start the ATG app (Hibernate creates tables automatically)
--  2. Open H2 console → http://localhost:8080/h2-console
--  3. Paste this entire file and click Run
-- ============================================================

SET REFERENTIAL_INTEGRITY FALSE;

-- Clear any rows seeded by data.sql on startup (prevents PK conflicts)
DELETE FROM subject_faculty_assignment;
DELETE FROM subject;
DELETE FROM timeslot;
DELETE FROM classroom;
DELETE FROM division;
DELETE FROM users;
DELETE FROM department;

-- ─── DEPARTMENT ───────────────────────────────────────────────────────────────
INSERT INTO department (id, code, hod, name) VALUES
(1, 'IT', 'Dr. A. A. Kadam', 'Information Technology');

-- ─── USERS ────────────────────────────────────────────────────────────────────
-- Logins: admin/admin | hod_it/hod_it | pgk/123 | others: username=password | apcoer/atg
INSERT INTO users (id, deleted, department, email, max_lectures_per_day, max_lectures_per_week, name, password, role, specialization, subjects_handled, username, working_hours) VALUES
(1,  FALSE, NULL,                     NULL,             NULL, NULL, 'System Admin',          'admin',     'ADMIN',   NULL, NULL, 'admin',     NULL),
(2,  FALSE, 'Information Technology', 'amit@gamil.com', NULL, NULL, 'Dr. A. A. Kadam',       'hod_it',    'HOD',     NULL, NULL, 'hod_it',    NULL),
(3,  FALSE, 'Information Technology', NULL,             NULL, NULL, 'Prof. P. G. Khaire',    '123',       'FACULTY', NULL, NULL, 'pgk',       NULL),
(4,  FALSE, 'Information Technology', NULL,             NULL, NULL, 'Prof. R. A. Nikam',     'ran',       'FACULTY', NULL, NULL, 'ran',       NULL),
(5,  FALSE, 'Information Technology', NULL,             NULL, NULL, 'Prof. S. S. Khote',     'ssk',       'FACULTY', NULL, NULL, 'ssk',       NULL),
(6,  FALSE, 'Information Technology', NULL,             NULL, NULL, 'Prof. A. R. Dodke',     'ard',       'FACULTY', NULL, NULL, 'ard',       NULL),
(7,  FALSE, 'Information Technology', NULL,             NULL, NULL, 'Prof. R. S. Lavhe',     'rsl',       'FACULTY', NULL, NULL, 'rsl',       NULL),
(8,  FALSE, 'Information Technology', NULL,             NULL, NULL, 'Dr. S. R. Kokane',      'srk',       'FACULTY', NULL, NULL, 'srk',       NULL),
(9,  FALSE, 'Information Technology', NULL,             NULL, NULL, 'Prof. Rashmi Kenvat',   'rk',        'FACULTY', NULL, NULL, 'rk',        NULL),
(10, FALSE, 'Information Technology', NULL,             NULL, NULL, 'Prof. D. P. Rankhambe', 'dpr',       'FACULTY', NULL, NULL, 'dpr',       NULL),
(11, FALSE, 'Information Technology', NULL,             NULL, NULL, 'Prof. A. N. Kalal',     'ank',       'FACULTY', NULL, NULL, 'ank',       NULL),
(12, FALSE, 'Information Technology', NULL,             NULL, NULL, 'Dr. A. A. Kadam',       'aak',       'FACULTY', NULL, NULL, 'aak',       NULL),
(13, FALSE, 'Information Technology', NULL,             NULL, NULL, 'Library Coordinator',   'lib_coord', 'FACULTY', NULL, NULL, 'lib_coord', NULL),
(14, FALSE, NULL,                     NULL,             NULL, NULL, 'System Admin',          'atg',       'ADMIN',   NULL, NULL, 'apcoer',    NULL);

-- ─── DIVISION ─────────────────────────────────────────────────────────────────
-- "year" column omitted — H2 stores it as YEAR (uppercase) which conflicts
-- with quoted "year" (lowercase). Set the Year field via UI after seeding.
INSERT INTO division (id, batch_count, batch_prefix, capacity, classroom, department, name, semester_number, lab_preference, roll_number_start, student_count, class_teacher) VALUES
(1, 4, 'S', 75, NULL, 'Information Technology', 'SE-IT', 4, 'AFTERNOON', 5201, 75, NULL),
(2, 4, 'T', 75, NULL, 'Information Technology', 'TE-IT', 6, 'MIDDAY',    5301, 74, 'PROF. PRAJAKTA KHAIRE'),
(3, 4, 'B', 75, NULL, 'Information Technology', 'BE-IT', 8, 'MORNING',   5401, 80, 'Prof. A. N. Kalal');

-- ─── CLASSROOM ────────────────────────────────────────────────────────────────
INSERT INTO classroom (id, building, capacity, room, type) VALUES
(1,  NULL,       75,   'Room No. 305',                     'Classroom'),
(2,  NULL,       75,   'Room No. 306',                     'Classroom'),
(3,  'IT Block', 30,   'Network Lab',                      'Lab'),
(4,  'IT Block', 30,   'Distributed Lab',                  'Lab'),
(5,  'IT Block', 30,   'Web Engineering & Technology Lab', 'Lab'),
(6,  'IT Block', 30,   'Software Testing And Design Lab',  'Lab'),
(7,  'IT Block', 30,   'Multimedia Lab',                   'Lab'),
(8,  'IT Block', 30,   'Operating System Lab',             'Lab'),
(9,  'IT Block', 30,   'Computer Center Lab',              'Lab'),
(10, 'IT Block', 30,   'Project Lab',                      'Lab'),
(11, NULL,       NULL, 'Room No. 307',                     'Classroom');

-- ─── TIMESLOT ─────────────────────────────────────────────────────────────────
INSERT INTO timeslot (id, end_time, is_break, start_time) VALUES
(1, '10:00', FALSE, '09:00'),
(2, '11:00', FALSE, '10:00'),
(3, '11:15', TRUE,  '11:00'),
(4, '12:15', FALSE, '11:15'),
(5, '13:15', FALSE, '12:15'),
(6, '14:00', TRUE,  '13:15'),
(7, '15:00', FALSE, '14:00'),
(8, '16:00', FALSE, '15:00'),
(9, '17:00', FALSE, '16:00');

-- ─── SUBJECT ──────────────────────────────────────────────────────────────────
-- SE-IT Sem 4 — Theory
INSERT INTO subject (id, code, credits, deleted, department, description, faculty, hours, lecture_hours_per_week, name, practical_hours_per_week, practical_slot_duration, semester, type, pin_days, pin_slot) VALUES
(1,  'CG',   3,    FALSE, 'Information Technology', NULL, 'Prof. P. G. Khaire',    NULL, 3, 'Computer Graphics',          0, 0, 4, 'Theory', NULL,              NULL),
(2,  'PA',   3,    FALSE, 'Information Technology', NULL, 'Prof. S. S. Khote',     NULL, 3, 'Processor Architecture',     0, 0, 4, 'Theory', NULL,              NULL),
(3,  'DBMS', 3,    FALSE, 'Information Technology', NULL, 'Prof. R. A. Nikam',     NULL, 3, 'Database Management System', 0, 0, 4, 'Theory', NULL,              NULL),
(4,  'PS',   NULL, FALSE, 'Information Technology', NULL, 'Prof. Rashmi Kenvat',   NULL, 4, 'Probability & Statistics',   0, 2, 4, 'Theory', NULL, NULL),
(5,  'ES',   3,    FALSE, 'Information Technology', NULL, 'Prof. D. P. Rankhambe', NULL, 2, 'Environment Studies',        0, 0, 4, 'Theory', NULL,              NULL),
(6,  'OE2',  2,    FALSE, 'Information Technology', NULL, 'Dr. S. R. Kokane',      NULL, 3, 'Project Management',         0, 0, 4, 'Theory', NULL,              NULL),
(7,  'MIL',  2,    FALSE, 'Information Technology', NULL, 'Prof. R. S. Lavhe',     NULL, 1, 'Modern Indian Language',     0, 0, 4, 'Theory', NULL,              NULL),
(8,  'EC',   2,    FALSE, 'Information Technology', NULL, 'Prof. A. N. Kalal',     NULL, 1, 'E Commerce',                 0, 0, 4, 'Theory', NULL,              NULL);

-- SE-IT Sem 4 — Labs
INSERT INTO subject (id, code, credits, deleted, department, description, faculty, hours, lecture_hours_per_week, name, practical_hours_per_week, practical_slot_duration, semester, type, pin_days, pin_slot) VALUES
(9,  'CGL',   1, FALSE, 'Information Technology', NULL, NULL, NULL, 0, 'Computer Graphics Lab',            2, 2, 4, 'Lab', NULL, NULL),
(10, 'DBMSL', 1, FALSE, 'Information Technology', NULL, NULL, NULL, 0, 'Database Management System Lab',   2, 2, 4, 'Lab', NULL, NULL),
(11, 'MILL',  1, FALSE, 'Information Technology', NULL, NULL, NULL, 0, 'Modern Indian Language Lab',       2, 2, 4, 'Lab', NULL, NULL),
(12, 'ECL',   1, FALSE, 'Information Technology', NULL, NULL, NULL, 0, 'E Commerce Lab',                   2, 2, 4, 'Lab', NULL, NULL),
(13, 'DMSML', 1, FALSE, 'Information Technology', NULL, NULL, NULL, 0, 'Digital Marketing & Social Media', 4, 2, 4, 'Lab', NULL, NULL);

-- SE-IT Sem 4 — Audit / VL / Library
INSERT INTO subject (id, code, credits, deleted, department, description, faculty, hours, lecture_hours_per_week, name, practical_hours_per_week, practical_slot_duration, semester, type, pin_days, pin_slot) VALUES
(14, 'VL4',   NULL, FALSE, 'Information Technology', NULL, NULL, NULL, 1, 'Virtual Lab', NULL, NULL, 4, 'Audit', NULL, NULL),
(15, 'LIBSE', NULL, FALSE, 'Information Technology', NULL, NULL, NULL, 1, 'Library',     NULL, NULL, 4, 'Audit', NULL, NULL);

-- TE-IT Sem 6 — Theory
INSERT INTO subject (id, code, credits, deleted, department, description, faculty, hours, lecture_hours_per_week, name, practical_hours_per_week, practical_slot_duration, semester, type, pin_days, pin_slot) VALUES
(16, 'CNS',   3, FALSE, 'Information Technology', NULL, 'Prof. R. S. Lavhe',     NULL, 3, 'Computer Networks & Security',     0, 0, 6, 'Theory', NULL, NULL),
(17, 'WAD',   3, FALSE, 'Information Technology', NULL, 'Prof. A. N. Kalal',     NULL, 3, 'Web Application Development',      0, 0, 6, 'Theory', NULL, NULL),
(18, 'DSBDA', 3, FALSE, 'Information Technology', NULL, 'Prof. D. P. Rankhambe', NULL, 3, 'Data Science & Big Data Analysis', 0, 0, 6, 'Theory', NULL, NULL),
(19, 'EL2',   3, FALSE, 'Information Technology', NULL, 'Prof. A. R. Dodke',     NULL, 3, 'Elective II',                      0, 0, 6, 'Theory', NULL, NULL),
(20, 'EACH',  2, FALSE, 'Information Technology', NULL, 'Prof. P. G. Khaire',    NULL, 4, 'EAC Honours',                      0, 0, 6, 'Theory', NULL, NULL);

-- TE-IT Sem 6 — Labs
INSERT INTO subject (id, code, credits, deleted, department, description, faculty, hours, lecture_hours_per_week, name, practical_hours_per_week, practical_slot_duration, semester, type, pin_days, pin_slot) VALUES
(21, 'CNSL',   2, FALSE, 'Information Technology', NULL, NULL, NULL, 0, 'Computer Networks & Security Lab', 4, 2, 6, 'Lab', NULL, NULL),
(22, 'DSBDAL', 1, FALSE, 'Information Technology', NULL, NULL, NULL, 0, 'DS & BDA-Lab',                     2, 2, 6, 'Lab', NULL, NULL),
(23, 'LP2',    2, FALSE, 'Information Technology', NULL, NULL, NULL, 0, 'Laboratory Practice-II',            4, 2, 6, 'Lab', NULL, NULL);

-- TE-IT Sem 6 — Audit / Special
INSERT INTO subject (id, code, credits, deleted, department, description, faculty, hours, lecture_hours_per_week, name, practical_hours_per_week, practical_slot_duration, semester, type, pin_days, pin_slot) VALUES
(24, 'INTP', NULL, FALSE, 'Information Technology', NULL, NULL, NULL, 4, 'Internship',      NULL, 2, 6, 'Audit', NULL, NULL),
(25, 'AC6',  NULL, FALSE, 'Information Technology', NULL, NULL, NULL, 4, 'Audit Course VI', NULL, 1, 6, 'Audit', NULL, NULL),
(26, 'VL6',  NULL, FALSE, 'Information Technology', NULL, NULL, NULL, 1, 'Virtual Lab',     NULL, 1, 6, 'Audit', NULL, NULL);

-- BE-IT Sem 8 — Theory
INSERT INTO subject (id, code, credits, deleted, department, description, faculty, hours, lecture_hours_per_week, name, practical_hours_per_week, practical_slot_duration, semester, type, pin_days, pin_slot) VALUES
(27, 'SE8',  3, FALSE, 'Information Technology', NULL, 'Dr. A. A. Kadam',       NULL, 3, 'Startup and Entrepreneurship', 0, 0, 8, 'Theory', NULL, NULL),
(28, 'DS8',  3, FALSE, 'Information Technology', NULL, 'Dr. S. R. Kokane',      NULL, 3, 'Distributed Systems',         0, 0, 8, 'Theory', NULL, NULL),
(29, 'EL5',  3, FALSE, 'Information Technology', NULL, 'Prof. S. S. Khote',     NULL, 3, 'Social Computing',            0, 0, 8, 'Theory', NULL, NULL),
(30, 'EL6',  3, FALSE, 'Information Technology', NULL, 'Prof. R. A. Nikam',     NULL, 3, 'Elective VI',                 0, 0, 8, 'Theory', NULL, NULL),
(31, 'ISMH', 2, FALSE, 'Information Technology', NULL, 'Prof. A. N. Kalal',     NULL, 4, 'ISM Honours',                 0, 0, 8, 'Theory', NULL, NULL);

-- BE-IT Sem 8 — Labs
INSERT INTO subject (id, code, credits, deleted, department, description, faculty, hours, lecture_hours_per_week, name, practical_hours_per_week, practical_slot_duration, semester, type, pin_days, pin_slot) VALUES
(32, 'LP5',  2,    FALSE, 'Information Technology', NULL, NULL,                     NULL, 0, 'Lab Practice V',   4, 2, 8, 'Lab',     NULL, NULL),
(33, 'LP6',  1,    FALSE, 'Information Technology', NULL, NULL,                     NULL, 0, 'Lab Practice VI',  2, 2, 8, 'Lab',     NULL, NULL),
(34, 'PS2',  NULL, FALSE, 'Information Technology', NULL, NULL,                     NULL, 5, 'Project Stage II', 2, 2, 8, 'Project', NULL, NULL),
(35, 'SEM8', 1,    FALSE, 'Information Technology', NULL, 'Prof. D. P. Rankhambe', NULL, 0, 'Seminar',          2, 2, 8, 'Seminar', NULL, NULL);

-- BE-IT Sem 8 — Audit / Special
INSERT INTO subject (id, code, credits, deleted, department, description, faculty, hours, lecture_hours_per_week, name, practical_hours_per_week, practical_slot_duration, semester, type, pin_days, pin_slot) VALUES
(36, 'T&P',  NULL, FALSE, 'Information Technology', NULL, NULL, NULL, 1, 'Training & placement', NULL, NULL, 8, 'Audit', NULL, NULL),
(37, 'AC8',  NULL, FALSE, 'Information Technology', NULL, NULL, NULL, 2, 'Audit Course-VIII',    0,    NULL, 8, 'Audit', NULL, NULL),
(38, 'VL8',  NULL, FALSE, 'Information Technology', NULL, NULL, NULL, 1, 'Virtual Lab',          NULL, NULL, 8, 'Audit', NULL, NULL);

-- ─── SUBJECT FACULTY ASSIGNMENT ───────────────────────────────────────────────
-- SE-IT: CGL (id=9) — PGK all batches
INSERT INTO subject_faculty_assignment (id, batch, division_name, faculty_name, subject_id) VALUES
(1, 'S1', 'SE-IT', 'Prof. P. G. Khaire', 9),
(2, 'S2', 'SE-IT', 'Prof. P. G. Khaire', 9),
(3, 'S3', 'SE-IT', 'Prof. P. G. Khaire', 9),
(4, 'S4', 'SE-IT', 'Prof. P. G. Khaire', 9);

-- SE-IT: DBMSL (id=10) — RAN all batches
INSERT INTO subject_faculty_assignment (id, batch, division_name, faculty_name, subject_id) VALUES
(5, 'S1', 'SE-IT', 'Prof. R. A. Nikam', 10),
(6, 'S2', 'SE-IT', 'Prof. R. A. Nikam', 10),
(7, 'S3', 'SE-IT', 'Prof. R. A. Nikam', 10),
(8, 'S4', 'SE-IT', 'Prof. R. A. Nikam', 10);

-- SE-IT: DMSML (id=13) — SSK for S1/S2, ARD for S3/S4
INSERT INTO subject_faculty_assignment (id, batch, division_name, faculty_name, subject_id) VALUES
(9,  'S1', 'SE-IT', 'Prof. S. S. Khote', 13),
(10, 'S2', 'SE-IT', 'Prof. S. S. Khote', 13),
(11, 'S3', 'SE-IT', 'Prof. A. R. Dodke', 13),
(12, 'S4', 'SE-IT', 'Prof. A. R. Dodke', 13);

-- SE-IT: MIL Lab (id=11) — RSL for S1/S2, SRK for S3/S4
INSERT INTO subject_faculty_assignment (id, batch, division_name, faculty_name, subject_id) VALUES
(13, 'S1', 'SE-IT', 'Prof. R. S. Lavhe', 11),
(14, 'S2', 'SE-IT', 'Prof. R. S. Lavhe', 11),
(15, 'S3', 'SE-IT', 'Dr. S. R. Kokane',  11),
(16, 'S4', 'SE-IT', 'Dr. S. R. Kokane',  11);

-- SE-IT: ECL (id=12) — ANK for S1/S2, AAK for S3/S4
INSERT INTO subject_faculty_assignment (id, batch, division_name, faculty_name, subject_id) VALUES
(17, 'S1', 'SE-IT', 'Prof. A. N. Kalal', 12),
(18, 'S2', 'SE-IT', 'Prof. A. N. Kalal', 12),
(19, 'S3', 'SE-IT', 'Dr. A. A. Kadam',   12),
(20, 'S4', 'SE-IT', 'Dr. A. A. Kadam',   12);

-- TE-IT: CNSL (id=21) — RSL for T1/T2, SSK for T3/T4
INSERT INTO subject_faculty_assignment (id, batch, division_name, faculty_name, subject_id) VALUES
(21, 'T1', 'TE-IT', 'Prof. R. S. Lavhe', 21),
(22, 'T2', 'TE-IT', 'Prof. R. S. Lavhe', 21),
(23, 'T3', 'TE-IT', 'Prof. S. S. Khote', 21),
(24, 'T4', 'TE-IT', 'Prof. S. S. Khote', 21);

-- TE-IT: DSBDAL (id=22) — DPR all batches
INSERT INTO subject_faculty_assignment (id, batch, division_name, faculty_name, subject_id) VALUES
(25, 'T1', 'TE-IT', 'Prof. D. P. Rankhambe', 22),
(26, 'T2', 'TE-IT', 'Prof. D. P. Rankhambe', 22),
(27, 'T3', 'TE-IT', 'Prof. D. P. Rankhambe', 22),
(28, 'T4', 'TE-IT', 'Prof. D. P. Rankhambe', 22);

-- TE-IT: LP-II (id=23) — ARD for T1/T3, ANK for T2/T4
INSERT INTO subject_faculty_assignment (id, batch, division_name, faculty_name, subject_id) VALUES
(29, 'T1', 'TE-IT', 'Prof. A. R. Dodke', 23),
(30, 'T2', 'TE-IT', 'Prof. A. N. Kalal', 23),
(31, 'T3', 'TE-IT', 'Prof. A. R. Dodke', 23),
(32, 'T4', 'TE-IT', 'Prof. A. N. Kalal', 23);

-- BE-IT: LP-V (id=32) — SRK for B1/B3, AAK for B2/B4
INSERT INTO subject_faculty_assignment (id, batch, division_name, faculty_name, subject_id) VALUES
(33, 'B1', 'BE-IT', 'Dr. S. R. Kokane', 32),
(34, 'B2', 'BE-IT', 'Dr. A. A. Kadam',  32),
(35, 'B3', 'BE-IT', 'Dr. S. R. Kokane', 32),
(36, 'B4', 'BE-IT', 'Dr. A. A. Kadam',  32);

-- BE-IT: LP-VI (id=33) — PGK for B1/B2, RAN for B3/B4
INSERT INTO subject_faculty_assignment (id, batch, division_name, faculty_name, subject_id) VALUES
(37, 'B1', 'BE-IT', 'Prof. P. G. Khaire', 33),
(38, 'B2', 'BE-IT', 'Prof. P. G. Khaire', 33),
(39, 'B3', 'BE-IT', 'Prof. R. A. Nikam',  33),
(40, 'B4', 'BE-IT', 'Prof. R. A. Nikam',  33);

SET REFERENTIAL_INTEGRITY TRUE;

-- ─── RESET AUTO-INCREMENT SEQUENCES ──────────────────────────────────────────
ALTER TABLE department                 ALTER COLUMN id RESTART WITH 2;
ALTER TABLE users                      ALTER COLUMN id RESTART WITH 20;
ALTER TABLE division                   ALTER COLUMN id RESTART WITH 4;
ALTER TABLE classroom                  ALTER COLUMN id RESTART WITH 12;
ALTER TABLE timeslot                   ALTER COLUMN id RESTART WITH 10;
ALTER TABLE subject                    ALTER COLUMN id RESTART WITH 39;
ALTER TABLE subject_faculty_assignment ALTER COLUMN id RESTART WITH 41;
