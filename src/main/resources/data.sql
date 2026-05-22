-- ============================================================
-- data.sql  —  Seed data for Automatic Timetable Generator
-- DB: PostgreSQL | Department: IT, APCOER | AY 2025-26 Sem-II
-- ============================================================
-- Uses INSERT ... SELECT ... WHERE NOT EXISTS for idempotency.
-- "user" is quoted because it is a reserved word in PostgreSQL.
-- Timetable table excluded — populated by the generation engine.
-- ============================================================


-- ============================================================
-- 1. ACADEMIC SETTING
-- ============================================================
INSERT INTO academic_setting (id, working_days)
SELECT 1, 'Monday,Tuesday,Wednesday,Thursday,Friday'
WHERE NOT EXISTS (SELECT 1 FROM academic_setting WHERE id = 1);


-- ============================================================
-- 2. DEPARTMENTS
-- ============================================================
INSERT INTO department (name, code, hod)
SELECT 'Information Technology', 'IT', 'Dr. A. A. Kadam'
WHERE NOT EXISTS (SELECT 1 FROM department WHERE code = 'IT');

INSERT INTO department (name, code, hod)
SELECT 'Computer Science', 'CSE', 'Computer Science HOD'
WHERE NOT EXISTS (SELECT 1 FROM department WHERE code = 'CSE');


-- ============================================================
-- 3. USERS
-- ============================================================

INSERT INTO "users" (name, username, password, role, department, email, specialization, subjects_handled, max_lectures_per_day, max_lectures_per_week, deleted)
SELECT 'System Admin','admin','admin123','ADMIN',NULL,'admin@apcoer.edu',NULL,NULL,NULL,NULL,false
WHERE NOT EXISTS (SELECT 1 FROM "users" WHERE username='admin');

INSERT INTO "users" (name, username, password, role, department, email, specialization, subjects_handled, max_lectures_per_day, max_lectures_per_week, deleted)
SELECT 'Dr. A. A. Kadam','hod_it','hod123','HOD','IT','aak@apcoer.edu','Software Engineering',NULL,3,8,false
WHERE NOT EXISTS (SELECT 1 FROM "users" WHERE username='hod_it');

-- Faculty 1 — Dr. A. A. Kadam (BE: Software Engineering, LP-V Lab) | load 8
INSERT INTO "users" (name, username, password, role, department, email, specialization, subjects_handled, max_lectures_per_day, max_lectures_per_week, deleted)
SELECT 'Dr. A. A. Kadam','fac_aak','fac123','FACULTY','IT','aak@apcoer.edu','Software Engineering','Software Engineering,LP-V Lab,Electronics Circuits',3,8,false
WHERE NOT EXISTS (SELECT 1 FROM "users" WHERE username='fac_aak');

-- Faculty 2 — Dr. S. R. Kokane (BE: Data Structures, OE-II, LP-V Lab) | load 8
INSERT INTO "users" (name, username, password, role, department, email, specialization, subjects_handled, max_lectures_per_day, max_lectures_per_week, deleted)
SELECT 'Dr. S. R. Kokane','fac_srk','fac123','FACULTY','IT','srk@apcoer.edu','Data Structures','Data Structures,Open Elective-II,LP-V Lab',3,8,false
WHERE NOT EXISTS (SELECT 1 FROM "users" WHERE username='fac_srk');

-- Faculty 3 — Prof. R. A. Nikam (SE+BE: DBMS, DBMS Lab, Elective-VI, LP-VI Lab) | load 13
INSERT INTO "users" (name, username, password, role, department, email, specialization, subjects_handled, max_lectures_per_day, max_lectures_per_week, deleted)
SELECT 'Prof. R. A. Nikam','fac_ran','fac123','FACULTY','IT','ran@apcoer.edu','Database Systems','Database Management System,DBMS Lab,Elective-VI,LP-VI Lab',4,13,false
WHERE NOT EXISTS (SELECT 1 FROM "users" WHERE username='fac_ran');

