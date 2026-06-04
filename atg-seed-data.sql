-- ============================================================
-- ATG — Complete Seed Data
-- APCOER, Parvati, Pune — IT Dept, AY 2025-26, Sem-II
--
-- HOW TO USE:
--   1. Open http://localhost:8765/h2-console
--   2. JDBC URL : jdbc:h2:file:/home/sudhir/.atg/atg-data
--   3. User Name: atg-data   Password: atgadmin
--   4. Paste this entire file and click Run (Ctrl+Enter)
--   5. Refresh the app, go to Generate Timetable, click Generate
-- ============================================================

-- ============================================================
-- 0. CLEAR EXISTING DATA (safe re-run)
-- ============================================================
DELETE FROM subject_faculty_assignment;
DELETE FROM timetable;
DELETE FROM subject      WHERE deleted = false OR deleted = true;
DELETE FROM classroom;
DELETE FROM timeslot;
DELETE FROM division;
DELETE FROM department;
DELETE FROM academic_setting;
DELETE FROM users        WHERE username <> 'apcoer';

-- ============================================================
-- 1. ACADEMIC SETTING  (working days)
-- ============================================================
INSERT INTO academic_setting (id, working_days)
VALUES (1, 'Monday,Tuesday,Wednesday,Thursday,Friday');

-- ============================================================
-- 2. DEPARTMENT
-- ============================================================
INSERT INTO department (name, code, hod)
VALUES ('Information Technology', 'IT', 'HOD-IT');

-- ============================================================
-- 3. CLASSROOMS
-- ============================================================
-- Lecture rooms (only these are used for theory)
INSERT INTO classroom (room, building, capacity, type) VALUES ('Room 305', 'Main', 60, 'Lecture');
INSERT INTO classroom (room, building, capacity, type) VALUES ('Room 306', 'Main', 60, 'Lecture');

-- Lab rooms (used for batch practicals)
INSERT INTO classroom (room, building, capacity, type) VALUES ('IT-203', 'IT Block', 30, 'Lab');
INSERT INTO classroom (room, building, capacity, type) VALUES ('IT-204', 'IT Block', 30, 'Lab');
INSERT INTO classroom (room, building, capacity, type) VALUES ('IT-205', 'IT Block', 30, 'Lab');
INSERT INTO classroom (room, building, capacity, type) VALUES ('IT-206', 'IT Block', 30, 'Lab');

-- ============================================================
-- 4. TIMESLOTS
-- ============================================================
INSERT INTO timeslot (start_time, end_time, is_break) VALUES ('09:00', '10:00', false);
INSERT INTO timeslot (start_time, end_time, is_break) VALUES ('10:00', '11:00', false);
INSERT INTO timeslot (start_time, end_time, is_break) VALUES ('11:00', '11:15', true);   -- SHORT BREAK
INSERT INTO timeslot (start_time, end_time, is_break) VALUES ('11:15', '12:15', false);
INSERT INTO timeslot (start_time, end_time, is_break) VALUES ('12:15', '13:15', false);
INSERT INTO timeslot (start_time, end_time, is_break) VALUES ('13:15', '14:00', true);   -- LUNCH BREAK
INSERT INTO timeslot (start_time, end_time, is_break) VALUES ('14:00', '15:00', false);
INSERT INTO timeslot (start_time, end_time, is_break) VALUES ('15:00', '16:00', false);
INSERT INTO timeslot (start_time, end_time, is_break) VALUES ('16:00', '17:00', false);

-- ============================================================
-- 5. DIVISIONS
-- lab_preference controls which window is tried first:
--   MORNING   = Window A (09-11) — BE-IT
--   MIDDAY    = Window B (11:15-13:15) — TE-IT
--   AFTERNOON = Window C (14-16) — SE-IT
-- ============================================================
INSERT INTO division (name, department, year, semester_number, capacity, classroom, batch_count, batch_prefix, lab_preference)
VALUES ('SE-IT', 'IT', 'SE', 4, 60, 'Room 305', 4, 'S', 'AFTERNOON');

INSERT INTO division (name, department, year, semester_number, capacity, classroom, batch_count, batch_prefix, lab_preference)
VALUES ('TE-IT', 'IT', 'TE', 6, 60, 'Room 305', 4, 'T', 'MIDDAY');

INSERT INTO division (name, department, year, semester_number, capacity, classroom, batch_count, batch_prefix, lab_preference)
VALUES ('BE-IT', 'IT', 'BE', 8, 60, 'Room 306', 4, 'B', 'MORNING');

