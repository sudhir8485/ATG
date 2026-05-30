-- ============================================================
-- ATG Project — Seed Data
-- Source: Master Timetable ACA/R/003A  (IT.xlsx → 03A_MASTER)
-- Department of IT, APCOER Parvati Pune, A.Y. 2025-26 SEM-II
-- ============================================================

-- 1. ADMIN
INSERT INTO users (name, username, password, role, department, deleted)
SELECT 'System Admin', 'admin', 'admin123', 'ADMIN', NULL, false
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin');

-- 2. DEPARTMENT
INSERT INTO department (name, code, hod)
SELECT 'Information Technology', 'IT', 'Dr. A. A. Kadam'
WHERE NOT EXISTS (SELECT 1 FROM department WHERE code = 'IT');

-- 3. HOD
INSERT INTO users (name, username, password, role, department, deleted)
SELECT 'Dr. A. A. Kadam', 'hod_it', 'hod123', 'HOD', 'Information Technology', false
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'hod_it');

-- ============================================================
-- 4. FACULTY
-- ============================================================

-- PGK: CG + CG Lab (SE), EAC Honours (TE), LP-VI B1,B2 (BE)
INSERT INTO users (name, username, password, role, department, subjects_handled, deleted)
SELECT 'Prof. P. G. Khaire', 'pgk', 'fac123', 'FACULTY', 'Information Technology',
       'Computer Graphics,Computer Graphics Lab,EAC Honours,Lab Practice VI', false
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'pgk');

-- RAN: DBMS + DBMS Lab (SE), Elective VI + LP-VI B3,B4 (BE)
INSERT INTO users (name, username, password, role, department, subjects_handled, deleted)
SELECT 'Prof. R. A. Nikam', 'ran', 'fac123', 'FACULTY', 'Information Technology',
       'Database Management System,Database Management System Lab,Elective VI,Lab Practice VI', false
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'ran');

-- SSK: PA + DM&SM Lab (SE, T3/T4 batches), CNS Lab T3,T4 (TE), Elective V (BE)
INSERT INTO users (name, username, password, role, department, subjects_handled, deleted)
SELECT 'Prof. S. S. Khote', 'ssk', 'fac123', 'FACULTY', 'Information Technology',
       'Principles of Analog Electronics,Discrete Mathematics & Simulation Lab,Computer Networks & Security Lab,Elective V', false
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'ssk');

-- ARD: DM&SM Lab S1,S2 (SE), Elective II + LP-II(EL-II) (TE)
INSERT INTO users (name, username, password, role, department, subjects_handled, deleted)
SELECT 'Prof. A. R. Dodke', 'ard', 'fac123', 'FACULTY', 'Information Technology',
       'Discrete Mathematics & Simulation Lab,Elective II,Laboratory Practice-II', false
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'ard');

-- RSL: MIL + MIL Lab S1,S2 (SE), CNS + CNS Lab T1,T2 (TE)
INSERT INTO users (name, username, password, role, department, subjects_handled, deleted)
SELECT 'Prof. R. S. Lavhe', 'rsl', 'fac123', 'FACULTY', 'Information Technology',
       'Modern Indian Language,Modern Indian Language Lab,Computer Networks & Security,Computer Networks & Security Lab', false
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'rsl');

-- SRK: OE-II + MIL Lab S3,S4 (SE), Distributed Systems + LP-V B1,B3 (BE)
INSERT INTO users (name, username, password, role, department, subjects_handled, deleted)
SELECT 'Dr. S. R. Kokane', 'srk', 'fac123', 'FACULTY', 'Information Technology',
       'Open Elective-II,Modern Indian Language Lab,Distributed Systems,Lab Practice V', false
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'srk');

-- RK: P&S (SE)
INSERT INTO users (name, username, password, role, department, subjects_handled, deleted)
SELECT 'Prof. Rashmi Kenvat', 'rk', 'fac123', 'FACULTY', 'Information Technology',
       'Probability & Statistics', false
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'rk');

-- DPR: ES (SE), DSBDA + DS & BDA-Lab (TE), Seminar (BE)
INSERT INTO users (name, username, password, role, department, subjects_handled, deleted)
SELECT 'Prof. D. P. Rankhambe', 'dpr', 'fac123', 'FACULTY', 'Information Technology',
       'Engineering Sciences,Data Science and Big Data Analytics,DS & BDA-Lab,Seminar', false
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'dpr');

-- ANK: EC + EC Lab S1,S2 (SE), WAD + LP-II(WAD) + Internship (TE), ISM Honours (BE)
INSERT INTO users (name, username, password, role, department, subjects_handled, deleted)
SELECT 'Prof. A. N. Kalal', 'ank', 'fac123', 'FACULTY', 'Information Technology',
       'Electronics Circuits,Electronics Circuits Lab,Web Application Development,Laboratory Practice-II,Internship,ISM Honours', false
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'ank');

