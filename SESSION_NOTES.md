# AutomatedTimetable — Session Notes

## Project
Spring Boot timetable generator for APCOER, Parvati, Pune — IT Dept, AY 2025-26, Sem-II.
- Working dir: `/home/sudhir/Desktop/ATG/Original_ATG/New folder/AutomatedTimetable`
- Reference: `/home/sudhir/Desktop/ATG/IT.xlsx` — SOURCE OF TRUTH for all timetable patterns
- DB: PostgreSQL on localhost:5432, database=`atg`, user=`postgres`, pass=`root`
- App runs on port 8080 via `./mvnw spring-boot:run`
- Login: admin/admin123 (role=ADMIN), or hod_it/hod123 (HOD), fac username/fac123 (FACULTY)

## Divisions
- SE-IT: Second Year IT, Sem-4, 4 batches (S1–S4), prefix=S
- TE-IT: Third Year IT, Sem-6, 4 batches (T1–T4), prefix=T
- BE-IT: Final Year IT, Sem-8, 4 batches (B1–B4), prefix=B

---

## Current Status (end of session)
- **207/207 sessions placed (100%)**
- **0 faculty conflicts**
- **0 empty time slots** (Mon–Fri all 7 slots filled for all 3 divisions)
- App is running on port 8080

---

## Key Files Changed This Session

### 1. `src/main/resources/data.sql`
Full seed file. Changes made from original:

**Lab rooms** — added 4 extra lab rooms (now 8 total, was 4):
```sql
Lab WET-1, Lab WET-2, Lab IT-201, Lab IT-202  ← original 4
Lab IT-203, Lab IT-204, Lab IT-205, Lab IT-206 ← added (needed for 3 divisions to overlap)
```

**LP2 (TE Lab Practice II)** — increased from 2 to 4 practical hours:
```sql
UPDATE subject SET practical_hours_per_week=4, practical_slot_duration=2 WHERE code='LP2' AND semester=6;
```
*Why: Each TE batch does LP-II twice (WAD track + EL-II track) per reference IT.xlsx.*

**AC4 soft-deleted** — was a stale audit subject blocking VL4's slot:
```sql
UPDATE subject SET deleted=true WHERE code='AC4' AND semester=4;
```

**Fill empty slots** — BE had 5 empty slots, TE had 3:
```sql
-- Reset stale values from any previous runs:
UPDATE subject SET lecture_hours_per_week=3 WHERE code='SE8'   AND semester=8;
UPDATE subject SET lecture_hours_per_week=3 WHERE code='DS8'   AND semester=8;
UPDATE subject SET lecture_hours_per_week=4 WHERE code='ISMH'  AND semester=8;
UPDATE subject SET lecture_hours_per_week=3 WHERE code='CNS'   AND semester=6;
UPDATE subject SET lecture_hours_per_week=3 WHERE code='WAD'   AND semester=6;
UPDATE subject SET lecture_hours_per_week=3 WHERE code='DSBDA' AND semester=6;
UPDATE subject SET lecture_hours_per_week=3 WHERE code='EL5'   AND semester=8;
UPDATE subject SET lecture_hours_per_week=3 WHERE code='EL6'   AND semester=8;
UPDATE subject SET lecture_hours_per_week=3 WHERE code='EL2'   AND semester=6;
UPDATE subject SET lecture_hours_per_week=5 WHERE code='EACH'  AND semester=6;
-- BE-IT: fill 5 empty slots (SSK teaches EL5, RAN teaches EL6)
UPDATE subject SET lecture_hours_per_week=6 WHERE code='EL5' AND semester=8;
UPDATE subject SET lecture_hours_per_week=5 WHERE code='EL6' AND semester=8;
-- TE-IT: fill 3 empty slots (SMD teaches EL2, PGK teaches EACH)
UPDATE subject SET lecture_hours_per_week=5 WHERE code='EL2'  AND semester=6;
UPDATE subject SET lecture_hours_per_week=5 WHERE code='EACH' AND semester=6;
```

**DPR added as Internship coordinator** (ANK was sole faculty, too busy):
```sql
UPDATE users SET subjects_handled = 'Engineering Sciences,Data Science and Big Data Analytics,DS & BDA-Lab,Seminar,Internship'
    WHERE username = 'dpr';
```

---

### 2. `src/main/java/com/nt/service/TimetableGeneratorService.java`

**Fix 1 — Lab list: single-pass consecutive duplicates**
Old: two-pass (extras appended at end) → `[LP5, LP6, PS2, LP5]`
New: one-pass, each subject repeated blocks times → `[LP5, LP5, LP6, PS2]`
*Why: Adjacent duplicates match reference — BE Block 1 (Mon 9-11) = B1+B2 both doing LP5.*

**Fix 2 — Rotation fallback: added `usedFacHere` check**
Old fallback allowed assigning same faculty to two different subjects in same window (LP6 + PS2 both getting PGK).
New fallback also checks `usedFacHere` before reusing faculty.

**Fix 3 — Removed all time-window preferences**
Removed `getLabWindowPriority()` and `getTheorySlotPriority()` methods entirely.
Labs and theory now try slots in natural time order (09:00 → 10:00 → 11:15 → 12:15 → 14:00 → 15:00 → 16:00).
*Why user wanted: "don't try to put slot exactly like morning/evening". With 8 rooms, divisions naturally avoid each other via room availability.*

**Fix 4 — Natural Mon→Fri day order for theory**
Old: `Collections.rotate(shuffledDays, -(subjectSeq++ % workingDays.size()))` (each subject starts different day)
New: `List<String> shuffledDays = new ArrayList<>(workingDays)` (all start from Monday)
*Why: With subjectDayBusy preventing same-subject-same-day, multiple subjects fill each day from the top. Any leftover empty slots consolidate to Friday instead of scattering mid-week.*

