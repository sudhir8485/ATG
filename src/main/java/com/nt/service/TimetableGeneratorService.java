package com.nt.service;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.nt.entity.AcademicSetting;
import com.nt.entity.Classroom;
import com.nt.entity.Division;
import com.nt.entity.Subject;
import com.nt.entity.Timeslot;
import com.nt.entity.Timetable;
import com.nt.entity.User;
import com.nt.entity.SubjectFacultyAssignment;
import com.nt.repository.AcademicSettingRepository;
import com.nt.repository.ClassroomRepository;
import com.nt.repository.DivisionRepository;
import com.nt.repository.SubjectFacultyAssignmentRepository;
import com.nt.repository.SubjectRepository;
import com.nt.repository.TimeslotRepository;
import com.nt.repository.TimetableRepository;
import com.nt.repository.UserRepository;

@Service
public class TimetableGeneratorService {

    @Autowired
    private SubjectRepository subjectRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private ClassroomRepository roomRepo;

    @Autowired
    private TimeslotRepository timeRepo;

    @Autowired
    private TimetableRepository timetableRepo;

    @Autowired
    private DivisionRepository divisionRepo;

    @Autowired
    private AcademicSettingRepository academicSettingRepo;

    @Autowired
    private SubjectFacultyAssignmentRepository sfaRepo;


    public GenerationResult generate() {
        GenerationResult result = new GenerationResult();

        // Sort by id for deterministic processing order — PostgreSQL heap order can change
        // after UPDATEs, causing subjects to interleave unpredictably across semesters
        List<Subject> allSubjects = subjectRepo.findByDeletedFalse().stream()
                .sorted(Comparator.comparingInt(Subject::getId))
                .collect(Collectors.toList());
        List<Timeslot> slots = getEffectiveSlots();

        List<String> workingDays = getWorkingDays();
        List<Classroom> rooms = new ArrayList<>(roomRepo.findAll());
        // Sort divisions: MORNING first → claims Window A before AFTERNOON overflow can steal faculty.
        // Order: MORNING (BE) → MIDDAY (TE) → AFTERNOON (SE) → others.
        List<Division> allDivisions = divisionRepo.findAll().stream()
                .sorted(Comparator.comparingInt(d -> {
                    String pref = d.getLabPreference();
                    if (pref == null) return 99;
                    switch (pref.toUpperCase().trim()) {
                        case "MORNING":   return 0;
                        case "MIDDAY":    return 1;
                        case "AFTERNOON": return 2;
                        default:          return 3;
                    }
                }))
                .collect(Collectors.toList());
        List<User> allFaculty = userRepo.findByRoleAndDeletedFalse("FACULTY");

        if (rooms.isEmpty()) {
            Classroom fallbackRoom = new Classroom();
            fallbackRoom.setRoom("AutoRoom");
            fallbackRoom.setType("Lecture");
            rooms.add(fallbackRoom);
        }

        if (slots.isEmpty()) {
            result.addMessage("No timeslots configured. Add time slots first.");
            return result;
        }
        if (workingDays.isEmpty()) {
            result.addMessage("No working days configured. Please set working days under Time Slots.");
            return result;
        }
        if (rooms.isEmpty()) {
            result.addMessage("No classrooms available. Add at least one classroom to generate timetable.");
            return result;
        }
        if (allSubjects.isEmpty()) {
            result.addMessage("No subjects found to schedule.");
            return result;
        }

        List<Timetable> existingRows = timetableRepo.findByDeletedFalse();
        result.existingCount = existingRows.size();

        Map<String, User> facultyByName = new HashMap<>();
        for (User u : allFaculty) {
            if (u.getName() != null && !u.getName().isBlank()) {
                String key = safeLower(u.getName());
                if (!facultyByName.containsKey(key)) facultyByName.put(key, u);
            }
        }

        // Split divisions and subjects by semester parity so that odd-semester
        // faculty conflicts are NOT checked against even-semester slots and vice versa.
        // Even (0): sem 2,4,6,8 — Term II (Jan–May).  Odd (1): sem 1,3,5,7 — Term I (Jul–Nov).
        List<Division> evenDivs = allDivisions.stream()
                .filter(d -> semParity(d) == 0).collect(Collectors.toList());
        List<Division> oddDivs  = allDivisions.stream()
                .filter(d -> semParity(d) == 1).collect(Collectors.toList());
        List<Subject> evenSubjs = allSubjects.stream()
                .filter(s -> subjectParity(s) == 0).collect(Collectors.toList());
        List<Subject> oddSubjs  = allSubjects.stream()
                .filter(s -> subjectParity(s) == 1).collect(Collectors.toList());

        // Filter existing rows by parity to avoid polluting cross-term teacherBusy/roomBusy
        // Fallback className sets for rows that pre-date the semesterNumber column (null).
        Set<String> evenClassNames = evenDivs.stream()
                .map(d -> classNameForDiv(d).toLowerCase()).filter(s -> !s.isBlank())
                .collect(Collectors.toCollection(java.util.HashSet::new));
        Set<String> oddClassNames  = oddDivs.stream()
                .map(d -> classNameForDiv(d).toLowerCase()).filter(s -> !s.isBlank())
                .collect(Collectors.toCollection(java.util.HashSet::new));
        // Prefer semesterNumber (unambiguous); fall back to className for legacy rows.
        List<Timetable> evenExisting = existingRows.stream()
                .filter(t -> t.getSemesterNumber() != null
                        ? t.getSemesterNumber() % 2 == 0
                        : evenClassNames.contains(safeLower(t.getClassName())))
                .collect(Collectors.toList());
        List<Timetable> oddExisting  = existingRows.stream()
                .filter(t -> t.getSemesterNumber() != null
                        ? t.getSemesterNumber() % 2 != 0
                        : oddClassNames.contains(safeLower(t.getClassName())))
                .collect(Collectors.toList());

        List<Timetable> generated = new ArrayList<>();
        if (!evenDivs.isEmpty() || !evenSubjs.isEmpty()) {
            generated.addAll(runGenerationGroup(evenSubjs, evenDivs, slots, workingDays, rooms,
                    allFaculty, facultyByName, evenExisting, result));
        }
        if (!oddDivs.isEmpty() || !oddSubjs.isEmpty()) {
            generated.addAll(runGenerationGroup(oddSubjs, oddDivs, slots, workingDays, rooms,
                    allFaculty, facultyByName, oddExisting, result));
        }

        if (!generated.isEmpty()) timetableRepo.saveAll(generated);
        result.generated = generated;
        return result;
    }