-- AAK: EC + EC Lab S3,S4 (SE), Software Engineering + LP-V B2,B4 (BE)
INSERT INTO users (name, username, password, role, department, subjects_handled, deleted)
SELECT 'Dr. A. A. Kadam', 'aak', 'fac123', 'FACULTY', 'Information Technology',
       'Electronics Circuits,Electronics Circuits Lab,Software Engineering,Lab Practice V', false
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'aak');

-- SMD: Elective II (TE)
INSERT INTO users (name, username, password, role, department, subjects_handled, deleted)
SELECT 'Prof. S. M. Dhane', 'smd', 'fac123', 'FACULTY', 'Information Technology',
       'Elective II', false
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'smd');

-- Library Coordinator: dedicated to Library rotation slots so real faculty stay free
INSERT INTO users (name, username, password, role, department, subjects_handled, deleted)
SELECT 'Library Coordinator', 'lib_coord', 'lib123', 'FACULTY', 'Information Technology',
       'Library', false
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'lib_coord');
UPDATE users SET subjects_handled='Library' WHERE username='lib_coord';

-- Force-refresh subjects_handled for all faculty
UPDATE users SET subjects_handled = 'Computer Graphics,Computer Graphics Lab,EAC Honours,Lab Practice VI'
    WHERE username = 'pgk';
UPDATE users SET subjects_handled = 'Database Management System,Database Management System Lab,Elective VI,Lab Practice VI'
    WHERE username = 'ran';
UPDATE users SET subjects_handled = 'Principles of Analog Electronics,Discrete Mathematics & Simulation,Discrete Mathematics & Simulation Lab,Computer Networks & Security Lab,Elective V'
    WHERE username = 'ssk';
UPDATE users SET subjects_handled = 'Discrete Mathematics & Simulation,Discrete Mathematics & Simulation Lab,Elective II,Laboratory Practice-II'
    WHERE username = 'ard';
UPDATE users SET subjects_handled = 'Modern Indian Language,Modern Indian Language Lab,Computer Networks & Security,Computer Networks & Security Lab'
    WHERE username = 'rsl';
UPDATE users SET subjects_handled = 'Open Elective-II,Modern Indian Language Lab,Distributed Systems,Lab Practice V'
    WHERE username = 'srk';
UPDATE users SET subjects_handled = 'Probability & Statistics'
    WHERE username = 'rk';
UPDATE users SET subjects_handled = 'Engineering Sciences,Data Science and Big Data Analytics,DS & BDA-Lab,Seminar,Internship'
    WHERE username = 'dpr';
UPDATE users SET subjects_handled = 'Electronics Circuits,Electronics Circuits Lab,Web Application Development,Laboratory Practice-II,Internship,ISM Honours'
    WHERE username = 'ank';
UPDATE users SET subjects_handled = 'Electronics Circuits,Electronics Circuits Lab,Software Engineering,Lab Practice V'
    WHERE username = 'aak';
UPDATE users SET subjects_handled = 'Elective II,Laboratory Practice-II'
    WHERE username = 'smd';

-- ============================================================
-- 5. DIVISIONS
-- labPreference: AFTERNOON (SE), MIDDAY (TE), MORNING (BE)
-- ============================================================
INSERT INTO division (name, year, department, semester_number, capacity, batch_count, batch_prefix, lab_preference)
SELECT 'SE-IT', 'Second Year', 'Information Technology', 4, 75, 4, 'S', 'AFTERNOON'
WHERE NOT EXISTS (SELECT 1 FROM division WHERE name = 'SE-IT');

INSERT INTO division (name, year, department, semester_number, capacity, batch_count, batch_prefix, lab_preference)
SELECT 'TE-IT', 'Third Year', 'Information Technology', 6, 75, 4, 'T', 'MIDDAY'
WHERE NOT EXISTS (SELECT 1 FROM division WHERE name = 'TE-IT');

INSERT INTO division (name, year, department, semester_number, capacity, batch_count, batch_prefix, lab_preference)
SELECT 'BE-IT', 'Final Year', 'Information Technology', 8, 75, 4, 'B', 'MORNING'
WHERE NOT EXISTS (SELECT 1 FROM division WHERE name = 'BE-IT');

-- Update labPreference for existing rows
UPDATE division SET lab_preference = 'AFTERNOON' WHERE name = 'SE-IT';
UPDATE division SET lab_preference = 'MIDDAY'    WHERE name = 'TE-IT';
UPDATE division SET lab_preference = 'MORNING'   WHERE name = 'BE-IT';

-- ============================================================
-- 6. CLASSROOMS
-- Reference timetable uses 6 distinct lab rooms (Distributed Lab, Network Lab,
-- WET Lab, Software Testing Lab, Multimedia Lab, OS Lab). We map these to 8
-- lab rooms so multiple divisions can run simultaneous labs without room conflicts.
-- (17 rotation slots across SE/TE/BE require up to 2 divisions sharing a window.)
-- ============================================================
INSERT INTO classroom (room, building, capacity, type)
SELECT 'Room IT-305', 'IT Block', 75, 'Lecture'
WHERE NOT EXISTS (SELECT 1 FROM classroom WHERE room = 'Room IT-305');

