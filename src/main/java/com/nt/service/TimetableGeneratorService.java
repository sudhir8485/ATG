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

        List<Subject> subjects = subjectRepo.findByDeletedFalse();
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

        for (Subject subject : subjects) {
            // Shuffle working days to avoid early-week clustering for this subject
            List<String> shuffledDays = new ArrayList<>(workingDays);
            Collections.shuffle(shuffledDays);

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
                // Lectures first
                for (int i = 0; i < lectureSessions; i++) {
                    result.requested++;
                    Timetable placed = tryPlace(subject, division, shuffledDays, slots, rooms, allFaculty,
                            teacherBusy, roomBusy, classBusy, subjectDayBusy,
                            facultyWeekCount, facultyDayCount, facultyByName, "Lecture");
                    if (placed == null) {
                        // fallback: ignore subject-per-day & faculty caps to avoid empty timetables
                        placed = tryPlaceRelaxed(subject, division, shuffledDays, slots, rooms, allFaculty,
                                teacherBusy, roomBusy, classBusy, subjectDayBusy, facultyByName, "Lecture");
                    }
                    if (placed != null) {
                        generated.add(placed);
                        result.placed++;
                    } else {
                        result.addMessage("Could not place " + subject.getName() +
                                labelForDivision(division) + " (lecture " + (i + 1) + "/" + lectureSessions + ")");
                    }
                }

                // Practicals / labs
                if (practicalHours > 0 && practicalDuration > 0) {
                    int blocks = Math.max(1, (int) Math.ceil(practicalHours / (double) practicalDuration));
                    for (int b = 0; b < blocks; b++) {
                        result.requested += practicalDuration;
                        List<Timetable> block = tryPlaceLab(subject, division, shuffledDays, slots, rooms, allFaculty,
                                teacherBusy, roomBusy, classBusy, subjectDayBusy,
                                facultyWeekCount, facultyDayCount, facultyByName, practicalDuration, "Practical");
                        if (block == null) {
                            block = tryPlaceLabFallback(subject, division, shuffledDays, slots, rooms, allFaculty,
                                    teacherBusy, roomBusy, classBusy, subjectDayBusy,
                                    facultyWeekCount, facultyDayCount, facultyByName, practicalDuration, "Practical");
                        }
                        if (block != null) {
                            generated.addAll(block);
                            result.placed += block.size();
                        } else {
                            result.addMessage("Could not place practical block (" + practicalDuration + " slots) for "
                                    + subject.getName() + labelForDivision(division) + " (block " + (b + 1) + "/" + blocks + ")");
                        }
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
            // Avoid same subject twice on the same day for the class
            if (subjectDayBusy.contains(key(dayKey, safeLower(className), safeLower(divisionName), safeLower(subject.getName())))) {
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

    // Relaxed placer: allows same subject twice a day and ignores faculty max caps
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

        for (String day : days) {
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
                    subjectDayBusy.add(key(dayKey, safeLower(className), safeLower(divisionName), safeLower(subject.getName())));
                    return t;
                }
            }
        }
        return null;
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
            String div = division.getName() != null ? division.getName().trim() : "";
            if (!year.isEmpty() && !div.isEmpty()) {
                return year + " - " + div;
            }
            if (!year.isEmpty()) {
                return year;
            }
            if (!div.isEmpty()) {
                return div;
            }
        }
        if (subject.getSemester() != null) {
            return "SEM " + subject.getSemester();
        }
        if (subject.getDepartment() != null && !subject.getDepartment().isBlank()) {
            return subject.getDepartment();
        }
        return "Class";
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
        // if explicit practical hours are set, do not infer lectures from total hours
        if(subject.getPracticalHoursPerWeek() != null && subject.getPracticalHoursPerWeek() > 0){
            return 1; // still schedule at least one lecture to avoid empty grids
        }
        Integer legacy = subject.getHours();
        if(legacy != null && legacy > 0){
            return isLab(subject) ? 0 : legacy;
        }
        // fallback to 1 session to keep behaviour for legacy empty records
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
                .anyMatch(s -> s.equals(subjectKey) || s.contains(subjectKey));
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