    // Each parity group (odd/even semesters) runs with its own isolated conflict-tracking sets
    // so a faculty teaching in sem 3 (odd, Term I) is never falsely blocked for sem 4 (even, Term II).
    private List<Timetable> runGenerationGroup(
            List<Subject> subjects,
            List<Division> divisions,
            List<Timeslot> slots,
            List<String> workingDays,
            List<Classroom> rooms,
            List<User> allFaculty,
            Map<String, User> facultyByName,
            List<Timetable> existingRows,
            GenerationResult result) {

        Set<String> teacherBusy = new HashSet<>();
        Set<String> roomBusy = new HashSet<>();
        Set<String> classBusy = new HashSet<>();
        Set<String> subjectDayBusy = new HashSet<>();
        Map<String, Integer> facultyWeekCount = new HashMap<>();
        Map<String, Integer> facultyDayCount  = new HashMap<>();

        for (Timetable t : existingRows) {
            String slot = safeSlot(t.getTimeSlot());
            String day  = safeLower(t.getDay());
            teacherBusy.add(key(day, slot, safeLower(t.getFaculty())));
            roomBusy.add(key(day, slot, safeLower(t.getRoom())));
            classBusy.add(key(day, slot, safeLower(t.getClassName()), safeLower(t.getDivision())));
            subjectDayBusy.add(key(day, safeLower(t.getClassName()), safeLower(t.getDivision()), safeLower(t.getSubject())));
            if (t.getFaculty() != null) {
                String facKey = safeLower(t.getFaculty());
                facultyWeekCount.put(facKey, facultyWeekCount.getOrDefault(facKey, 0) + 1);
                facultyDayCount.put(key(facKey, day), facultyDayCount.getOrDefault(key(facKey, day), 0) + 1);
            }
        }

        List<Timetable> generated = new ArrayList<>();

        // ── LAB ROTATION PHASE ──
        // Build rotation list. Multi-block subjects are spread using round-robin interleaving
        // so they never land at consecutive indices. Consecutive identical subjects cause
        // same-faculty batches to collide in the same rotation window (e.g. DMSML S3+S4 both
        // need ARD simultaneously, CNSL T1+T2 both need RSL simultaneously).
        Map<Integer, List<Subject>> labsByDivId = new LinkedHashMap<>();
        // Collect subjects and their block counts first
        Map<Integer, List<Object[]>> divPendingLabs = new LinkedHashMap<>(); // divId → [(subj,blocks)]
        for (Subject subj : subjects) {
            if (isWholeClassActivity(subj)) continue;
            int _phpw = resolvePracticalHours(subj);
            int _pd   = _phpw > 0 ? practicalDuration(subj) : 0;
            if (_phpw > 0 && _pd > 0) {
                int _blocks = Math.max(1, (int) Math.ceil(_phpw / (double) _pd));
                for (Division div : findMatchingDivisions(divisions, subj)) {
                    divPendingLabs.computeIfAbsent(div.getId(), k -> new ArrayList<>())
                                  .add(new Object[]{subj, _blocks});
                }
            }
        }
        // Round-robin interleaving: add one slot per subject per round until all blocks placed.
        // This spaces multi-block subjects (DMSML×2, CNSL×2, LP5×2) apart in the rotation
        // so same-faculty batches never share a rotation window.
        for (Map.Entry<Integer, List<Object[]>> pe : divPendingLabs.entrySet()) {
            List<Object[]> pending = pe.getValue();
            List<Subject> divList = labsByDivId.computeIfAbsent(pe.getKey(), k -> new ArrayList<>());
            int[] remaining = pending.stream().mapToInt(ob -> (Integer)ob[1]).toArray();
            boolean anyLeft = true;
            while (anyLeft) {
                anyLeft = false;
                for (int i = 0; i < pending.size(); i++) {
                    if (remaining[i] > 0) {
                        divList.add((Subject) pending.get(i)[0]);
                        remaining[i]--;
                        if (remaining[i] > 0) anyLeft = true;
                    }
                }
            }
        }
        // ── PINNED SESSIONS PHASE ──
        // Subjects whose admin set pinDays + pinSlot are placed first at those exact positions.
        // No subject codes hardcoded here — fully data-driven.
        // To change next semester: edit the subject's pin fields via the Subject form in the UI.
        Set<String> rotationPlaced = new HashSet<>();
        for (Subject subject : subjects) {
            String rawPinDays = subject.getPinDays();
            String rawPinSlot = subject.getPinSlot();
            if (rawPinDays == null || rawPinDays.isBlank()) continue;
            if (rawPinSlot == null || rawPinSlot.isBlank()) continue;
            int slotMinutes = parseMinutes(rawPinSlot.trim());
            if (slotMinutes == Integer.MAX_VALUE) continue;
            String[] pinDayArr = rawPinDays.split(",");
            // For 2-hour pinned blocks (INTP, AC6, AC8, SEM8) place consecutive slots
            int pinDuration = (isWholeClassActivity(subject)
                    && subject.getPracticalSlotDuration() != null
                    && subject.getPracticalSlotDuration() > 1)
                    ? subject.getPracticalSlotDuration() : 1;
            for (Division div : findMatchingDivisions(divisions, subject)) {
                for (String rawDay : pinDayArr) {
                    String day = rawDay.trim();
                    if (day.isBlank()) continue;
                    for (int h = 0; h < pinDuration; h++) {
                        int slotMin = slotMinutes + (h * 60);
                        Timetable t = forcePlaceAt(subject, div, day, slotMin, slots, rooms, allFaculty,
                                teacherBusy, roomBusy, classBusy, subjectDayBusy,
                                facultyWeekCount, facultyDayCount, facultyByName, result);
                        if (t != null) generated.add(t);
                    }
                }
                rotationPlaced.add(subject.getId() + "_" + div.getId());
            }
        }

        for (Division division : divisions) {
            int bc = (division.getBatchCount() != null && division.getBatchCount() > 0)
                    ? division.getBatchCount() : 1;
            List<Subject> divLabs = labsByDivId.getOrDefault(division.getId(), Collections.emptyList());
            if (bc <= 1 || divLabs.size() <= 1) continue; // need multiple batches AND labs
            List<Timetable> rotRows = generateLabRotation(
                    division, divLabs, workingDays, slots, rooms, allFaculty,
                    teacherBusy, roomBusy, classBusy, subjectDayBusy,
                    facultyWeekCount, facultyDayCount, facultyByName, result);
            generated.addAll(rotRows);
            divLabs.forEach(s -> rotationPlaced.add(s.getId() + "_" + division.getId()));
        }

        // Pre-compute which subject names have at least one faculty assigned.
        // Uses already-loaded allFaculty list — no extra DB query needed.
        Set<String> facultyHandledNames = allFaculty.stream()
                .filter(u -> u.getSubjectsHandled() != null && !u.getSubjectsHandled().isBlank())
                .flatMap(u -> Arrays.stream(u.getSubjectsHandled().split(",")))
                .map(s -> safeLower(s.trim()))
                .collect(Collectors.toSet());

        // Sort: faculty-assigned subjects first (they are more constrained — fewer valid slots),
        // then no-faculty subjects fill the remaining gaps.
        // Within each group, higher lecture-hour subjects come first (existing behaviour).
        List<Subject> theoryOrderedSubjects = subjects.stream()
                .sorted(Comparator
                        .<Subject, Integer>comparing(s -> {
                            boolean hasFac = (s.getFaculty() != null && !s.getFaculty().isBlank())
                                    || facultyHandledNames.contains(safeLower(s.getName()));
                            return hasFac ? 0 : 1;
                        })
                        .thenComparingInt((Subject s) -> -resolveLectureHours(s)))
                .collect(Collectors.toList());

        for (Subject subject : theoryOrderedSubjects) {
            // Use natural Mon→Fri order. subjectDayBusy prevents same subject on same day,
            // so theory fills each day from the top and any spare capacity moves to later days.
            List<String> shuffledDays = new ArrayList<>(workingDays);
            // Subjects with pin_days but NO pin_slot: theory sessions must land on those days only.
            // (pin_slot subjects are whole-class activities handled above by the pin phase.)
            boolean pinnedDaysOnly = subject.getPinDays() != null && !subject.getPinDays().isBlank()
                    && (subject.getPinSlot() == null || subject.getPinSlot().isBlank());
            if (pinnedDaysOnly) {
                Set<String> pinDaySet = Arrays.stream(subject.getPinDays().split(","))
                        .map(d -> d.trim().toLowerCase()).collect(Collectors.toSet());
                List<String> restricted = shuffledDays.stream()
                        .filter(d -> pinDaySet.contains(d.toLowerCase()))
                        .collect(Collectors.toList());
                if (!restricted.isEmpty()) shuffledDays = restricted;
            }

            List<Division> targetDivisions = findMatchingDivisions(divisions, subject);
            if (targetDivisions.isEmpty()) {
                // create an in-memory placeholder division so something is generated
                Division placeholder = new Division();
                placeholder.setName("Auto");
                placeholder.setDepartment(subject.getDepartment());
                placeholder.setYear(mapSemToYear(subject.getSemester()));
                placeholder.setSemesterNumber(subject.getSemester());
                targetDivisions = List.of(placeholder);
                result.addMessage("Placed \"" + subject.getName() + "\" using an auto-created class because no division matched.");
            }

            int lectureSessions = resolveLectureHours(subject);
            int practicalHours = resolvePracticalHours(subject);
            int practicalDuration = practicalHours > 0 ? practicalDuration(subject) : 0;

            for (Division division : targetDivisions) {
                String rotKey = subject.getId() + "_" + division.getId();

            	if (!rotationPlaced.contains(rotKey) && practicalHours > 0 && practicalDuration > 0) {
                    int blocks = Math.max(1, (int) Math.ceil(practicalHours / (double) practicalDuration));
                    // Audit/Seminar subjects are whole-class activities — no batch split
                    boolean isWholeClass = isWholeClassActivity(subject);
                    int batchCount = isWholeClass ? 1
                            : (division.getBatchCount() != null && division.getBatchCount() > 0)
                                    ? division.getBatchCount() : 1;
                    String batchPrefix = isWholeClass ? ""
                            : (division.getBatchPrefix() != null && !division.getBatchPrefix().isBlank())
                                    ? division.getBatchPrefix() : "";
                    // Whole-class audit activities (VL, INTP, AC, SEM, TP) don't need a
                    // physical lab room — use the subject's own type so any free room qualifies.
                    String practicalSessionType = isWholeClass
                            ? (subject.getType() != null ? subject.getType() : "Audit")
                            : "Practical";
                    result.requested += blocks * batchCount * practicalDuration;
                    Set<String> practicalDaysUsed = new HashSet<>();
                    for (int blk = 0; blk < blocks; blk++) {
                        List<String> availableDays = shuffledDays.stream()
                                .filter(d -> !practicalDaysUsed.contains(d.toLowerCase()))
                                .collect(Collectors.toList());
                        if (availableDays.isEmpty()) availableDays = new ArrayList<>(shuffledDays);
                        // Place ALL batches simultaneously in the same timeslot (different rooms)
                        List<Timetable> placed = tryPlaceAllBatchesSimultaneously(
                                subject, division, availableDays, slots, rooms, allFaculty,
                                teacherBusy, roomBusy, classBusy, subjectDayBusy,
                                facultyWeekCount, facultyDayCount, facultyByName,
                                practicalDuration, practicalSessionType, batchCount, batchPrefix);
                        if (placed == null) {
                            // Fallback: relax day-exclusivity constraint
                            placed = tryPlaceAllBatchesSimultaneously(
                                    subject, division, new ArrayList<>(shuffledDays), slots, rooms, allFaculty,
                                    teacherBusy, roomBusy, classBusy, subjectDayBusy,
                                    facultyWeekCount, facultyDayCount, facultyByName,
                                    practicalDuration, practicalSessionType, batchCount, batchPrefix);
                        }
                        if (placed != null) {
                            practicalDaysUsed.add(placed.get(0).getDay().toLowerCase());
                            generated.addAll(placed);
                            result.placed += placed.size();
                        } else {
                            result.addMessage("Could not place practical block (" + practicalDuration + " slots) for "
                                    + subject.getName() + labelForDivision(division)
                                    + " (block " + (blk + 1) + "/" + blocks + ")");
                        }
                    }
                }

                List<Timeslot> lectureSlots = slots.stream()
                        .filter(ts -> !ts.isBreak())
                        .collect(Collectors.toList());
                Collections.shuffle(lectureSlots); // randomise slot preference per subject for fair first-lecture distribution
                String className = deriveClassName(subject, division);
                String divisionName = division != null ? division.getName() : "";
                for (int i = 0; i < lectureSessions; i++) {
                    result.requested++;
                    // Spread theory sessions: sort days so non-adjacent days come first,
                    // avoiding 3+ consecutive calendar days for the same subject.
                    List<String> spreadDays = spreadTheoryDays(shuffledDays, subject.getName(),
                            className, divisionName, subjectDayBusy, workingDays);
                    Timetable placed = tryPlace(subject, division, spreadDays, lectureSlots, rooms, allFaculty,
                            teacherBusy, roomBusy, classBusy, subjectDayBusy,
                            facultyWeekCount, facultyDayCount, facultyByName, "Lecture", pinnedDaysOnly);
                    if (placed == null) {
                        placed = tryPlaceRelaxed(subject, division, spreadDays, slots, rooms, allFaculty,
                                teacherBusy, roomBusy, classBusy, subjectDayBusy, facultyByName, "Lecture");
                    }
                    if (placed != null) {
                        generated.add(placed);
                        result.placed++;
                    } else {
                        if (subject.getCode() != null && subject.getCode().equals("CG")) {
                            System.out.println("[CG DEBUG] failed session " + (i+1) + "/" + lectureSessions
                                + " div=" + division.getName()
                                + " shuffledDays=" + shuffledDays
                                + " classBusy(SE-IT)=" + classBusy.stream().filter(k->k.contains("se(it)")).toList()
                                + " teacherBusy(PGK)=" + teacherBusy.stream().filter(k->k.contains("khaire")).toList());
                        }
                        result.addMessage("Could not place " + subject.getName() +
                                labelForDivision(division) + " (lecture " + (i + 1) + "/" + lectureSessions + ")");
                    }
                }
            }

        }

        return generated;
    }

    private int semParity(Division d) {
        Integer sem = d.getSemesterNumber();
        return (sem != null && sem % 2 != 0) ? 1 : 0; // 1=odd term, 0=even term
    }

    private int subjectParity(Subject s) {
        Integer sem = s.getSemester();
        return (sem != null && sem % 2 != 0) ? 1 : 0;
    }

    // Resolves the semester number for a timetable row being created.
    // Division is the authoritative source; subject is a fallback if division has no number.
    private Integer resolveRowSem(Subject subject, Division division) {
        if (division != null && division.getSemesterNumber() != null) return division.getSemesterNumber();
        if (subject != null) return subject.getSemester();
        return null;
    }

    private String classNameForDiv(Division d) {
        String year = d.getYear() != null ? d.getYear().trim() : "";
        String dept = d.getDepartment() != null ? d.getDepartment().trim() : "";
        String deptCode = shortDeptCode(dept);
        String shortYear = toShortYear(year, d.getSemesterNumber());
        if (!shortYear.isEmpty() && !deptCode.isEmpty()) return shortYear + "(" + deptCode + ")";
        if (!shortYear.isEmpty()) return shortYear;
        return year;
    }