INSERT INTO classroom (room, building, capacity, type)
SELECT 'Lab WET-1', 'IT Block', 20, 'Lab'
WHERE NOT EXISTS (SELECT 1 FROM classroom WHERE room = 'Lab WET-1');

INSERT INTO classroom (room, building, capacity, type)
SELECT 'Lab WET-2', 'IT Block', 20, 'Lab'
WHERE NOT EXISTS (SELECT 1 FROM classroom WHERE room = 'Lab WET-2');

INSERT INTO classroom (room, building, capacity, type)
SELECT 'Lab IT-201', 'IT Block', 20, 'Lab'
WHERE NOT EXISTS (SELECT 1 FROM classroom WHERE room = 'Lab IT-201');

INSERT INTO classroom (room, building, capacity, type)
SELECT 'Lab IT-202', 'IT Block', 20, 'Lab'
WHERE NOT EXISTS (SELECT 1 FROM classroom WHERE room = 'Lab IT-202');

INSERT INTO classroom (room, building, capacity, type)
SELECT 'Lab IT-203', 'IT Block', 20, 'Lab'
WHERE NOT EXISTS (SELECT 1 FROM classroom WHERE room = 'Lab IT-203');

INSERT INTO classroom (room, building, capacity, type)
SELECT 'Lab IT-204', 'IT Block', 20, 'Lab'
WHERE NOT EXISTS (SELECT 1 FROM classroom WHERE room = 'Lab IT-204');

INSERT INTO classroom (room, building, capacity, type)
SELECT 'Lab IT-205', 'IT Block', 20, 'Lab'
WHERE NOT EXISTS (SELECT 1 FROM classroom WHERE room = 'Lab IT-205');

INSERT INTO classroom (room, building, capacity, type)
SELECT 'Lab IT-206', 'IT Block', 20, 'Lab'
WHERE NOT EXISTS (SELECT 1 FROM classroom WHERE room = 'Lab IT-206');

-- ============================================================
-- 7. TIMESLOTS (from 03A_MASTER column headers)
-- ============================================================
INSERT INTO timeslot (start_time, end_time, is_break)
SELECT '09:00', '10:00', false
WHERE NOT EXISTS (SELECT 1 FROM timeslot WHERE start_time='09:00' AND end_time='10:00');

INSERT INTO timeslot (start_time, end_time, is_break)
SELECT '10:00', '11:00', false
WHERE NOT EXISTS (SELECT 1 FROM timeslot WHERE start_time='10:00' AND end_time='11:00');

INSERT INTO timeslot (start_time, end_time, is_break)
SELECT '11:00', '11:15', true
WHERE NOT EXISTS (SELECT 1 FROM timeslot WHERE start_time='11:00' AND end_time='11:15');

INSERT INTO timeslot (start_time, end_time, is_break)
SELECT '11:15', '12:15', false
WHERE NOT EXISTS (SELECT 1 FROM timeslot WHERE start_time='11:15' AND end_time='12:15');

INSERT INTO timeslot (start_time, end_time, is_break)
SELECT '12:15', '13:15', false
WHERE NOT EXISTS (SELECT 1 FROM timeslot WHERE start_time='12:15' AND end_time='13:15');

INSERT INTO timeslot (start_time, end_time, is_break)
SELECT '13:15', '14:00', true
WHERE NOT EXISTS (SELECT 1 FROM timeslot WHERE start_time='13:15' AND end_time='14:00');

INSERT INTO timeslot (start_time, end_time, is_break)
SELECT '14:00', '15:00', false
WHERE NOT EXISTS (SELECT 1 FROM timeslot WHERE start_time='14:00' AND end_time='15:00');

INSERT INTO timeslot (start_time, end_time, is_break)
SELECT '15:00', '16:00', false
WHERE NOT EXISTS (SELECT 1 FROM timeslot WHERE start_time='15:00' AND end_time='16:00');

INSERT INTO timeslot (start_time, end_time, is_break)
SELECT '16:00', '17:00', false
WHERE NOT EXISTS (SELECT 1 FROM timeslot WHERE start_time='16:00' AND end_time='17:00');

-- ============================================================
-- 8. ACADEMIC SETTING
-- ============================================================
INSERT INTO academic_setting (id, working_days)
SELECT 1, 'Monday,Tuesday,Wednesday,Thursday,Friday'
WHERE NOT EXISTS (SELECT 1 FROM academic_setting WHERE id = 1);

-- ============================================================
-- 9. SUBJECTS — SE SEM-4
-- Hours verified against IT.xlsx 03A_MASTER
-- ============================================================