-- Faculty 4 — Prof. P. G. Khaire (SE+TE+BE: CG, CG Lab, EAC Honours, LP-VI Lab) | load 13
INSERT INTO "users" (name, username, password, role, department, email, specialization, subjects_handled, max_lectures_per_day, max_lectures_per_week, deleted)
SELECT 'Prof. P. G. Khaire','fac_pgk','fac123','FACULTY','IT','pgk@apcoer.edu','Computer Graphics','Computer Graphics,Computer Graphics Lab,EAC Honours,LP-VI Lab',4,13,false
WHERE NOT EXISTS (SELECT 1 FROM "users" WHERE username='fac_pgk');

-- Faculty 5 — Prof. A. N. Kalal (TE+BE: WAD, ISM Honours, Electronics Circuits) | load 10
INSERT INTO "users" (name, username, password, role, department, email, specialization, subjects_handled, max_lectures_per_day, max_lectures_per_week, deleted)
SELECT 'Prof. A. N. Kalal','fac_ank','fac123','FACULTY','IT','ank@apcoer.edu','Web Technologies','Web Application Development,ISM Honours,Electronics Circuits',4,10,false
WHERE NOT EXISTS (SELECT 1 FROM "users" WHERE username='fac_ank');

-- Faculty 6 — Prof. R. S. Lavhe (TE+SE: CNS, Modern Indian Language) | load 7
INSERT INTO "users" (name, username, password, role, department, email, specialization, subjects_handled, max_lectures_per_day, max_lectures_per_week, deleted)
SELECT 'Prof. R. S. Lavhe','fac_rsl','fac123','FACULTY','IT','rsl@apcoer.edu','Computer Networks','Computer Networks Security,Modern Indian Language',3,7,false
WHERE NOT EXISTS (SELECT 1 FROM "users" WHERE username='fac_rsl');

-- Faculty 7 — Prof. D. P. Rankhambe (TE+SE: DSBDA, DSBDA Lab, Engg Sciences) | load 10
INSERT INTO "users" (name, username, password, role, department, email, specialization, subjects_handled, max_lectures_per_day, max_lectures_per_week, deleted)
SELECT 'Prof. D. P. Rankhambe','fac_dpr','fac123','FACULTY','IT','dpr@apcoer.edu','Data Science','Data Science & Big Data Analytics,DSBDA Lab,Engineering Sciences',4,10,false
WHERE NOT EXISTS (SELECT 1 FROM "users" WHERE username='fac_dpr');

-- Faculty 8 — Prof. S. S. Khote (SE+TE+BE: PAE, CNS Lab, Elective-V) | load 9
INSERT INTO "users" (name, username, password, role, department, email, specialization, subjects_handled, max_lectures_per_day, max_lectures_per_week, deleted)
SELECT 'Prof. S. S. Khote','fac_ssk','fac123','FACULTY','IT','ssk@apcoer.edu','Analog Electronics','Principles of Analog Electronics,CNS Lab,Elective-V',3,9,false
WHERE NOT EXISTS (SELECT 1 FROM "users" WHERE username='fac_ssk');

-- Faculty 9 — Prof. A. R. Dodke (SE+TE: DMS, LP-II WAD Lab) | load 6
INSERT INTO "users" (name, username, password, role, department, email, specialization, subjects_handled, max_lectures_per_day, max_lectures_per_week, deleted)
SELECT 'Prof. A. R. Dodke','fac_ard','fac123','FACULTY','IT','ard@apcoer.edu','Discrete Mathematics','Discrete Mathematics & Simulation,LP-II (WAD Lab)',3,6,false
WHERE NOT EXISTS (SELECT 1 FROM "users" WHERE username='fac_ard');

-- Faculty 10 — Dr. Rashmi Kenvat (SE: Probability & Statistics) | load 4
INSERT INTO "users" (name, username, password, role, department, email, specialization, subjects_handled, max_lectures_per_day, max_lectures_per_week, deleted)
SELECT 'Dr. Rashmi Kenvat','fac_rk','fac123','FACULTY','IT','rk@apcoer.edu','Probability & Statistics','Probability & Statistics',2,4,false
WHERE NOT EXISTS (SELECT 1 FROM "users" WHERE username='fac_rk');