    /**
     * Hard-pins a subject at an exact day + slot identified by start time (minutes since midnight).
     * Used for VL, INTP, AC6, AC8, SEM8, TP8 which have fixed reference positions.
     */
    private Timetable forcePlaceAt(Subject subject, Division division,
                                   String day, int startMinutes,
                                   List<Timeslot> allSlots, List<Classroom> rooms, List<User> allFaculty,
                                   Set<String> teacherBusy, Set<String> roomBusy, Set<String> classBusy,
                                   Set<String> subjectDayBusy,
                                   Map<String,Integer> facultyWeekCount, Map<String,Integer> facultyDayCount,
                                   Map<String,User> facultyByName, GenerationResult result) {
        Timeslot targetSlot = allSlots.stream()
                .filter(s -> !s.isBreak())
                .filter(s -> { Integer m = parseMinutes(s.getStartTime()); return m != null && m == startMinutes; })
                .findFirst().orElse(null);
        if (targetSlot == null) {
            result.addMessage("Cannot pin " + subject.getName() + " — no slot at " + startMinutes + " min");
            return null;
        }
        String dayKey   = safeLower(day);
        String slotLabel = formatSlot(targetSlot);
        String slotKey   = safeSlot(slotLabel);
        String className     = deriveClassName(subject, division);
        String divisionName  = division != null ? division.getName() : "";

        if (classBusy.contains(key(dayKey, slotKey, safeLower(className), safeLower(divisionName)))) {
            result.addMessage("Cannot pin " + subject.getName() + " on " + day + " " + slotLabel + " — slot already busy");
            return null;
        }
        // Audit-type subjects (VL, Audit Course) are whole-class informational sessions.
        // No specialist faculty needed — leave blank so no real teacher is blocked
        // for simultaneous lab/theory sessions in other divisions.
        String subjType = subject.getType() != null ? subject.getType().toLowerCase() : "";
        boolean noFacultyNeeded = subjType.equals("audit") && (subject.getPracticalHoursPerWeek() != null
                && subject.getPracticalHoursPerWeek() > 0 && (subject.getLectureHoursPerWeek() == null
                || subject.getLectureHoursPerWeek() == 0));
        List<String> facultyNames = noFacultyNeeded ? Collections.emptyList() : buildFacultyList(subject, allFaculty);
        String assignedFaculty = facultyNames.stream()
                .filter(f -> f != null && !f.isBlank())
                .filter(f -> !teacherBusy.contains(key(dayKey, slotKey, safeLower(f))))
                .findFirst().orElse("");

        String sessionType   = subject.getType() != null ? subject.getType() : "Audit";
        String preferredRoom = division != null ? division.getClassroom() : null;
        Classroom room = pickRoom(rooms, preferredRoom, roomBusy, dayKey, slotKey, sessionType);
        if (room == null) {
            result.addMessage("Cannot pin " + subject.getName() + " on " + day + " " + slotLabel + " — no room");
            return null;
        }
        Timetable t = new Timetable();
        t.setDay(day);
        t.setDepartment(subject.getDepartment());
        t.setClassName(className);
        t.setDivision(divisionName);
        t.setSubject(subject.getName());
        t.setFaculty(assignedFaculty);
        t.setRoom(room.getRoom());
        t.setLectureType(sessionType);
        t.setTimeSlot(slotLabel);
        t.setSemesterNumber(resolveRowSem(subject, division));
        t.setStatus("Pending");

        if (!assignedFaculty.isBlank()) {
            String facKey = safeLower(assignedFaculty);
            teacherBusy.add(key(dayKey, slotKey, facKey));
            facultyWeekCount.merge(facKey, 1, Integer::sum);
            facultyDayCount.merge(key(facKey, dayKey), 1, Integer::sum);
        }
        roomBusy.add(key(dayKey, slotKey, safeLower(room.getRoom())));
        classBusy.add(key(dayKey, slotKey, safeLower(className), safeLower(divisionName)));
        subjectDayBusy.add(key(dayKey, safeLower(className), safeLower(divisionName), safeLower(subject.getName())));

        result.requested++;
        result.placed++;
        return t;
    }

    private Timetable tryPlace(Subject subject,
                               Division division,
                               List<String> days,
                               List<Timeslot> slots,
                               List<Classroom> rooms,
                               List<User> faculty,
                               Set<String> teacherBusy,
                               Set<String> roomBusy,
                               Set<String> classBusy,
                               Set<String> subjectDayBusy,
                               Map<String,Integer> facWeek,
                               Map<String,Integer> facDay,
                               Map<String,User> facultyByName,
                               String sessionType,
                               boolean allowSameDay) {

        String className = deriveClassName(subject, division);
        String divisionName = division != null ? division.getName() : "";
        String preferredRoom = division != null ? division.getClassroom() : null;

        List<String> facultyNames = buildFacultyList(subject, faculty);

        String subjectKey = safeLower(subject.getName());

        for (String day : days) {
            String dayKey = safeLower(day);
            // Skip days where this subject is already placed (unless pinned to this day)
            if (!allowSameDay && subjectDayBusy.contains(key(dayKey, safeLower(className), safeLower(divisionName), subjectKey))) {
                continue;
            }

            for (Timeslot slot : slots) {
                String slotLabel = formatSlot(slot);
                String slotKey = safeSlot(slotLabel);

                if (classBusy.contains(key(dayKey, slotKey, safeLower(className), safeLower(divisionName)))) {
                    continue; // class already busy in this slot
                }

                // Sort faculty for this day/slot by current load (day first, then week)
                List<String> sortedByLoad = facultyNames.stream()
                        .sorted(Comparator
                                .comparingInt((String f) -> isSpecialist(f, facultyByName, subjectKey) ? 0 : 1)
                                .thenComparingInt((String f) -> facDay.getOrDefault(key(safeLower(f), dayKey), 0))
                                .thenComparingInt(f -> facWeek.getOrDefault(safeLower(f), 0))
                        )
                        .toList();

                for (String facultyName : sortedByLoad) {
                    if (facultyName == null || facultyName.isBlank()) {
                        continue;
                    }
                    String facKey = safeLower(facultyName);
                    if (teacherBusy.contains(key(dayKey, slotKey, facKey))) {
                        continue; // faculty clash
                    }
                    // capacity limits
                    User facUser = facultyByName.get(facKey);
                    Integer maxWeek = facUser != null ? facUser.getMaxLecturesPerWeek() : null;
                    Integer maxDay = facUser != null ? facUser.getMaxLecturesPerDay() : null;
                    int currentWeek = facWeek.getOrDefault(facKey, 0);
                    int currentDay = facDay.getOrDefault(key(facKey, dayKey), 0);
                    if(maxWeek != null && maxWeek > 0 && currentWeek >= maxWeek) continue;
                    if(maxDay != null && maxDay > 0 && currentDay >= maxDay) continue;

                    Classroom room = pickRoom(rooms, preferredRoom, roomBusy, dayKey, slotKey, sessionType);
                    if (room == null) {
                        continue; // no free room for this slot
                    }

                    Timetable t = new Timetable();
                    t.setDay(day);
                    t.setDepartment(subject.getDepartment());
                    t.setClassName(className);
                    t.setDivision(divisionName);
                    t.setSubject(subject.getName());
                    t.setFaculty(facultyName);
                    t.setRoom(room.getRoom());
                    t.setLectureType(sessionType != null && !sessionType.isBlank()
                            ? sessionType
                            : (subject.getType() != null ? subject.getType() : "Lecture"));
                    t.setTimeSlot(slotLabel);
                    t.setSemesterNumber(resolveRowSem(subject, division));
                    t.setStatus("Pending");

                    // mark clashes
                    teacherBusy.add(key(dayKey, slotKey, facKey));
                    roomBusy.add(key(dayKey, slotKey, safeLower(room.getRoom())));
                    classBusy.add(key(dayKey, slotKey, safeLower(className), safeLower(divisionName)));
                    subjectDayBusy.add(key(dayKey, safeLower(className), safeLower(divisionName), safeLower(subject.getName())));
                    facWeek.put(facKey, facWeek.getOrDefault(facKey,0)+1);
                    facDay.put(key(facKey, dayKey), facDay.getOrDefault(key(facKey, dayKey),0)+1);

                    return t;
                }
            }
        }
        return null;
    }

    // Relaxed placer: ignores faculty max caps but still prefers days where subject isn't yet placed.
    // Same-day placement is allowed only when every other day has already been tried and failed.
    private Timetable tryPlaceRelaxed(Subject subject,
                                      Division division,
                                      List<String> days,
                                      List<Timeslot> slots,
                                      List<Classroom> rooms,
                                      List<User> faculty,
                                      Set<String> teacherBusy,
                                      Set<String> roomBusy,
                                      Set<String> classBusy,
                                      Set<String> subjectDayBusy,
                                      Map<String,User> facultyByName,
                                      String sessionType){

        String className = deriveClassName(subject, division);
        String divisionName = division != null ? division.getName() : "";
        String preferredRoom = division != null ? division.getClassroom() : null;
        List<String> facultyNames = buildFacultyList(subject, faculty);
        String subjectKey = safeLower(subject.getName());

        // Sort: days where subject NOT yet placed come first, same-day as last resort
        List<String> sortedDays = days.stream().sorted(Comparator.comparingInt(d ->
                subjectDayBusy.contains(key(safeLower(d), safeLower(className), safeLower(divisionName), subjectKey))
                        ? 1 : 0)).collect(Collectors.toList());

        // Pass 1: hard-skip days where this subject is already placed today
        // Pass 2: fallback — allow same-day only if no other day had a free slot
        for (int pass = 1; pass <= 2; pass++) {
            for (String day : sortedDays) {
                String dayKey = safeLower(day);
                if (pass == 1 && subjectDayBusy.contains(key(dayKey, safeLower(className), safeLower(divisionName), subjectKey))) continue;

                for (Timeslot slot : slots) {
                    String slotLabel = formatSlot(slot);
                    String slotKey = safeSlot(slotLabel);
                    if (classBusy.contains(key(dayKey, slotKey, safeLower(className), safeLower(divisionName)))) continue;

                    if (facultyNames.isEmpty()) {
                        // No faculty required (Project, PS2) — place without teacher constraint
                        Classroom room = pickRoom(rooms, preferredRoom, roomBusy, dayKey, slotKey, sessionType);
                        if (room == null) continue;
                        Timetable t = new Timetable();
                        t.setDay(day); t.setDepartment(subject.getDepartment());
                        t.setClassName(className); t.setDivision(divisionName);
                        t.setSubject(subject.getName()); t.setFaculty("");
                        t.setRoom(room.getRoom());
                        t.setLectureType(sessionType != null && !sessionType.isBlank() ? sessionType : (subject.getType() != null ? subject.getType() : "Lecture"));
                        t.setTimeSlot(slotLabel); t.setSemesterNumber(resolveRowSem(subject, division)); t.setStatus("Pending");
                        roomBusy.add(key(dayKey, slotKey, safeLower(room.getRoom())));
                        classBusy.add(key(dayKey, slotKey, safeLower(className), safeLower(divisionName)));
                        subjectDayBusy.add(key(dayKey, safeLower(className), safeLower(divisionName), subjectKey));
                        return t;
                    }

                    for (String facultyName : facultyNames) {
                        if (facultyName == null || facultyName.isBlank()) continue;
                        String facKey = safeLower(facultyName);
                        if (teacherBusy.contains(key(dayKey, slotKey, facKey))) continue;

                        Classroom room = pickRoom(rooms, preferredRoom, roomBusy, dayKey, slotKey, sessionType);
                        if (room == null) continue;

                        Timetable t = new Timetable();
                        t.setDay(day);
                        t.setDepartment(subject.getDepartment());
                        t.setClassName(className);
                        t.setDivision(divisionName);
                        t.setSubject(subject.getName());
                        t.setFaculty(facultyName);
                        t.setRoom(room.getRoom());
                        t.setLectureType(sessionType != null && !sessionType.isBlank()
                                ? sessionType
                                : (subject.getType() != null ? subject.getType() : "Lecture"));
                        t.setTimeSlot(slotLabel);
                        t.setSemesterNumber(resolveRowSem(subject, division));
                        t.setStatus("Pending");

                        teacherBusy.add(key(dayKey, slotKey, facKey));
                        roomBusy.add(key(dayKey, slotKey, safeLower(room.getRoom())));
                        classBusy.add(key(dayKey, slotKey, safeLower(className), safeLower(divisionName)));
                        subjectDayBusy.add(key(dayKey, safeLower(className), safeLower(divisionName), subjectKey));
                        return t;
                    }
                }
            }
        }
        return null;
    }
    /** Returns true only if all slots in the list are time-adjacent (no gaps between them) */
    private boolean areConsecutive(List<Timeslot> window) {
        for (int i = 0; i < window.size() - 1; i++) {
            Integer endOfCurrent = parseMinutes(window.get(i).getEndTime());
            Integer startOfNext  = parseMinutes(window.get(i + 1).getStartTime());
            if (endOfCurrent == null || startOfNext == null) return false;
            if (!endOfCurrent.equals(startOfNext)) return false; // gap or overlap
        }
        return true;
    }