-- Theory subjects (lecture_hours_per_week = sessions per week from master timetable)
-- CG: Mon 9-10, Tue 11:15, Wed 11:15 = 3 sessions
INSERT INTO subject (name, code, department, semester, credits, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, deleted)
SELECT 'Computer Graphics', 'CG', 'Information Technology', 4, 3, 3, 0, 0, 'Theory', false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='CG' AND semester=4);

-- PA: Mon 10-11, Tue 10-11, Wed 9-10 = 3 sessions
INSERT INTO subject (name, code, department, semester, credits, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, deleted)
SELECT 'Principles of Analog Electronics', 'PAE', 'Information Technology', 4, 3, 3, 0, 0, 'Theory', false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='PAE' AND semester=4);

-- DBMS: Tue 9-10, Wed 12:15, Thu 12:15 = 3 sessions
INSERT INTO subject (name, code, department, semester, credits, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, deleted)
SELECT 'Database Management System', 'DBMS', 'Information Technology', 4, 3, 3, 0, 0, 'Theory', false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='DBMS' AND semester=4);

-- DM&SM: Mon 11:15(OE-II slot shared), actually: appears as theory separately.
-- From master: not shown as dedicated theory slot — only as lab.
-- But DM&SM IS a theory subject (PA shares SSK). Setting 3 sessions.
INSERT INTO subject (name, code, department, semester, credits, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, deleted)
SELECT 'Discrete Mathematics & Simulation', 'DMS', 'Information Technology', 4, 3, 3, 0, 0, 'Theory', false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='DMS' AND semester=4);

-- P&S: Mon 12:15, Wed 10-11, Wed 4-5, Fri 12:15 = 4 sessions
INSERT INTO subject (name, code, department, semester, credits, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, deleted)
SELECT 'Probability & Statistics', 'PS', 'Information Technology', 4, 2, 4, 0, 0, 'Theory', false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='PS' AND semester=4);

-- ES: Tue 4-5, Thu 11:15 = 2 sessions
INSERT INTO subject (name, code, department, semester, credits, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, deleted)
SELECT 'Engineering Sciences', 'ES', 'Information Technology', 4, 3, 2, 0, 0, 'Theory', false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='ES' AND semester=4);

-- EC: Thu 4-5 = 1 session
INSERT INTO subject (name, code, department, semester, credits, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, deleted)
SELECT 'Electronics Circuits', 'EC', 'Information Technology', 4, 3, 1, 0, 0, 'Theory', false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='EC' AND semester=4);

-- OE-II: Mon 11:15, Tue 12:15, Fri 11:15 = 3 sessions
INSERT INTO subject (name, code, department, semester, credits, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, deleted)
SELECT 'Open Elective-II', 'OE2', 'Information Technology', 4, 2, 3, 0, 0, 'Elective', false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='OE2' AND semester=4);

-- MIL: Mon 4-5 = 1 session
INSERT INTO subject (name, code, department, semester, credits, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, deleted)
SELECT 'Modern Indian Language', 'MIL', 'Information Technology', 4, 2, 1, 0, 0, 'Theory', false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='MIL' AND semester=4);

-- ============================================================
-- SE Labs — each batch gets each lab once per week (2 hrs = 1 block)
-- EXCEPT DM&SM Lab: each batch gets it TWICE per week (4 hrs = 2 blocks)
-- ============================================================

-- CGL: 1 block per batch/week  → practical_hours=2, slot_duration=2
INSERT INTO subject (name, code, department, semester, credits, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, deleted)
SELECT 'Computer Graphics Lab', 'CGL', 'Information Technology', 4, 1, 0, 2, 2, 'Lab', false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='CGL' AND semester=4);

-- DBMSL: 1 block per batch/week → 2/2
INSERT INTO subject (name, code, department, semester, credits, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, deleted)
SELECT 'Database Management System Lab', 'DBMSL', 'Information Technology', 4, 1, 0, 2, 2, 'Lab', false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='DBMSL' AND semester=4);

-- DMSL: 2 blocks per batch/week (appears twice in rotation) → practical_hours=4, slot_duration=2
INSERT INTO subject (name, code, department, semester, credits, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, deleted)
SELECT 'Discrete Mathematics & Simulation Lab', 'DMSL', 'Information Technology', 4, 1, 0, 4, 2, 'Lab', false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='DMSL' AND semester=4);

-- MILL: 1 block per batch/week → 2/2
INSERT INTO subject (name, code, department, semester, credits, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, deleted)
SELECT 'Modern Indian Language Lab', 'MILL', 'Information Technology', 4, 1, 0, 2, 2, 'Lab', false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='MILL' AND semester=4);

-- ECL (Electronics Circuits Lab): 1 block per batch/week → 2/2 — NEW (was missing)
INSERT INTO subject (name, code, department, semester, credits, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, deleted)
SELECT 'Electronics Circuits Lab', 'ECL', 'Information Technology', 4, 1, 0, 2, 2, 'Lab', false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='ECL' AND semester=4);

