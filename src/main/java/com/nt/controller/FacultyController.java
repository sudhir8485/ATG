package com.nt.controller;

import com.nt.entity.Timetable;
import com.nt.repository.TimetableRepository;
import com.nt.repository.UserRepository;
import com.nt.repository.ChangeRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class FacultyController {

    @Autowired
    TimetableRepository timetableRepo;

    @Autowired
    UserRepository userRepo;

    @Autowired
    jakarta.servlet.http.HttpSession session;

    @Autowired
    ChangeRequestRepository changeRepo;

    @GetMapping("/faculty-dashboard")
    public String facultyDashboard(Model model){
        String facKey = resolveFacultyKey(null);
        List<Timetable> personal = filterByFaculty(facKey);
        List<Timetable> today = filterToday(personal);

        model.addAttribute("totalLectures", personal.size());
        model.addAttribute("currentFaculty", facKey);
        model.addAttribute("personalTimetable", personal);
        model.addAttribute("todayLectures", today);
        model.addAttribute("usingFallbackAll", false);
        return "faculty-dashboard";
    }

    @GetMapping("/faculty-timetable")
    public String facultyTimetable(@RequestParam(required=false) String faculty,
                                   @RequestParam(required=false, defaultValue = "false") boolean today,
                                   Model model){
        String fac = resolveFacultyKey(
                (faculty != null && !faculty.isBlank())
                        ? faculty
                        : (session.getAttribute("currentFacultyKey") != null
                            ? session.getAttribute("currentFacultyKey").toString()
                            : null)
        );
        List<Timetable> list = filterByFaculty(fac);
        if(today){
            list = filterToday(list);
        }
        model.addAttribute("selectedFaculty", fac);
        model.addAttribute("showTodayOnly", today);
        model.addAttribute("timetableList", list);
        return "faculty-timetable";
    }

    @PostMapping("/faculty-lecture/status")
    public String updateLectureStatus(@RequestParam int id,
                                      @RequestParam String status){
        Timetable t = timetableRepo.findById(id).orElse(null);
        if(t != null){
            t.setStatus(status);
            timetableRepo.save(t);
        }
        return "redirect:/faculty-dashboard";
    }

    @PostMapping("/faculty-request-change")
    public String requestChange(@RequestParam(required=false) Integer timetableId,
                                @RequestParam String message){
        com.nt.entity.ChangeRequest cr = new com.nt.entity.ChangeRequest();
        cr.setTimetableId(timetableId);
        Object key = session.getAttribute("currentFacultyKey");
        cr.setFaculty(key != null ? key.toString() : "Unknown");
        cr.setMessage(message);
        cr.setStatus("Pending");
        changeRepo.save(cr);
        return "redirect:/faculty-dashboard";
    }

    private String norm(String s){
        return s == null ? "" : s.toLowerCase().replaceAll("\\s+","");
    }

    private int compareByDayAndTime(Timetable a, Timetable b){
        int dayOrder = dayIndex(a.getDay()) - dayIndex(b.getDay());
        if(dayOrder != 0) return dayOrder;
        return startMinutes(a.getTimeSlot()) - startMinutes(b.getTimeSlot());
    }

    private int dayIndex(String day){
        String d = day == null ? "" : day.toLowerCase();
        return switch(d){
            case "monday" -> 1;
            case "tuesday" -> 2;
            case "wednesday" -> 3;
            case "thursday" -> 4;
            case "friday" -> 5;
            case "saturday" -> 6;
            case "sunday" -> 7;
            default -> 99;
        };
    }

    private int startMinutes(String slot){
        if(slot == null) return 9999;
        String[] parts = slot.split("-");
        if(parts.length==0) return 9999;
        return parseMinutes(parts[0].trim());
    }

    private int parseMinutes(String hm){
        try{
            java.time.LocalTime t = java.time.LocalTime.parse(hm.replace(" ", ""));
            return t.getHour()*60 + t.getMinute();
        }catch(Exception e){
            return 9999;
        }
    }

    private String resolveFacultyKey(String param){
        String fac = (param != null && !param.isBlank()) ? param : null;
        if(session.getAttribute("currentUserId") != null){
            try{
                Integer uid = Integer.parseInt(session.getAttribute("currentUserId").toString());
                com.nt.entity.User u = userRepo.findById(uid).orElse(null);
                if(u != null){
                    if(fac == null && u.getName()!=null && !u.getName().isBlank()) fac = u.getName();
                    if(fac == null && u.getUsername()!=null && !u.getUsername().isBlank()) fac = u.getUsername();
                    if(fac == null && u.getEmail()!=null && !u.getEmail().isBlank()) fac = u.getEmail();
                }
            }catch(Exception ignored){}
        }
        if(fac == null){
            Object key = session.getAttribute("currentFacultyKey");
            if(key != null) fac = key.toString();
        }
        if(fac != null){
            session.setAttribute("currentFacultyKey", fac);
        }
        return fac;
    }

    private List<Timetable> filterByFaculty(String fac){
        if(fac == null || fac.isBlank()){
            return java.util.Collections.emptyList();
        }
        String keyNorm = norm(fac);
        return timetableRepo.findAll().stream()
                .filter(t -> keyNorm.equals(norm(t.getFaculty())))
                .sorted(this::compareByDayAndTime)
                .toList();
    }

    private List<Timetable> filterToday(List<Timetable> list){
        String today = java.time.LocalDate.now(java.time.ZoneId.systemDefault())
                .getDayOfWeek().name().toLowerCase();
        return list.stream()
                .filter(t -> today.equals(norm(t.getDay())))
                .sorted(this::compareByDayAndTime)
                .toList();
    }
}