    private List<Timetable> tryPlaceLab(Subject subject,
                                        Division division,
                                        List<String> days,
                                        List<Timeslot> slots,
                                        List<Classroom> rooms,
                                        List<User> faculty,
                                        Set<String> teacherBusy,
                                        Set<String> roomBusy,
                                        Set<String> classBusy,
                                        Set<String> subjectDayBusy,
                                        Map<String,Integer> facWeek,
                                        Map<String,Integer> facDay,
                                        Map<String,User> facultyByName,
                                        int durationSlots,
                                        String sessionType) {

        String className = deriveClassName(subject, division);
        String divisionName = division != null ? division.getName() : "";
        String preferredRoom = division != null ? division.getClassroom() : null;

        List<String> facultyNames = buildFacultyList(subject, faculty);
        List<Timeslot> orderedSlots = getOrderedSlots(slots);
        if (orderedSlots.size() < durationSlots) {
            return null;
        }

        String subjectKey = safeLower(subject.getName());

        for (String day : days) {
            String dayKey = safeLower(day);
            if (subjectDayBusy.contains(key(dayKey, safeLower(className), safeLower(divisionName), safeLower(subject.getName())))) {
                continue;
            }
            for (int start = 0; start <= orderedSlots.size() - durationSlots; start++) {
                List<Timeslot> window = orderedSlots.subList(start, start + durationSlots);
                if (!areConsecutive(window)) continue; // skip non-adjacent windows

                List<String> sortedByLoad = facultyNames.stream()
                        .sorted(Comparator
                                .comparingInt((String f) -> isSpecialist(f, facultyByName, subjectKey) ? 0 : 1)
                                .thenComparingInt((String f) -> facDay.getOrDefault(key(safeLower(f), dayKey), 0))
                                .thenComparingInt(f -> facWeek.getOrDefault(safeLower(f), 0))
                        )
                        .toList();

                for (String facultyName : sortedByLoad) {
                    if (facultyName == null || facultyName.isBlank()) continue;
                    String facKey = safeLower(facultyName);

                    boolean clash = false;
                    for (Timeslot slot : window) {
                        String slotKey = safeSlot(formatSlot(slot));
                        if (teacherBusy.contains(key(dayKey, slotKey, facKey)) ||
                                classBusy.contains(key(dayKey, slotKey, safeLower(className), safeLower(divisionName)))) {
                            clash = true;
                            break;
                        }
                    }
                    if (clash) continue;

                    User facUser = facultyByName.get(facKey);
                    Integer maxWeek = facUser != null ? facUser.getMaxLecturesPerWeek() : null;
                    Integer maxDay = facUser != null ? facUser.getMaxLecturesPerDay() : null;
                    int currentWeek = facWeek.getOrDefault(facKey, 0);
                    int currentDay = facDay.getOrDefault(key(facKey, dayKey), 0);
                    if (maxWeek != null && maxWeek > 0 && currentWeek + durationSlots > maxWeek) continue;
                    if (maxDay != null && maxDay > 0 && currentDay + durationSlots > maxDay) continue;

                    Classroom room = pickRoomForBlock(rooms, preferredRoom, roomBusy, dayKey, window, sessionType);
                    if (room == null) continue;

                    List<Timetable> block = new ArrayList<>();
                    for (Timeslot slot : window) {
                        String slotLabel = formatSlot(slot);
                        String slotKey = safeSlot(slotLabel);

                        Timetable t = new Timetable();
                        t.setDay(day);
                        t.setDepartment(subject.getDepartment());
                        t.setClassName(className);
                        t.setDivision(divisionName);
                        t.setSubject(subject.getName());
                        t.setFaculty(facultyName);
                        t.setRoom(room.getRoom());
                        t.setLectureType(sessionType != null && !sessionType.isBlank()
                                ? sessionType
                                : (subject.getType() != null ? subject.getType() : "Lab"));
                        t.setTimeSlot(slotLabel);
                        t.setSemesterNumber(resolveRowSem(subject, division));
                        t.setStatus("Pending");

                        block.add(t);

                        teacherBusy.add(key(dayKey, slotKey, facKey));
                        roomBusy.add(key(dayKey, slotKey, safeLower(room.getRoom())));
                        classBusy.add(key(dayKey, slotKey, safeLower(className), safeLower(divisionName)));
                        facWeek.put(facKey, facWeek.getOrDefault(facKey,0)+1);
                        facDay.put(key(facKey, dayKey), facDay.getOrDefault(key(facKey, dayKey),0)+1);
                    }
                    subjectDayBusy.add(key(dayKey, safeLower(className), safeLower(divisionName), safeLower(subject.getName())));
                    return block;
                }
            }
        }
        return null;
    }

    // Fallback that progressively shortens lab duration and relaxes subject-per-day constraint
    private List<Timetable> tryPlaceLabFallback(Subject subject,
                                                Division division,
                                                List<String> days,
                                                List<Timeslot> slots,
                                                List<Classroom> rooms,
                                                List<User> faculty,
                                                Set<String> teacherBusy,
                                                Set<String> roomBusy,
                                                Set<String> classBusy,
                                                Set<String> subjectDayBusy,
                                                Map<String,Integer> facWeek,
                                                Map<String,Integer> facDay,
                                                Map<String,User> facultyByName,
                                                int durationSlots,
                                                String sessionType){

        int minDuration = 1;
        for(int d = Math.min(durationSlots, slots.size()); d >= minDuration; d--){
            List<Timetable> block = tryPlaceLabRelaxed(subject, division, days, slots, rooms, faculty,
                    teacherBusy, roomBusy, classBusy, subjectDayBusy,
                    facWeek, facDay, facultyByName, d, sessionType);
            if(block != null) return block;
        }
        return null;
    }

    private List<Timetable> tryPlaceLabRelaxed(Subject subject,
                                               Division division,
                                               List<String> days,
                                               List<Timeslot> slots,
                                               List<Classroom> rooms,
                                               List<User> faculty,
                                               Set<String> teacherBusy,
                                               Set<String> roomBusy,
                                               Set<String> classBusy,
                                               Set<String> subjectDayBusy,
                                               Map<String,Integer> facWeek,
                                               Map<String,Integer> facDay,
                                               Map<String,User> facultyByName,
                                               int durationSlots,
                                               String sessionType){

        String className = deriveClassName(subject, division);
        String divisionName = division != null ? division.getName() : "";
        String preferredRoom = division != null ? division.getClassroom() : null;
        List<String> facultyNames = buildFacultyList(subject, faculty);
        List<Timeslot> orderedSlots = getOrderedSlots(slots);
        if (orderedSlots.size() < durationSlots) return null;

        for (String day : days) {
            String dayKey = safeLower(day);
            for (int start = 0; start <= orderedSlots.size() - durationSlots; start++) {
                List<Timeslot> window = orderedSlots.subList(start, start + durationSlots);
                if (!areConsecutive(window)) continue; // skip non-adjacent windows
                for (String facultyName : facultyNames) {
                    if (facultyName == null || facultyName.isBlank()) continue;
                    String facKey = safeLower(facultyName);

                    boolean clash = false;
                    for (Timeslot slot : window) {
                        String slotKey = safeSlot(formatSlot(slot));
                        if (teacherBusy.contains(key(dayKey, slotKey, facKey)) ||
                                classBusy.contains(key(dayKey, slotKey, safeLower(className), safeLower(divisionName)))) {
                            clash = true;
                            break;
                        }
                    }
                    if (clash) continue;

                    Classroom room = pickRoomForBlock(rooms, preferredRoom, roomBusy, dayKey, window, sessionType);
                    if (room == null) continue;

                    List<Timetable> block = new ArrayList<>();
                    for (Timeslot slot : window) {
                        String slotLabel = formatSlot(slot);
                        String slotKey = safeSlot(slotLabel);

                        Timetable t = new Timetable();
                        t.setDay(day);
                        t.setDepartment(subject.getDepartment());
                        t.setClassName(className);
                        t.setDivision(divisionName);
                        t.setSubject(subject.getName());
                        t.setFaculty(facultyName);
                        t.setRoom(room.getRoom());
                        t.setLectureType(sessionType != null && !sessionType.isBlank()
                                ? sessionType
                                : (subject.getType() != null ? subject.getType() : "Lab"));
                        t.setTimeSlot(slotLabel);
                        t.setSemesterNumber(resolveRowSem(subject, division));
                        t.setStatus("Pending");

                        block.add(t);

                        teacherBusy.add(key(dayKey, slotKey, facKey));
                        roomBusy.add(key(dayKey, slotKey, safeLower(room.getRoom())));
                        classBusy.add(key(dayKey, slotKey, safeLower(className), safeLower(divisionName)));
                        facWeek.put(facKey, facWeek.getOrDefault(facKey,0)+1);
                        facDay.put(key(facKey, dayKey), facDay.getOrDefault(key(facKey, dayKey),0)+1);
                    }
                    subjectDayBusy.add(key(dayKey, safeLower(className), safeLower(divisionName), safeLower(subject.getName())));
                    return block;
                }
            }
        }
        return null;
    }

