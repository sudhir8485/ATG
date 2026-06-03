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
        // Explicit param takes highest priority (e.g. admin viewing a faculty's timetable)
        if(param != null && !param.isBlank()){
            session.setAttribute("currentFacultyKey", param.trim());
            return param.trim();
        }
        // Return cached key if already resolved this session
        Object cached = session.getAttribute("currentFacultyKey");
        if(cached != null && !cached.toString().isBlank()) return cached.toString();

        // Derive from logged-in user — try each identifier against actual timetable data
        if(session.getAttribute("currentUserId") != null){
            try{
                Integer uid = Integer.parseInt(session.getAttribute("currentUserId").toString());
                com.nt.entity.User u = userRepo.findById(uid).orElse(null);
                if(u != null){
                    String fac = bestFacultyMatch(u);
                    if(fac != null) session.setAttribute("currentFacultyKey", fac);
                    return fac;
                }
            }catch(Exception ignored){}
        }
        return null;
    }

    private String bestFacultyMatch(com.nt.entity.User u){
        // Build candidate list: username first (most likely to match the stored faculty code)
        List<String> candidates = new java.util.ArrayList<>();
        if(u.getUsername() != null && !u.getUsername().isBlank()) candidates.add(u.getUsername().trim());
        if(u.getName()     != null && !u.getName().isBlank())     candidates.add(u.getName().trim());
        if(u.getEmail()    != null && !u.getEmail().isBlank())     candidates.add(u.getEmail().trim());

        // Load all distinct faculty codes present in the timetable once
        java.util.Set<String> timetableKeys = timetableRepo.findAll().stream()
                .map(t -> norm(t.getFaculty()))
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toSet());

        // Return first candidate that actually has timetable rows
        for(String c : candidates){
            if(timetableKeys.contains(norm(c))) return c;
        }
        // Nothing matched — fall back to username so the UI shows something meaningful
        return candidates.isEmpty() ? null : candidates.get(0);
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