-- Faculty 11 — Prof. S. M. Deshpande (TE+BE: Elective-II, Project Stage-II) | load 4
INSERT INTO "users" (name, username, password, role, department, email, specialization, subjects_handled, max_lectures_per_day, max_lectures_per_week, deleted)
SELECT 'Prof. S. M. Deshpande','fac_smd','fac123','FACULTY','IT','smd@apcoer.edu','Elective Subjects','Elective-II,Project Stage-II',2,4,false
WHERE NOT EXISTS (SELECT 1 FROM "users" WHERE username='fac_smd');

-- Faculty 12 — Dr. S. B. Thakare (Principal / Project guide) | load 4
INSERT INTO "users" (name, username, password, role, department, email, specialization, subjects_handled, max_lectures_per_day, max_lectures_per_week, deleted)
SELECT 'Dr. S. B. Thakare','fac_sbt','fac123','FACULTY','IT','sbt@apcoer.edu','Principal / Project Guide','Project Stage-II',2,4,false
WHERE NOT EXISTS (SELECT 1 FROM "users" WHERE username='fac_sbt');


-- ============================================================
-- 4. CLASSROOMS
-- ============================================================

INSERT INTO classroom (room, building, capacity, type)
SELECT 'IT-305','IT Block',60,'Lecture'
WHERE NOT EXISTS (SELECT 1 FROM classroom WHERE room='IT-305');

INSERT INTO classroom (room, building, capacity, type)
SELECT 'IT-307','IT Block',60,'Lecture'
WHERE NOT EXISTS (SELECT 1 FROM classroom WHERE room='IT-307');

INSERT INTO classroom (room, building, capacity, type)
SELECT 'WET','IT Block',30,'Lab'
WHERE NOT EXISTS (SELECT 1 FROM classroom WHERE room='WET');

INSERT INTO classroom (room, building, capacity, type)
SELECT 'ML1','IT Block',30,'Lab'
WHERE NOT EXISTS (SELECT 1 FROM classroom WHERE room='ML1');

INSERT INTO classroom (room, building, capacity, type)
SELECT 'DS Lab','IT Block',30,'Lab'
WHERE NOT EXISTS (SELECT 1 FROM classroom WHERE room='DS Lab');

INSERT INTO classroom (room, building, capacity, type)
SELECT 'Project Lab','IT Block',30,'Lab'
WHERE NOT EXISTS (SELECT 1 FROM classroom WHERE room='Project Lab');

INSERT INTO classroom (room, building, capacity, type)
SELECT 'OS Lab','IT Block',30,'Lab'
WHERE NOT EXISTS (SELECT 1 FROM classroom WHERE room='OS Lab');

INSERT INTO classroom (room, building, capacity, type)
SELECT 'SDTL','IT Block',30,'Lab'
WHERE NOT EXISTS (SELECT 1 FROM classroom WHERE room='SDTL');

INSERT INTO classroom (room, building, capacity, type)
SELECT 'NL Lab','IT Block',30,'Lab'
WHERE NOT EXISTS (SELECT 1 FROM classroom WHERE room='NL Lab');

INSERT INTO classroom (room, building, capacity, type)
SELECT 'CC Lab','IT Block',30,'Lab'
WHERE NOT EXISTS (SELECT 1 FROM classroom WHERE room='CC Lab');


-- ============================================================
-- 5. DIVISIONS
-- ============================================================

INSERT INTO division (name, department, year, semester_number, capacity, classroom)
SELECT 'SE-IT','IT','Second Year',4,60,'IT-305'
WHERE NOT EXISTS (SELECT 1 FROM division WHERE name='SE-IT' AND department='IT');

INSERT INTO division (name, department, year, semester_number, capacity, classroom)
SELECT 'TE-IT','IT','Third Year',6,60,'IT-305'
WHERE NOT EXISTS (SELECT 1 FROM division WHERE name='TE-IT' AND department='IT');

INSERT INTO division (name, department, year, semester_number, capacity, classroom)
SELECT 'BE-IT','IT','Final Year',8,60,'IT-307'
WHERE NOT EXISTS (SELECT 1 FROM division WHERE name='BE-IT' AND department='IT');


-- ============================================================
-- 6. TIMESLOTS
-- ============================================================

INSERT INTO timeslot (start_time, end_time, is_break)
SELECT '09:00','10:00',false WHERE NOT EXISTS (SELECT 1 FROM timeslot WHERE start_time='09:00' AND end_time='10:00');

