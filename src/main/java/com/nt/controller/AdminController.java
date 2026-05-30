package com.nt.controller;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.nt.entity.AcademicSetting;
import com.nt.entity.Classroom;
import com.nt.entity.Department;
import com.nt.entity.Division;
import com.nt.entity.Subject;
import com.nt.entity.Timeslot;
import com.nt.entity.Timetable;
import com.nt.entity.User;
import com.nt.repository.AcademicSettingRepository;
import com.nt.repository.ClassroomRepository;
import com.nt.repository.DepartmentRepository;
import com.nt.repository.DivisionRepository;
import com.nt.repository.SubjectRepository;
import com.nt.repository.TimeslotRepository;
import com.nt.repository.TimetableRepository;
import com.nt.repository.UserRepository;
import com.nt.service.TimetableGeneratorService;

@Controller
public class AdminController {

	@Autowired
	DepartmentRepository deptRepo;

	@Autowired
	DivisionRepository divRepo;

	@Autowired
	SubjectRepository subRepo;

	@Autowired
	ClassroomRepository roomRepo;

	@Autowired
	TimeslotRepository timeRepo;

	@Autowired
	UserRepository userRepo;

	@Autowired
	AcademicSettingRepository academicSettingRepo;

	@Autowired
	TimetableRepository timetableRepo;

	@Autowired
	TimetableGeneratorService generatorService;

	/*
	 * ========================= DASHBOARD =========================
	 */

	@GetMapping("/admin")
	public String adminDashboard(Model model) {

		long deptCount = deptRepo.count();
		long divisionCount = divRepo.count();
		long subjectCount = subRepo.count();
		long roomCount = roomRepo.count();
		long timeslotCount = timeRepo.count();
		long hodCount = userRepo.countByRole("HOD");
		long facultyCount = userRepo.countByRole("FACULTY");
		long timetableCount = timetableRepo.findByDeletedFalse().size();

		// faculty per department for chart
		List<User> faculty = userRepo.findByRoleAndDeletedFalse("FACULTY");
		java.util.Map<String, Long> facultyByDept = faculty.stream().filter(f -> f.getDepartment() != null).collect(
				java.util.stream.Collectors.groupingBy(User::getDepartment, java.util.stream.Collectors.counting()));

		model.addAttribute("deptCount", deptCount);
		model.addAttribute("divisionCount", divisionCount);
		model.addAttribute("subjectCount", subjectCount);
		model.addAttribute("roomCount", roomCount);
		model.addAttribute("timeslotCount", timeslotCount);
		model.addAttribute("hodCount", hodCount);
		model.addAttribute("facultyCount", facultyCount);
		model.addAttribute("timetableCount", timetableCount);
		model.addAttribute("deptLabels", facultyByDept.keySet());
		model.addAttribute("facultyCounts", facultyByDept.values());

		return "admin-dashboard";
	}

	/*
	 * ========================= SPECIAL SLOTS =========================
	 */

	@GetMapping("/add-special-slot")
	public String addSpecialSlot(Model model) {
		List<Division> divisions = divRepo.findAll();
		List<Timeslot> times = timeRepo.findAll();
		List<String> specialTypes = java.util.Arrays.asList("INTERNSHIP", "AUDIT COURSE", "T & P", "LIBRARY",
				"Virtual Lab/Spoken Tutorial", "SEMINAR", "OTHER");
		model.addAttribute("divisions", divisions);
		model.addAttribute("times", times);
		model.addAttribute("specialTypes", specialTypes);
		model.addAttribute("workingDays", getWorkingDays());
		List<Timetable> specialSlots = timetableRepo.findByDeletedFalse().stream()
	            .filter(t -> "Special".equals(t.getLectureType()))
	            .toList();
	    model.addAttribute("specialSlots", specialSlots);
	    return "add-special-slot";
	}

	@PostMapping("/save-special-slot")
	public String saveSpecialSlot(@RequestParam String day, @RequestParam String className,
			@RequestParam String division, @RequestParam(required = false) String batch, @RequestParam String label,
			@RequestParam(required = false) String customLabel, @RequestParam String startTime,
			@RequestParam String endTime, @RequestParam(required = false) String department,
			RedirectAttributes redirect) {

		String finalLabel = "OTHER".equals(label) ? customLabel : label;
		if (finalLabel == null || finalLabel.isBlank()) {
			redirect.addFlashAttribute("message", "Label is required.");
			redirect.addFlashAttribute("messageType", "error");
			return "redirect:/add-special-slot";
		}

		Timetable t = new Timetable();
		t.setDay(day);
		t.setClassName(className);
		t.setDivision(division);
		t.setBatch(batch);
		t.setSubject(finalLabel);
		t.setFaculty("");
		t.setRoom("");
		t.setLectureType("Special");
		t.setTimeSlot(startTime + " - " + endTime);
		t.setDepartment(department);
		t.setStatus("Fixed");

		timetableRepo.save(t);
		redirect.addFlashAttribute("message", "Special slot added: " + finalLabel);
		redirect.addFlashAttribute("messageType", "success");
		return "redirect:/add-special-slot";
	}

	/*
	 * ========================= ADD DEPARTMENT PAGE =========================
	 */

	@GetMapping("/add-department")
	public String addDepartment(Model model) {

		List<Department> departments = deptRepo.findAll();
		List<User> hodList = userRepo.findByRoleAndDeletedFalse("HOD");

		model.addAttribute("departments", departments);
		model.addAttribute("hodList", hodList);
		model.addAttribute("department", new Department());
		model.addAttribute("isEdit", false);

		return "add-department";
	}

	@GetMapping("/edit-department/{id}")
	public String editDepartment(@PathVariable int id, Model model) {

		Department dept = deptRepo.findById(id).orElse(null);
		if (dept == null) {
			return "redirect:/add-department";
		}
		List<Department> departments = deptRepo.findAll();
		List<User> hodList = userRepo.findByRoleAndDeletedFalse("HOD");

		model.addAttribute("departments", departments);
		model.addAttribute("hodList", hodList);
		model.addAttribute("department", dept);
		model.addAttribute("isEdit", true);

		return "add-department";
	}

	/*
	 * ========================= ADD DIVISION PAGE =========================
	 */

	@GetMapping("/add-division")
	public String addDivision(Model model) {

		List<Department> departments = deptRepo.findAll();
		List<Classroom> rooms = roomRepo.findAll();
		List<Division> divisions = divRepo.findAll();

		model.addAttribute("departments", departments);
		model.addAttribute("rooms", rooms);
		model.addAttribute("divisions", divisions);
		model.addAttribute("divisionCount", divisions.size());

		return "add-division";
	}

	/*
	 * ========================= ADD CLASSROOM PAGE =========================
	 */

	@GetMapping("/add-classroom")
	public String addClassroom(Model model) {

		List<Classroom> rooms = roomRepo.findAll();

		model.addAttribute("rooms", rooms);
		model.addAttribute("classroom", new Classroom());
		model.addAttribute("isEdit", false);

		return "add-classroom";
	}

	/*
	 * ========================= ADD TIMESLOT PAGE =========================
	 */

	@GetMapping("/add-timeslot")
	public String addTimeslot(Model model) {

		List<Timeslot> times = timeRepo.findAll();

		model.addAttribute("times", times);
		model.addAttribute("timeslot", new Timeslot());
		model.addAttribute("isEdit", false);

		AcademicSetting setting = academicSettingRepo.findById(1).orElseGet(() -> {
			AcademicSetting s = new AcademicSetting();
			s.setId(1);
			s.setWorkingDays("Monday,Tuesday,Wednesday,Thursday,Friday");
			return academicSettingRepo.save(s);
		});
		model.addAttribute("workingDays", setting.getWorkingDays());

		return "add-timeslot";
	}

	/*
	 * ========================= ADD HOD PAGE =========================
	 */

	@GetMapping("/add-hod")
	public String addHod(Model model) {

		List<Department> departments = deptRepo.findAll();
		List<User> hodList = userRepo.findByRoleAndDeletedFalse("HOD");

		model.addAttribute("departments", departments);
		model.addAttribute("hodList", hodList);
		model.addAttribute("hod", new User());
		model.addAttribute("isEdit", false);

		return "add-hod";
	}

	/*
	 * ========================= ADD FACULTY PAGE =========================
	 */

	@GetMapping("/add-faculty")
	public String addFaculty(Model model) {

		List<Department> departments = deptRepo.findAll();
		List<User> facultyList = userRepo.findByRoleAndDeletedFalse("FACULTY");

		model.addAttribute("departments", departments);
		model.addAttribute("facultyList", facultyList);
		model.addAttribute("faculty", new User());
		model.addAttribute("isEdit", false);

		return "add-faculty";
	}

	/*
	 * ========================= ASSIGN SUBJECTS TO FACULTY
	 * =========================
	 */

	@GetMapping("/assign-subjects")
	public String assignSubjects(Model model) {
		List<Subject> subjects = subRepo.findByDeletedFalse();
		List<User> faculty = userRepo.findByRoleAndDeletedFalse("FACULTY");
		model.addAttribute("subjects", subjects);
		model.addAttribute("faculty", faculty);
		return "assign-subjects";
	}

