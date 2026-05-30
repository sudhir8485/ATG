package com.nt.controller;

import com.nt.entity.*;
import com.nt.repository.*;
import com.nt.service.TimetableGeneratorService;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class HodController {

	@Autowired
	UserRepository userRepo;
	
	@Autowired
	SubjectRepository subRepo;
	
	@Autowired
	TimetableRepository timetableRepo;
	
	@Autowired
	DivisionRepository divisionRepo;
	
	@Autowired
	TimeslotRepository timeRepo;
	
	@Autowired
	ClassroomRepository roomRepo;
	
	@Autowired
	AcademicSettingRepository academicSettingRepo;
	
	@Autowired
	TimetableGeneratorService generatorService;


/* HOD Dashboard */

@GetMapping("/hod")
public String hodDashboard(){
return "hod-dashboard";
}


/* Faculty List */

@GetMapping("/hod-faculty")
public String facultyList(Model model){

List<User> faculty = userRepo.findByRoleAndDeletedFalse("FACULTY");

model.addAttribute("facultyList", faculty);

return "hod-faculty";
}


/* Subject List */

@GetMapping("/hod-subjects")
public String subjectList(Model model){

List<Subject> subjects = subRepo.findByDeletedFalse();

model.addAttribute("subjectList", subjects);
model.addAttribute("subject", new Subject());
model.addAttribute("isEdit", false);

return "hod-subjects";
}

@GetMapping("/hod-subjects/edit/{id}")
public String editSubject(@PathVariable int id, Model model){
    Subject subject = subRepo.findById(id).orElse(null);
    if(subject == null){
        return "redirect:/hod-subjects";
    }
    List<Subject> subjects = subRepo.findByDeletedFalse();
    model.addAttribute("subjectList", subjects);
    model.addAttribute("subject", subject);
    model.addAttribute("isEdit", true);
    return "hod-subjects";
}

@PostMapping("/hod-subjects/save")
public String saveSubject(@RequestParam String name,
                          @RequestParam String semester,
                          @RequestParam String type,
                          @RequestParam(required=false) Integer hours,
                          @RequestParam(required=false) Integer lectureHoursPerWeek,
                          @RequestParam(required=false) Integer practicalHoursPerWeek,
                          @RequestParam(required=false) Integer practicalSlotDuration){

    Subject s = new Subject();
    s.setName(name);
    s.setSemester(parseSemester(semester));
    s.setType(type);
    s.setHours(hours);
    s.setLectureHoursPerWeek(lectureHoursPerWeek);
    s.setPracticalHoursPerWeek(practicalHoursPerWeek);
    s.setPracticalSlotDuration(practicalSlotDuration);
    s.setFaculty(null);

    subRepo.save(s);

    return "redirect:/hod-subjects";
}

@PostMapping("/hod-subjects/update/{id}")
public String updateSubject(@PathVariable int id,
                            @RequestParam String name,
                            @RequestParam String semester,
                            @RequestParam String type,
                            @RequestParam(required=false) Integer hours,
                            @RequestParam(required=false) Integer lectureHoursPerWeek,
                            @RequestParam(required=false) Integer practicalHoursPerWeek,
                            @RequestParam(required=false) Integer practicalSlotDuration){

    Subject s = subRepo.findById(id).orElse(null);
    if(s != null){
        s.setName(name);
        s.setSemester(parseSemester(semester));
        s.setType(type);
        s.setHours(hours);
        s.setLectureHoursPerWeek(lectureHoursPerWeek);
        s.setPracticalHoursPerWeek(practicalHoursPerWeek);
        s.setPracticalSlotDuration(practicalSlotDuration);
        subRepo.save(s);
    }
    return "redirect:/hod-subjects";
}

@GetMapping("/hod-subjects/delete/{id}")
public String deleteSubject(@PathVariable int id){
    subRepo.findById(id).ifPresent(s -> {
        s.setDeleted(true);
        subRepo.save(s);
    });
    return "redirect:/hod-subjects";
}

/* Assign / Change Faculty */

@GetMapping("/hod-subjects/assign/{id}")
public String assignSubject(@PathVariable int id, Model model){
    Subject subject = subRepo.findById(id).orElse(null);
    if(subject == null){
        return "redirect:/hod-subjects";
    }
List<User> faculty = userRepo.findByRoleAndDeletedFalse("FACULTY");
    model.addAttribute("subject", subject);
    model.addAttribute("facultyList", faculty);
    return "hod-assign-subject";
}

@PostMapping("/hod-subjects/assign/{id}")
public String saveAssignedFaculty(@PathVariable int id,
                                  @RequestParam String facultyName){
    Subject subject = subRepo.findById(id).orElse(null);
    if(subject != null){
        subject.setFaculty(facultyName);
        subRepo.save(subject);
    }
    return "redirect:/hod-subjects";
}

/* =========================
   HOD - AUTO TIMETABLE
   ========================= */

@GetMapping("/hod-generate")
public String showGenerator(Model model, jakarta.servlet.http.HttpSession session){
    String dept = session != null ? (String) session.getAttribute("currentDepartment") : null;
    var list = (dept != null && !dept.isBlank())
            ? timetableRepo.findByDepartmentContainingIgnoreCaseAndDeletedFalse(dept)
            : timetableRepo.findByDeletedFalse();
    model.addAttribute("lastCount", list.size());
    model.addAttribute("timetableList", list);
    return "hod-generate";
}

@PostMapping("/hod-generate/run")
public String generateTimetable(Model model, jakarta.servlet.http.HttpSession session){

    // Soft-clear existing timetable to avoid duplicates on repeated runs
    List<Timetable> allEntries = timetableRepo.findAll();
    allEntries.forEach(t -> t.setDeleted(true));
    timetableRepo.saveAll(allEntries);

    TimetableGeneratorService.GenerationResult result = generatorService.generate();

    String dept = session != null ? (String) session.getAttribute("currentDepartment") : null;

    var filteredGenerated = (dept != null && !dept.isBlank())
            ? result.getGenerated().stream()
                .filter(t -> t.getDepartment()!=null && t.getDepartment().toLowerCase().contains(dept.toLowerCase()))
                .toList()
            : result.getGenerated();

    model.addAttribute("lastCount", result.getExistingCount());
    model.addAttribute("errors", result.getAllMessages());
    model.addAttribute("timetableList", filteredGenerated);
    model.addAttribute("requestedCount", result.getRequested());
    model.addAttribute("placedCount", filteredGenerated.size());
    return "hod-generate";
}

/** Parse semester input like "SEM 3" or "3" into integer, safe fallback */
private Integer parseSemester(String sem){
    if(sem == null) return null;
    String digits = sem.replaceAll("[^0-9]", "");
    if(digits.isEmpty()) return null;
    try{
        return Integer.parseInt(digits);
    }catch(NumberFormatException e){
        return null;
    }
}

private List<String> getWorkingDays(){
    AcademicSetting setting = academicSettingRepo.findById(1).orElse(null);
    if(setting == null || setting.getWorkingDays() == null || setting.getWorkingDays().isBlank()){
        return java.util.Arrays.asList("Monday","Tuesday","Wednesday","Thursday","Friday");
    }
    return java.util.Arrays.stream(setting.getWorkingDays().split(","))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .toList();
}

/** If no or too few timeslots are configured, fall back to a standard 6-slot day. */
private List<Timeslot> getEffectiveSlots(){
    List<Timeslot> slots = timeRepo.findAll();
    if(slots == null) slots = new java.util.ArrayList<>();

    if(slots.size() >= 3){
        return slots;
    }

    // fallback: 6 one-hour slots 09:00–15:00
    String[][] defaults = {
            {"09:00","10:00"},
            {"10:00","11:00"},
            {"11:00","12:00"},
            {"12:00","13:00"},
            {"13:00","14:00"},
            {"14:00","15:00"}
    };
    java.util.List<Timeslot> generated = new java.util.ArrayList<>();
    for(String[] pair : defaults){
        Timeslot t = new Timeslot();
        t.setStartTime(pair[0]);
        t.setEndTime(pair[1]);
        generated.add(t);
    }
    return generated;
}

@PostMapping("/hod-timetable/approve")
public String approveTimetableAll(jakarta.servlet.http.HttpSession session){
    String dept = (String) session.getAttribute("currentDepartment");
    List<Timetable> list = (dept != null && !dept.isBlank())
            ? timetableRepo.findByDepartmentContainingIgnoreCaseAndDeletedFalse(dept)
            : timetableRepo.findByDeletedFalse();
    list.forEach(t -> t.setStatus("Approved"));
    timetableRepo.saveAll(list);
    return "redirect:/hod-timetable";
}


/* Department Timetable */

@GetMapping("/hod-timetable")
public String departmentTimetable(@RequestParam(required=false) String className,
                                  Model model,
                                  jakarta.servlet.http.HttpSession session){

    String dept = (String) session.getAttribute("currentDepartment");
    List<Timetable> base = (dept != null && !dept.isBlank())
            ? timetableRepo.findByDepartmentContainingIgnoreCaseAndDeletedFalse(dept)
            : timetableRepo.findByDeletedFalse();

    List<Timetable> list = base;
    if(className != null && !className.isBlank()){
        list = base.stream()
                .filter(t -> t.getClassName() != null && t.getClassName().toLowerCase().contains(className.toLowerCase()))
                .toList();
        model.addAttribute("selectedClass", className);
    }

    // distinct class names for dropdown
    List<String> classes = base.stream()
            .map(Timetable::getClassName)
            .filter(c -> c != null && !c.isBlank())
            .distinct()
            .sorted()
            .toList();

    model.addAttribute("classList", classes);
    model.addAttribute("timetableList", list);

    return "hod-timetable";
}

@GetMapping("/hod-timetable/edit/{id}")
public String editTimetable(@PathVariable int id, Model model, jakarta.servlet.http.HttpSession session){
    String dept = (String) session.getAttribute("currentDepartment");
    Timetable t = timetableRepo.findById(id).orElse(null);
    if(t == null || (dept != null && !dept.isBlank() && (t.getDepartment()==null || !t.getDepartment().toLowerCase().contains(dept.toLowerCase())))){
        return "redirect:/hod-timetable";
    }
    model.addAttribute("entry", t);
    model.addAttribute("times", getEffectiveSlots());
    model.addAttribute("rooms", roomRepo.findAll());
    model.addAttribute("facultyList", userRepo.findByRoleAndDeletedFalse("FACULTY"));
    return "hod-edit-timetable";
}

@PostMapping("/hod-timetable/update/{id}")
public String updateTimetable(@PathVariable int id,
                              @RequestParam String day,
                              @RequestParam String timeSlot,
                              @RequestParam(required=false) String room,
                              @RequestParam(required=false) String faculty,
                              jakarta.servlet.http.HttpSession session){
    String dept = (String) session.getAttribute("currentDepartment");
    Timetable t = timetableRepo.findById(id).orElse(null);
    if(t != null && !(dept != null && !dept.isBlank() && (t.getDepartment()==null || !t.getDepartment().toLowerCase().contains(dept.toLowerCase())))){
        t.setDay(day);
        t.setTimeSlot(timeSlot);
        t.setRoom(room);
        t.setFaculty(faculty);
        timetableRepo.save(t);
    }
    return "redirect:/hod-timetable";
}

@GetMapping("/hod-timetable/swap")
public String swapTimetablePage(@RequestParam(required=false) Integer firstId, Model model, jakarta.servlet.http.HttpSession session){
    String dept = (String) session.getAttribute("currentDepartment");
    List<Timetable> list = (dept != null && !dept.isBlank())
            ? timetableRepo.findByDepartmentContainingIgnoreCaseAndDeletedFalse(dept)
            : timetableRepo.findByDeletedFalse();
    model.addAttribute("timetableList", list);
    model.addAttribute("firstId", firstId);
    return "hod-swap-timetable";
}

@PostMapping("/hod-timetable/swap")
public String swapTimetable(@RequestParam int firstId, @RequestParam int secondId, jakarta.servlet.http.HttpSession session){
    if(firstId == secondId) return "redirect:/hod-timetable";
    String dept = (String) session.getAttribute("currentDepartment");
    Timetable a = timetableRepo.findById(firstId).orElse(null);
    Timetable b = timetableRepo.findById(secondId).orElse(null);
    if(a != null && b != null &&
            !(dept != null && !dept.isBlank() && (
                    a.getDepartment()==null || b.getDepartment()==null ||
                    !a.getDepartment().toLowerCase().contains(dept.toLowerCase()) ||
                    !b.getDepartment().toLowerCase().contains(dept.toLowerCase())
            ))){
        String day = a.getDay(); String time = a.getTimeSlot(); String room = a.getRoom(); String faculty = a.getFaculty();
        a.setDay(b.getDay()); a.setTimeSlot(b.getTimeSlot()); a.setRoom(b.getRoom()); a.setFaculty(b.getFaculty());
        b.setDay(day); b.setTimeSlot(time); b.setRoom(room); b.setFaculty(faculty);
        timetableRepo.save(a); timetableRepo.save(b);
    }
    return "redirect:/hod-timetable";
}


/* Approve Timetable Page */

@GetMapping("/approve-timetable")
public String approveTimetable(Model model, jakarta.servlet.http.HttpSession session){

String dept = (String) session.getAttribute("currentDepartment");
List<Timetable> list = (dept != null && !dept.isBlank())
        ? timetableRepo.findByDepartmentContainingIgnoreCaseAndDeletedFalse(dept)
        : timetableRepo.findByDeletedFalse();

model.addAttribute("timetableList", list);

return "hod-approve-timetable";
}


/* Approve */

@GetMapping("/approve/{id}")
public String approve(@PathVariable int id){

Timetable t = timetableRepo.findById(id).orElse(null);
if (t == null) return "redirect:/approve-timetable";

t.setStatus("Approved");

timetableRepo.save(t);

return "redirect:/approve-timetable";
}


/* Reject */

@GetMapping("/reject/{id}")
public String reject(@PathVariable int id){

Timetable t = timetableRepo.findById(id).orElse(null);
if (t == null) return "redirect:/approve-timetable";

t.setStatus("Rejected");

timetableRepo.save(t);

return "redirect:/approve-timetable";
}


/* =========================
   HOD - CLASSES / SEMESTERS
   ========================= */

@GetMapping("/hod-classes")
public String hodClasses(Model model){

    List<Division> divisions = divisionRepo.findAll();

    model.addAttribute("divisions", divisions);
    model.addAttribute("division", new Division());
    model.addAttribute("isEdit", false);

    return "hod-classes";
}

@GetMapping("/hod-classes/edit/{id}")
public String editClass(@PathVariable int id, Model model){

    Division division = divisionRepo.findById(id).orElse(null);
    if(division == null){
        return "redirect:/hod-classes";
    }

    List<Division> divisions = divisionRepo.findAll();

    model.addAttribute("divisions", divisions);
    model.addAttribute("division", division);
    model.addAttribute("isEdit", true);

    return "hod-classes";
}

@PostMapping("/hod-classes/save")
public String saveClass(@RequestParam String semester,
                        @RequestParam(required=false) Integer semesterNumber,
                        @RequestParam String division,
                        @RequestParam(required=false) String labPreference){

    Division d = new Division();
    d.setYear(semester);
    d.setSemesterNumber(semesterNumber != null ? semesterNumber : parseSemester(semester));
    d.setName(division);
    d.setLabPreference(labPreference != null ? labPreference : "AFTERNOON");
    divisionRepo.save(d);
    return "redirect:/hod-classes";
}

@PostMapping("/hod-classes/update/{id}")
public String updateClass(@PathVariable int id,
                          @RequestParam String semester,
                          @RequestParam(required=false) Integer semesterNumber,
                          @RequestParam String division,
                          @RequestParam(required=false) String labPreference){

    Division d = divisionRepo.findById(id).orElse(null);
    if(d != null){
        d.setYear(semester);
        d.setSemesterNumber(semesterNumber != null ? semesterNumber : parseSemester(semester));
        d.setName(division);
        d.setLabPreference(labPreference != null ? labPreference : "AFTERNOON");
        divisionRepo.save(d);
    }
    return "redirect:/hod-classes";
}

@GetMapping("/hod-classes/delete/{id}")
public String deleteClass(@PathVariable int id){
    if(divisionRepo.existsById(id)){
        divisionRepo.deleteById(id);
    }
    return "redirect:/hod-classes";
}

}
