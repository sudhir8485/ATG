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
import com.nt.repository.AcademicSettingRepository;
import com.nt.repository.ClassroomRepository;
import com.nt.repository.DivisionRepository;
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


    public GenerationResult generate() {
        GenerationResult result = new GenerationResult();
        result.existingCount = timetableRepo.findByDeletedFalse().size();

        // Sort by id for deterministic processing order — PostgreSQL heap order can change
        // after UPDATEs, causing subjects to interleave unpredictably across semesters
        List<Subject> subjects = subjectRepo.findByDeletedFalse().stream()
                .sorted(Comparator.comparingInt(Subject::getId))
                .collect(Collectors.toList());
        List<Timeslot> slots = getEffectiveSlots();

        List<String> workingDays = getWorkingDays();
        List<Classroom> rooms = new ArrayList<>(roomRepo.findAll());
        List<Division> divisions = divisionRepo.findAll();
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
        if (subjects.isEmpty()) {
            result.addMessage("No subjects found to schedule.");
            return result;
        }

        // Build clash sets from existing timetable
        Set<String> teacherBusy = new HashSet<>();
        Set<String> roomBusy = new HashSet<>();
        Set<String> classBusy = new HashSet<>();
        Set<String> subjectDayBusy = new HashSet<>();
        
        Map<String, Integer> facultyWeekCount = new HashMap<>(); // name -> total per week
        Map<String, Integer> facultyDayCount = new HashMap<>();  // key(name,day) -> per day
        Map<String, User> facultyByName = new HashMap<>();

        for(User u : allFaculty){

            if(u.getName() != null && !u.getName().isBlank()){

                String key = safeLower(u.getName());

                if(!facultyByName.containsKey(key)){
                    facultyByName.put(key, u);
                }
            }
        }

        for (Timetable t : timetableRepo.findByDeletedFalse()) {
            String slot = safeSlot(t.getTimeSlot());
            String day = safeLower(t.getDay());
            teacherBusy.add(key(day, slot, safeLower(t.getFaculty())));
            roomBusy.add(key(day, slot, safeLower(t.getRoom())));
            classBusy.add(key(day, slot, safeLower(t.getClassName()), safeLower(t.getDivision())));
            subjectDayBusy.add(key(day, safeLower(t.getClassName()), safeLower(t.getDivision()), safeLower(t.getSubject())));
            if(t.getFaculty()!=null){
                String facKey = safeLower(t.getFaculty());
                facultyWeekCount.put(facKey, facultyWeekCount.getOrDefault(facKey,0)+1);
                facultyDayCount.put(key(facKey, day), facultyDayCount.getOrDefault(key(facKey, day),0)+1);
            }
        }

        List<Timetable> generated = new ArrayList<>();

        // ── LAB ROTATION PHASE ──
        // Build rotation list with consecutive duplicates so multi-block subjects
        // appear adjacent in the list. This ensures paired batches (e.g. B1+B2 on LP5)
        // match the reference pattern: [LP5,LP5,LP6,PS2] not [LP5,LP6,PS2,LP5].
        // Each lab subject is repeated blocks times in sequence.
        Map<Integer, List<Subject>> labsByDivId = new LinkedHashMap<>();
        for (Subject subj : subjects) {
            if (isWholeClassActivity(subj)) continue; // Audit/Seminar placed separately
            int _phpw = resolvePracticalHours(subj);
            int _pd   = _phpw > 0 ? practicalDuration(subj) : 0;
            if (_phpw > 0 && _pd > 0) {
                int _blocks = Math.max(1, (int) Math.ceil(_phpw / (double) _pd));
                for (Division div : findMatchingDivisions(divisions, subj)) {
                    List<Subject> divList = labsByDivId.computeIfAbsent(div.getId(), k -> new ArrayList<>());
                    for (int _e = 0; _e < _blocks; _e++) {
                        divList.add(subj);
                    }
                }
            }
        }
        // Track which subject+division pairs are already placed by the rotation
        Set<String> rotationPlaced = new HashSet<>();
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

        // Sort theory subjects by lecture hours DESC so high-frequency subjects (e.g. ISM=4, EL5=6)
        // are placed first and can spread across multiple days before lower-frequency ones consume slots.
        List<Subject> theoryOrderedSubjects = subjects.stream()
                .sorted(Comparator.comparingInt((Subject s) -> resolveLectureHours(s)).reversed())
                .collect(Collectors.toList());

        for (Subject subject : theoryOrderedSubjects) {
            // Use natural Mon→Fri order. subjectDayBusy prevents same subject on same day,
            // so theory fills each day from the top and any spare capacity moves to later days.
            List<String> shuffledDays = new ArrayList<>(workingDays);

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

                // Use natural slot order (earliest-first); labs are already placed so
                // classBusy prevents double-booking — no time-window bias needed.
                List<Timeslot> lectureSlots = slots.stream()
                        .filter(ts -> !ts.isBreak())
                        .collect(Collectors.toList());
                for (int i = 0; i < lectureSessions; i++) {
                    result.requested++;
                    Timetable placed = tryPlace(subject, division, shuffledDays, lectureSlots, rooms, allFaculty,
                            teacherBusy, roomBusy, classBusy, subjectDayBusy,
                            facultyWeekCount, facultyDayCount, facultyByName, "Lecture");
                    if (placed == null) {
                        placed = tryPlaceRelaxed(subject, division, shuffledDays, slots, rooms, allFaculty,
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

        if (!generated.isEmpty()) {
            timetableRepo.saveAll(generated);
        }
        result.generated = generated;
        return result;
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
                               String sessionType) {

        String className = deriveClassName(subject, division);
        String divisionName = division != null ? division.getName() : "";
        String preferredRoom = division != null ? division.getClassroom() : null;

        List<String> facultyNames = buildFacultyList(subject, faculty);

        String subjectKey = safeLower(subject.getName());

        for (String day : days) {
            String dayKey = safeLower(day);
            // Skip days where this subject is already placed
            if (subjectDayBusy.contains(key(dayKey, safeLower(className), safeLower(divisionName), subjectKey))) {
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

        for (String day : sortedDays) {
            String dayKey = safeLower(day);
            for (Timeslot slot : slots) {
                String slotLabel = formatSlot(slot);
                String slotKey = safeSlot(slotLabel);
                if (classBusy.contains(key(dayKey, slotKey, safeLower(className), safeLower(divisionName)))) continue;

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
                    t.setStatus("Pending");

                    teacherBusy.add(key(dayKey, slotKey, facKey));
                    roomBusy.add(key(dayKey, slotKey, safeLower(room.getRoom())));
                    classBusy.add(key(dayKey, slotKey, safeLower(className), safeLower(divisionName)));
                    subjectDayBusy.add(key(dayKey, safeLower(className), safeLower(divisionName), subjectKey));
                    return t;
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

        // Pre-build faculty candidate list per lab
        List<List<String>> facPerLab = new ArrayList<>();
        for (Subject lab : labSubjects) facPerLab.add(buildFacultyList(lab, allFaculty));

        List<Timeslot> orderedSlots = getOrderedSlots(slots);
        if (orderedSlots.size() < durationSlots) return Collections.emptyList();

        // Use natural (earliest-first) window order — no morning/midday/afternoon bias.
        // With 8 lab rooms, different divisions naturally land in non-overlapping rooms
        // even in the same time window, so no artificial time-preference is needed.
        List<Integer> windowStarts = new ArrayList<>();
        for (int i = 0; i <= orderedSlots.size() - durationSlots; i++) {
            if (areConsecutive(orderedSlots.subList(i, i + durationSlots))) windowStarts.add(i);
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

                    // Pre-check: count free distinct faculty per subject in this window.
                    // If a subject is assigned to N batches, it needs N distinct free faculty.
                    // This prevents > N batches sharing a subject when only N faculty exist.
                    Map<Integer, Long> freeFacPerSubject = new HashMap<>();
                    for (int b = 0; b < batchCount; b++) {
                        int labIdx = (b + rotSlot) % numLabs;
                        Subject lab = labSubjects.get(labIdx);
                        final List<String> facListForSubject = facPerLab.get(labIdx);
                        freeFacPerSubject.computeIfAbsent(lab.getId(), id -> {
                            long cnt = 0;
                            for (String f2 : facListForSubject) {
                                if (f2 == null || f2.isBlank()) continue;
                                String fk2 = safeLower(f2);
                                boolean isFree = window.stream().noneMatch(ts ->
                                        teacherBusy.contains(key(dayKey, safeSlot(formatSlot(ts)), fk2)));
                                if (isFree) cnt++;
                            }
                            return cnt;
                        });
                    }
                    // Count how many batches each subject is assigned in this rotation slot
                    Map<Integer, Integer> batchesPerSubject = new HashMap<>();
                    for (int b = 0; b < batchCount; b++) {
                        int labIdx = (b + rotSlot) % numLabs;
                        batchesPerSubject.merge(labSubjects.get(labIdx).getId(), 1, Integer::sum);
                    }
                    // Reject this window if any subject needs more faculty than are available
                    for (Map.Entry<Integer, Integer> e : batchesPerSubject.entrySet()) {
                        long available = freeFacPerSubject.getOrDefault(e.getKey(), 0L);
                        if (e.getValue() > available) { feasible = false; break; }
                    }
                    if (!feasible) continue;

                    for (int b = 0; b < batchCount; b++) {
                        int labIdx = (b + rotSlot) % numLabs;
                        Subject lab = labSubjects.get(labIdx);
                        String fac = null;
                        for (String f : facPerLab.get(labIdx)) {
                            if (f == null || f.isBlank()) continue;
                            String fk = safeLower(f);
                            if (usedFacHere.contains(fk)) continue;
                            boolean free = window.stream().noneMatch(ts ->
                                    teacherBusy.contains(key(dayKey, safeSlot(formatSlot(ts)), fk)));
                            if (free) { fac = f; usedFacHere.add(fk); break; }
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
                            t.setStatus("Pending");
                            allRows.add(t);
                            roomBusy.add(key(dayKey, slotKey, safeLower(room.getRoom())));
                            teacherBusy.add(key(dayKey, slotKey, facK));
                            classBusy.add(key(dayKey, slotKey, safeLower(className), safeLower(divName)));
                        }
                        subjectDayBusy.add(key(dayKey, safeLower(className), safeLower(divName), safeLower(lab.getName())));
                        // Record per-batch placement to prevent same batch repeating same lab on same day
                        batchLabDayPlaced.add(b + "|" + dayKey + "|" + safeLower(lab.getName()));
                        // Count faculty load only once per unique faculty per rotation slot
                        if (countedFac.add(facK)) {
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
        boolean needsLab = subjectType != null && subjectType.toLowerCase().matches(".*(lab|practical).*");
        boolean anyLabCapable = rooms.stream().anyMatch(r -> r.getType() != null && r.getType().toLowerCase().contains("lab"));
        // try preferred classroom first
        if (preferredRoom != null && !preferredRoom.isBlank()) {
            for (Classroom r : rooms) {
                if (preferredRoom.equalsIgnoreCase(r.getRoom()) && isRoomFree(r, dayKey, slotKey, roomBusy, subjectType)) {
                    return r;
                }
            }
        }
        // then find any free room that fits type
        for (Classroom r : rooms) {
            if (isRoomFree(r, dayKey, slotKey, roomBusy, subjectType)) {
                return r;
            }
        }
        // last-resort: if need lab but no lab room available, allow any free room
        if(needsLab && !anyLabCapable){
            for (Classroom r : rooms) {
                String key = key(dayKey, slotKey, safeLower(r.getRoom()));
                if (!roomBusy.contains(key)) {
                    return r;
                }
            }
        }
        return null;
    }

    private Classroom pickRoomForBlock(List<Classroom> rooms,
                                       String preferredRoom,
                                       Set<String> roomBusy,
                                       String dayKey,
                                       List<Timeslot> window,
                                       String subjectType) {
        if (preferredRoom != null && !preferredRoom.isBlank()) {
            for (Classroom r : rooms) {
                if (preferredRoom.equalsIgnoreCase(r.getRoom()) && isRoomFreeForWindow(r, dayKey, window, roomBusy, subjectType)) {
                    return r;
                }
            }
        }
        for (Classroom r : rooms) {
            if (isRoomFreeForWindow(r, dayKey, window, roomBusy, subjectType)) {
                return r;
            }
        }
        boolean needsLab = subjectType != null && subjectType.toLowerCase().matches(".*(lab|practical).*");
        boolean anyLabCapable = rooms.stream().anyMatch(r -> r.getType() != null && r.getType().toLowerCase().contains("lab"));
        if(needsLab && !anyLabCapable){
            for (Classroom r : rooms) {
                boolean free = true;
                for (Timeslot slot : window) {
                    String slotKey = safeSlot(formatSlot(slot));
                    if(roomBusy.contains(key(dayKey, slotKey, safeLower(r.getRoom())))){
                        free = false; break;
                    }
                }
                if(free) return r;
            }
        }
        return null;
    }

    private boolean isRoomFree(Classroom room,
                               String dayKey,
                               String slotKey,
                               Set<String> roomBusy,
                               String subjectType) {
        if (roomBusy.contains(key(dayKey, slotKey, safeLower(room.getRoom())))) {
            return false;
        }
        if (subjectType != null) {
            String t = subjectType.toLowerCase();
            if (t.contains("lab") || t.contains("practical")) {
                return room.getType() != null && room.getType().toLowerCase().contains("lab");
            }
        }
        return true;
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
        Set<String> ordered = new LinkedHashSet<>();
        String department = subject.getDepartment();
        String normTargetDept = normalizeDept(department);
        String targetSubject = safeLower(subject.getName());

        // current load per faculty (existing timetable rows)
        Map<String, Long> loadMap = timetableRepo.findByDeletedFalse().stream()
                .filter(t -> t.getFaculty() != null)
                .collect(Collectors.groupingBy(t -> t.getFaculty().toLowerCase().trim(), Collectors.counting()));

        Comparator<User> byLoad = Comparator.comparingLong(u ->
                loadMap.getOrDefault(u.getName().toLowerCase().trim(), 0L));

        // 1) Prioritize faculty who explicitly handle this subject
        List<User> subjectSpecialists = faculty.stream()
                .filter(u -> u.getName() != null && !u.getName().isBlank())
                .filter(u -> handlesSubject(u, targetSubject))
                .sorted(byLoad)
                .toList();
        if(!subjectSpecialists.isEmpty()){
            subjectSpecialists.forEach(u -> ordered.add(u.getName()));
            return new ArrayList<>(ordered); // strict specialist-only when available
        }

        // 2) Then faculty from the same department, ordered by current load (lighter first)
        List<User> sameDept = faculty.stream()
                .filter(u -> u.getName() != null && !u.getName().isBlank())
                .filter(u -> {
                    if (normTargetDept.isEmpty()) return true;
                    String facDept = normalizeDept(u.getDepartment());
                    return !facDept.isEmpty() && facDept.equals(normTargetDept);
                })
                .sorted(byLoad)
                .toList();
        sameDept.forEach(u -> ordered.add(u.getName()));

        // If nothing matched but we have faculty, allow any as a last resort
        if (ordered.isEmpty()) {
            for (User u : faculty) {
                if (u.getName() != null && !u.getName().isBlank()) {
                    ordered.add(u.getName());
                }
            }
        }
        // If still empty (no faculty configured), use a synthetic placeholder to unblock generation
        if (ordered.isEmpty()) {
            ordered.add("Auto Faculty");
        }
        return new ArrayList<>(ordered);
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