	@PostMapping("/assign-subjects/{id}")
	public String assignSubjectFaculty(@PathVariable int id, @RequestParam(required = false) String facultyName) {
		Subject s = subRepo.findById(id).orElse(null);
		if (s != null) {
			s.setFaculty(facultyName);
			subRepo.save(s);
		}
		return "redirect:/assign-subjects";
	}

	@GetMapping("/edit-faculty/{id}")
	public String editFaculty(@PathVariable int id, Model model) {

		User faculty = userRepo.findById(id).orElse(null);
		if (faculty == null) {
			return "redirect:/add-faculty";
		}

		List<Department> departments = deptRepo.findAll();
		List<User> facultyList = userRepo.findByRoleAndDeletedFalse("FACULTY");

		model.addAttribute("departments", departments);
		model.addAttribute("facultyList", facultyList);
		model.addAttribute("faculty", faculty);
		model.addAttribute("isEdit", true);

		return "add-faculty";
	}
	/*
	 * ========================= ADD TIMETABLE PAGE =========================
	 */

	@GetMapping("/add-timetable")
	public String addTimetable(Model model) {

		List<Department> departments = deptRepo.findAll();
		List<Subject> subjects = subRepo.findByDeletedFalse();
		List<User> faculty = userRepo.findByRoleAndDeletedFalse("FACULTY");
		List<Classroom> rooms = roomRepo.findAll();
		List<Timeslot> times = timeRepo.findAll();

		model.addAttribute("departments", departments);
		model.addAttribute("subjects", subjects);
		model.addAttribute("faculty", faculty);
		model.addAttribute("rooms", rooms);
		model.addAttribute("times", times);

		return "add-timetable";
	}

	/*
	 * ========================= AUTO GENERATE (ADMIN) =========================
	 */

	@GetMapping("/auto-generate")
	public String autoGenerate(Model model) {
		List<Timetable> active = timetableRepo.findByDeletedFalse();
		model.addAttribute("lastCount", active.size());
		model.addAttribute("timetableList", active);
		return "admin-generate";
	}

	@PostMapping("/auto-generate/run")
	public String runAutoGenerate(Model model) {
		// Clear existing timetable so regenerated data reflects current entries
		// soft-delete existing entries instead of hard delete
		List<Timetable> all = timetableRepo.findAll();
		all.forEach(t -> t.setDeleted(true));
		timetableRepo.saveAll(all);
		TimetableGeneratorService.GenerationResult result = generatorService.generate();
		model.addAttribute("lastCount", result.getExistingCount());
		model.addAttribute("errors", result.getAllMessages());
		model.addAttribute("requestedCount", result.getRequested());
		model.addAttribute("placedCount", result.getPlaced());
		model.addAttribute("timetableList", result.getGenerated());
		return "admin-generate";
	}

	/*
	 * ========================= SAVE DEPARTMENT =========================
	 */

