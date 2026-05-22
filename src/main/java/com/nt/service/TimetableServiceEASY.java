package com.nt.service;

import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.nt.entity.*;
import com.nt.repository.*;

@Service
public class TimetableServiceEASY {

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


    public void generateTimetable() {

        List<Subject> subjects = subjectRepo.findByDeletedFalse();
        List<User> facultyList = userRepo.findByRoleAndDeletedFalse("FACULTY");
        List<Classroom> rooms = roomRepo.findAll();
        List<Timeslot> slots = timeRepo.findAll();
        List<Division> divisions = divisionRepo.findAll();


        // ================= BUSY TRACKERS =================

        Set<String> teacherBusy = new HashSet<>();
        Set<String> roomBusy = new HashSet<>();
        Set<String> classBusy = new HashSet<>();


        // ================= FACULTY COUNTS =================

        Map<String,Integer> facultyWeekCount = new HashMap<>();
        Map<String,Integer> facultyDayCount = new HashMap<>();


        // ================= FACULTY MAP =================

        Map<String,User> facultyByName = new HashMap<>();

        for(User u : facultyList){

            String name = safeLower(u.getName());

            if(!name.isBlank()){
                facultyByName.put(name, u);
            }
        }


        // ================= LOAD EXISTING TIMETABLE =================

        List<Timetable> oldTimetable = timetableRepo.findByDeletedFalse();

        for(Timetable t : oldTimetable){

            String day = safeLower(t.getDay());
            String slot = safeLower(t.getTimeSlot());
            String faculty = safeLower(t.getFaculty());
            String room = safeLower(t.getRoom());
            String className = safeLower(t.getClassName());
            String division = safeLower(t.getDivision());


            // Teacher busy
            teacherBusy.add(day + "|" + slot + "|" + faculty);


            // Room busy
            roomBusy.add(day + "|" + slot + "|" + room);


            // Class busy
            classBusy.add(day + "|" + slot + "|" + className + "|" + division);


            // Faculty weekly count
            facultyWeekCount.put(
                    faculty,
                    facultyWeekCount.getOrDefault(faculty,0)+1
            );


            // Faculty daily count
            String dayKey = faculty + "|" + day;

            facultyDayCount.put(
                    dayKey,
                    facultyDayCount.getOrDefault(dayKey,0)+1
            );
        }


        // ================= GENERATE TIMETABLE =================

        List<Timetable> generated = new ArrayList<>();

        for(Subject subject : subjects){

            for(Division division : divisions){

                for(String day : getWorkingDays()){

                    for(Timeslot slot : slots){


                        String dayKey = safeLower(day);
                        String slotKey = safeLower(formatSlot(slot));


                        // ================= CHECK CLASS CLASH =================

                        String classKey = dayKey + "|" + slotKey + "|" +
                                safeLower(division.getYear()) + "|" +
                                safeLower(division.getName());

                        if(classBusy.contains(classKey)){
                            continue;
                        }


                        // ================= FIND FACULTY =================

                        User selectedFaculty = null;

                        for(User faculty : facultyList){

                            String facultyName = safeLower(faculty.getName());

                            String teacherKey = dayKey + "|" + slotKey + "|" + facultyName;      

                            if(!teacherBusy.contains(teacherKey)){
                                selectedFaculty = faculty;
                                break;
                            }
                        }

                        if(selectedFaculty == null){
                            continue;
                        }


                        // ================= FIND ROOM =================

                        Classroom selectedRoom = null;

                        for(Classroom room : rooms){

                            String roomKey = dayKey + "|" + slotKey + "|" +
                                    safeLower(room.getRoom());

                            if(!roomBusy.contains(roomKey)){
                                selectedRoom = room;
                                break;
                            }
                        }

                        if(selectedRoom == null){
                            continue;
                        }


                        // ================= CREATE TIMETABLE =================

                        Timetable t = new Timetable();

                        t.setDay(day);
                        t.setDepartment(subject.getDepartment());
                        t.setClassName(division.getYear());
                        t.setDivision(division.getName());
                        t.setSubject(subject.getName());
                        t.setFaculty(selectedFaculty.getName());
                        t.setRoom(selectedRoom.getRoom());
                        t.setTimeSlot(formatSlot(slot));
                        t.setLectureType(subject.getType());
                        t.setStatus("Pending");


                        generated.add(t);


                        // ================= UPDATE BUSY SETS =================

                        String facultyName = safeLower(selectedFaculty.getName());

                        teacherBusy.add(dayKey + "|" + slotKey + "|" + facultyName);

                        roomBusy.add(dayKey + "|" + slotKey + "|" +
                                safeLower(selectedRoom.getRoom()));

                        classBusy.add(classKey);


                        // ================= UPDATE COUNTS =================

                        facultyWeekCount.put(
                                facultyName,
                                facultyWeekCount.getOrDefault(facultyName,0)+1
                        );

                        String facultyDayKey = facultyName + "|" + dayKey;

                        facultyDayCount.put(
                                facultyDayKey,
                                facultyDayCount.getOrDefault(facultyDayKey,0)+1
                        );


                        break;
                    }
                }
            }
        }


        // ================= SAVE =================

        timetableRepo.saveAll(generated);
    }


    // ================= HELPER METHODS =================


    private String safeLower(String value){

        if(value == null){
            return "";
        }

        return value.toLowerCase();
    }


    private String formatSlot(Timeslot slot){

        return slot.getStartTime() + " - " + slot.getEndTime();
    }


    private List<String> getWorkingDays(){

        return Arrays.asList(
                "Monday",
                "Tuesday",
                "Wednesday",
                "Thursday",
                "Friday"
        );
    }
}