    /**
     * Re-orders a lab rotation list so no two occurrences of the same subject are spaced
     * such that same-faculty batches would collide in the same rotation window.
     * Conflict condition: spacing (j-i) % n equals (B2-B1) % n for a same-faculty batch pair.
     * Fix: move extra occurrences to spacing = nearest value to n/2 that avoids all bad spacings.
     */
    private List<Subject> resolveLabRotationConflicts(List<Subject> labs, int batchCount,
            String batchPrefix, Map<String, String> batchPinned) {
        List<Subject> result = new ArrayList<>(labs);
        Set<Integer> processed = new HashSet<>();
        // Iterate over subjects; for each multi-occurrence subject, reposition extras
        int scan = 0;
        while (scan < result.size()) {
            Subject s = result.get(scan);
            if (!processed.add(s.getId())) { scan++; continue; }
            // Find ALL positions of this subject in the current list
            List<Integer> positions = new ArrayList<>();
            for (int k = 0; k < result.size(); k++) {
                if (result.get(k).getId() == s.getId()) positions.add(k);
            }
            if (positions.size() < 2) { scan++; continue; }
            // Re-position each extra occurrence (index 1+) for optimal spacing
            for (int extra = 1; extra < positions.size(); extra++) {
                int extraPos = positions.get(extra);
                result.remove(extraPos);
                int firstPos = -1;
                for (int k = 0; k < result.size(); k++) {
                    if (result.get(k).getId() == s.getId()) { firstPos = k; break; }
                }
                int newN = result.size() + 1;
                int spacing = findOptimalSpacing(s, batchCount, batchPrefix, batchPinned, newN);
                int insertAt = (firstPos + spacing) % newN;
                result.add(insertAt, s);
                // Refresh positions for any remaining extras
                positions = new ArrayList<>();
                for (int k = 0; k < result.size(); k++) {
                    if (result.get(k).getId() == s.getId()) positions.add(k);
                }
            }
            scan++;
        }
        return result;
    }

    /** Returns spacing S (1 ≤ S < n) that avoids same-faculty batch conflicts for subject s. */
    private int findOptimalSpacing(Subject s, int batchCount, String batchPrefix,
            Map<String, String> batchPinned, int n) {
        Set<Integer> bad = new HashSet<>();
        for (int b1 = 0; b1 < batchCount; b1++) {
            String bl1 = batchCount > 1 ? batchPrefix + (b1 + 1) : "";
            String f1 = batchPinned.get(s.getId() + "_" + bl1);
            if (f1 == null || f1.isBlank()) continue;
            for (int b2 = b1 + 1; b2 < batchCount; b2++) {
                String bl2 = batchCount > 1 ? batchPrefix + (b2 + 1) : "";
                String f2 = batchPinned.get(s.getId() + "_" + bl2);
                if (f2 == null || f2.isBlank()) continue;
                if (safeLower(f1).equals(safeLower(f2))) {
                    int diff = (b2 - b1 + n) % n;
                    bad.add(diff);
                    bad.add((n - diff) % n); // symmetric
                }
            }
        }
        int target = n / 2;
        for (int delta = 0; delta < n; delta++) {
            int s1 = (target + delta) % n;
            if (s1 > 0 && !bad.contains(s1)) return s1;
            int s2 = ((target - delta) % n + n) % n;
            if (s2 > 0 && s2 != s1 && !bad.contains(s2)) return s2;
        }
        return target > 0 ? target : 1;
    }

    /**
     * Rotation schedule: numLabs days, each day all batches are in different labs simultaneously.
     * On rotation day R, batch B does labSubjects[(B + R) % numLabs].
     * This produces the same layout as the reference master timetable.
     */
    private List<Timetable> generateLabRotation(
            Division division, List<Subject> labSubjects, List<String> workingDays,
            List<Timeslot> slots, List<Classroom> rooms, List<User> allFaculty,
            Set<String> teacherBusy, Set<String> roomBusy, Set<String> classBusy,
            Set<String> subjectDayBusy,
            Map<String, Integer> facWeek, Map<String, Integer> facDay,
            Map<String, User> facultyByName, GenerationResult result) {

        int batchCount = (division.getBatchCount() != null && division.getBatchCount() > 0)
                ? division.getBatchCount() : 1;
        String batchPrefix = (division.getBatchPrefix() != null && !division.getBatchPrefix().isBlank())
                ? division.getBatchPrefix() : "";
        String className  = deriveClassName(labSubjects.get(0), division);
        String divName    = division.getName() != null ? division.getName() : "";
        int numLabs       = labSubjects.size();
        int durationSlots = labSubjects.stream().mapToInt(this::practicalDuration).max().orElse(2);

        // Load batch-pinned faculty from subject_faculty_assignment for this division.
        // Key: subjectId + "_" + batchLabel → facultyName (e.g. "28_B1" → "Dr. S. R. Kokane")
        List<Integer> labSubjectIds = labSubjects.stream().map(Subject::getId).collect(Collectors.toList());
        Map<String, String> batchPinned = new HashMap<>();
        for (SubjectFacultyAssignment a : sfaRepo.findBySubjectIdIn(labSubjectIds)) {
            String sfaDivName = a.getDivisionName() != null ? a.getDivisionName().trim() : "";
            if (divName.trim().equals(sfaDivName) && a.getBatch() != null && a.getFacultyName() != null
                    && !a.getFacultyName().isBlank()) {
                batchPinned.put(a.getSubjectId() + "_" + a.getBatch().trim(), a.getFacultyName());
            }
        }


        // Re-order labSubjects so same-subject duplicates have non-conflicting spacing.
        // A duplicate at adjacent indices causes same-faculty batches to collide in the same
        // rotation window (e.g. DMSML S3+S4 both need ARD when DMSML is at indices 5 and 6).
        labSubjects = resolveLabRotationConflicts(new ArrayList<>(labSubjects), batchCount, batchPrefix, batchPinned);
        numLabs = labSubjects.size(); // re-derive in case list was rebuilt

        // Pre-build faculty candidate list per lab.
        // Project-type subjects (PS2) and Library subjects need no faculty — return empty list
        // so the rotation places them with a blank faculty entry (no teacher blocked).
        List<List<String>> facPerLab = new ArrayList<>();
        for (Subject lab : labSubjects) {
            String labType = lab.getType() != null ? lab.getType().toLowerCase() : "";
            String labCode = lab.getCode() != null ? lab.getCode().toUpperCase() : "";
            boolean noFacNeeded = labType.equals("project")
                    || labCode.startsWith("LIB");
            facPerLab.add(noFacNeeded ? Collections.emptyList() : buildFacultyList(lab, allFaculty));
        }

        List<Timeslot> orderedSlots = getOrderedSlots(slots);
        if (orderedSlots.size() < durationSlots) return Collections.emptyList();

        // Build all valid consecutive windows, then sort by division's lab_preference.
        // MORNING (BE)  → Window A (09:00) first; MIDDAY (TE) → Window B (11:15) first;
        // AFTERNOON (SE) → Window C (14:00) first. This prevents cross-division faculty
        // conflicts caused by different divisions sharing the same window.
        List<Integer> windowStarts = new ArrayList<>();
        for (int i = 0; i <= orderedSlots.size() - durationSlots; i++) {
            if (areConsecutive(orderedSlots.subList(i, i + durationSlots))) windowStarts.add(i);
        }
        // Sort windows by a strict priority order per division, preventing cross-division overflow
        // conflicts.  Pure distance-sort puts Window B before Window A for SE-IT (AFTERNOON),
        // causing SE-IT overflow to collide with TE-IT's primary Window B.
        // Priority order:
        //   AFTERNOON (SE-IT): C(840) → A(540) → B(675)
        //   MIDDAY    (TE-IT): B(675) → C(840) → A(540)
        //   MORNING   (BE-IT): A(540) → C(840) → B(675)
        String labPref = division.getLabPreference();
        if (labPref != null && !labPref.isBlank()) {
            final List<Integer> priority;
            switch (labPref.toUpperCase().trim()) {
                case "AFTERNOON": priority = java.util.Arrays.asList(840, 540, 675); break; // C→A→B
                case "MIDDAY":    priority = java.util.Arrays.asList(675, 840, 540); break; // B→C→A
                default:          priority = java.util.Arrays.asList(540, 840, 675); break; // A→C→B
            }
            windowStarts.sort(Comparator.comparingInt(wStart -> {
                int sm = slotStartMinutes(orderedSlots.get(wStart));
                // Find nearest priority bucket (A=540, B=675, C=840)
                int rank = 0;
                int bestDist = Integer.MAX_VALUE;
                for (int pi = 0; pi < priority.size(); pi++) {
                    int dist = Math.abs(sm - priority.get(pi));
                    if (dist < bestDist) { bestDist = dist; rank = pi; }
                }
                return rank;
            }));
        }

        // Use natural day order (Mon→Fri) for deterministic, conflict-free rotation.
        List<String> shuffledDays = new ArrayList<>(workingDays);

        List<Timetable> allRows  = new ArrayList<>();
        Set<String> usedDays     = new HashSet<>();
        // Track per-batch which labs have been placed on which day: "batchIdx|day|labName"
        // Used to prevent same batch getting same lab twice on the same day (overflow guard).
        Set<String> batchLabDayPlaced = new HashSet<>();
        // Track how many rotation slots each day already has — used to spread overflow
        Map<String, Integer> dayRotCount = new HashMap<>();

        result.requested += (long) numLabs * batchCount * durationSlots;

        for (int rotSlot = 0; rotSlot < numLabs; rotSlot++) {
            List<String> candidates = shuffledDays.stream()
                    .filter(d -> !usedDays.contains(safeLower(d)))
                    .collect(Collectors.toList());
            if (candidates.isEmpty()) {
                // All days have been used at least once — spread overflow to least-loaded days
                candidates = new ArrayList<>(shuffledDays);
                candidates.sort(Comparator.comparingInt(d ->
                        dayRotCount.getOrDefault(safeLower(d), 0)));
            }

            boolean placed = false;
            outer:
            for (String day : candidates) {
                String dayKey = safeLower(day);
                for (int wStart : windowStarts) {
                    List<Timeslot> window = orderedSlots.subList(wStart, wStart + durationSlots);

                    // Class must be free in this window
                    boolean blocked = window.stream().anyMatch(ts ->
                            classBusy.contains(key(dayKey, safeSlot(formatSlot(ts)),
                                    safeLower(className), safeLower(divName))));
                    if (blocked) continue;

                    // Overflow guard: prevent the same batch from doing the same lab twice on the same day.
                    // Uses per-batch tracking (batchLabDayPlaced) not division-level subjectDayBusy,
                    // so overflow slots can still use days where OTHER batches do the same subject.
                    boolean batchRepeatOnDay = false;
                    for (int b = 0; b < batchCount; b++) {
                        int chkIdx = (b + rotSlot) % numLabs;
                        String batchLabDayKey = b + "|" + dayKey + "|" + safeLower(labSubjects.get(chkIdx).getName());
                        if (batchLabDayPlaced.contains(batchLabDayKey)) {
                            batchRepeatOnDay = true;
                            break;
                        }
                    }
                    if (batchRepeatOnDay) continue;

                    // Assign faculty: batch B → lab[(B+rotSlot)%numLabs]
                    // Prefer distinct faculty per batch; fall back to reuse when numLabs < batchCount
                    List<String> chosenFac  = new ArrayList<>();
                    List<Subject> chosenLab = new ArrayList<>();
                    Set<String> usedFacHere = new HashSet<>();
                    boolean feasible = true;

                    // Per-batch faculty pre-check: for each batch, verify the required faculty
                    // (batchPinned → facList → blank) is available in this window.
                    // noFacNeeded subjects (Library, Project) always pass — they use blank faculty.
                    // This replaces the old aggregate "count free faculty per subject" check.
                    Set<String> preCheckUsed = new HashSet<>();
                    for (int b = 0; b < batchCount && feasible; b++) {
                        int labIdx = (b + rotSlot) % numLabs;
                        Subject lab = labSubjects.get(labIdx);
                        String labType2 = lab.getType() != null ? lab.getType().toLowerCase() : "";
                        String labCode2 = lab.getCode() != null ? lab.getCode().toUpperCase() : "";
                        boolean labNoFac = labType2.equals("project") || labCode2.startsWith("LIB");
                        if (labNoFac) continue; // no faculty needed — always OK
                        String bl = batchCount > 1 ? batchPrefix + (b + 1) : "";
                        String pinned = batchPinned.get(lab.getId() + "_" + bl);
                        if (pinned != null && !pinned.isBlank()) {
                            // This batch has a specific faculty assigned — must be free
                            String pk = safeLower(pinned);
                            if (preCheckUsed.contains(pk)) { feasible = false; break; }
                            boolean free = window.stream().noneMatch(ts ->
                                    teacherBusy.contains(key(dayKey, safeSlot(formatSlot(ts)), pk)));
                            if (!free) { feasible = false; break; }
                            preCheckUsed.add(pk);
                        } else {
                            // No batch-specific pin — check facList (from subject.faculty)
                            List<String> fl = facPerLab.get(labIdx);
                            if (!fl.isEmpty()) {
                                boolean found = false;
                                for (String f2 : fl) {
                                    if (f2 == null || f2.isBlank()) continue;
                                    String fk2 = safeLower(f2);
                                    if (preCheckUsed.contains(fk2)) continue;
                                    boolean free = window.stream().noneMatch(ts ->
                                            teacherBusy.contains(key(dayKey, safeSlot(formatSlot(ts)), fk2)));
                                    if (free) { preCheckUsed.add(fk2); found = true; break; }
                                }
                                if (!found) { feasible = false; break; }
                            }
                            // facList empty + no pinned → blank faculty, no constraint → OK
                        }
                    }
                    if (!feasible) continue;

                    for (int b = 0; b < batchCount; b++) {
                        int labIdx = (b + rotSlot) % numLabs;
                        Subject lab = labSubjects.get(labIdx);
                        List<String> facList = facPerLab.get(labIdx);
                        String batchLabel = batchCount > 1 ? batchPrefix + (b + 1) : "";
                        String labType2 = lab.getType() != null ? lab.getType().toLowerCase() : "";
                        String labCode2 = lab.getCode() != null ? lab.getCode().toUpperCase() : "";
                        boolean labNoFac = labType2.equals("project") || labCode2.startsWith("LIB");
                        String fac = null;

                        if (labNoFac) {
                            fac = ""; // Library / PS2: blank faculty, no conflict
                        } else {
                            // 1. batchPinned: explicit per-batch assignment from subject_faculty_assignment
                            String pinned = batchPinned.get(lab.getId() + "_" + batchLabel);
                            if (pinned != null && !pinned.isBlank()) {
                                String pk = safeLower(pinned);
                                if (!usedFacHere.contains(pk)) {
                                    boolean free = window.stream().noneMatch(ts ->
                                            teacherBusy.contains(key(dayKey, safeSlot(formatSlot(ts)), pk)));
                                    if (free) { fac = pinned; usedFacHere.add(pk); }
                                }
                                // pinned faculty not assignable → feasible=false (enforced below)
                            } else if (!facList.isEmpty()) {
                                // 2. facList: from subject.faculty (theory-style assignment for labs)
                                for (String f : facList) {
                                    if (f == null || f.isBlank()) continue;
                                    String fk = safeLower(f);
                                    if (usedFacHere.contains(fk)) continue;
                                    boolean free = window.stream().noneMatch(ts ->
                                            teacherBusy.contains(key(dayKey, safeSlot(formatSlot(ts)), fk)));
                                    if (free) { fac = f; usedFacHere.add(fk); break; }
                                }
                            } else {
                                // 3. No assignment at all → blank faculty, place freely
                                fac = "";
                            }
                        }
                        if (fac == null) { feasible = false; break; }
                        chosenFac.add(fac);
                        chosenLab.add(lab);
                    }
                    if (!feasible) continue;

                    // Collect free lab rooms
                    List<Classroom> freeRooms = new ArrayList<>();
                    for (Classroom r : rooms) {
                        if (isRoomFreeForWindow(r, dayKey, window, roomBusy, "Practical")) freeRooms.add(r);
                    }
                    if (freeRooms.isEmpty()) continue;

                    // Commit placements
                    Set<String> countedFac = new HashSet<>();
                    for (int b = 0; b < batchCount; b++) {
                        String batchLabel = batchCount > 1 ? batchPrefix + (b + 1) : "";
                        String fac  = chosenFac.get(b);
                        String facK = safeLower(fac);
                        Subject lab = chosenLab.get(b);
                        Classroom room = freeRooms.get(b % freeRooms.size());

                        for (Timeslot ts : window) {
                            String slotLabel = formatSlot(ts);
                            String slotKey   = safeSlot(slotLabel);
                            Timetable t = new Timetable();
                            t.setDay(day);
                            t.setDepartment(lab.getDepartment());
                            t.setClassName(className);
                            t.setDivision(divName);
                            t.setSubject(lab.getName());
                            t.setFaculty(fac);
                            t.setRoom(room.getRoom());
                            t.setLectureType("Practical");
                            t.setTimeSlot(slotLabel);
                            t.setBatch(batchLabel);
                            t.setSemesterNumber(resolveRowSem(lab, division));
                            t.setStatus("Pending");
                            allRows.add(t);
                            roomBusy.add(key(dayKey, slotKey, safeLower(room.getRoom())));
                            if (!facK.isBlank()) teacherBusy.add(key(dayKey, slotKey, facK));
                            classBusy.add(key(dayKey, slotKey, safeLower(className), safeLower(divName)));
                        }
                        subjectDayBusy.add(key(dayKey, safeLower(className), safeLower(divName), safeLower(lab.getName())));
                        // Record per-batch placement to prevent same batch repeating same lab on same day
                        batchLabDayPlaced.add(b + "|" + dayKey + "|" + safeLower(lab.getName()));
                        // Count faculty load only once per unique faculty per rotation slot
                        if (!facK.isBlank() && countedFac.add(facK)) {
                            facWeek.merge(facK, durationSlots, Integer::sum);
                            facDay.merge(key(facK, dayKey), durationSlots, Integer::sum);
                        }
                    }
                    result.placed += batchCount * durationSlots;
                    usedDays.add(dayKey);
                    dayRotCount.merge(dayKey, 1, Integer::sum);
                    placed = true;
                    break outer;
                }
            }
            if (!placed) {
                result.addMessage("Lab rotation slot " + (rotSlot + 1) + "/" + numLabs
                        + " could not be placed for " + divName);
            }
        }
        return allRows;
    }