INSERT INTO timeslot (start_time, end_time, is_break)
SELECT '10:00','11:00',false WHERE NOT EXISTS (SELECT 1 FROM timeslot WHERE start_time='10:00' AND end_time='11:00');

INSERT INTO timeslot (start_time, end_time, is_break)
SELECT '11:00','11:15',true  WHERE NOT EXISTS (SELECT 1 FROM timeslot WHERE start_time='11:00' AND end_time='11:15');

INSERT INTO timeslot (start_time, end_time, is_break)
SELECT '11:15','12:15',false WHERE NOT EXISTS (SELECT 1 FROM timeslot WHERE start_time='11:15' AND end_time='12:15');

INSERT INTO timeslot (start_time, end_time, is_break)
SELECT '12:15','13:15',false WHERE NOT EXISTS (SELECT 1 FROM timeslot WHERE start_time='12:15' AND end_time='13:15');

INSERT INTO timeslot (start_time, end_time, is_break)
SELECT '13:15','14:00',true  WHERE NOT EXISTS (SELECT 1 FROM timeslot WHERE start_time='13:15' AND end_time='14:00');

INSERT INTO timeslot (start_time, end_time, is_break)
SELECT '14:00','15:00',false WHERE NOT EXISTS (SELECT 1 FROM timeslot WHERE start_time='14:00' AND end_time='15:00');

INSERT INTO timeslot (start_time, end_time, is_break)
SELECT '15:00','16:00',false WHERE NOT EXISTS (SELECT 1 FROM timeslot WHERE start_time='15:00' AND end_time='16:00');

INSERT INTO timeslot (start_time, end_time, is_break)
SELECT '16:00','17:00',false WHERE NOT EXISTS (SELECT 1 FROM timeslot WHERE start_time='16:00' AND end_time='17:00');


-- ============================================================
-- 7. SUBJECTS
-- ============================================================

-- ── SE-IT (Sem 4) ────────────────────────────────────────────

INSERT INTO subject (name, code, department, semester, credits, hours, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
SELECT 'Computer Graphics','CG','IT',4,4,8,5,3,2,'Theory+Lab','Prof. P. G. Khaire',false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='CG' AND department='IT' AND semester=4);

INSERT INTO subject (name, code, department, semester, credits, hours, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
SELECT 'Principles of Analog Electronics','PAE','IT',4,3,3,3,0,0,'Theory','Prof. S. S. Khote',false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='PAE' AND department='IT' AND semester=4);

INSERT INTO subject (name, code, department, semester, credits, hours, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
SELECT 'Database Management System','DBMS','IT',4,5,8,5,3,2,'Theory+Lab','Prof. R. A. Nikam',false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='DBMS' AND department='IT' AND semester=4);

INSERT INTO subject (name, code, department, semester, credits, hours, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
SELECT 'Discrete Mathematics & Simulation','DMS','IT',4,3,3,3,0,0,'Theory','Prof. A. R. Dodke',false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='DMS' AND department='IT' AND semester=4);

INSERT INTO subject (name, code, department, semester, credits, hours, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
SELECT 'Probability & Statistics','PS','IT',4,4,4,4,0,0,'Theory','Dr. Rashmi Kenvat',false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='PS' AND department='IT' AND semester=4);

INSERT INTO subject (name, code, department, semester, credits, hours, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
SELECT 'Engineering Sciences','ES','IT',4,2,2,2,0,0,'Theory','Prof. D. P. Rankhambe',false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='ES' AND department='IT' AND semester=4);

INSERT INTO subject (name, code, department, semester, credits, hours, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
SELECT 'Electronics Circuits','EC','IT',4,1,1,1,0,0,'Theory','Prof. A. N. Kalal',false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='EC' AND department='IT' AND semester=4);

INSERT INTO subject (name, code, department, semester, credits, hours, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
SELECT 'Open Elective-II','OE2','IT',4,2,2,2,0,0,'Elective','Dr. S. R. Kokane',false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='OE2' AND department='IT' AND semester=4);

INSERT INTO subject (name, code, department, semester, credits, hours, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
SELECT 'Modern Indian Language','MIL','IT',4,2,2,2,0,0,'Theory','Prof. R. S. Lavhe',false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='MIL' AND department='IT' AND semester=4);