-- Library SE: 1 block per batch/week (self-study/library rotation) — NEW
INSERT INTO subject (name, code, department, semester, credits, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, deleted)
SELECT 'Library', 'LIBSE', 'Information Technology', 4, 0, 0, 2, 2, 'Lab', false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='LIBSE' AND semester=4);

-- ============================================================
-- SE Audit/Filler — whole-class, 1-slot blocks
-- lecture_hours=0 so generator does NOT treat as theory sessions
-- practical_hours=N, slot_duration=1 → placed as 1-hour whole-class blocks
-- ============================================================

-- Virtual Lab / Spoken Tutorial: 1 slot/week for SE (Friday 4-5 PM)
INSERT INTO subject (name, code, department, semester, credits, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, deleted)
SELECT 'Virtual Lab / Spoken Tutorial', 'VL4', 'Information Technology', 4, 0, 0, 1, 1, 'Audit', false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='VL4' AND semester=4);

-- ============================================================
-- 10. SUBJECTS — TE SEM-6
-- ============================================================

-- CNS: Mon 9-10, Tue 10-11, Wed 10-11 = 3 theory sessions (RSL teaches)
INSERT INTO subject (name, code, department, semester, credits, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, deleted)
SELECT 'Computer Networks & Security', 'CNS', 'Information Technology', 6, 3, 3, 0, 0, 'Theory', false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='CNS' AND semester=6);

-- WAD: Mon 10-11, Wed 9-10, Thu 10-11 = 3 sessions
INSERT INTO subject (name, code, department, semester, credits, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, deleted)
SELECT 'Web Application Development', 'WAD', 'Information Technology', 6, 3, 3, 0, 0, 'Theory', false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='WAD' AND semester=6);

-- DSBDA: Tue 9-10, Wed 3-4, Fri 10-11 = 3 sessions (+ Thu slot = 3 total from master)
INSERT INTO subject (name, code, department, semester, credits, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, deleted)
SELECT 'Data Science and Big Data Analytics', 'DSBDA', 'Information Technology', 6, 3, 3, 0, 0, 'Theory', false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='DSBDA' AND semester=6);

-- Elective II: Wed 2-3, Thu 4-5, Fri 9-10 = 3 sessions (ARD + SMD share)
INSERT INTO subject (name, code, department, semester, credits, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, deleted)
SELECT 'Elective II', 'EL2', 'Information Technology', 6, 3, 3, 0, 0, 'Elective', false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='EL2' AND semester=6);

-- EAC Honours: Mon 4-5, Tue 4-5, Wed 4-5, Thu 9-10 = 4 sessions
INSERT INTO subject (name, code, department, semester, credits, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, deleted)
SELECT 'EAC Honours', 'EACH', 'Information Technology', 6, 2, 4, 0, 0, 'Honours', false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='EACH' AND semester=6);

-- TE Labs
-- CNSL: each batch gets it TWICE per week → practical_hours=4, slot_duration=2
INSERT INTO subject (name, code, department, semester, credits, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, deleted)
SELECT 'Computer Networks & Security Lab', 'CNSL', 'Information Technology', 6, 2, 0, 4, 2, 'Lab', false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='CNSL' AND semester=6);

-- DSBDAL: 1 block per batch → 2/2
INSERT INTO subject (name, code, department, semester, credits, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, deleted)
SELECT 'DS & BDA-Lab', 'DSBDAL', 'Information Technology', 6, 1, 0, 2, 2, 'Lab', false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='DSBDAL' AND semester=6);

-- LP-II(WAD): 1 block per batch → 2/2
INSERT INTO subject (name, code, department, semester, credits, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, deleted)
SELECT 'Laboratory Practice-II', 'LP2', 'Information Technology', 6, 2, 0, 2, 2, 'Lab', false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='LP2' AND semester=6);

-- Library TE: 1 block per batch → 2/2 — NEW
INSERT INTO subject (name, code, department, semester, credits, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, deleted)
SELECT 'Library', 'LIBTE', 'Information Technology', 6, 0, 0, 2, 2, 'Lab', false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='LIBTE' AND semester=6);

-- TE Audit/Special — whole-class 1-slot blocks (lecture_hours=0)
-- INTERNSHIP: Mon 2-3 + Thu 2-3 = 2 slots/week
INSERT INTO subject (name, code, department, semester, credits, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, deleted)
SELECT 'Internship', 'INTP', 'Information Technology', 6, 0, 0, 2, 1, 'Audit', false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='INTP' AND semester=6);

-- Audit Course VI: Fri 2-3 = 1 slot/week
INSERT INTO subject (name, code, department, semester, credits, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, deleted)
SELECT 'Audit Course VI', 'AC6', 'Information Technology', 6, 0, 0, 1, 1, 'Audit', false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='AC6' AND semester=6);