    /**
     * Places ALL batches of one lab subject at the SAME day+timeslot simultaneously.
     * Each batch gets a distinct room; faculty is cycled across batches if fewer
     * distinct free faculty are available than batches.
     */
    private List<Timetable> tryPlaceAllBatchesSimultaneously(
            Subject subject, Division division, List<String> days, List<Timeslot> slots,
            List<Classroom> rooms, List<User> allFaculty,
            Set<String> teacherBusy, Set<String> roomBusy, Set<String> classBusy,
            Set<String> subjectDayBusy,
            Map<String, Integer> facWeek, Map<String, Integer> facDay,
            Map<String, User> facultyByName,
            int durationSlots, String sessionType,
            int batchCount, String batchPrefix) {

        String className = deriveClassName(subject, division);
        String divisionName = division != null ? division.getName() : "";
        String subjectKey = safeLower(subject.getName());
        List<String> facultyNames = buildFacultyList(subject, allFaculty);
        List<Timeslot> orderedSlots = getOrderedSlots(slots);
        if (orderedSlots.size() < durationSlots) return null;

        for (String day : days) {
            String dayKey = safeLower(day);
            if (subjectDayBusy.contains(key(dayKey, safeLower(className), safeLower(divisionName), subjectKey))) {
                continue;
            }

            // Build valid consecutive windows in natural (earliest-first) order.
            List<Integer> windowStarts = new ArrayList<>();
            for (int i = 0; i <= orderedSlots.size() - durationSlots; i++) {
                if (areConsecutive(orderedSlots.subList(i, i + durationSlots))) windowStarts.add(i);
            }

            for (int start : windowStarts) {
                List<Timeslot> window = orderedSlots.subList(start, start + durationSlots);

                // Whole class must not already be in a lecture during this window
                boolean classBlocked = false;
                for (Timeslot slot : window) {
                    if (classBusy.contains(key(dayKey, safeSlot(formatSlot(slot)),
                            safeLower(className), safeLower(divisionName)))) {
                        classBlocked = true;
                        break;
                    }
                }
                if (classBlocked) continue;

                // Collect rooms that are free for the entire window (prefer lab type)
                List<Classroom> freeRooms = new ArrayList<>();
                for (Classroom room : rooms) {
                    if (isRoomFreeForWindow(room, dayKey, window, roomBusy, sessionType)) {
                        freeRooms.add(room);
                    }
                }
                // If not enough typed rooms, also accept any free room
                if (freeRooms.size() < batchCount) {
                    for (Classroom room : rooms) {
                        if (freeRooms.contains(room)) continue;
                        boolean free = true;
                        for (Timeslot slot : window) {
                            if (roomBusy.contains(key(dayKey, safeSlot(formatSlot(slot)), safeLower(room.getRoom())))) {
                                free = false;
                                break;
                            }
                        }
                        if (free) freeRooms.add(room);
                    }
                }
                if (freeRooms.isEmpty()) continue;

                // Collect faculty that are free for the entire window, sorted by load
                List<String> freeFac = facultyNames.stream()
                        .filter(f -> f != null && !f.isBlank())
                        .filter(f -> {
                            String fk = safeLower(f);
                            for (Timeslot slot : window) {
                                if (teacherBusy.contains(key(dayKey, safeSlot(formatSlot(slot)), fk))) return false;
                            }
                            return true;
                        })
                        .sorted(Comparator
                                .comparingInt((String f) -> isSpecialist(f, facultyByName, subjectKey) ? 0 : 1)
                                .thenComparingInt(f -> facDay.getOrDefault(key(safeLower(f), dayKey), 0))
                                .thenComparingInt(f -> facWeek.getOrDefault(safeLower(f), 0)))
                        .collect(Collectors.toList());
                if (freeFac.isEmpty()) continue;

                // Build batch rows — cycle rooms and faculty if fewer available than batchCount
                List<Timetable> rows = new ArrayList<>();
                Set<String> countedFac = new HashSet<>();

                for (int b = 0; b < batchCount; b++) {
                    String batchLabel = batchCount > 1 ? batchPrefix + (b + 1) : "";
                    Classroom room = freeRooms.get(b % freeRooms.size());
                    String facName = freeFac.get(b % freeFac.size());
                    String facKey = safeLower(facName);

                    for (Timeslot slot : window) {
                        String slotLabel = formatSlot(slot);
                        String slotKey = safeSlot(slotLabel);

                        Timetable t = new Timetable();
                        t.setDay(day);
                        t.setDepartment(subject.getDepartment());
                        t.setClassName(className);
                        t.setDivision(divisionName);
                        t.setSubject(subject.getName());
                        t.setFaculty(facName);
                        t.setRoom(room.getRoom());
                        t.setLectureType(sessionType != null && !sessionType.isBlank() ? sessionType
                                : (subject.getType() != null ? subject.getType() : "Practical"));
                        t.setTimeSlot(slotLabel);
                        t.setBatch(batchLabel);
                        t.setSemesterNumber(resolveRowSem(subject, division));
                        t.setStatus("Pending");
                        rows.add(t);

                        roomBusy.add(key(dayKey, slotKey, safeLower(room.getRoom())));
                        teacherBusy.add(key(dayKey, slotKey, facKey));
                        classBusy.add(key(dayKey, slotKey, safeLower(className), safeLower(divisionName)));
                    }
                    // Count faculty load only once per unique faculty
                    if (countedFac.add(facKey)) {
                        facWeek.merge(facKey, durationSlots, Integer::sum);
                        facDay.merge(key(facKey, dayKey), durationSlots, Integer::sum);
                    }
                }

                subjectDayBusy.add(key(dayKey, safeLower(className), safeLower(divisionName), subjectKey));
                return rows;
            }
        }
        return null;
    }