-- ── TE-IT (Sem 6) ────────────────────────────────────────────

INSERT INTO subject (name, code, department, semester, credits, hours, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
SELECT 'Computer Networks Security','CNS','IT',6,5,5,5,0,0,'Theory','Prof. R. S. Lavhe',false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='CNS' AND department='IT' AND semester=6);

INSERT INTO subject (name, code, department, semester, credits, hours, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
SELECT 'Web Application Development','WAD','IT',6,5,5,5,0,0,'Theory','Prof. A. N. Kalal',false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='WAD' AND department='IT' AND semester=6);

INSERT INTO subject (name, code, department, semester, credits, hours, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
SELECT 'Data Science & Big Data Analytics','DSBDA','IT',6,5,8,5,3,2,'Theory+Lab','Prof. D. P. Rankhambe',false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='DSBDA' AND department='IT' AND semester=6);

INSERT INTO subject (name, code, department, semester, credits, hours, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
SELECT 'Elective-II','EL2','IT',6,3,3,3,0,0,'Elective','Prof. S. M. Deshpande',false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='EL2' AND department='IT' AND semester=6);

INSERT INTO subject (name, code, department, semester, credits, hours, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
SELECT 'EAC Honours','EAC','IT',6,4,4,4,0,0,'Honours','Prof. P. G. Khaire',false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='EAC' AND department='IT' AND semester=6);

INSERT INTO subject (name, code, department, semester, credits, hours, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
SELECT 'CNS Lab','CNSL','IT',6,3,3,0,3,2,'Lab','Prof. S. S. Khote',false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='CNSL' AND department='IT' AND semester=6);

INSERT INTO subject (name, code, department, semester, credits, hours, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
SELECT 'LP-II (WAD Lab)','LP2','IT',6,3,3,0,3,2,'Lab','Prof. A. R. Dodke',false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='LP2' AND department='IT' AND semester=6);

-- ── BE-IT (Sem 8) ────────────────────────────────────────────

INSERT INTO subject (name, code, department, semester, credits, hours, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
SELECT 'Software Engineering','SE8','IT',8,5,5,5,0,0,'Theory','Dr. A. A. Kadam',false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='SE8' AND department='IT' AND semester=8);

INSERT INTO subject (name, code, department, semester, credits, hours, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
SELECT 'Data Structures','DS8','IT',8,5,5,5,0,0,'Theory','Dr. S. R. Kokane',false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='DS8' AND department='IT' AND semester=8);

INSERT INTO subject (name, code, department, semester, credits, hours, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
SELECT 'Elective-V','EL5','IT',8,3,3,3,0,0,'Elective','Prof. S. S. Khote',false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='EL5' AND department='IT' AND semester=8);

INSERT INTO subject (name, code, department, semester, credits, hours, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
SELECT 'ISM Honours','ISM','IT',8,4,4,4,0,0,'Honours','Prof. A. N. Kalal',false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='ISM' AND department='IT' AND semester=8);

INSERT INTO subject (name, code, department, semester, credits, hours, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
SELECT 'Elective-VI','EL6','IT',8,3,3,3,0,0,'Elective','Prof. R. A. Nikam',false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='EL6' AND department='IT' AND semester=8);

INSERT INTO subject (name, code, department, semester, credits, hours, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
SELECT 'Project Stage-II','PST2','IT',8,4,4,0,4,2,'Project','Dr. S. B. Thakare',false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='PST2' AND department='IT' AND semester=8);

INSERT INTO subject (name, code, department, semester, credits, hours, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
SELECT 'LP-V Lab','LP5','IT',8,3,3,0,3,2,'Lab','Dr. A. A. Kadam',false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='LP5' AND department='IT' AND semester=8);

INSERT INTO subject (name, code, department, semester, credits, hours, lecture_hours_per_week, practical_hours_per_week, practical_slot_duration, type, faculty, deleted)
SELECT 'LP-VI Lab','LP6','IT',8,2,2,0,2,2,'Lab','Prof. R. A. Nikam',false
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE code='LP6' AND department='IT' AND semester=8);

-- ============================================================
-- END OF data.sql
-- ============================================================