-- Virtual Lab TE: Fri 4-5 = 1 slot/week
INSERT INTO subject (name, code, department, semester, credits, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, deleted)
SELECT 'Virtual Lab / Spoken Tutorial', 'VL6', 'Information Technology', 6, 0, 0, 1, 1, 'Audit', false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='VL6' AND semester=6);

-- ============================================================
-- 11. SUBJECTS — BE SEM-8
-- ============================================================

-- SE (Software Engineering): Mon 11:15, Tue 2-3, Wed 2-3 = 3 sessions
INSERT INTO subject (name, code, department, semester, credits, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, deleted)
SELECT 'Software Engineering', 'SE8', 'Information Technology', 8, 3, 3, 0, 0, 'Theory', false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='SE8' AND semester=8);

-- DS (Distributed Systems): Tue 3-4, Wed 11:15, Thu 12:15 = 3 sessions
INSERT INTO subject (name, code, department, semester, credits, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, deleted)
SELECT 'Distributed Systems', 'DS8', 'Information Technology', 8, 3, 3, 0, 0, 'Theory', false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='DS8' AND semester=8);

-- Elective V: Mon 2-3, Tue 4-5, Wed 12:15 = 3 sessions
INSERT INTO subject (name, code, department, semester, credits, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, deleted)
SELECT 'Elective V', 'EL5', 'Information Technology', 8, 3, 3, 0, 0, 'Elective', false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='EL5' AND semester=8);

-- Elective VI: Mon 12:15, Tue 12:15, Thu 11:15 = 3 sessions
INSERT INTO subject (name, code, department, semester, credits, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, deleted)
SELECT 'Elective VI', 'EL6', 'Information Technology', 8, 3, 3, 0, 0, 'Elective', false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='EL6' AND semester=8);

-- ISM Honours: Mon 3-4, Mon 4-5, Tue 11:15, Wed 3-4 = 4 sessions
INSERT INTO subject (name, code, department, semester, credits, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, deleted)
SELECT 'ISM Honours', 'ISMH', 'Information Technology', 8, 2, 4, 0, 0, 'Honours', false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='ISMH' AND semester=8);

-- BE Labs
-- LP-V: each batch gets it TWICE per week → practical_hours=4, slot_duration=2
INSERT INTO subject (name, code, department, semester, credits, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, deleted)
SELECT 'Lab Practice V', 'LP5', 'Information Technology', 8, 2, 0, 4, 2, 'Lab', false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='LP5' AND semester=8);

-- LP-VI: 1 block per batch/week → 2/2
INSERT INTO subject (name, code, department, semester, credits, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, deleted)
SELECT 'Lab Practice VI', 'LP6', 'Information Technology', 8, 1, 0, 2, 2, 'Lab', false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='LP6' AND semester=8);

-- PS-II (Project Stage II):
--   Morning rotation: 1 block per batch (Mon-Thu AM)  → practical_hours=2, slot_duration=2
--   Friday whole-class: 2 additional theory sessions  → lecture_hours=2
INSERT INTO subject (name, code, department, semester, credits, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, deleted)
SELECT 'Project Stage II', 'PS2', 'Information Technology', 8, 5, 2, 2, 2, 'Project', false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='PS2' AND semester=8);

-- BE Audit/Special
-- Seminar: Thu 2-3 = 1 slot/week
INSERT INTO subject (name, code, department, semester, credits, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, deleted)
SELECT 'Seminar', 'SEM8', 'Information Technology', 8, 1, 0, 1, 1, 'Seminar', false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='SEM8' AND semester=8);

-- T&P: Wed 4-5 = 1 slot/week
INSERT INTO subject (name, code, department, semester, credits, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, deleted)
SELECT 'Training & Placement', 'TP8', 'Information Technology', 8, 0, 0, 1, 1, 'Audit', false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='TP8' AND semester=8);

-- Audit Course VIII: Fri 9-10 = 1 slot/week
INSERT INTO subject (name, code, department, semester, credits, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, deleted)
SELECT 'Audit Course VIII', 'AC8', 'Information Technology', 8, 0, 0, 1, 1, 'Audit', false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='AC8' AND semester=8);

-- Virtual Lab BE: Thu 4-5 = 1 slot/week
INSERT INTO subject (name, code, department, semester, credits, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, deleted)
SELECT 'Virtual Lab / Spoken Tutorial', 'VL8', 'Information Technology', 8, 0, 0, 1, 1, 'Audit', false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='VL8' AND semester=8);

-- ============================================================
-- Update subject hours for existing rows (in case DB already seeded)
-- ============================================================

-- DMS theory: reference has no theory sessions for DM&SM (only labs via DMSL)
UPDATE subject SET lecture_hours_per_week=0 WHERE code='DMS' AND semester=4;

-- AC4 was an old audit placeholder — not in the reference IT.xlsx timetable; soft-delete it
-- to free the slot that Virtual Lab / Spoken Tutorial (VL4) needs.
UPDATE subject SET deleted=true WHERE code='AC4' AND semester=4;