    private Classroom pickRoom(List<Classroom> rooms,
                               String preferredRoom,
                               Set<String> roomBusy,
                               String dayKey,
                               String slotKey,
                               String subjectType) {
        // Pass 1: preferred room with type match
        if (preferredRoom != null && !preferredRoom.isBlank()) {
            for (Classroom r : rooms) {
                if (preferredRoom.equalsIgnoreCase(r.getRoom())
                        && isRoomFree(r, dayKey, slotKey, roomBusy, subjectType)) {
                    return r;
                }
            }
        }
        // Pass 2: any free room with correct type (lab→lab, theory→lecture)
        for (Classroom r : rooms) {
            if (isRoomFree(r, dayKey, slotKey, roomBusy, subjectType)) {
                return r;
            }
        }
        // No fallback to wrong room type — theory sessions must use lecture rooms only,
        // lab sessions must use lab rooms only. If no matching room is free, return null
        // so the session is reported as unplaced rather than assigned to the wrong room.
        return null;
    }

    private Classroom pickRoomForBlock(List<Classroom> rooms,
                                       String preferredRoom,
                                       Set<String> roomBusy,
                                       String dayKey,
                                       List<Timeslot> window,
                                       String subjectType) {
        // Pass 1: preferred room with type match
        if (preferredRoom != null && !preferredRoom.isBlank()) {
            for (Classroom r : rooms) {
                if (preferredRoom.equalsIgnoreCase(r.getRoom())
                        && isRoomFreeForWindow(r, dayKey, window, roomBusy, subjectType)) {
                    return r;
                }
            }
        }
        // Pass 2: any type-matched free room
        for (Classroom r : rooms) {
            if (isRoomFreeForWindow(r, dayKey, window, roomBusy, subjectType)) {
                return r;
            }
        }
        // Pass 3: fallback — any room free for the whole window
        for (Classroom r : rooms) {
            boolean free = true;
            for (Timeslot slot : window) {
                if (roomBusy.contains(key(dayKey, safeSlot(formatSlot(slot)), safeLower(r.getRoom())))) {
                    free = false; break;
                }
            }
            if (free) return r;
        }
        return null;
    }

    private boolean isRoomFree(Classroom room,
                               String dayKey,
                               String slotKey,
                               Set<String> roomBusy,
                               String subjectType) {
        if (roomBusy.contains(key(dayKey, slotKey, safeLower(room.getRoom())))) return false;
        return roomTypeMatches(room, subjectType);
    }

    /** Returns true when the room type is appropriate for the session type.
     *  Labs → lab rooms only.  Theory/Audit/Seminar → lecture rooms only.
     *  If the room has no type set, allow anything (backward-compat). */
    private boolean roomTypeMatches(Classroom room, String subjectType) {
        if (subjectType == null || room.getType() == null || room.getType().isBlank()) return true;
        boolean isLab = subjectType.toLowerCase().matches(".*(lab|practical).*");
        boolean roomIsLab = room.getType().toLowerCase().contains("lab");
        return isLab == roomIsLab; // lab session ↔ lab room; theory session ↔ lecture room
    }

    private boolean isRoomFreeForWindow(Classroom room,
                                        String dayKey,
                                        List<Timeslot> window,
                                        Set<String> roomBusy,
                                        String subjectType) {
        for (Timeslot slot : window) {
            String slotKey = safeSlot(formatSlot(slot));
            if (!isRoomFree(room, dayKey, slotKey, roomBusy, subjectType)) {
                return false;
            }
        }
        return true;
    }

    private List<String> buildFacultyList(Subject subject, List<User> faculty) {
        // 1. Explicitly assigned via UI (subject.faculty field) — highest priority.
        if (subject.getFaculty() != null && !subject.getFaculty().isBlank()) {
            return List.of(subject.getFaculty().trim());
        }

        // 2. subjects_handled match (legacy path, for backwards compatibility).
        String targetSubject = safeLower(subject.getName());
        Map<String, Long> loadMap = timetableRepo.findByDeletedFalse().stream()
                .filter(t -> t.getFaculty() != null)
                .collect(Collectors.groupingBy(t -> t.getFaculty().toLowerCase().trim(), Collectors.counting()));
        Comparator<User> byLoad = Comparator.comparingLong(u ->
                loadMap.getOrDefault(u.getName().toLowerCase().trim(), 0L));
        List<User> specialists = faculty.stream()
                .filter(u -> u.getName() != null && !u.getName().isBlank())
                .filter(u -> handlesSubject(u, targetSubject))
                .sorted(byLoad).toList();
        if (!specialists.isEmpty()) {
            return specialists.stream().map(User::getName).collect(Collectors.toList());
        }

        // 3. No explicit assignment — return empty list.
        //    Theory sessions placed with blank faculty; lab rotation uses batchPinned directly.
        //    NEVER fall through to department-wide random assignment.
        return new ArrayList<>();
    }

    private List<Division> findMatchingDivisions(List<Division> divisions, Subject subject) {
        String semLabel = deriveClassName(subject, null);
        String mappedYear = mapSemToYear(subject.getSemester());
        int targetSem = subject.getSemester() != null ? subject.getSemester() : -1;
        String dept = subject.getDepartment();
        String normDept = normalizeDept(dept);

        return divisions.stream()
                .filter(d -> {
                    boolean matchesYear = false;
                    String year = d.getYear();
                    Integer storedSem = d.getSemesterNumber();
                    int divSem = storedSem != null ? storedSem : parseSemesterFromLabel(year);
                    boolean inferredSem = storedSem == null;

                    if (targetSem > 0 && divSem > 0) {
                        matchesYear = divSem == targetSem;
                        if (!matchesYear && inferredSem) {
                            // allow broad mapping for legacy labels (e.g., TE covers sem 5/6)
                            matchesYear = (divSem == 1 && targetSem <= 2) ||
                                    (divSem == 3 && targetSem >= 3 && targetSem <= 4) ||
                                    (divSem == 5 && targetSem >= 5 && targetSem <= 6) ||
                                    (divSem == 7 && targetSem >= 7);
                        }
                    } else if (year != null) {
                        matchesYear = year.equalsIgnoreCase(semLabel) ||
                                year.equalsIgnoreCase(mappedYear) ||
                                year.replaceAll("\\s+", "").equalsIgnoreCase(semLabel.replaceAll("\\s+", ""));
                    }

                    String divDept = normalizeDept(d.getDepartment());
                    boolean matchesDept = normDept.isEmpty() || (!divDept.isEmpty() && divDept.equals(normDept));
                    return matchesYear && matchesDept;
                })
                .collect(Collectors.toList());
    }

    private String deriveClassName(Subject subject, Division division) {
        if (division != null) {
            String year = division.getYear() != null ? division.getYear().trim() : "";
            String dept = division.getDepartment() != null ? division.getDepartment().trim() : "";

            // Build short dept code e.g. "Information Technology" → "IT"
            String deptCode = shortDeptCode(dept.isEmpty() ? subject.getDepartment() : dept);

            // Map year label to short form e.g. "Second Year" or "SE" → "SE"
            String shortYear = toShortYear(year, subject.getSemester());

            if (!shortYear.isEmpty() && !deptCode.isEmpty()) {
                return shortYear + "(" + deptCode + ")"; // e.g. SE(IT)
            }
            if (!shortYear.isEmpty()) {
                return shortYear;
            }
            if (!year.isEmpty()) {
                return year; // fallback to whatever was stored
            }
        }
        if (subject.getSemester() != null) {
            String shortYear = mapSemToYear(subject.getSemester());
            String deptCode = shortDeptCode(subject.getDepartment());
            if (!deptCode.isEmpty()) return shortYear + "(" + deptCode + ")";
            return shortYear;
        }
        if (subject.getDepartment() != null && !subject.getDepartment().isBlank()) {
            return subject.getDepartment();
        }
        return "Class";
    }

    /** Converts full department name to short code e.g. "Information Technology" → "IT" */
    private String shortDeptCode(String dept) {
        if (dept == null || dept.isBlank()) return "";
        // Check known mappings first
        String d = dept.trim().toLowerCase();
        if (d.contains("information technology") || d.equals("it")) return "IT";
        if (d.contains("computer") || d.equals("cs") || d.equals("cse")) return "CS";
        if (d.contains("mechanical") || d.equals("me")) return "ME";
        if (d.contains("civil") || d.equals("ce")) return "CE";
        if (d.contains("electrical") || d.equals("ee")) return "EE";
        if (d.contains("electronics") || d.equals("entc") || d.equals("ece")) return "ENTC";
        // Generic fallback: take initials of each word
        String[] words = dept.trim().split("\\s+");
        if (words.length == 1) return dept.trim().toUpperCase().substring(0, Math.min(3, dept.trim().length()));
        StringBuilder code = new StringBuilder();
        for (String w : words) {
            if (!w.isBlank()) code.append(Character.toUpperCase(w.charAt(0)));
        }
        return code.toString();
    }

    /** Converts year label or semester number to short form e.g. "Second Year" → "SE", sem 4 → "SE" */
    private String toShortYear(String yearLabel, Integer semester) {
        if (yearLabel != null && !yearLabel.isBlank()) {
            String y = yearLabel.trim().toLowerCase().replaceAll("\\s+", "");
            if (y.equals("fe") || y.contains("first"))  return "FE";
            if (y.equals("se") || y.contains("second")) return "SE";
            if (y.equals("te") || y.contains("third"))  return "TE";
            if (y.equals("be") || y.contains("final") || y.contains("fourth")) return "BE";
            // already short — return as-is uppercased
            if (yearLabel.trim().length() <= 4) return yearLabel.trim().toUpperCase();
        }
        // fall back to semester number
        return mapSemToYear(semester);
    }