	@PostMapping("/save-department")
	public String saveDepartment(@RequestParam String name, @RequestParam String code,
			@RequestParam(required = false) String hod,
			org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {

		if (name == null || name.isBlank()) {
			redirect.addFlashAttribute("message", "Department name is required");
			redirect.addFlashAttribute("messageType", "error");
			return "redirect:/add-department";
		}

		try {
			Department d = new Department();
			d.setName(name.trim());
			d.setCode(code != null ? code.trim() : "");
			d.setHod(hod);
			deptRepo.save(d);
			redirect.addFlashAttribute("message", "Department \"" + d.getName() + "\" added.");
			redirect.addFlashAttribute("messageType", "success");
		} catch (Exception ex) {
			redirect.addFlashAttribute("message", "Could not save department. " + ex.getMessage());
			redirect.addFlashAttribute("messageType", "error");
		}

		return "redirect:/add-department";
	}

	@PostMapping("/update-department/{id}")
	public String updateDepartment(@PathVariable int id, @RequestParam String name, @RequestParam String code,
			@RequestParam(required = false) String hod,
			org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {

		Department d = deptRepo.findById(id).orElse(null);
		if (d == null) {
			redirect.addFlashAttribute("message", "Department not found");
			redirect.addFlashAttribute("messageType", "error");
			return "redirect:/add-department";
		}

		try {
			d.setName(name.trim());
			d.setCode(code != null ? code.trim() : "");
			d.setHod(hod);
			deptRepo.save(d);
			redirect.addFlashAttribute("message", "Department updated.");
			redirect.addFlashAttribute("messageType", "success");
		} catch (Exception ex) {
			redirect.addFlashAttribute("message", "Could not update department. " + ex.getMessage());
			redirect.addFlashAttribute("messageType", "error");
		}

		return "redirect:/add-department";
	}

	@GetMapping("/delete-department/{id}")
	public String deleteDepartment(@PathVariable int id) {
		if (deptRepo.existsById(id)) {
			deptRepo.deleteById(id);
		}
		return "redirect:/add-department";
	}

	/*
	 * ========================= SAVE DIVISION =========================
	 */

	@PostMapping("/save-division")
	public String saveDivision(
			@RequestParam String name, @RequestParam String department, @RequestParam String year,
			@RequestParam(required = false) Integer semesterNumber, @RequestParam(required = false) Integer capacity,
			@RequestParam(required = false) String classroom,
			@RequestParam(required = false) String labPreference) {

		Division d = new Division();
		d.setName(name);
		d.setDepartment(department);
		d.setYear(year);
		d.setSemesterNumber(semesterNumber != null ? semesterNumber : parseSemester(year));
		d.setCapacity(capacity);
		d.setClassroom(classroom);
		d.setLabPreference(labPreference != null ? labPreference : "AFTERNOON");
		divRepo.save(d);
		return "redirect:/add-division";
	}

	/*
	 * ========================= SUBJECT PAGES =========================
	 */

	@GetMapping("/add-subject")
	public String addSubject(Model model) {
		List<Department> departments = deptRepo.findAll();
		List<Subject> subjects = subRepo.findByDeletedFalse();
		model.addAttribute("departments", departments);
		model.addAttribute("subjects", subjects);
		model.addAttribute("subject", new Subject());
		model.addAttribute("isEdit", false);
		return "add-subject";
	}

	@GetMapping("/edit-subject/{id}")
	public String editSubject(@PathVariable int id, Model model) {
		Subject subject = subRepo.findById(id).orElse(null);
		if (subject == null) {
			return "redirect:/add-subject";
		}
		List<Department> departments = deptRepo.findAll();
		List<Subject> subjects = subRepo.findByDeletedFalse();
		model.addAttribute("departments", departments);
		model.addAttribute("subjects", subjects);
		model.addAttribute("subject", subject);
		model.addAttribute("isEdit", true);
		return "add-subject";
	}

	@PostMapping("/save-subject")
	public String saveSubject(

			@RequestParam String name, @RequestParam String code, @RequestParam(required = false) String department,
			@RequestParam(required = false) Integer semester, @RequestParam(required = false) Integer credits,
			@RequestParam(required = false) Integer hours, @RequestParam(required = false) Integer lectureHoursPerWeek,
			@RequestParam(required = false) Integer practicalHoursPerWeek,
			@RequestParam(required = false) Integer practicalSlotDuration, @RequestParam(required = false) String type,
			@RequestParam(required = false) String description) {

		Subject s = new Subject();

		s.setName(name);
		s.setCode(code);
		s.setDepartment(department);
		s.setSemester(semester);
		s.setCredits(credits);
		s.setHours(hours);
		s.setLectureHoursPerWeek(lectureHoursPerWeek);
		s.setPracticalHoursPerWeek(practicalHoursPerWeek);
		s.setPracticalSlotDuration(practicalSlotDuration);
		s.setType(type);
		s.setDescription(description);

		subRepo.save(s);

		return "redirect:/add-subject";
	}

	@PostMapping("/update-subject/{id}")
	public String updateSubject(@PathVariable int id, @RequestParam String name, @RequestParam String code,
			@RequestParam(required = false) String department, @RequestParam(required = false) Integer semester,
			@RequestParam(required = false) Integer credits, @RequestParam(required = false) Integer hours,
			@RequestParam(required = false) Integer lectureHoursPerWeek,
			@RequestParam(required = false) Integer practicalHoursPerWeek,
			@RequestParam(required = false) Integer practicalSlotDuration, @RequestParam(required = false) String type,
			@RequestParam(required = false) String description) {

		Subject s = subRepo.findById(id).orElse(null);
		if (s != null) {
			s.setName(name);
			s.setCode(code);
			s.setDepartment(department);
			s.setSemester(semester);
			s.setCredits(credits);
			s.setHours(hours);
			s.setLectureHoursPerWeek(lectureHoursPerWeek);
			s.setPracticalHoursPerWeek(practicalHoursPerWeek);
			s.setPracticalSlotDuration(practicalSlotDuration);
			s.setType(type);
			s.setDescription(description);
			subRepo.save(s);
		}
		return "redirect:/add-subject";
	}

	@GetMapping("/delete-subject/{id}")
	public String deleteSubject(@PathVariable int id) {
		subRepo.findById(id).ifPresent(s -> {
			s.setDeleted(true);
			subRepo.save(s);
		});
		return "redirect:/add-subject";
	}

	/*
	 * ========================= SAVE CLASSROOM =========================
	 */

	@PostMapping("/save-classroom")
	public String saveClassroom(@RequestParam String room, @RequestParam(required = false) String building,
			@RequestParam(required = false) Integer capacity, @RequestParam(required = false) String type) {

		Classroom c = new Classroom();
		c.setRoom(room);
		c.setBuilding(building);
		c.setCapacity(capacity);
		c.setType(type);

		roomRepo.save(c);

		return "redirect:/add-classroom";
	}

	@GetMapping("/edit-classroom/{id}")
	public String editClassroom(@PathVariable int id, Model model) {
		Classroom c = roomRepo.findById(id).orElse(null);
		if (c == null) {
			return "redirect:/add-classroom";
		}
		List<Classroom> rooms = roomRepo.findAll();
		model.addAttribute("rooms", rooms);
		model.addAttribute("classroom", c);
		model.addAttribute("isEdit", true);
		return "add-classroom";
	}

	@PostMapping("/update-classroom/{id}")
	public String updateClassroom(@PathVariable int id, @RequestParam String room,
			@RequestParam(required = false) String building, @RequestParam(required = false) Integer capacity,
			@RequestParam(required = false) String type) {

		Classroom c = roomRepo.findById(id).orElse(null);
		if (c != null) {
			c.setRoom(room);
			c.setBuilding(building);
			c.setCapacity(capacity);
			c.setType(type);
			roomRepo.save(c);
		}
		return "redirect:/add-classroom";
	}

	@GetMapping("/delete-classroom/{id}")
	public String deleteClassroom(@PathVariable int id) {
		if (roomRepo.existsById(id)) {
			roomRepo.deleteById(id);
		}
		return "redirect:/add-classroom";
	}

	/*
	 * ========================= SAVE TIMESLOT =========================
	 */

	@PostMapping("/save-timeslot")
	public String saveTimeslot(

			@RequestParam String start, @RequestParam String end,
			@RequestParam(required = false, defaultValue = "false") boolean isBreak) {

		Timeslot t = new Timeslot();

		t.setStartTime(start);
		t.setEndTime(end);
		t.setBreak(isBreak);

		timeRepo.save(t);

		return "redirect:/add-timeslot";
	}

	@GetMapping("/edit-timeslot/{id}")
	public String editTimeslot(@PathVariable int id, Model model) {

		Timeslot t = timeRepo.findById(id).orElse(null);
		if (t == null) {
			return "redirect:/add-timeslot";
		}

		List<Timeslot> times = timeRepo.findAll();
		AcademicSetting setting = academicSettingRepo.findById(1).orElseGet(() -> {
			AcademicSetting s = new AcademicSetting();
			s.setId(1);
			s.setWorkingDays("Monday,Tuesday,Wednesday,Thursday,Friday");
			return academicSettingRepo.save(s);
		});

		model.addAttribute("times", times);
		model.addAttribute("timeslot", t);
		model.addAttribute("isEdit", true);
		model.addAttribute("workingDays", setting.getWorkingDays());

		return "add-timeslot";
	}

	@PostMapping("/update-timeslot/{id}")
	public String updateTimeslot(@PathVariable int id, @RequestParam String start, @RequestParam String end,
			@RequestParam(required = false, defaultValue = "false") boolean isBreak) {

		Timeslot t = timeRepo.findById(id).orElse(null);
		if (t != null) {
			t.setStartTime(start);
			t.setEndTime(end);
			t.setBreak(isBreak);
			timeRepo.save(t);
		}
		return "redirect:/add-timeslot";
	}

	@GetMapping("/delete-timeslot/{id}")
	public String deleteTimeslot(@PathVariable int id) {
		if (timeRepo.existsById(id)) {
			timeRepo.deleteById(id);
		}
		return "redirect:/add-timeslot";
	}

	@PostMapping("/save-working-days")
	public String saveWorkingDays(@RequestParam(required = false) String[] days) {
		String joined = "";
		if (days != null && days.length > 0) {
			joined = String.join(",", days);
		}

		AcademicSetting setting = academicSettingRepo.findById(1).orElseGet(() -> {
			AcademicSetting s = new AcademicSetting();
			s.setId(1);
			return s;
		});
		setting.setWorkingDays(joined);
		academicSettingRepo.save(setting);
		return "redirect:/add-timeslot";
	}

	/*
	 * ========================= SAVE HOD (HTML form pramane)
	 * =========================
	 */

	@PostMapping("/save-hod")
	public String saveHod(@RequestParam String name, @RequestParam String department, @RequestParam String email,
			@RequestParam String password, RedirectAttributes redirect) {

		String desiredUsername = email != null ? email.trim() : null;
		if (isUsernameTaken(desiredUsername, null)) {
			redirect.addFlashAttribute("message", "Username already exists. Choose a different username/email.");
			redirect.addFlashAttribute("messageType", "error");
			return "redirect:/add-hod";
		}

		User u = new User();

		u.setName(name);
		u.setUsername(desiredUsername);
		u.setEmail(email);
		u.setPassword(password);
		u.setDepartment(department);
		u.setRole("HOD");

		try {
			userRepo.save(u);
		} catch (DataIntegrityViolationException ex) {
			redirect.addFlashAttribute("message", "Username already exists.");
			redirect.addFlashAttribute("messageType", "error");
			return "redirect:/add-hod";
		}

		// update department record with current HOD name
		deptRepo.findAll().stream().filter(d -> department.equalsIgnoreCase(d.getName())).findFirst().ifPresent(d -> {
			d.setHod(name);
			deptRepo.save(d);
		});

		return "redirect:/add-hod";
	}

	@GetMapping("/edit-hod/{id}")
	public String editHod(@PathVariable int id, Model model) {

		User hod = userRepo.findById(id).orElse(null);
		if (hod == null) {
			return "redirect:/add-hod";
		}

		List<Department> departments = deptRepo.findAll();
		List<User> hodList = userRepo.findByRole("HOD");

		model.addAttribute("departments", departments);
		model.addAttribute("hodList", hodList);
		model.addAttribute("hod", hod);
		model.addAttribute("isEdit", true);

		return "add-hod";
	}

	@PostMapping("/update-hod/{id}")
	public String updateHod(@PathVariable int id, @RequestParam String name, @RequestParam String department,
			@RequestParam String email, @RequestParam(required = false) String password, RedirectAttributes redirect) {

		User u = userRepo.findById(id).orElse(null);
		if (u != null) {
			u.setName(name);
			String desiredUsername = email != null ? email.trim() : null;
			if (isUsernameTaken(desiredUsername, u.getId())) {
				redirect.addFlashAttribute("message", "Username already exists. Choose a different username/email.");
				redirect.addFlashAttribute("messageType", "error");
				return "redirect:/add-hod";
			}
			u.setUsername(desiredUsername);
			u.setEmail(email);
			if (password != null && !password.isBlank()) {
				u.setPassword(password);
			}
			u.setDepartment(department);
			u.setRole("HOD");
			try {
				userRepo.save(u);
			} catch (DataIntegrityViolationException ex) {
				redirect.addFlashAttribute("message", "Username already exists.");
				redirect.addFlashAttribute("messageType", "error");
				return "redirect:/add-hod";
			}

			deptRepo.findAll().stream().filter(d -> department.equalsIgnoreCase(d.getName())).findFirst()
					.ifPresent(d -> {
						d.setHod(name);
						deptRepo.save(d);
					});
		}
		return "redirect:/add-hod";
	}

	@GetMapping("/delete-hod/{id}")
	public String deleteHod(@PathVariable int id) {
		userRepo.findById(id).ifPresent(u -> {
			// Clear department.hod if this HOD is assigned
			deptRepo.findAll().forEach(d -> {
				if (u.getName() != null && u.getName().equalsIgnoreCase(d.getHod())) {
					d.setHod(null);
					deptRepo.save(d);
				}
			});
			u.setDeleted(true);
			userRepo.save(u);
		});
		return "redirect:/add-hod";
	}

	/*
	 * ========================= SAVE FACULTY =========================
	 */

	@PostMapping("/save-faculty")
	public String saveFaculty(

			@RequestParam List<String> name, @RequestParam(required = false) List<String> username,
			@RequestParam(required = false) List<String> password, @RequestParam List<String> email,
			@RequestParam(required = false) List<String> department,
			@RequestParam(required = false) List<String> custom_department,
			@RequestParam(required = false) List<String> subjectsHandled,
			@RequestParam(required = false) List<String> maxLecturesPerDay,
			@RequestParam(required = false) List<String> maxLecturesPerWeek, RedirectAttributes redirect) {

		int created = 0;
		int skipped = 0;
		StringBuilder errors = new StringBuilder();

		int rows = name != null ? name.size() : 0;
		for (int i = 0; i < rows; i++) {
			String nm = safeListVal(name, i);
			if (nm.isBlank()) {
				skipped++;
				continue;
			}
			String emailVal = safeListVal(email, i);
			String userVal = safeListVal(username, i);
			String finalUsername = !userVal.isBlank() ? userVal : (!emailVal.isBlank() ? emailVal : nm);
			if (finalUsername.isBlank()) {
				skipped++;
				errors.append("Row ").append(i + 1).append(": missing username/email; ");
				continue;
			}
			if (isUsernameTaken(finalUsername, null)) {
				skipped++;
				errors.append("Row ").append(i + 1).append(": username already exists; ");
				continue;
			}

			User u = new User();
			u.setName(nm);
			u.setUsername(finalUsername);
			String pwdVal = safeListVal(password, i);
			String finalPassword = !pwdVal.isBlank() ? pwdVal : finalUsername;
			u.setPassword(finalPassword);
			u.setEmail(emailVal);
			u.setSubjectsHandled(safeListVal(subjectsHandled, i));

			u.setMaxLecturesPerDay(parseIntSafe(safeListVal(maxLecturesPerDay, i)));
			u.setMaxLecturesPerWeek(parseIntSafe(safeListVal(maxLecturesPerWeek, i)));

			String deptVal = safeListVal(department, i);
			String customDept = safeListVal(custom_department, i);
			if ("other".equalsIgnoreCase(deptVal)) {
				u.setDepartment(customDept);
			} else {
				u.setDepartment(deptVal);
			}

			u.setRole("FACULTY");

			try {
				userRepo.save(u);
				created++;
			} catch (DataIntegrityViolationException ex) {
				skipped++;
				errors.append("Row ").append(i + 1).append(": username collision; ");
			}
		}

		String msg = "Created " + created + " faculty.";
		if (skipped > 0) {
			msg += " Skipped " + skipped + " row(s).";
		}
		redirect.addFlashAttribute("message", msg + (errors.length() > 0 ? " " + errors.toString() : ""));
		redirect.addFlashAttribute("messageType", skipped > 0 ? "warning" : "success");

		return "redirect:/add-faculty";
	}

	@PostMapping("/update-faculty/{id}")
	public String updateFaculty(@PathVariable int id, @RequestParam String name,
			@RequestParam(required = false) String username, @RequestParam(required = false) String password,
			@RequestParam String email, @RequestParam(required = false) String department,
			@RequestParam(required = false) String custom_department,
			@RequestParam(required = false) String subjectsHandled,
			@RequestParam(required = false) Integer maxLecturesPerDay,
			@RequestParam(required = false) Integer maxLecturesPerWeek, RedirectAttributes redirect) {

		User u = userRepo.findById(id).orElse(null);
		if (u != null) {
			u.setName(name);
			String desiredUsername = u.getUsername();
			if (username != null && !username.isBlank()) {
				desiredUsername = username;
			} else if (email != null && !email.isBlank()) {
				desiredUsername = email;
			}
			if (isUsernameTaken(desiredUsername, u.getId())) {
				redirect.addFlashAttribute("message", "Username already exists. Choose a different username.");
				redirect.addFlashAttribute("messageType", "error");
				return "redirect:/add-faculty";
			}
			u.setUsername(desiredUsername);
			u.setEmail(email);
			if (password != null && !password.isBlank()) {
				u.setPassword(password);
			}
			u.setSubjectsHandled(subjectsHandled);
			u.setMaxLecturesPerDay(maxLecturesPerDay);
			u.setMaxLecturesPerWeek(maxLecturesPerWeek);

			if (department != null && department.equals("other")) {
				u.setDepartment(custom_department);
			} else {
				u.setDepartment(department);
			}
			u.setRole("FACULTY");
			try {
				userRepo.save(u);
			} catch (DataIntegrityViolationException ex) {
				redirect.addFlashAttribute("message", "Username already exists.");
				redirect.addFlashAttribute("messageType", "error");
				return "redirect:/add-faculty";
			}
		}
		return "redirect:/add-faculty";
	}

	@GetMapping("/delete-faculty/{id}")
	public String deleteFaculty(@PathVariable int id) {
		userRepo.findById(id).ifPresent(u -> {
			u.setDeleted(true);
			userRepo.save(u);
		});
		return "redirect:/add-faculty";
	}

	/*
	 * ========================= SAVE TIMETABLE =========================
	 */

	@PostMapping("/save-timetable")
	public String saveTimetable(

			@RequestParam String day, @RequestParam String department, @RequestParam String className,
			@RequestParam String division, @RequestParam String subject, @RequestParam String faculty,
			@RequestParam String room, @RequestParam String lectureType, @RequestParam String startTime,
			@RequestParam String endTime, @RequestParam String breakTime) {

		Timetable t = new Timetable();

		t.setDay(day);
		t.setDepartment(department);
		t.setClassName(className);
		t.setDivision(division);
		t.setSubject(subject);
		t.setFaculty(faculty);
		t.setRoom(room);
		t.setLectureType(lectureType);

		String timeSlot = startTime + " - " + endTime;

		t.setTimeSlot(timeSlot);
		t.setBreakTime(breakTime);

		timetableRepo.save(t);

		return "redirect:/view-timetable";
	}

	/*
	 * ========================= VIEW TIMETABLE =========================
	 */

	@GetMapping("/view-timetable")
	public String viewTimetable(@RequestParam(required = false) String className,
			@RequestParam(required = false) String department, Model model) {

		List<Timetable> list = timetableRepo.findByDeletedFalse();
		model.addAttribute("selectedDept", department);
		model.addAttribute("selectedClass", className);

		if (department != null && !department.isBlank()) {
			list = list.stream()
					.filter(t -> t.getDepartment() != null && t.getDepartment().equalsIgnoreCase(department)).toList();
		}
		if (className != null && !className.isBlank()) {
			list = list.stream().filter(
					t -> t.getClassName() != null && t.getClassName().toLowerCase().contains(className.toLowerCase()))
					.toList();
		}

		List<String> departments = timetableRepo.findByDeletedFalse().stream().map(Timetable::getDepartment)
				.filter(d -> d != null && !d.isBlank()).distinct().sorted().toList();
		List<String> classes = timetableRepo.findByDeletedFalse().stream().map(Timetable::getClassName)
				.filter(c -> c != null && !c.isBlank()).distinct().sorted().toList();

		model.addAttribute("deptList", departments);
		model.addAttribute("classList", classes);
		model.addAttribute("timetableList", list);

		return "view-timetable";
	}

	/*
	 * ========================= CLEAR TIMETABLE =========================
	 */

	@PostMapping("/clear-timetable")
	public String clearTimetable(org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {
		List<Timetable> allEntries = timetableRepo.findAll();
		allEntries.forEach(t -> t.setDeleted(true));
		timetableRepo.saveAll(allEntries);
		redirect.addFlashAttribute("message", "All timetable entries removed.");
		redirect.addFlashAttribute("messageType", "success");
		return "redirect:/view-timetable";
	}

	/*
	 * ========================= DELETE TIMETABLE =========================
	 */

	@GetMapping("/delete-timetable/{id}")
	public String deleteTimetable(@PathVariable int id) {

		if (timetableRepo.existsById(id)) {
			timetableRepo.findById(id).ifPresent(t -> {
				t.setDeleted(true);
				timetableRepo.save(t);
			});
		}

		return "redirect:/view-timetable";
	}

	/*
	 * ========================= TIMETABLE GRID VIEW =========================
	 */

	@GetMapping("/timetable-grid")
	public String timetableGrid(@RequestParam(required = false) String department,
			@RequestParam(required = false) String className, Model model) {

		List<Timetable> list = filterTimetables(department, className);
		List<String> days = getWorkingDays();
		List<Timeslot> slots = insertBreakSlots(getEffectiveSlots());
		List<String> slotLabels = slots.stream().map(ts -> ts.getStartTime() + " - " + ts.getEndTime()).toList();

		Map<String, Map<String, List<Timetable>>> grid = new java.util.LinkedHashMap<>();
		for (String slot : slotLabels) {
			Map<String, List<Timetable>> dayMap = new java.util.LinkedHashMap<>();
			for (String day : days) {
				dayMap.put(day, new java.util.ArrayList<>());
			}
			grid.put(slot, dayMap);
		}

		for (Timetable t : list) {
			String day = matchDay(days, t.getDay());
			String slot = matchSlot(slotLabels, t.getTimeSlot());
			if (day == null || slot == null) {
				continue;
			}
			grid.get(slot).get(day).add(t);
		}

		List<String> departments = timetableRepo.findByDeletedFalse().stream().map(Timetable::getDepartment)
				.filter(d -> d != null && !d.isBlank()).distinct().sorted().toList();
		List<String> classes = timetableRepo.findByDeletedFalse().stream().map(Timetable::getClassName)
				.filter(c -> c != null && !c.isBlank()).distinct().sorted().toList();

		model.addAttribute("selectedDept", department);
		model.addAttribute("selectedClass", className);
		model.addAttribute("deptList", departments);
		model.addAttribute("classList", classes);
		model.addAttribute("timetableList", list);
		model.addAttribute("days", days);
		model.addAttribute("slotLabels", slotLabels);
		model.addAttribute("grid", grid);

		return "timetable-grid";
	}

	/*
	 * ========================= RECYCLE BIN =========================
	 */

	@GetMapping("/recycle-bin")
	public String recycleBin(Model model) {
		model.addAttribute("deletedUsers", userRepo.findByDeletedTrue());
		model.addAttribute("deletedSubjects", subRepo.findByDeletedTrue());
		model.addAttribute("deletedTimetables", timetableRepo.findByDeletedTrue());
		return "recycle-bin";
	}

	@GetMapping("/restore-faculty/{id}")
	public String restoreFaculty(@PathVariable int id) {
		userRepo.findById(id).ifPresent(u -> {
			u.setDeleted(false);
			userRepo.save(u);
		});
		return "redirect:/recycle-bin";
	}

	@GetMapping("/restore-subject/{id}")
	public String restoreSubject(@PathVariable int id) {
		subRepo.findById(id).ifPresent(s -> {
			s.setDeleted(false);
			subRepo.save(s);
		});
		return "redirect:/recycle-bin";
	}

	@GetMapping("/restore-timetable/{id}")
	public String restoreTimetable(@PathVariable int id) {
		timetableRepo.findById(id).ifPresent(t -> {
			t.setDeleted(false);
			timetableRepo.save(t);
		});
		return "redirect:/recycle-bin";
	}

	/*
	 * ========================= EXPORT PDF =========================
	 */

	@GetMapping("/export-load-distribution")
	public ResponseEntity<byte[]> exportLoadDistribution(@RequestParam(required = false) String department,
			@RequestParam(required = false) String className) {

		List<Timetable> entries = filterTimetables(department, className);
		if (entries.isEmpty()) {
			return ResponseEntity.noContent().build();
		}

		Map<String, FacultyLoad> rows = new LinkedHashMap<>();
		for (Timetable t : entries) {
			String faculty = emptySafe(t.getFaculty()).isBlank() ? "Unassigned" : t.getFaculty().trim();
			int semester = extractSemester(t.getClassName());
			int yearBucket = mapYearFromSemester(semester);
			boolean isPractical = isPracticalSlot(t.getLectureType(), t.getSubject());
			String type = isPractical ? "PR" : "TH";
			String subject = emptySafe(t.getSubject()).isBlank() ? "Subject" : t.getSubject().trim();
			rows.computeIfAbsent(faculty, k -> new FacultyLoad()).add(yearBucket, type, subject);
		}

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		Document doc = new Document(PageSize.A4, 28, 28, 32, 32);
		try {
			PdfWriter.getInstance(doc, baos);
			doc.open();

			Font collegeFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
			Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
			Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
			Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 9);
			Font smallFont = FontFactory.getFont(FontFactory.HELVETICA, 8);

			Paragraph college = new Paragraph("ASM's Pawar College of Engineering & Research, Parvat, Pune",
					collegeFont);
			college.setAlignment(Element.ALIGN_CENTER);
			college.setSpacingAfter(4f);
			doc.add(college);

			String deptLabel = (department != null && !department.isBlank()) ? department : "All Departments";
			Paragraph deptLine = new Paragraph("Department: " + deptLabel + " - Load Distribution", titleFont);
			deptLine.setAlignment(Element.ALIGN_CENTER);
			deptLine.setSpacingAfter(2f);
			doc.add(deptLine);

			Paragraph ayLine = new Paragraph("Academic Year: " + computeAcademicYearLabel(), bodyFont);
			ayLine.setAlignment(Element.ALIGN_CENTER);
			ayLine.setSpacingAfter(12f);
			doc.add(ayLine);

			float[] widths = { 5f, 24f, 9f, 9f, 9f, 9f, 9f, 9f, 9f, 9f, 6f };
			PdfPTable table = new PdfPTable(widths);
			table.setWidthPercentage(100);

			PdfPCell h1 = new PdfPCell(new Phrase("Sr. No.", headerFont));
			h1.setRowspan(2);
			h1.setHorizontalAlignment(Element.ALIGN_CENTER);
			h1.setVerticalAlignment(Element.ALIGN_MIDDLE);
			h1.setPadding(6f);
			table.addCell(h1);

			PdfPCell h2 = new PdfPCell(new Phrase("Name of Faculty", headerFont));
			h2.setRowspan(2);
			h2.setHorizontalAlignment(Element.ALIGN_CENTER);
			h2.setVerticalAlignment(Element.ALIGN_MIDDLE);
			h2.setPadding(6f);
			table.addCell(h2);

			addYearSpan(table, "First Year", headerFont);
			addYearSpan(table, "Second Year", headerFont);
			addYearSpan(table, "Third Year", headerFont);
			addYearSpan(table, "Final Year", headerFont);

			PdfPCell hTotal = new PdfPCell(new Phrase("Total", headerFont));
			hTotal.setRowspan(2);
			hTotal.setHorizontalAlignment(Element.ALIGN_CENTER);
			hTotal.setVerticalAlignment(Element.ALIGN_MIDDLE);
			hTotal.setPadding(6f);
			table.addCell(hTotal);

			// second header row: TH and PR under each year
			addYearSubHeaders(table, headerFont);
			table.setHeaderRows(2);

			int sr = 1;
			List<Map.Entry<String, FacultyLoad>> sortedRows = rows.entrySet().stream()
					.sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER)).toList();
			for (Map.Entry<String, FacultyLoad> row : sortedRows) {
				FacultyLoad load = row.getValue();
				PdfPCell srCell = new PdfPCell(new Phrase(String.valueOf(sr++), bodyFont));
				srCell.setHorizontalAlignment(Element.ALIGN_CENTER);
				srCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
				srCell.setPadding(5f);
				table.addCell(srCell);

				PdfPCell nameCell = new PdfPCell(new Phrase(row.getKey(), bodyFont));
				nameCell.setPadding(5f);
				table.addCell(nameCell);

				for (int year = 1; year <= 4; year++) {
					table.addCell(yearCell(load, year, "TH", bodyFont));
					table.addCell(yearCell(load, year, "PR", bodyFont));
				}

				PdfPCell total = new PdfPCell(new Phrase(String.valueOf(load.totalHours), bodyFont));
				total.setHorizontalAlignment(Element.ALIGN_CENTER);
				total.setVerticalAlignment(Element.ALIGN_MIDDLE);
				total.setPadding(5f);
				table.addCell(total);
			}

			doc.add(table);

			Paragraph note = new Paragraph(
					"Note: Counts represent scheduled hours per week by faculty and year bucket. TH = Theory, PR = Practical/Lab.",
					smallFont);
			note.setSpacingBefore(8f);
			doc.add(note);

			doc.close();
		} catch (Exception ex) {
			doc.close();
			return ResponseEntity.internalServerError().build();
		}

		byte[] pdf = baos.toByteArray();
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_PDF);
		headers.setContentDispositionFormData("attachment", "load-distribution.pdf");
		headers.setContentLength(pdf.length);
		return ResponseEntity.ok().headers(headers).body(pdf);
	}

	/** Converts 24hr time string "14:00" → "02:00 PM", "09:00" → "09:00 AM" */
	private String to12Hr(String time24) {
		if (time24 == null || time24.isBlank())
			return time24;
		try {
			java.time.LocalTime t = java.time.LocalTime.parse(time24.trim());
			return t.format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a"));
		} catch (Exception e) {
			return time24; // return original if parsing fails
		}
	}

	@GetMapping("/export-pdf")
	public ResponseEntity<byte[]> exportPdf(@RequestParam(required = false) String department,
			@RequestParam(required = false) String className) {

		List<Timetable> entries = filterTimetables(department, className);
		if (entries.isEmpty()) {
			return ResponseEntity.noContent().build();
		}

		List<String> days = getWorkingDays();
		List<SlotDef> slots = getExportSlots();
		List<String> slotLabels = slots.stream().map(s -> to12Hr(s.start()) + " - " + to12Hr(s.end())).toList();

		// Group per class/division — each gets its own dedicated page
		Map<String, List<Timetable>> grouped = new LinkedHashMap<>();
		for (Timetable t : entries) {
			grouped.computeIfAbsent(buildClassLabel(t), k -> new java.util.ArrayList<>()).add(t);
		}

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		Document doc = new Document(PageSize.A3.rotate(), 20, 20, 26, 26);
		try {
			PdfWriter.getInstance(doc, baos);
			doc.open();

			String academicYear = computeAcademicYearLabel();
			String dept = entries.stream().map(Timetable::getDepartment)
					.filter(java.util.Objects::nonNull).filter(s -> !s.isBlank())
					.findFirst().orElse((department != null && !department.isBlank()) ? department : "Information Technology");

			Font titleFont   = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
			Font headerFont  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
			Font bodyFont    = FontFactory.getFont(FontFactory.HELVETICA, 9);
			Font noteFont    = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.BOLD);
			Font metaFont    = FontFactory.getFont(FontFactory.HELVETICA, 7);
			Font infoFont    = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
			Font collegeFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
			Font signFont    = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);

			java.awt.Color headerBg = new java.awt.Color(233, 236, 241);
			java.awt.Color breakBg  = new java.awt.Color(239, 242, 247);

			// Column widths: Days col + slot cols (no Class col — each page is one class)
			int breakCount   = (int) slots.stream().filter(SlotDef::isBreak).count();
			int regularCount = slotLabels.size() - breakCount;
			float breakSlotW   = 3.5f;
			float regularSlotW = regularCount > 0 ? (92f - breakCount * breakSlotW) / regularCount : 10f;
			int cols = slotLabels.size() + 1; // Days + slots
			float[] widths = new float[cols];
			widths[0] = 8f;
			for (int i = 0; i < slotLabels.size(); i++) {
				widths[i + 1] = slots.get(i).isBreak() ? breakSlotW : regularSlotW;
			}

			String[] signLabels = { "PROF. R. A. NIKAM", "DR. A. A. KADAM", "DR. S. B. THAKARE" };
			String[] signRoles  = { "TIME TABLE COORDINATOR", "HOD", "PRINCIPAL" };

			boolean firstPage = true;
			for (Map.Entry<String, List<Timetable>> group : grouped.entrySet()) {
				if (!firstPage) doc.newPage();
				firstPage = false;

				List<Timetable> list = group.getValue();
				String classLabel = group.getKey(); // e.g. "SE(IT) - SE-IT"

				// ── Per-page header ──────────────────────────────────────────────────
				PdfPTable metaTable = new PdfPTable(2);
				metaTable.setWidthPercentage(100);
				metaTable.setWidths(new float[]{50f, 50f});
				metaTable.setSpacingAfter(2f);
				PdfPCell metaLeft = new PdfPCell();
				metaLeft.setBorder(Rectangle.NO_BORDER);
				metaLeft.addElement(new Phrase("Record No.: ACA/R/003A       DoI: 01/02/2025", metaFont));
				metaLeft.addElement(new Phrase("Revision: 00       Version: 3A.0", metaFont));
				metaTable.addCell(metaLeft);
				PdfPCell metaRight = new PdfPCell();
				metaRight.setBorder(Rectangle.NO_BORDER);
				com.lowagie.text.Paragraph wef = new com.lowagie.text.Paragraph("W. E. F.: 01/01/2026", metaFont);
				wef.setAlignment(Element.ALIGN_RIGHT);
				metaRight.addElement(wef);
				metaTable.addCell(metaRight);
				doc.add(metaTable);

				Paragraph collegeLine1 = new Paragraph("Akhil Bharatiya Maratha Shikshan Parishad's", collegeFont);
				collegeLine1.setAlignment(Element.ALIGN_CENTER);
				doc.add(collegeLine1);
				Paragraph collegeLine2 = new Paragraph(
						"Anantrao Pawar College of Engineering & Research, Parvati, Pune", collegeFont);
				collegeLine2.setAlignment(Element.ALIGN_CENTER);
				collegeLine2.setSpacingAfter(4f);
				doc.add(collegeLine2);
				doc.add(new Chunk(new com.lowagie.text.pdf.draw.LineSeparator()));

				Paragraph mainTitle = new Paragraph("Master Timetable", titleFont);
				mainTitle.setAlignment(Element.ALIGN_CENTER);
				mainTitle.setSpacingBefore(4f);
				mainTitle.setSpacingAfter(2f);
				doc.add(mainTitle);

				// Info line now includes the class/division name — matches reference format
				Paragraph infoLine = new Paragraph(
						"A.Y. " + academicYear + "  |  Dept. of " + dept + "  |  " + classLabel + "  |  W.E.F.: 01/01/2026",
						infoFont);
				infoLine.setAlignment(Element.ALIGN_CENTER);
				infoLine.setSpacingAfter(4f);
				doc.add(infoLine);
				doc.add(new Chunk(new com.lowagie.text.pdf.draw.LineSeparator()));

				// ── Timetable table (Days rows × slot columns) ───────────────────────
				PdfPTable table = new PdfPTable(cols);
				table.setWidthPercentage(100);
				table.setWidths(widths);
				table.setSpacingBefore(6f);
				table.setHeaderRows(1); // column headers repeat on every new page

				// Column header row
				PdfPCell dayH = new PdfPCell(new Phrase("Days", headerFont));
				dayH.setHorizontalAlignment(Element.ALIGN_CENTER);
				dayH.setVerticalAlignment(Element.ALIGN_MIDDLE);
				dayH.setPadding(5f);
				dayH.setBackgroundColor(headerBg);
				table.addCell(dayH);
				for (int i = 0; i < slotLabels.size(); i++) {
					PdfPCell h = new PdfPCell(new Phrase(slotLabels.get(i), headerFont));
					h.setHorizontalAlignment(Element.ALIGN_CENTER);
					h.setVerticalAlignment(Element.ALIGN_MIDDLE);
					h.setPadding(5f);
					h.setBackgroundColor(slots.get(i).isBreak() ? breakBg : headerBg);
					table.addCell(h);
				}

				// Day rows
				for (String day : days) {
					PdfPCell dayCell = new PdfPCell(new Phrase(day, bodyFont));
					dayCell.setHorizontalAlignment(Element.ALIGN_CENTER);
					dayCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
					dayCell.setPadding(5f);
					table.addCell(dayCell);

					java.util.Set<Integer> skipIndices = new java.util.HashSet<>();
					for (int i = 0; i < slots.size(); i++) {
						if (skipIndices.contains(i)) continue;

						SlotDef ts = slots.get(i);
						String slotLabel = slotLabels.get(i);

						if (ts.isBreak()) {
							PdfPCell breakCell = new PdfPCell(new Phrase(getBreakLabel(ts), headerFont));
							breakCell.setHorizontalAlignment(Element.ALIGN_CENTER);
							breakCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
							breakCell.setPadding(6f);
							breakCell.setBackgroundColor(breakBg);
							table.addCell(breakCell);
							continue;
						}

						final String slot24hr = slots.get(i).start() + " - " + slots.get(i).end();
						List<Timetable> matches = list.stream()
								.filter(t -> normalizeKey(t.getDay()).equals(normalizeKey(day)))
								.filter(t -> normalizeKey(t.getTimeSlot()).equals(normalizeKey(slotLabel))
										|| normalizeKey(t.getTimeSlot()).equals(normalizeKey(slot24hr)))
								.toList();

						if (matches.isEmpty()) {
							PdfPCell empty = new PdfPCell(new Phrase("-", bodyFont));
							empty.setHorizontalAlignment(Element.ALIGN_CENTER);
							empty.setVerticalAlignment(Element.ALIGN_MIDDLE);
							empty.setPadding(6f);
							table.addCell(empty);
							continue;
						}

						// colspan: merge consecutive slots that carry identical content (2-hr labs)
						int colspan = 1;
						for (int j = i + 1; j < slots.size() && !slots.get(j).isBreak(); j++) {
							final String nxtLabel  = slotLabels.get(j);
							final String nxtSlot24 = slots.get(j).start() + " - " + slots.get(j).end();
							List<Timetable> nxtMatches = list.stream()
									.filter(t -> normalizeKey(t.getDay()).equals(normalizeKey(day)))
									.filter(t -> normalizeKey(t.getTimeSlot()).equals(normalizeKey(nxtLabel))
											|| normalizeKey(t.getTimeSlot()).equals(normalizeKey(nxtSlot24)))
									.toList();
							if (isSameContent(matches, nxtMatches)) {
								colspan++;
								skipIndices.add(j);
							} else {
								break;
							}
						}

						// Build cell paragraph
						com.lowagie.text.Paragraph cellPara = new com.lowagie.text.Paragraph();
						cellPara.setLeading(12f);
						boolean anyPractical = false;
						boolean hasBatches = matches.stream()
								.anyMatch(t -> t.getBatch() != null && !t.getBatch().isBlank());
						for (int idx = 0; idx < matches.size(); idx++) {
							Timetable t = matches.get(idx);
							if (idx > 0) cellPara.add(com.lowagie.text.Chunk.NEWLINE);
							if (hasBatches && t.getBatch() != null && !t.getBatch().isBlank())
								cellPara.add(new com.lowagie.text.Chunk(t.getBatch() + ": ", bodyFont));
							if (t.getSubject() != null && !t.getSubject().isBlank())
								cellPara.add(new com.lowagie.text.Chunk(t.getSubject(), bodyFont));
							if (t.getFaculty() != null && !t.getFaculty().isBlank())
								cellPara.add(new com.lowagie.text.Chunk(
										" (" + toInitials(t.getFaculty()) + ")", bodyFont));
							if (isPracticalSlot(t.getLectureType(), t.getSubject())) anyPractical = true;
						}

						PdfPCell detail = new PdfPCell(cellPara);
						detail.setPadding(6f);
						detail.setColspan(colspan);
						boolean allSpecial = matches.stream().allMatch(t -> "Special".equals(t.getLectureType()));
						if (allSpecial) {
							detail.setBackgroundColor(new java.awt.Color(255, 243, 205));
							detail.setHorizontalAlignment(Element.ALIGN_CENTER);
							detail.setVerticalAlignment(Element.ALIGN_MIDDLE);
						} else if (anyPractical) {
							detail.setBackgroundColor(new java.awt.Color(232, 246, 255));
						}
						table.addCell(detail);
					}
				}
				doc.add(table);

				// ── Per-page footer ──────────────────────────────────────────────────
				Paragraph note = new Paragraph(
						"* Break slots are shaded. Practical cells show batch labels (S1/S2/T1/T2 etc). Multiple entries stacked line by line.",
						noteFont);
				note.setSpacingBefore(6f);
				doc.add(note);

				PdfPTable signTable = new PdfPTable(3);
				signTable.setWidthPercentage(100);
				signTable.setWidths(new float[]{33f, 34f, 33f});
				signTable.setSpacingBefore(16f);
				for (int s = 0; s < 3; s++) {
					PdfPCell sc = new PdfPCell();
					sc.setBorder(Rectangle.NO_BORDER);
					sc.setPaddingTop(4f);
					PdfPTable sigLine = new PdfPTable(1);
					sigLine.setWidthPercentage(60f);
					sigLine.setHorizontalAlignment(s == 0 ? Element.ALIGN_LEFT
							: s == 1 ? Element.ALIGN_CENTER : Element.ALIGN_RIGHT);
					PdfPCell sigCell = new PdfPCell(new Phrase(" "));
					sigCell.setBorder(Rectangle.BOTTOM);
					sigCell.setBorderWidthBottom(1f);
					sigCell.setBorderColorBottom(new java.awt.Color(0, 0, 0));
					sigCell.setPaddingBottom(6f);
					sigLine.addCell(sigCell);
					sc.addElement(sigLine);
					com.lowagie.text.Paragraph nameP = new com.lowagie.text.Paragraph(signLabels[s], signFont);
					nameP.setAlignment(s == 0 ? Element.ALIGN_LEFT : s == 1 ? Element.ALIGN_CENTER : Element.ALIGN_RIGHT);
					sc.addElement(nameP);
					com.lowagie.text.Paragraph roleP = new com.lowagie.text.Paragraph(signRoles[s], metaFont);
					roleP.setAlignment(s == 0 ? Element.ALIGN_LEFT : s == 1 ? Element.ALIGN_CENTER : Element.ALIGN_RIGHT);
					sc.addElement(roleP);
					signTable.addCell(sc);
				}
				doc.add(signTable);
			}

			doc.close();
		} catch (Exception ex) {
			doc.close();
			return ResponseEntity.internalServerError().build();
		}

		byte[] pdf = baos.toByteArray();
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_PDF);
		headers.setContentDispositionFormData("attachment", "timetable.pdf");
		headers.setContentLength(pdf.length);
		return ResponseEntity.ok().headers(headers).body(pdf);
	}

	/**
	 * Returns true if two slot's timetable entries have identical
	 * subject+faculty+batch combinations — used to detect mergeable consecutive
	 * practical cells.
	 */
	private boolean isSameContent(List<Timetable> a, List<Timetable> b) {
		if (a.size() != b.size())
			return false;
		if (a.isEmpty())
			return false;
		java.util.Set<String> keysA = a.stream()
				.map(t -> safeLower(t.getSubject()) + "|" + safeLower(t.getFaculty()) + "|" + safeLower(t.getBatch()))
				.collect(java.util.stream.Collectors.toSet());
		java.util.Set<String> keysB = b.stream()
				.map(t -> safeLower(t.getSubject()) + "|" + safeLower(t.getFaculty()) + "|" + safeLower(t.getBatch()))
				.collect(java.util.stream.Collectors.toSet());
		return keysA.equals(keysB);
	}

	private String safeLower(String val) {
		return val == null ? "" : val.toLowerCase();
	}

	private String getBreakLabel(SlotDef ts) {
		int dur = durationMinutes(ts);
		if (dur >= 45)
			return "LUNCH BREAK";
		if (dur >= 15)
			return "SHORT BREAK";
		return "BREAK";
	}

	private List<Timeslot> insertBreakSlots(List<Timeslot> slots) {
		if (slots == null || slots.size() < 2)
			return slots;
		List<Timeslot> sorted = slots.stream().sorted((a, b) -> {
			Integer sa = parseSlotStart(a.getStartTime());
			Integer sb = parseSlotStart(b.getStartTime());
			if (sa == null && sb == null)
				return 0;
			if (sa == null)
				return 1;
			if (sb == null)
				return -1;
			return Integer.compare(sa, sb);
		}).toList();
		List<Timeslot> result = new java.util.ArrayList<>();
		for (int i = 0; i < sorted.size(); i++) {
			Timeslot cur = sorted.get(i);
			result.add(cur);
			if (i == sorted.size() - 1)
				break;
			Timeslot next = sorted.get(i + 1);
			Integer curEnd = parseSlotEnd(cur.getEndTime());
			Integer nextStart = parseSlotStart(next.getStartTime());
			if (curEnd != null && nextStart != null) {
				int gap = nextStart - curEnd;
				if (gap >= 15) { // insert break if 15+ minutes gap
					Timeslot br = new Timeslot();
					br.setStartTime(cur.getEndTime());
					br.setEndTime(next.getStartTime());
					br.setBreak(true);
					result.add(br);
				}
			}
		}
		return result;
	}

	private String buildClassLabel(Timetable t) {
		String base = (t.getClassName() != null && !t.getClassName().isBlank()) ? t.getClassName().trim() : "Class";
		if (t.getDivision() != null && !t.getDivision().isBlank()) {
			return base + " - " + t.getDivision().trim();
		}
		return base;
	}

	private String computeAcademicYearLabel() {
		LocalDate today = LocalDate.now();
		int startYear = (today.getMonthValue() >= 7) ? today.getYear() : today.getYear() - 1;
		int endYear = startYear + 1;
		String endShort = String.valueOf(endYear).substring(2);
		return startYear + "-" + endShort;
	}

	private void addYearSpan(PdfPTable table, String label, Font headerFont) {
		PdfPCell span = new PdfPCell(new Phrase(label, headerFont));
		span.setColspan(2);
		span.setHorizontalAlignment(Element.ALIGN_CENTER);
		span.setVerticalAlignment(Element.ALIGN_MIDDLE);
		span.setPadding(6f);
		table.addCell(span);
	}

	private void addYearSubHeaders(PdfPTable table, Font headerFont) {
		for (int i = 0; i < 4; i++) {
			PdfPCell th = new PdfPCell(new Phrase("TH", headerFont));
			th.setHorizontalAlignment(Element.ALIGN_CENTER);
			th.setVerticalAlignment(Element.ALIGN_MIDDLE);
			th.setPadding(6f);
			table.addCell(th);

			PdfPCell pr = new PdfPCell(new Phrase("PR", headerFont));
			pr.setHorizontalAlignment(Element.ALIGN_CENTER);
			pr.setVerticalAlignment(Element.ALIGN_MIDDLE);
			pr.setPadding(6f);
			table.addCell(pr);
		}
	}

	private PdfPCell yearCell(FacultyLoad load, int year, String type, Font font) {
		String text = load.getEntries(year, type);
		PdfPCell cell = new PdfPCell(new Phrase(text.isBlank() ? "-" : text, font));
		cell.setPadding(5f);
		return cell;
	}

	private int extractSemester(String className) {
		if (className == null)
			return 0;
		String digits = className.replaceAll("[^0-9]", "");
		if (digits.isEmpty())
			return 0;
		try {
			return Integer.parseInt(digits);
		} catch (NumberFormatException ex) {
			return 0;
		}
	}

	private int mapYearFromSemester(int sem) {
		if (sem == 1 || sem == 2)
			return 1;
		if (sem == 3 || sem == 4)
			return 2;
		if (sem == 5 || sem == 6)
			return 3;
		if (sem >= 7)
			return 4;
		return 1;
	}

	private boolean isPracticalSlot(String lectureType, String subject) {
		String lt = lectureType != null ? lectureType.toLowerCase() : "";
		String sub = subject != null ? subject.toLowerCase() : "";
		return lt.contains("lab") || lt.contains("pr") || sub.contains("lab");
	}

	/**
	 * Converts a full faculty name to initials. e.g. "Prof. R. A. Nikam" → "R.A.N."
	 */
	private String toInitials(String fullName) {
		if (fullName == null || fullName.isBlank())
			return "";
		// strip common honorific prefixes
		String cleaned = fullName.replaceAll("(?i)\\b(prof\\.?|dr\\.?|mr\\.?|mrs\\.?|ms\\.?)\\s*", "").trim();
		String[] parts = cleaned.split("[\\s.]+");
		StringBuilder initials = new StringBuilder();
		for (String part : parts) {
			if (!part.isBlank()) {
				initials.append(Character.toUpperCase(part.charAt(0))).append(".");
			}
		}
		return initials.toString();
	}

	/** Parse semester input like "SEM 5" or "5" to integer, null on failure */
	private Integer parseSemester(String sem) {
		if (sem == null)
			return null;
		String digits = sem.replaceAll("[^0-9]", "");
		if (digits.isEmpty())
			return null;
		try {
			return Integer.parseInt(digits);
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	/** Check username uniqueness with optional self exclusion */
	private boolean isUsernameTaken(String username, Integer excludeId) {
		if (username == null || username.isBlank())
			return false;
		User existing = userRepo.findByUsernameIgnoreCase(username.trim());
		return existing != null && (excludeId == null || existing.getId() != excludeId);
	}

	private String safeListVal(List<String> list, int idx) {
		if (list == null || idx >= list.size() || idx < 0)
			return "";
		String v = list.get(idx);
		return v == null ? "" : v.trim();
	}

	private Integer parseIntSafe(String v) {
		if (v == null || v.isBlank())
			return null;
		try {
			return Integer.parseInt(v);
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	private List<String> getWorkingDays() {
		AcademicSetting setting = academicSettingRepo.findById(1).orElse(null);
		if (setting == null || setting.getWorkingDays() == null || setting.getWorkingDays().isBlank()) {
			return java.util.Arrays.asList("Monday", "Tuesday", "Wednesday", "Thursday", "Friday");
		}
		return java.util.Arrays.stream(setting.getWorkingDays().split(",")).map(String::trim).filter(s -> !s.isBlank())
				.toList();
	}

	private List<Timeslot> getEffectiveSlots() {
		List<Timeslot> slots = timeRepo.findAll();
		if (slots != null && slots.size() >= 1) {
			return slots.stream().sorted(java.util.Comparator.comparingInt(
					ts -> parseSlotStart(ts.getStartTime()) == null ? Integer.MAX_VALUE : parseSlotStart(ts.getStartTime())
			)).toList();
		}
		String[][] defaults = { { "09:00", "10:00" }, { "10:00", "11:00" }, { "11:00", "12:00" }, { "12:00", "13:00" },
				{ "13:00", "14:00" }, { "14:00", "15:00" } };
		List<Timeslot> generated = new java.util.ArrayList<>();
		for (String[] pair : defaults) {
			Timeslot t = new Timeslot();
			t.setStartTime(pair[0]);
			t.setEndTime(pair[1]);
			generated.add(t);
		}
		return generated;
	}

	private String normalizeKey(String val) {
		return val == null ? "" : val.toLowerCase().replaceAll("\\s+", "");
	}

	private String matchDay(List<String> days, String dayVal) {
		if (dayVal == null)
			return null;
		String key = normalizeKey(dayVal);
		return days.stream().filter(d -> normalizeKey(d).equals(key)).findFirst().orElse(null);
	}

	private String matchSlot(List<String> slots, String slotVal) {
		if (slotVal == null)
			return null;
		String key = normalizeKey(slotVal);
		Integer minutes = parseSlotStart(slotVal);
		return slots.stream()
				.filter(s -> normalizeKey(s).equals(key) || (minutes != null && minutes.equals(parseSlotStart(s))))
				.findFirst().orElse(null);
	}

	private int slotStartMinutes(String slot) {
		if (slot == null)
			return Integer.MAX_VALUE;
		String[] parts = slot.split("-");
		if (parts.length == 0)
			return Integer.MAX_VALUE;
		String start = parts[0].trim();
		try {
			java.time.LocalTime t = java.time.LocalTime.parse(start);
			return t.getHour() * 60 + t.getMinute();
		} catch (Exception ex) {
			return Integer.MAX_VALUE;
		}
	}

	private String emptySafe(String v) {
		return v == null ? "" : v;
	}

	private Integer parseSlotStart(String slot) {
		if (slot == null)
			return null;
		String[] parts = slot.split("-");
		if (parts.length == 0)
			return null;
		String start = parts[0].trim().toUpperCase();
		try {
			return java.time.LocalTime.parse(start).getHour() * 60 + java.time.LocalTime.parse(start).getMinute();
		} catch (Exception ignore) {
		}
		// try with AM/PM
		try {
			java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("hh:mm a");
			return java.time.LocalTime.parse(start.replace(".", ""), fmt).getHour() * 60
					+ java.time.LocalTime.parse(start.replace(".", ""), fmt).getMinute();
		} catch (Exception ignore) {
		}
		// strip non-digits to handle 9:00AM without space
		String digits = start.replaceAll("[^0-9]", "");
		if (digits.length() >= 3) {
			try {
				int hour = Integer.parseInt(digits.substring(0, digits.length() == 3 ? 1 : 2));
				int minute = Integer.parseInt(digits.substring(digits.length() == 3 ? 1 : 2, digits.length()));
				return hour * 60 + minute;
			} catch (Exception ignore) {
			}
		}
		return null;
	}

	private Integer parseSlotEnd(String end) {
		if (end == null)
			return null;
		String val = end.trim().toUpperCase();
		try {
			return java.time.LocalTime.parse(val).getHour() * 60 + java.time.LocalTime.parse(val).getMinute();
		} catch (Exception ignore) {
		}
		try {
			java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("hh:mm a");
			java.time.LocalTime t = java.time.LocalTime.parse(val.replace(".", ""), fmt);
			return t.getHour() * 60 + t.getMinute();
		} catch (Exception ignore) {
		}
		String digits = val.replaceAll("[^0-9]", "");
		if (digits.length() >= 3) {
			try {
				int hour = Integer.parseInt(digits.substring(0, digits.length() == 3 ? 1 : 2));
				int minute = Integer.parseInt(digits.substring(digits.length() == 3 ? 1 : 2, digits.length()));
				return hour * 60 + minute;
			} catch (Exception ignore) {
			}
		}
		return null;
	}

	private int durationMinutes(SlotDef ts) {
		Integer s = parseSlotStart(ts.start());
		Integer e = parseSlotEnd(ts.end());
		if (s == null || e == null)
			return 0;
		return Math.max(0, e - s);
	}

// legacy helper for Timeslot-based callers
	private int durationMinutes(Timeslot ts) {
		Integer s = parseSlotStart(ts.getStartTime());
		Integer e = parseSlotEnd(ts.getEndTime());
		if (s == null || e == null)
			return 0;
		return Math.max(0, e - s);
	}

	private List<SlotDef> getExportSlots() {
		List<Timeslot> stored = timeRepo.findAll();
		if (stored != null && !stored.isEmpty()) {
			return stored
					.stream().sorted(
							java.util.Comparator
									.comparingInt(
											ts -> parseSlotStart(ts.getStartTime()) == null ? Integer.MAX_VALUE
													: parseSlotStart(ts.getStartTime())))
					.map(ts -> new SlotDef(
							(ts.getStartTime() != null ? ts.getStartTime() : "") + " - "
									+ (ts.getEndTime() != null ? ts.getEndTime() : ""),
							ts.getStartTime(), ts.getEndTime(), ts.isBreak()))
					.toList();
		}
		// fallback defaults
		return java.util.List.of(new SlotDef("09:00 AM - 10:00 AM", "09:00", "10:00", false),
				new SlotDef("10:00 AM - 11:00 AM", "10:00", "11:00", false),
				new SlotDef("11:00 AM - 11:15 AM (SHORT BREAK)", "11:00", "11:15", true),
				new SlotDef("11:15 AM - 12:15 PM", "11:15", "12:15", false),
				new SlotDef("12:15 PM - 01:15 PM", "12:15", "13:15", false),
				new SlotDef("01:15 PM - 02:00 PM (LONG BREAK)", "13:15", "14:00", true),
				new SlotDef("02:00 PM - 03:00 PM", "14:00", "15:00", false),
				new SlotDef("03:00 PM - 04:00 PM", "15:00", "16:00", false),
				new SlotDef("04:00 PM - 05:00 PM", "16:00", "17:00", false));
	}

	private record SlotDef(String label, String start, String end, boolean isBreak) {
	}

	private List<Timetable> filterTimetables(String department, String className) {
		List<Timetable> list = timetableRepo.findByDeletedFalse();
		if (department != null && !department.isBlank()) {
			String deptKey = department.trim().toLowerCase();
			list = list.stream().filter(t -> t.getDepartment() != null && matchesDept(t.getDepartment(), deptKey))
					.toList();
		}
		if (className != null && !className.isBlank()) {
			String classKey = className.trim().toLowerCase();
			list = list.stream()
					.filter(t -> t.getClassName() != null && t.getClassName().toLowerCase().contains(classKey))
					.toList();
		}
		return list;
	}

	private boolean matchesDept(String department, String filterKey) {
		String dep = department.toLowerCase();
		return dep.equals(filterKey) || dep.contains(filterKey);
	}

	private static class FacultyLoad {
		private final Map<Integer, Map<String, Map<String, Integer>>> data = new LinkedHashMap<>();
		private int totalHours = 0;

		void add(int year, String type, String subject) {
			Map<String, Map<String, Integer>> byType = data.computeIfAbsent(year, k -> new LinkedHashMap<>());
			Map<String, Integer> subjMap = byType.computeIfAbsent(type, k -> new LinkedHashMap<>());
			subjMap.put(subject, subjMap.getOrDefault(subject, 0) + 1);
			totalHours++;
		}

		String getEntries(int year, String type) {
			Map<String, Map<String, Integer>> byType = data.get(year);
			if (byType == null)
				return "";
			Map<String, Integer> subjMap = byType.get(type);
			if (subjMap == null || subjMap.isEmpty())
				return "";
			return subjMap.entrySet().stream().map(e -> e.getKey() + " (" + e.getValue() + ")")
					.collect(Collectors.joining("\n"));
		}
	}

}