-- Fix DMSL: was 2, now 4 (2 blocks/week per batch)
UPDATE subject SET practical_hours_per_week=4, practical_slot_duration=2 WHERE code='DMSL' AND semester=4;

-- Fix CNSL: was 2, now 4 (2 blocks/week per batch)
UPDATE subject SET practical_hours_per_week=4, practical_slot_duration=2 WHERE code='CNSL' AND semester=6;

-- Fix LP5: was 2, now 4 (2 blocks/week per batch)
UPDATE subject SET practical_hours_per_week=4, practical_slot_duration=2 WHERE code='LP5' AND semester=8;

-- Fix LP2: each TE batch does LP-II twice (WAD track + EL-II track) per reference timetable
UPDATE subject SET practical_hours_per_week=4, practical_slot_duration=2 WHERE code='LP2' AND semester=6;

-- Fix PS2: lecture was 5 (wrong), now 2 (Friday whole-class sessions only)
UPDATE subject SET lecture_hours_per_week=2, practical_hours_per_week=2, practical_slot_duration=2 WHERE code='PS2' AND semester=8;

-- Reset any stale values from previous attempts, then set final correct hours:
UPDATE subject SET lecture_hours_per_week=3 WHERE code='SE8'   AND semester=8;
UPDATE subject SET lecture_hours_per_week=3 WHERE code='DS8'   AND semester=8;
UPDATE subject SET lecture_hours_per_week=4 WHERE code='ISMH'  AND semester=8;
UPDATE subject SET lecture_hours_per_week=3 WHERE code='CNS'   AND semester=6;
UPDATE subject SET lecture_hours_per_week=3 WHERE code='WAD'   AND semester=6;
UPDATE subject SET lecture_hours_per_week=3 WHERE code='DSBDA' AND semester=6;
UPDATE subject SET lecture_hours_per_week=3 WHERE code='EL5'   AND semester=8;
UPDATE subject SET lecture_hours_per_week=3 WHERE code='EL6'   AND semester=8;
UPDATE subject SET lecture_hours_per_week=3 WHERE code='EL2'   AND semester=6;

-- Fill remaining empty slots so every class has a full week:
-- BE-IT: 30/35 → need 5 more. EL5 (+3 via SSK) + EL6 (+2 via RAN).
UPDATE subject SET lecture_hours_per_week=6 WHERE code='EL5' AND semester=8;
UPDATE subject SET lecture_hours_per_week=5 WHERE code='EL6' AND semester=8;
-- TE-IT: 32/35 → need 3 more. EL2 (+2 via SMD) + EACH (+1 via PGK).
-- Split across two subjects avoids overloading SMD and leaves ANK free for INTP.
UPDATE subject SET lecture_hours_per_week=5 WHERE code='EL2'  AND semester=6;
UPDATE subject SET lecture_hours_per_week=5 WHERE code='EACH' AND semester=6;

-- Fix all audit/filler subjects: remove lecture hours, set as practical blocks
UPDATE subject SET lecture_hours_per_week=0, practical_hours_per_week=2, practical_slot_duration=1 WHERE code='INTP' AND semester=6;
UPDATE subject SET lecture_hours_per_week=0, practical_hours_per_week=1, practical_slot_duration=1 WHERE code='AC6'  AND semester=6;
UPDATE subject SET lecture_hours_per_week=0, practical_hours_per_week=1, practical_slot_duration=1 WHERE code='AC8'  AND semester=8;
UPDATE subject SET lecture_hours_per_week=0, practical_hours_per_week=1, practical_slot_duration=1 WHERE code='VL4'  AND semester=4;
UPDATE subject SET lecture_hours_per_week=0, practical_hours_per_week=1, practical_slot_duration=1 WHERE code='VL6'  AND semester=6;
UPDATE subject SET lecture_hours_per_week=0, practical_hours_per_week=1, practical_slot_duration=1 WHERE code='VL8'  AND semester=8;
UPDATE subject SET lecture_hours_per_week=0, practical_hours_per_week=1, practical_slot_duration=1 WHERE code='TP8'  AND semester=8;
UPDATE subject SET lecture_hours_per_week=0, practical_hours_per_week=1, practical_slot_duration=1 WHERE code='SEM8' AND semester=8;

-- Fix Seminar type (was set with lecture=1; change to practical block)
UPDATE subject SET lecture_hours_per_week=0, practical_hours_per_week=1, practical_slot_duration=1 WHERE code='SEM8';

-- ============================================================
-- 12. ASSIGN FACULTY TO SUBJECTS
-- ============================================================