-- ============================================================
-- 6. FACULTY USERS
-- username = faculty code used in timetable cells
-- subjects_handled = comma-separated subject names this faculty can teach
-- ============================================================
INSERT INTO users (name, username, password, role, department, subjects_handled, max_lectures_per_day, max_lectures_per_week, deleted)
VALUES ('Prof. PGK', 'PGK', 'PGK', 'FACULTY', 'IT',
        'Computer Graphics,Computer Graphics Lab,EAC Honours,LP-VI',
        4, 22, false);

INSERT INTO users (name, username, password, role, department, subjects_handled, max_lectures_per_day, max_lectures_per_week, deleted)
VALUES ('Prof. SSK', 'SSK', 'SSK', 'FACULTY', 'IT',
        'Processor Architecture,Discrete Mathematics & Simulation Lab,Computer Networks & Security Lab,Social Computing',
        4, 22, false);

INSERT INTO users (name, username, password, role, department, subjects_handled, max_lectures_per_day, max_lectures_per_week, deleted)
VALUES ('Prof. ARD', 'ARD', 'ARD', 'FACULTY', 'IT',
        'Discrete Mathematics & Simulation Lab,Elective II,LP-II',
        4, 22, false);

INSERT INTO users (name, username, password, role, department, subjects_handled, max_lectures_per_day, max_lectures_per_week, deleted)
VALUES ('Prof. RAN', 'RAN', 'RAN', 'FACULTY', 'IT',
        'Database Management System,DBMS Lab,Elective VI,LP-VI',
        4, 22, false);

INSERT INTO users (name, username, password, role, department, subjects_handled, max_lectures_per_day, max_lectures_per_week, deleted)
VALUES ('Prof. SRK', 'SRK', 'SRK', 'FACULTY', 'IT',
        'Project Management,Modern Indian Language Lab,Distributed Systems,LP-V',
        4, 22, false);

INSERT INTO users (name, username, password, role, department, subjects_handled, max_lectures_per_day, max_lectures_per_week, deleted)
VALUES ('Prof. RSL', 'RSL', 'RSL', 'FACULTY', 'IT',
        'Modern Indian Language,Modern Indian Language Lab,Computer Networks & Security,Computer Networks & Security Lab',
        4, 22, false);

INSERT INTO users (name, username, password, role, department, subjects_handled, max_lectures_per_day, max_lectures_per_week, deleted)
VALUES ('Prof. RK', 'RK', 'RK', 'FACULTY', 'IT',
        'Probability and Statistics',
        4, 22, false);

INSERT INTO users (name, username, password, role, department, subjects_handled, max_lectures_per_day, max_lectures_per_week, deleted)
VALUES ('Prof. ANK', 'ANK', 'ANK', 'FACULTY', 'IT',
        'Electronics Circuits Lab,Web Application Development,LP-II,ISM Honours',
        4, 22, false);

INSERT INTO users (name, username, password, role, department, subjects_handled, max_lectures_per_day, max_lectures_per_week, deleted)
VALUES ('Prof. AAK', 'AAK', 'AAK', 'FACULTY', 'IT',
        'Electronics Circuits Lab,Startup and Entrepreneurship,LP-V',
        4, 22, false);

INSERT INTO users (name, username, password, role, department, subjects_handled, max_lectures_per_day, max_lectures_per_week, deleted)
VALUES ('Prof. DPR', 'DPR', 'DPR', 'FACULTY', 'IT',
        'Engineering Sciences,Data Science and Big Data Analytics,DS & BDA-Lab,Seminar',
        4, 22, false);

INSERT INTO users (name, username, password, role, department, subjects_handled, max_lectures_per_day, max_lectures_per_week, deleted)
VALUES ('Prof. SMD', 'SMD', 'SMD', 'FACULTY', 'IT',
        'Elective II',
        4, 22, false);

-- ============================================================
-- 7. SUBJECTS
--
-- Column meanings:
--   lecture_hours_per_week  = theory sessions per week (1 slot each)
--   practical_hours_per_week = total lab hours per batch per week
--   practical_slot_duration  = size of ONE lab block (always 2)
--   faculty                  = faculty code for THEORY slot (blank for lab-only)
--   pin_days / pin_slot      = exact placement for special sessions
--   type: Theory | Lab | Audit | Seminar | Special
-- ============================================================

