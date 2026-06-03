-- Seed only the admin account on first run.
-- All other data (departments, divisions, subjects, faculty, classrooms, timeslots)
-- is entered through the admin UI.
INSERT INTO users (name, username, password, role, department, deleted)
SELECT 'System Admin', 'apcoer', 'atg', 'ADMIN', NULL, false
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'apcoer');