-- SE (Semester 4)
UPDATE subject SET faculty = 'Prof. P. G. Khaire'    WHERE code = 'CG'    AND semester = 4;
UPDATE subject SET faculty = 'Prof. S. S. Khote'     WHERE code = 'PAE'   AND semester = 4;
UPDATE subject SET faculty = 'Prof. R. A. Nikam'     WHERE code = 'DBMS'  AND semester = 4;
UPDATE subject SET faculty = 'Prof. A. R. Dodke'     WHERE code = 'DMS'   AND semester = 4;
UPDATE subject SET faculty = 'Prof. Rashmi Kenvat'   WHERE code = 'PS'    AND semester = 4;
UPDATE subject SET faculty = 'Prof. D. P. Rankhambe' WHERE code = 'ES'    AND semester = 4;
UPDATE subject SET faculty = 'Prof. A. N. Kalal'     WHERE code = 'EC'    AND semester = 4;
UPDATE subject SET faculty = 'Dr. S. R. Kokane'      WHERE code = 'OE2'   AND semester = 4;
UPDATE subject SET faculty = 'Prof. R. S. Lavhe'     WHERE code = 'MIL'   AND semester = 4;
-- SE Labs
UPDATE subject SET faculty = 'Prof. P. G. Khaire'    WHERE code = 'CGL'   AND semester = 4;
UPDATE subject SET faculty = 'Prof. R. A. Nikam'     WHERE code = 'DBMSL' AND semester = 4;
UPDATE subject SET faculty = 'Prof. S. S. Khote'     WHERE code = 'DMSL'  AND semester = 4;
UPDATE subject SET faculty = 'Prof. R. S. Lavhe'     WHERE code = 'MILL'  AND semester = 4;
UPDATE subject SET faculty = 'Prof. A. N. Kalal'     WHERE code = 'ECL'   AND semester = 4;
UPDATE subject SET faculty = 'Library Coordinator'  WHERE code = 'LIBSE' AND semester = 4;
UPDATE subject SET faculty = 'Dr. A. A. Kadam'       WHERE code = 'VL4'   AND semester = 4;

-- TE (Semester 6)
UPDATE subject SET faculty = 'Prof. R. S. Lavhe'     WHERE code = 'CNS'    AND semester = 6;
UPDATE subject SET faculty = 'Prof. A. N. Kalal'     WHERE code = 'WAD'    AND semester = 6;
UPDATE subject SET faculty = 'Prof. D. P. Rankhambe' WHERE code = 'DSBDA'  AND semester = 6;
UPDATE subject SET faculty = 'Prof. S. M. Dhane'     WHERE code = 'EL2'    AND semester = 6;
UPDATE subject SET faculty = 'Prof. P. G. Khaire'    WHERE code = 'EACH'   AND semester = 6;
-- TE Labs
UPDATE subject SET faculty = 'Prof. R. S. Lavhe'     WHERE code = 'CNSL'   AND semester = 6;
UPDATE subject SET faculty = 'Prof. D. P. Rankhambe' WHERE code = 'DSBDAL' AND semester = 6;
UPDATE subject SET faculty = 'Prof. A. N. Kalal'     WHERE code = 'LP2'    AND semester = 6;
UPDATE subject SET faculty = 'Library Coordinator'  WHERE code = 'LIBTE'  AND semester = 6;
UPDATE subject SET faculty = 'Prof. A. N. Kalal'     WHERE code = 'INTP'   AND semester = 6;
UPDATE subject SET faculty = 'Dr. A. A. Kadam'       WHERE code = 'AC6'    AND semester = 6;
UPDATE subject SET faculty = 'Dr. A. A. Kadam'       WHERE code = 'VL6'    AND semester = 6;

-- BE (Semester 8)
UPDATE subject SET faculty = 'Dr. A. A. Kadam'       WHERE code = 'SE8'   AND semester = 8;
UPDATE subject SET faculty = 'Dr. S. R. Kokane'      WHERE code = 'DS8'   AND semester = 8;
UPDATE subject SET faculty = 'Prof. S. S. Khote'     WHERE code = 'EL5'   AND semester = 8;
UPDATE subject SET faculty = 'Prof. R. A. Nikam'     WHERE code = 'EL6'   AND semester = 8;
UPDATE subject SET faculty = 'Prof. A. N. Kalal'     WHERE code = 'ISMH'  AND semester = 8;
UPDATE subject SET faculty = 'Prof. D. P. Rankhambe' WHERE code = 'SEM8'  AND semester = 8;
-- BE Labs
UPDATE subject SET faculty = 'Dr. S. R. Kokane'      WHERE code = 'LP5'   AND semester = 8;
UPDATE subject SET faculty = 'Prof. P. G. Khaire'    WHERE code = 'LP6'   AND semester = 8;
UPDATE subject SET faculty = 'Prof. P. G. Khaire'    WHERE code = 'PS2'   AND semester = 8;
UPDATE subject SET faculty = 'Dr. A. A. Kadam'       WHERE code = 'TP8'   AND semester = 8;
UPDATE subject SET faculty = 'Dr. A. A. Kadam'       WHERE code = 'AC8'   AND semester = 8;
UPDATE subject SET faculty = 'Dr. A. A. Kadam'       WHERE code = 'VL8'   AND semester = 8;