    /**
     * Returns the preferred lab window start in minutes-from-midnight for a division.
     * SE (AFTERNOON) → 14:00 = 840 min
     * TE (MIDDAY)    → 11:15 = 675 min
     * BE (MORNING)   → 09:00 = 540 min
     */
    /** True for Audit/Seminar/Special subjects that are whole-class single blocks, not batch rotations. */
    private boolean isWholeClassActivity(Subject s) {
        if (s == null || s.getType() == null) return false;
        String t = s.getType().toLowerCase();
        return t.equals("audit") || t.equals("seminar") || t.equals("special");
    }

    private String mapSemToYear(Integer sem) {
        if (sem == null) return "";
        int s = sem;
        if (s <= 2) return "FE";
        if (s <= 4) return "SE";
        if (s <= 6) return "TE";
        return "BE";
    }

    private List<String> getWorkingDays() {
        AcademicSetting setting = academicSettingRepo.findById(1).orElse(null);
        if (setting == null || setting.getWorkingDays() == null || setting.getWorkingDays().isBlank()) {
            return Arrays.asList("Monday", "Tuesday", "Wednesday", "Thursday", "Friday");
        }
        return Arrays.stream(setting.getWorkingDays().split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
    }

    private List<Timeslot> getEffectiveSlots() {
        List<Timeslot> slots = timeRepo.findAll();
        if (slots != null && slots.size() >= 1) {
            List<Timeslot> usable = slots.stream()
                    .filter(ts -> !ts.isBreak())
                    .toList();
            if(!usable.isEmpty()){
                return usable;
            }
            // if every configured slot is marked as break, still allow them all
            return slots;
        }

        String[][] defaults = {
                {"09:00", "10:00"},
                {"10:00", "11:00"},
                {"11:00", "12:00"},
                {"12:00", "13:00"},
                {"13:00", "14:00"},
                {"14:00", "15:00"}
        };
        List<Timeslot> generated = new ArrayList<>();
        for (String[] pair : defaults) {
            Timeslot t = new Timeslot();
            t.setStartTime(pair[0]);
            t.setEndTime(pair[1]);
            generated.add(t);
        }
        return generated;
    }

    private String formatSlot(Timeslot t) {
        return t.getStartTime() + " - " + t.getEndTime();
    }

    private String labelForDivision(Division d) {
        if (d == null) return "";
        String classLabel = d.getYear() != null ? d.getYear() : "";
        String divLabel = d.getName() != null ? ("-" + d.getName()) : "";
        return " [" + classLabel + divLabel + "]";
    }

    private String key(String... parts){
        String result = "";
        for(int i=0; i<parts.length; i++){
            result += safeLower(parts[i]);
            if(i != parts.length-1){
                result += "|";
            }
        }
        return result;
    }
    
    private String safeLower(String val) {
        return val == null ? "" : val.toLowerCase();
    }

    private String safeSlot(String slot) {
        return slot == null ? "" : slot.toLowerCase();
    }

    private String safeVal(Object v){
        return v == null ? "-" : v.toString();
    }

    private int resolveLectureHours(Subject subject){
        if(subject.getLectureHoursPerWeek() != null && subject.getLectureHoursPerWeek() > 0){
            return subject.getLectureHoursPerWeek();
        }
        // Explicitly set to 0 — respect the admin's intent, no fallback
        if(subject.getLectureHoursPerWeek() != null && subject.getLectureHoursPerWeek() == 0){
            return 0;
        }
        // Lab-only subjects: no lecture sessions
        if(subject.getPracticalHoursPerWeek() != null && subject.getPracticalHoursPerWeek() > 0){
            return 0;
        }
        Integer legacy = subject.getHours();
        if(legacy != null && legacy > 0){
            return isLab(subject) ? 0 : legacy;
        }
        // fallback to 1 session for truly unspecified legacy records (hours field also null)
        return 1;
    }

    private int resolvePracticalHours(Subject subject){
        if(subject.getPracticalHoursPerWeek() != null && subject.getPracticalHoursPerWeek() > 0){
            return subject.getPracticalHoursPerWeek();
        }
        if(isLab(subject)){
            Integer legacy = subject.getHours();
            if(legacy != null && legacy > 0){
                return legacy;
            }
            return 1; // keep at least 1 slot but minimal to fit
        }
        return 0;
    }

    private boolean isLab(Subject subject){
        if(subject.getPracticalHoursPerWeek() != null && subject.getPracticalHoursPerWeek() > 0){
            return true;
        }
        String type = subject.getType();
        if(type == null) return false;
        String t = type.toLowerCase();
        return t.contains("lab") || t.contains("practical");
    }

    private int practicalDuration(Subject subject){
        Integer configured = subject.getPracticalSlotDuration();
        if(configured != null && configured > 0){
            return configured;
        }
        Integer hrs = subject.getHours();
        if(hrs != null && hrs > 1) return hrs;
        return 1; // fallback to single-slot block so it always fits
    }

    private List<Timeslot> getOrderedSlots(List<Timeslot> slots){
        if(slots == null) return Collections.emptyList();
        return slots.stream()
                .sorted(Comparator.comparingInt(this::slotStartMinutes))
                .toList();
    }

    private int slotStartMinutes(Timeslot slot){
        return parseMinutes(slot != null ? slot.getStartTime() : null);
    }

    /**
     * Returns a reordered copy of {@code days} that prefers days not adjacent
     * (in the working-days calendar) to days already used for this subject.
     * This prevents theory sessions stacking on 3+ consecutive calendar days.
     * Days already blocked by subjectDayBusy are kept in the list — tryPlace
     * will skip them as usual; we are only changing their relative priority.
     */
    private List<String> spreadTheoryDays(List<String> days, String subjectName,
            String className, String divisionName,
            Set<String> subjectDayBusy, List<String> workingDays) {
        // Collect which working-day indices are already used for this subject
        Set<Integer> usedIndices = new HashSet<>();
        for (int i = 0; i < workingDays.size(); i++) {
            String dk = safeLower(workingDays.get(i));
            if (subjectDayBusy.contains(key(dk, safeLower(className), safeLower(divisionName), safeLower(subjectName)))) {
                usedIndices.add(i);
            }
        }
        if (usedIndices.isEmpty()) return days; // nothing to spread yet

        // For each candidate day, compute its minimum distance from any used day.
        // Higher distance → preferred (sort DESC by min distance).
        List<String> sorted = new ArrayList<>(days);
        Map<String, Integer> minDist = new HashMap<>();
        for (String d : days) {
            int idx = -1;
            for (int i = 0; i < workingDays.size(); i++) {
                if (workingDays.get(i).equalsIgnoreCase(d)) { idx = i; break; }
            }
            int dist = Integer.MAX_VALUE;
            for (int ui : usedIndices) {
                int gap = Math.abs((idx >= 0 ? idx : 0) - ui);
                if (gap < dist) dist = gap;
            }
            minDist.put(safeLower(d), dist);
        }

        // Count how many theory subjects are already placed on each day for this division.
        // Used as tie-breaker: days with lighter load are preferred so sessions fill
        // earlier weekdays (Tue, Wed, Thu) before piling onto Friday.
        final String classKey = "|" + safeLower(className) + "|" + safeLower(divisionName) + "|";
        Map<String, Long> dayLoad = subjectDayBusy.stream()
                .filter(k -> k.contains(classKey))
                .collect(Collectors.groupingBy(k -> k.split("\\|")[0], Collectors.counting()));

        // Primary: max distance from already-used days.
        // Secondary tie-break: prefer less-loaded days (fills Thu before Fri, etc.).
        sorted.sort((a, b) -> {
            int da = minDist.getOrDefault(safeLower(a), 0);
            int db = minDist.getOrDefault(safeLower(b), 0);
            if (db != da) return Integer.compare(db, da);
            long la = dayLoad.getOrDefault(safeLower(a), 0L);
            long lb = dayLoad.getOrDefault(safeLower(b), 0L);
            return Long.compare(la, lb);   // lower load = earlier in list
        });
        return sorted;
    }

    private int parseMinutes(String time){
        if(time == null) return Integer.MAX_VALUE;
        try{
            LocalTime t = LocalTime.parse(time);
            return t.getHour() * 60 + t.getMinute();
        }catch(DateTimeParseException ex){
            return Integer.MAX_VALUE;
        }
    }

    private String normalizeDept(String dept) {
        if (dept == null) return "";
        String d = dept.toLowerCase().trim();
        d = d.replace("department", "").replace("dept", "").trim();
        d = d.replaceAll("\\s+", " ");
        return d;
    }

    private boolean handlesSubject(User user, String targetSubject){
        if(user == null) return false;
        if(targetSubject == null || targetSubject.isBlank()) return false;
        String handled = user.getSubjectsHandled();
        if(handled == null || handled.isBlank()) return false;
        String subjectKey = targetSubject.toLowerCase().trim();
        return Arrays.stream(handled.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(String::toLowerCase)
                // s.startsWith(subjectKey + " "): lab name starts with theory name → lab faculty teaches theory
                // subjectKey.startsWith(s + " "): theory name starts with handled subject → e.g. "CG Lab" for "CG" handler
                // Avoids "Lab Practice V" ↔ "Lab Practice VI" false positive (no trailing space boundary)
                .anyMatch(s -> s.equals(subjectKey)
                        || s.startsWith(subjectKey + " ")
                        || subjectKey.startsWith(s + " "));
    }

    private boolean isSpecialist(String facultyName, Map<String,User> facultyByName, String targetSubjectLower){
        if(facultyName == null || facultyName.isBlank()) return false;
        User u = facultyByName.get(safeLower(facultyName));
        return handlesSubject(u, targetSubjectLower);
    }

    private int parseSemesterFromLabel(String label){
        if (label == null || label.isBlank()) return -1;
        String cleaned = label.toUpperCase().replaceAll("[^A-Z0-9]", "");
        // check explicit SEM number
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(SEM)?([0-9]+)").matcher(cleaned);
        if (m.find()) {
            try { return Integer.parseInt(m.group(2)); } catch (NumberFormatException ignored) {}
        }
        return switch (cleaned) {
            case "FE" -> 1; // treated as first-year start
            case "SE" -> 3;
            case "TE" -> 5;
            case "BE" -> 7;
            default -> -1;
        };
    }

    public static class GenerationResult {
        private long existingCount;
        private int requested;
        private int placed;
        private List<String> messages = new ArrayList<>();
        private List<Timetable> generated = new ArrayList<>();

        public long getExistingCount() {
            return existingCount;
        }

        public int getRequested() {
            return requested;
        }

        public int getPlaced() {
            return placed;
        }

        public List<String> getAllMessages() {
            return messages;
        }

        public List<Timetable> getGenerated() {
            return generated;
        }

        private void addMessage(String msg) {
            this.messages.add(msg);
        }
    }
}