-- ── SE-IT  (Semester 4) ─────────────────────────────────────

-- Theory subjects
INSERT INTO subject (name, code, department, semester, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
VALUES ('Computer Graphics',           'CG',   'IT', 4, 3, 0, 2, 'Theory', 'PGK', false);

INSERT INTO subject (name, code, department, semester, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
VALUES ('Processor Architecture',      'PA',   'IT', 4, 3, 0, 2, 'Theory', 'SSK', false);

INSERT INTO subject (name, code, department, semester, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
VALUES ('Database Management System',  'DBMS', 'IT', 4, 3, 0, 2, 'Theory', 'RAN', false);

INSERT INTO subject (name, code, department, semester, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
VALUES ('Project Management',          'OE2',  'IT', 4, 3, 0, 2, 'Theory', 'SRK', false);

INSERT INTO subject (name, code, department, semester, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
VALUES ('Probability and Statistics',  'PS',   'IT', 4, 4, 0, 2, 'Theory', 'RK',  false);

INSERT INTO subject (name, code, department, semester, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
VALUES ('Engineering Sciences',        'ES',   'IT', 4, 2, 0, 2, 'Theory', 'DPR', false);

-- EC: 1 theory slot pinned to Thursday 16:00
INSERT INTO subject (name, code, department, semester, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, pin_days, pin_slot, deleted)
VALUES ('Electronics Circuits',        'EC',   'IT', 4, 1, 0, 2, 'Theory', 'ANK', 'Thursday', '16:00', false);

-- MIL theory: 1 slot pinned to Monday 16:00
INSERT INTO subject (name, code, department, semester, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, pin_days, pin_slot, deleted)
VALUES ('Modern Indian Language',      'MIL',  'IT', 4, 1, 0, 2, 'Theory', 'RSL', 'Monday', '16:00', false);

-- Lab subjects (lecture=0, practical_hours = total hrs/batch/week)
-- DMSL: 2 sessions × 2hr = 4 practical hrs per batch per week
INSERT INTO subject (name, code, department, semester, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
VALUES ('Discrete Mathematics & Simulation Lab', 'DMSL', 'IT', 4, 0, 4, 2, 'Lab', 'SSK', false);

INSERT INTO subject (name, code, department, semester, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
VALUES ('Computer Graphics Lab',       'CGL',  'IT', 4, 0, 2, 2, 'Lab', 'PGK', false);

INSERT INTO subject (name, code, department, semester, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
VALUES ('DBMS Lab',                    'DBMSL','IT', 4, 0, 2, 2, 'Lab', 'RAN', false);

INSERT INTO subject (name, code, department, semester, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
VALUES ('Electronics Circuits Lab',    'ECL',  'IT', 4, 0, 2, 2, 'Lab', 'ANK', false);

INSERT INTO subject (name, code, department, semester, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
VALUES ('Modern Indian Language Lab',  'MILL', 'IT', 4, 0, 2, 2, 'Lab', 'RSL', false);

-- Library (no faculty)
INSERT INTO subject (name, code, department, semester, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
VALUES ('Library',                     'LIBSE','IT', 4, 0, 2, 2, 'Lab', '', false);

-- VL/Spoken Tutorial: pinned Friday 16:00 (whole-class, Audit type)
INSERT INTO subject (name, code, department, semester, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, pin_days, pin_slot, deleted)
VALUES ('VL/Spoken Tutorial',          'VL4',  'IT', 4, 0, 1, 1, 'Audit', '', 'Friday', '16:00', false);

-- ── TE-IT  (Semester 6) ─────────────────────────────────────

-- Theory subjects
INSERT INTO subject (name, code, department, semester, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
VALUES ('Computer Networks & Security',          'CNS',  'IT', 6, 3, 0, 2, 'Theory', 'RSL', false);

INSERT INTO subject (name, code, department, semester, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
VALUES ('Web Application Development',           'WAD',  'IT', 6, 3, 0, 2, 'Theory', 'ANK', false);

INSERT INTO subject (name, code, department, semester, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
VALUES ('Data Science and Big Data Analytics',   'DSBDA','IT', 6, 3, 0, 2, 'Theory', 'DPR', false);

INSERT INTO subject (name, code, department, semester, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
VALUES ('Elective II',                           'EL2',  'IT', 6, 3, 0, 2, 'Theory', 'ARD', false);

INSERT INTO subject (name, code, department, semester, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
VALUES ('EAC Honours',                           'EACH', 'IT', 6, 4, 0, 2, 'Theory', 'PGK', false);

-- Internship: 2 slots pinned Mon+Thu at 14:00 (whole-class, Audit)
INSERT INTO subject (name, code, department, semester, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, pin_days, pin_slot, deleted)
VALUES ('Internship',                            'INTP', 'IT', 6, 0, 2, 1, 'Audit', '', 'Monday,Thursday', '14:00', false);

-- Audit Course VI: pinned Friday 14:00 (2-hr block, whole-class)
INSERT INTO subject (name, code, department, semester, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, pin_days, pin_slot, deleted)
VALUES ('Audit Course VI',                       'AC6',  'IT', 6, 0, 2, 2, 'Audit', '', 'Friday', '14:00', false);

-- VL/Spoken Tutorial: pinned Friday 16:00
INSERT INTO subject (name, code, department, semester, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, pin_days, pin_slot, deleted)
VALUES ('VL/Spoken Tutorial',                    'VL6',  'IT', 6, 0, 1, 1, 'Audit', '', 'Friday', '16:00', false);

-- Lab subjects for TE-IT
-- CNSL: 2 sessions × 2hr = 4 practical hrs/batch/week
INSERT INTO subject (name, code, department, semester, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
VALUES ('Computer Networks & Security Lab',      'CNSL', 'IT', 6, 0, 4, 2, 'Lab', 'RSL', false);

INSERT INTO subject (name, code, department, semester, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
VALUES ('DS & BDA-Lab',                          'DSBDAL','IT', 6, 0, 2, 2, 'Lab', 'DPR', false);

-- LP-II: 2 sessions × 2hr = 4 practical hrs/batch/week (EL-II track ARD + WAD track ANK)
INSERT INTO subject (name, code, department, semester, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
VALUES ('LP-II',                                 'LP2',  'IT', 6, 0, 4, 2, 'Lab', 'ANK', false);

-- Library (no faculty)
INSERT INTO subject (name, code, department, semester, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
VALUES ('Library',                               'LIBTE','IT', 6, 0, 2, 2, 'Lab', '', false);

-- ── BE-IT  (Semester 8) ─────────────────────────────────────

-- Theory subjects
INSERT INTO subject (name, code, department, semester, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
VALUES ('Startup and Entrepreneurship',          'SE8',  'IT', 8, 3, 0, 2, 'Theory', 'AAK', false);

INSERT INTO subject (name, code, department, semester, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
VALUES ('Distributed Systems',                   'DS8',  'IT', 8, 3, 0, 2, 'Theory', 'SRK', false);

INSERT INTO subject (name, code, department, semester, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
VALUES ('Social Computing',                      'EL5',  'IT', 8, 3, 0, 2, 'Theory', 'SSK', false);

INSERT INTO subject (name, code, department, semester, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
VALUES ('Elective VI',                           'EL6',  'IT', 8, 3, 0, 2, 'Theory', 'RAN', false);

-- ISM Honours: 4 theory sessions
INSERT INTO subject (name, code, department, semester, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
VALUES ('ISM Honours',                           'ISMH', 'IT', 8, 4, 0, 2, 'Theory', 'ANK', false);

-- Seminar: 1 slot pinned Thursday 14:00 (2-hr block)
INSERT INTO subject (name, code, department, semester, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, pin_days, pin_slot, deleted)
VALUES ('Seminar',                               'SEM8', 'IT', 8, 0, 2, 2, 'Seminar', 'DPR', 'Thursday', '14:00', false);

-- T&P: 1 slot pinned Wednesday 16:00
INSERT INTO subject (name, code, department, semester, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, pin_days, pin_slot, deleted)
VALUES ('Training & Placement',                  'TP8',  'IT', 8, 0, 1, 1, 'Audit', '', 'Wednesday', '16:00', false);

-- Audit Course VIII: pinned Friday 09:00 (2-hr block, whole-class)
INSERT INTO subject (name, code, department, semester, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, pin_days, pin_slot, deleted)
VALUES ('Audit Course VIII',                     'AC8',  'IT', 8, 0, 2, 2, 'Audit', '', 'Friday', '09:00', false);

-- VL8: pinned Thursday 16:00
INSERT INTO subject (name, code, department, semester, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, pin_days, pin_slot, deleted)
VALUES ('VL/Spoken Tutorial',                    'VL8',  'IT', 8, 0, 1, 1, 'Audit', '', 'Thursday', '16:00', false);

-- Project Stage II: pinned Friday (theory sessions on Friday + batch rotation Mon-Thu)
-- pin_days without pin_slot means theory must land on those days
INSERT INTO subject (name, code, department, semester, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, pin_days, deleted)
VALUES ('Project Stage II',                      'PS2',  'IT', 8, 5, 2, 2, 'Lab', '', 'Friday', false);

-- Lab subjects for BE-IT
-- LP-V: 2 sessions × 2hr = 4 practical hrs/batch/week (SRK for B1/B3, AAK for B2/B4)
INSERT INTO subject (name, code, department, semester, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
VALUES ('LP-V',                                  'LP5',  'IT', 8, 0, 4, 2, 'Lab', 'SRK', false);

-- LP-VI: 1 session × 2hr = 2 practical hrs/batch/week (PGK for B1/B2, RAN for B3/B4)
INSERT INTO subject (name, code, department, semester, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
VALUES ('LP-VI',                                 'LP6',  'IT', 8, 0, 2, 2, 'Lab', 'PGK', false);

-- ============================================================
-- 8. SUBJECT-FACULTY ASSIGNMENTS  (batch-wise lab faculty)
--
-- These override the default subject.faculty for specific batches.
-- The generator picks faculty from here first (SFA), falling back
-- to subject.faculty for batches not listed.
-- ============================================================

-- ─── SE-IT labs ─────────────────────────────────────────────

-- DMSL: SSK for S1/S2, ARD for S3/S4
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'SE-IT', 'S1', 'SSK' FROM subject s WHERE s.code = 'DMSL' AND s.semester = 4;
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'SE-IT', 'S2', 'SSK' FROM subject s WHERE s.code = 'DMSL' AND s.semester = 4;
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'SE-IT', 'S3', 'ARD' FROM subject s WHERE s.code = 'DMSL' AND s.semester = 4;
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'SE-IT', 'S4', 'ARD' FROM subject s WHERE s.code = 'DMSL' AND s.semester = 4;

-- ECL: ANK for S1/S2, AAK for S3/S4
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'SE-IT', 'S1', 'ANK' FROM subject s WHERE s.code = 'ECL' AND s.semester = 4;
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'SE-IT', 'S2', 'ANK' FROM subject s WHERE s.code = 'ECL' AND s.semester = 4;
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'SE-IT', 'S3', 'AAK' FROM subject s WHERE s.code = 'ECL' AND s.semester = 4;
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'SE-IT', 'S4', 'AAK' FROM subject s WHERE s.code = 'ECL' AND s.semester = 4;

-- MILL: RSL for S1/S2, SRK for S3/S4
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'SE-IT', 'S1', 'RSL' FROM subject s WHERE s.code = 'MILL' AND s.semester = 4;
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'SE-IT', 'S2', 'RSL' FROM subject s WHERE s.code = 'MILL' AND s.semester = 4;
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'SE-IT', 'S3', 'SRK' FROM subject s WHERE s.code = 'MILL' AND s.semester = 4;
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'SE-IT', 'S4', 'SRK' FROM subject s WHERE s.code = 'MILL' AND s.semester = 4;

-- CGL: PGK for all batches (default already PGK, explicit here for clarity)
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'SE-IT', 'S1', 'PGK' FROM subject s WHERE s.code = 'CGL' AND s.semester = 4;
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'SE-IT', 'S2', 'PGK' FROM subject s WHERE s.code = 'CGL' AND s.semester = 4;
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'SE-IT', 'S3', 'PGK' FROM subject s WHERE s.code = 'CGL' AND s.semester = 4;
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'SE-IT', 'S4', 'PGK' FROM subject s WHERE s.code = 'CGL' AND s.semester = 4;

-- DBMSL: RAN for all batches
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'SE-IT', 'S1', 'RAN' FROM subject s WHERE s.code = 'DBMSL' AND s.semester = 4;
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'SE-IT', 'S2', 'RAN' FROM subject s WHERE s.code = 'DBMSL' AND s.semester = 4;
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'SE-IT', 'S3', 'RAN' FROM subject s WHERE s.code = 'DBMSL' AND s.semester = 4;
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'SE-IT', 'S4', 'RAN' FROM subject s WHERE s.code = 'DBMSL' AND s.semester = 4;

-- ─── TE-IT labs ─────────────────────────────────────────────

-- CNSL: RSL for T1/T2, SSK for T3/T4
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'TE-IT', 'T1', 'RSL' FROM subject s WHERE s.code = 'CNSL' AND s.semester = 6;
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'TE-IT', 'T2', 'RSL' FROM subject s WHERE s.code = 'CNSL' AND s.semester = 6;
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'TE-IT', 'T3', 'SSK' FROM subject s WHERE s.code = 'CNSL' AND s.semester = 6;
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'TE-IT', 'T4', 'SSK' FROM subject s WHERE s.code = 'CNSL' AND s.semester = 6;

-- DSBDAL: DPR for all batches
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'TE-IT', 'T1', 'DPR' FROM subject s WHERE s.code = 'DSBDAL' AND s.semester = 6;
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'TE-IT', 'T2', 'DPR' FROM subject s WHERE s.code = 'DSBDAL' AND s.semester = 6;
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'TE-IT', 'T3', 'DPR' FROM subject s WHERE s.code = 'DSBDAL' AND s.semester = 6;
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'TE-IT', 'T4', 'DPR' FROM subject s WHERE s.code = 'DSBDAL' AND s.semester = 6;

-- LP-II: ARD (EL-II track) + ANK (WAD track) — each batch gets both sessions
-- 2 SFA entries per batch so generator assigns different faculty each session
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'TE-IT', 'T1', 'ARD' FROM subject s WHERE s.code = 'LP2' AND s.semester = 6;
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'TE-IT', 'T1', 'ANK' FROM subject s WHERE s.code = 'LP2' AND s.semester = 6;
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'TE-IT', 'T2', 'ARD' FROM subject s WHERE s.code = 'LP2' AND s.semester = 6;
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'TE-IT', 'T2', 'ANK' FROM subject s WHERE s.code = 'LP2' AND s.semester = 6;
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'TE-IT', 'T3', 'ARD' FROM subject s WHERE s.code = 'LP2' AND s.semester = 6;
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'TE-IT', 'T3', 'ANK' FROM subject s WHERE s.code = 'LP2' AND s.semester = 6;
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'TE-IT', 'T4', 'ARD' FROM subject s WHERE s.code = 'LP2' AND s.semester = 6;
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'TE-IT', 'T4', 'ANK' FROM subject s WHERE s.code = 'LP2' AND s.semester = 6;

-- ─── BE-IT labs ─────────────────────────────────────────────

-- LP-V: SRK for B1/B3, AAK for B2/B4  (max 2 batches same window — 2 faculty)
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'BE-IT', 'B1', 'SRK' FROM subject s WHERE s.code = 'LP5' AND s.semester = 8;
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'BE-IT', 'B2', 'AAK' FROM subject s WHERE s.code = 'LP5' AND s.semester = 8;
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'BE-IT', 'B3', 'SRK' FROM subject s WHERE s.code = 'LP5' AND s.semester = 8;
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'BE-IT', 'B4', 'AAK' FROM subject s WHERE s.code = 'LP5' AND s.semester = 8;

-- LP-VI: PGK for B1/B2, RAN for B3/B4
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'BE-IT', 'B1', 'PGK' FROM subject s WHERE s.code = 'LP6' AND s.semester = 8;
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'BE-IT', 'B2', 'PGK' FROM subject s WHERE s.code = 'LP6' AND s.semester = 8;
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'BE-IT', 'B3', 'RAN' FROM subject s WHERE s.code = 'LP6' AND s.semester = 8;
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'BE-IT', 'B4', 'RAN' FROM subject s WHERE s.code = 'LP6' AND s.semester = 8;

-- PS2: no faculty (multiple supervisors, generator leaves blank)
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'BE-IT', 'B1', '' FROM subject s WHERE s.code = 'PS2' AND s.semester = 8;
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'BE-IT', 'B2', '' FROM subject s WHERE s.code = 'PS2' AND s.semester = 8;
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'BE-IT', 'B3', '' FROM subject s WHERE s.code = 'PS2' AND s.semester = 8;
INSERT INTO subject_faculty_assignment (subject_id, division_name, batch, faculty_name)
SELECT s.id, 'BE-IT', 'B4', '' FROM subject s WHERE s.code = 'PS2' AND s.semester = 8;

-- ============================================================
-- Done. Run the generator at http://localhost:8765/auto-generate
-- Expected: ~205/207 sessions placed, 0 faculty conflicts
-- ============================================================