**Fix 5 — Lab rotation: removed shuffle**
Old: `Collections.shuffle(shuffledDays)` in `generateLabRotation`
New: natural Mon→Fri order (no shuffle)

**Fix 6 — Whole-class audit activities use any room type**
Old: `sessionType = "Practical"` for all practicals → forced lab room
New: `practicalSessionType = isWholeClass ? subject.getType() : "Practical"`
*Why: VL, INTP, AC, TP, SEM don't need a physical lab room and can use Room IT-305 (lecture room).*

---

## Algorithm Architecture (current)

### Generation sequence:
1. **Lab rotation** (generateLabRotation) runs first for each division
   - Division order: natural DB order (SE-IT, TE-IT, BE-IT)
   - Lab list built: consecutive duplicates e.g. `[CGL, DBMSL, DMSL, DMSL, MILL, ECL, LIBSE]` for SE
   - Each rotation slot places ALL 4 batches simultaneously in different rooms
   - Natural window order (earliest first), natural day order (Mon→Fri), overflow to least-loaded day
2. **Theory/audit placement** runs after all rotations
   - All subjects in id-sorted order, starting from Mon each time
   - `subjectDayBusy` prevents same subject on same day
   - Slots tried in natural order (09:00 first)
   - Fallback: `tryPlaceRelaxed` allows same subject on same day if strict fails

### Room strategy:
- 8 lab rooms: WET-1, WET-2, IT-201..206
- SE labs use rooms 1-4 (WET-1,2, IT-201,202), BE labs use rooms 5-8 (IT-203..206)
- TE labs also use rooms 1-4 but in different time windows (no conflict)
- Multiple divisions CAN share a time window by using different rooms

---

## Remaining Issue (user mentioned "still has issue")
Session ended before user described the remaining issue. The previous confirmed state was:
- 207/207 placed
- 0 empty slots
- 0 faculty conflicts

**Likely remaining issues to check:**
1. PDF display — batch lab entries (S1/S2/S3/S4) may not show correctly in class timetable view
2. Some subjects might be on same day too many times (e.g. 2 sessions of same subject on Monday)
3. Lab block shown as 3+ slots in PDF when it should be 2 (display bug in PDF generator)
4. Specific faculty assignments not matching IT.xlsx reference

---

## Key Subject Hours (current state after all fixes)
```
SE-IT (sem=4):
  CG=3, PA=3, DBMS=3, DMS=0, PS=4, ES=2, EC=1, OE2=3, MIL=1
  CGL: prac=2,dur=2 | DBMSL: prac=2,dur=2 | DMSL: prac=4,dur=2
  MILL: prac=2,dur=2 | ECL: prac=2,dur=2 | LIBSE: prac=2,dur=2
  VL4: prac=1,dur=1,type=Audit

TE-IT (sem=6):
  CNS=3, WAD=3, DSBDA=3, EL2=5 (was 3), EACH=5 (was 4)
  CNSL: prac=4,dur=2 | DSBDAL: prac=2,dur=2 | LP2: prac=4,dur=2 (was 2!)
  LIBTE: prac=2,dur=2 | INTP: prac=2,dur=1,type=Audit
  AC6: prac=1,dur=1 | VL6: prac=1,dur=1

BE-IT (sem=8):
  SE8=3, DS8=3, EL5=6 (was 3), EL6=5 (was 3), ISMH=4
  LP5: prac=4,dur=2 | LP6: prac=2,dur=2 | PS2: lec=2,prac=2,dur=2
  SEM8: prac=1,dur=1,type=Seminar | TP8: prac=1,dur=1,type=Audit
  AC8: prac=1,dur=1 | VL8: prac=1,dur=1
```

## Key Faculty Assignments
```
PGK (pgk): CG, CGL, EAC Honours, Lab Practice VI
RAN (ran): DBMS, DBMSL, Elective VI, Lab Practice VI
SSK (ssk): PA, DMSL, CNSL(T3/T4), Elective V
ARD (ard): DMSL, Elective II, Laboratory Practice-II
RSL (rsl): MIL, MILL, Computer Networks & Security, CNSL
SRK (srk): OE-II, MILL(S3/S4), Distributed Systems, Lab Practice V
RK  (rk):  Probability & Statistics
DPR (dpr): Engineering Sciences, DSBDA, DS&BDA-Lab, Seminar, Internship ← added INTP
ANK (ank): EC, ECL, WAD, LP-II, Internship, ISM Honours
AAK (aak): EC, ECL, Software Engineering, Lab Practice V
SMD (smd): Elective II, Laboratory Practice-II
LIB (lib_coord): Library
```

---

## How to Run
```bash
cd "/home/sudhir/Desktop/ATG/Original_ATG/New folder/AutomatedTimetable"
./mvnw spring-boot:run
# App starts on http://localhost:8080
# Login: admin / admin123 (select role ADMIN)
# Generate: Admin menu → Auto Generate → Run
```

## How to Regenerate Timetable
Go to `http://localhost:8080/auto-generate` → click Generate.
This soft-deletes old entries and places fresh ones.
Expected result: "Placed 207 of 207 sessions" with no errors.

---

## Git State
Branch: master. Last commit: "fix the cell structure".
All changes are UNCOMMITTED (working tree modifications).
Modified files:
- src/main/java/com/nt/service/TimetableGeneratorService.java
- src/main/resources/data.sql
- src/main/java/com/nt/controller/AdminController.java (unrelated UI changes)
- src/main/java/com/nt/controller/HodController.java
- src/main/java/com/nt/entity/Division.java, Timetable.java
- src/main/resources/templates/add-division.html, admin-layout.html
- Untracked: src/main/resources/templates/add-special-slot.html
