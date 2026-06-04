package com.nt.service;

import com.nt.entity.AcademicSetting;
import com.nt.entity.Division;
import com.nt.entity.Subject;
import com.nt.entity.Timetable;
import com.nt.repository.AcademicSettingRepository;
import com.nt.repository.DivisionRepository;
import com.nt.repository.SubjectRepository;
import com.nt.repository.TimetableRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Exact layout replication of IT.xlsx MASTER sheet.
 * Column widths, row heights, merge ranges, font sizes, header/footer structure
 * all match IT.xlsx reference precisely.
 */
@Service
public class TimetableXlsxExportService {

    @Autowired private TimetableRepository    timetableRepo;
    @Autowired private SubjectRepository      subjectRepo;
    @Autowired private DivisionRepository     divisionRepo;
    @Autowired private AcademicSettingRepository academicSettingRepo;

    /* ── DB slot keys ── */
    static final String[] SLOTS = {
        "09:00 - 10:00","10:00 - 11:00",
        "11:15 - 12:15","12:15 - 13:15",
        "14:00 - 15:00","15:00 - 16:00",
        "16:00 - 17:00"
    };
    static final String[] DAYS    = {"Monday","Tuesday","Wednesday","Thursday","Friday"};
    static final String[] CLASSES = {"SE(IT)","TE(IT)","BE(IT)"};
    static final int[][] WIN_PAIRS = {{0,1},{2,3},{4,5}};

    /* CLASS/MASTER columns (0-based):  C=2,D=3,E=4,F=5,G=6,H=7,I=8,J=9,K=10,L=11,M=12 */
    static final int CC_DAY=2, CC_CLS=3, CC_BRK=6, CC_LNG=9, CC_NCOLS=13;
    static final int[] CC_SCOL = {4,5,7,8,10,11,12};

    /* FACULTY/ROOM columns: C=2=Day, D=3..L=11 */
    static final int FC_DAY=2, FC_BRK=5, FC_LNG=8, FC_NCOLS=12;
    static final int[] FC_SCOL = {3,4,6,7,9,10,11};

    /* ── MASTER: header rows 0-6 + per-class data row heights ── */
    // Row 0=institution(bordered), 1=Record/DoI(bordered), 2=Revision(bordered),
    // 3=titleBar(gray), 4=AY/Dept/Sem, 5=WEF, 6=colHeaders → data at row 7
    static final float[] HDR_HT   = {48.0f,16.0f,14.0f,18.0f,28.0f,18.0f,42.0f};
    static final float[] SE_HT    = {40.0f,32.0f,32.0f,40.0f,32.0f};
    static final float[] TE_HT    = {50.0f,32.0f,32.0f,40.0f,40.0f};
    static final float[] BE_HT    = {40.0f,40.0f,40.0f,40.0f,30.0f};
    static final float   SEP1_HT  = 10.0f;
    static final float   SEP2_HT  = 10.0f;

    /* ── CLASS sheet: 7 header rows + data row heights ── */
    // Row 0=institution, 1=Record/DoI, 2=Revision, 3=titleBar,
    // 4=Dept/AY/Sem, 5=WEF, 6=colHeaders → data at row 7
    static final float[] CLASS_HDR_HT = {48.0f,16.0f,14.0f,18.0f,16.0f,16.0f,42.0f};
    static final float[] SE_CLASS_HT  = {40.0f,32.0f,32.0f,40.0f,32.0f};
    static final float[] TE_CLASS_HT  = {50.0f,32.0f,32.0f,40.0f,40.0f};
    static final float[] BE_CLASS_HT  = {40.0f,40.0f,40.0f,40.0f,30.0f};

    /* ── FACULTY sheet: 8 header rows + data row heights ── */
    // Row 0=institution, 1=Record/DoI, 2=Revision, 3=titleBar,
    // 4=Dept/Sem, 5=WEF, 6=FacultyName, 7=colHeaders → data at row 8
    static final float[] FAC_HDR_HT   = {48.0f,16.0f,14.0f,18.0f,14.0f,14.0f,14.0f,42.0f};
    static final float[] FAC_DATA_HT  = {32.0f,32.0f,32.0f,36.0f,36.0f};

    /* ── MASTER column widths — print-optimised (A4 landscape) ── */
    static final double[] COL_WIDTHS = {
        0,     // A  hidden spacer
        0,     // B  hidden spacer
        12.0,  // C  Day
        10.0,  // D  Class
        18.0,  // E  09-10
        22.0,  // F  10-11
        8.0,   // G  SHORT BREAK
        20.0,  // H  11:15-12:15
        22.0,  // I  12:15-13:15
        8.0,   // J  LONG BREAK
        20.0,  // K  14-15
        22.0,  // L  15-16
        18.0,  // M  16-17
        0,     // N  hidden margin
    };

    /* ── CLASS sheet column widths — print-optimised ── */
    static final double[] CLASS_COL_WIDTHS = {
        0,     // A  hidden spacer
        0,     // B  hidden spacer
        12.0,  // C  Day/Time
        8.0,   // D  Class (hidden on individual sheets)
        20.0,  // E  09-10
        24.0,  // F  10-11
        8.0,   // G  SHORT BREAK
        22.0,  // H  11:15-12:15
        24.0,  // I  12:15-13:15
        8.0,   // J  LONG BREAK
        22.0,  // K  14-15
        24.0,  // L  15-16
        20.0,  // M  16-17
        0,     // N  hidden margin
    };

    /* ── FACULTY sheet column widths — print-optimised ── */
    static final double[] FAC_COL_WIDTHS = {
        0,     // A  hidden spacer
        0,     // B  hidden spacer
        12.0,  // C  Day
        18.0,  // D  09-10
        18.0,  // E  10-11
        8.0,   // F  SHORT BREAK
        18.0,  // G  11:15-12:15
        18.0,  // H  12:15-13:15
        8.0,   // I  LONG BREAK
        18.0,  // J  14-15
        18.0,  // K  15-16
        18.0,  // L  16-17
        0,     // M  hidden margin
    };

    /* ── LAB/ROOM sheet column widths — print-optimised ── */
    static final double[] LAB_COL_WIDTHS = {
        0,     // A  hidden spacer
        0,     // B  hidden spacer
        12.0,  // C  Day
        18.0,  // D  09-10
        18.0,  // E  10-11
        8.0,   // F  SHORT BREAK
        18.0,  // G  11:15-12:15
        18.0,  // H  12:15-13:15
        8.0,   // I  LONG BREAK
        18.0,  // J  14-15
        18.0,  // K  15-16
        14.0,  // L  16-17
        0,     // M  hidden margin
    };

    /* ── Subject code display overrides ── */
    static final Map<String,String> CODE_DISP;
    static {
        Map<String,String> m = new LinkedHashMap<>();
        m.put("PAE","PA"); m.put("OE2","OE-II"); m.put("EL2","EL-II");
        m.put("EL5","EL-V"); m.put("EL6","EL-VI"); m.put("LP5","LP-V");
        m.put("LP6","LP-VI"); m.put("PS2","PS-II"); m.put("DS8","DS");
        m.put("SE8","SE"); m.put("PS","P & S");
        m.put("AC4","AUDIT COURSE-IV"); m.put("AC6","AUDIT COURSE-VI");
        m.put("AC8","AUDIT COURSE-VIII"); m.put("SEM8","SEMINAR(Hon)");
        m.put("TP8","T & P");
        m.put("VL4","Virtual Lab/ Spoken Tutorial");
        m.put("VL6","Virtual Lab/ Spoken Tutorial");
        m.put("VL8","Virtual Lab/ Spoken Tutorial");
        m.put("INTP","INTERNSHIP"); m.put("LIBSE","LIBRARY"); m.put("LIBTE","LIBRARY");
        m.put("EACH","EAC Hon"); m.put("ISMH","ISM Hon");
        m.put("DMSL","DM&SM"); m.put("MILL","MIL");
        CODE_DISP = Collections.unmodifiableMap(m);
    }

    static final Set<String> NO_FAC = Set.of(
        "PS-II","LIBRARY","INTERNSHIP","Virtual Lab/ Spoken Tutorial",
        "AUDIT COURSE-IV","AUDIT COURSE-VI","AUDIT COURSE-VIII"
    );

    /* Default fallback values (used when AcademicSetting fields are null) */
    static final String DEF_INST_FULL  =
        "Akhil Bharatiya Maratha Shikshan Parishad's\n" +
        "Anantrao Pawar college of Engineering & Research, Parvati, Pune";
    static final String DEF_INST_SHORT =
        "Akhil Bharatiya Maratha Shikshan Parishad's\n" +
        "Anantrao Pawar college of Engineering & Research";
    static final String DEF_DEPT      = "Information Technology";
    static final String DEF_AY        = "2025-26";
    static final String DEF_SEM       = "II";
    static final String DEF_WEF       = "01/01/2026";
    static final String DEF_DOI       = "01/02/2025";
    static final String DEF_REV       = "00";
    static final String DEF_REC_M     = "ACA/R/003A";
    static final String DEF_REC_C     = "ACA/R/003B";
    static final String DEF_REC_L     = "ACA/R/003D";
    static final String DEF_REC_F     = "ACA/R/003E";
    static final String DEF_TTC       = "PROF. R. A. NIKAM";
    static final String DEF_HOD       = "DR. A. A. KADAM";
    static final String DEF_PRINCIPAL = "DR. S. B. THAKARE";
    static final String DEF_CLG_LOGO  = "";   // falls back to bundled classpath image
    static final String DEF_OWN_LOGO  = "";   // falls back to bundled classpath image

    /* ═══════════════════════════ PUBLIC ENTRY ═══════════════════════════════ */

    public byte[] generate() throws IOException {
        List<Timetable> all = timetableRepo.findByDeletedFalse();
        Map<String,String> nc = nameToCode(subjectRepo.findAll());
        List<Division> divisions = divisionRepo.findAll();
        AcademicSetting cfg = academicSettingRepo.findById(1).orElseGet(AcademicSetting::new);

        XSSFWorkbook wb = new XSSFWorkbook();
        Styles st = new Styles(wb);

        byte[] clgBytes   = readLogo(cfg.getCollegeLogo());
        byte[] ownerBytes = readOwnerLogo(cfg.getOwnerLogo());

        /* Class sheets */
        for (String cls : CLASSES)
            writeClassSheet(wb, st, cls, filter(all,cls), nc, clgBytes, ownerBytes, divisions, cfg);

        /* Master sheet */
        writeMasterSheet(wb, st, all, nc, clgBytes, ownerBytes, divisions, cfg);

        /* Faculty sheets (skip Library Coordinator) */
        all.stream()
            .filter(t -> nb(t.getFaculty()) && !"Library Coordinator".equalsIgnoreCase(t.getFaculty()))
            .collect(Collectors.groupingBy(Timetable::getFaculty))
            .entrySet().stream().sorted(Map.Entry.comparingByKey())
            .forEach(e -> writeFacSheet(wb, st, e.getKey(), e.getValue(), nc, clgBytes, ownerBytes, cfg));

        /* Room sheets */
        all.stream()
            .filter(t -> nb(t.getRoom()))
            .collect(Collectors.groupingBy(Timetable::getRoom))
            .entrySet().stream().sorted(Map.Entry.comparingByKey())
            .forEach(e -> writeRoomSheet(wb, st, e.getKey(), e.getValue(), nc, clgBytes, ownerBytes, cfg));

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        wb.write(bos); wb.close();
        return bos.toByteArray();
    }

    /* ═══════════════════════════ SHEET WRITERS ══════════════════════════════ */

    private void writeClassSheet(XSSFWorkbook wb, Styles st, String cls,
                                  List<Timetable> rows, Map<String,String> nc,
                                  byte[] clgLogo, byte[] ownerLogo,
                                  List<Division> divisions, AcademicSetting cfg) {
        XSSFSheet sh = wb.createSheet(cls.replace("(","_").replace(")",""));
        int hi = classHeader(sh, wb, st, cls, clgLogo, ownerLogo, cfg);
        float[] heights = classSheetRowHeights(cls);
        int dataEnd = hi + DAYS.length - 1;
        writeClassRows(sh, st, hi, cls, buildGrid(rows), nc, heights, CC_SCOL);
        mergeBreaks(sh, hi, dataEnd, CC_BRK, CC_LNG);
        applyColWidths(sh, CLASS_COL_WIDTHS);
        // Hide spacer/redundant columns so PDF shows only timetable content
        sh.setColumnHidden(0, true);      // A
        sh.setColumnHidden(1, true);      // B
        sh.setColumnHidden(CC_CLS, true); // D — Class label (redundant on individual sheet)
        sh.setColumnHidden(13, true);     // N — right margin
        applyPageSetup(sh);
        classFooter(sh, st, cls, rows, nc, hi + DAYS.length, divisions, cfg);
        sh.createFreezePane(0, hi + 1);
    }

    private void writeMasterSheet(XSSFWorkbook wb, Styles st, List<Timetable> all,
                                   Map<String,String> nc, byte[] clgLogo, byte[] ownerLogo,
                                   List<Division> divisions, AcademicSetting cfg) {
        XSSFSheet sh = wb.createSheet("MASTER");

        /* ── header rows 0-7 ── */
        int hi = masterHeader(sh, wb, st, clgLogo, ownerLogo, cfg);
        // hi = 8 (data starts at row 8)

        /* ── SE-IT rows 8-12 ── */
        writeClassRows(sh, st, hi, "SE(IT)", buildGrid(filter(all,"SE(IT)")), nc, SE_HT, CC_SCOL);
        /* ── separator row 13 ── */
        int sep1 = hi + 5;
        writeSeparatorRow(sh, st, sep1, SEP1_HT);

        /* ── TE-IT rows 14-18 ── */
        int teStart = sep1 + 1;
        writeClassRows(sh, st, teStart, "TE(IT)", buildGrid(filter(all,"TE(IT)")), nc, TE_HT, CC_SCOL);
        int teEnd = teStart + 4;

        /* ── separator row 19 ── */
        int sep2 = teEnd + 1;
        writeSeparatorRow(sh, st, sep2, SEP2_HT);

        /* ── BE-IT rows 20-24 ── */
        int beStart = sep2 + 1;
        writeClassRows(sh, st, beStart, "BE(IT)", buildGrid(filter(all,"BE(IT)")), nc, BE_HT, CC_SCOL);
        int beEnd = beStart + 4;

        /* ── break column vertical merges ── */
        // G9:G25 and J9:J25 in Excel = rows 8-24 in POI
        mergeBreaks(sh, hi, beEnd, CC_BRK, CC_LNG);

        /* ── footer ── */
        masterFooter(sh, st, beEnd + 1, divisions, cfg);

        applyColWidths(sh, COL_WIDTHS);
        sh.setColumnHidden(0, true); sh.setColumnHidden(1, true); sh.setColumnHidden(13, true);
        applyPageSetup(sh);
        sh.createFreezePane(0, hi + 1);
    }

    private void writeFacSheet(XSSFWorkbook wb, Styles st, String fac,
                                List<Timetable> rows, Map<String,String> nc,
                                byte[] clgLogo, byte[] ownerLogo, AcademicSetting cfg) {
        XSSFSheet sh = wb.createSheet(safe("Fac_" + abbrev(fac)));
        int hi = facRoomHeader(sh, wb, st, fac, "Individual Time Table", clgLogo, ownerLogo,
                               FAC_COL_WIDTHS[FC_DAY], FAC_COL_WIDTHS[11], cfg);
        writeFacRoomRows(sh, st, hi, buildGrid(rows), nc, FC_SCOL, "fac");
        mergeBreaks(sh, hi, hi + DAYS.length - 1, FC_BRK, FC_LNG);
        applyColWidths(sh, FAC_COL_WIDTHS);
        sh.setColumnHidden(0, true); sh.setColumnHidden(1, true); sh.setColumnHidden(12, true);
        applyPageSetup(sh);
        facFooter(sh, st, fac, rows, nc, hi + DAYS.length, cfg);
        sh.createFreezePane(0, hi + 1);
    }

    private void writeRoomSheet(XSSFWorkbook wb, Styles st, String room,
                                 List<Timetable> rows, Map<String,String> nc,
                                 byte[] clgLogo, byte[] ownerLogo, AcademicSetting cfg) {
        XSSFSheet sh = wb.createSheet(safe(roomAbbrev(room)));
        int hi = facRoomHeader(sh, wb, st, room, "Laboratory Timetable", clgLogo, ownerLogo,
                               LAB_COL_WIDTHS[FC_DAY], LAB_COL_WIDTHS[11], cfg);
        writeFacRoomRows(sh, st, hi, buildGrid(rows), nc, FC_SCOL, "room");
        mergeBreaks(sh, hi, hi + DAYS.length - 1, FC_BRK, FC_LNG);
        applyColWidths(sh, LAB_COL_WIDTHS);
        sh.setColumnHidden(0, true); sh.setColumnHidden(1, true); sh.setColumnHidden(12, true);
        applyPageSetup(sh);
        labFooter(sh, st, room, rows, nc, hi + DAYS.length, cfg);
        sh.createFreezePane(0, hi + 1);
    }

    /* ═══════════════════════════ HEADER BUILDERS ════════════════════════════ */

    /** Returns POI row index of first data row. */
    private int masterHeader(XSSFSheet sh, XSSFWorkbook wb, Styles st,
                              byte[] clgLogo, byte[] ownerLogo, AcademicSetting cfg) {
        String inst = nvl(cfg.getInstitutionName(), DEF_INST_FULL);
        String sem  = nvl(cfg.getSemesterDisplay(), DEF_SEM);

        // Row 0 — bordered institution name (logo overlaid as floating image)
        XSSFRow r0 = row(sh, 0, HDR_HT[0]);
        fillRow(r0, st.emptyCell, CC_NCOLS + 1);
        cell(r0, 3, st.hdrBorderC).setCellValue(inst);
        merge(sh, 0, 0, 3, 11);      // D:L — institution name
        merge(sh, 0, 2, 12, 12);     // M (right logo col, spans rows 0-2)

        // Row 1 — Record No. | DoI (bordered)
        XSSFRow r1 = row(sh, 1, HDR_HT[1]);
        fillRow(r1, st.emptyCell, CC_NCOLS + 1);
        cell(r1, 3, st.hdrBorderL).setCellValue("Record No.: " + nvl(cfg.getRecordNoMaster(), DEF_REC_M));
        cell(r1, 8, st.hdrBorderL).setCellValue("DoI: " + nvl(cfg.getDoiDate(), DEF_DOI));
        merge(sh, 1, 1, 3, 7); merge(sh, 1, 1, 8, 11);

        // Row 2 — Revision (bordered)
        XSSFRow r2 = row(sh, 2, HDR_HT[2]);
        fillRow(r2, st.emptyCell, CC_NCOLS + 1);
        cell(r2, 3, st.hdrBorderL).setCellValue("Revision: " + nvl(cfg.getRevisionNumber(), DEF_REV));
        merge(sh, 2, 2, 3, 11);

        // Row 3 — Gray title bar (full-width, bordered, gray fill)
        XSSFRow r3 = row(sh, 3, HDR_HT[3]);
        fillRow(r3, st.titleBarCell, CC_NCOLS + 1);
        cell(r3, 2, st.titleBarCell).setCellValue("Master Timetable");
        merge(sh, 3, 3, 2, 12);

        // Row 4 — A.Y. | Dept | SEM-II (plain, outside bordered table)
        XSSFRow r4 = row(sh, 4, HDR_HT[4]);
        cell(r4, 2, st.ay24b).setCellValue("A.Y. " + nvl(cfg.getAcademicYear(), DEF_AY));
        cell(r4, 4, st.dept36b).setCellValue("Department of " + nvl(cfg.getDepartmentName(), DEF_DEPT));
        cell(r4, 12, st.ay24b).setCellValue("SEM-" + sem);
        merge(sh, 4, 4, 2, 3); merge(sh, 4, 4, 4, 11);

        // Row 5 — W.E.F.
        XSSFRow r5 = row(sh, 5, HDR_HT[5]);
        cell(r5, 2, st.wef26b).setCellValue("W. E. F.: " + nvl(cfg.getWefDate(), DEF_WEF));
        merge(sh, 5, 5, 2, 12);

        // Row 6 — column headers
        colHeaderRow(sh, 6, HDR_HT[6], st, CC_SCOL, CC_BRK, CC_LNG, CC_NCOLS, true);
        sh.getRow(6).getCell(CC_DAY).setCellValue("Days");

        addLogos(sh, wb, clgLogo, ownerLogo, COL_WIDTHS[CC_DAY], 12, COL_WIDTHS[12]);
        return 7;  // data starts at row 7
    }

    /** CLASS sheet header — consistent with sample_header.png. Data starts at row 7. */
    private int classHeader(XSSFSheet sh, XSSFWorkbook wb, Styles st, String cls,
                             byte[] clgLogo, byte[] ownerLogo, AcademicSetting cfg) {
        String inst = nvl(cfg.getInstitutionName(), DEF_INST_FULL);
        String dept = nvl(cfg.getDepartmentName(), DEF_DEPT);
        String ay   = nvl(cfg.getAcademicYear(), DEF_AY);
        String sem  = nvl(cfg.getSemesterDisplay(), DEF_SEM);
        String wef  = nvl(cfg.getWefDate(), DEF_WEF);

        // Row 0 — bordered institution name (logo overlaid)
        XSSFRow r0 = row(sh, 0, CLASS_HDR_HT[0]);
        fillRow(r0, st.emptyCell, CC_NCOLS + 1);
        cell(r0, 3, st.hdrBorderC).setCellValue(inst);
        merge(sh, 0, 0, 3, 11);     // D:L — institution name
        merge(sh, 0, 2, 12, 12);    // M (right logo col, rows 0-2)

        // Row 1 — Record No. | DoI (bordered)
        XSSFRow r1 = row(sh, 1, CLASS_HDR_HT[1]);
        fillRow(r1, st.emptyCell, CC_NCOLS + 1);
        cell(r1, 3, st.hdrBorderL).setCellValue("Record No.: " + nvl(cfg.getRecordNoClass(), DEF_REC_C));
        cell(r1, 8, st.hdrBorderL).setCellValue("DoI: " + nvl(cfg.getDoiDate(), DEF_DOI));
        merge(sh, 1, 1, 3, 7); merge(sh, 1, 1, 8, 11);

        // Row 2 — Revision (bordered)
        XSSFRow r2 = row(sh, 2, CLASS_HDR_HT[2]);
        fillRow(r2, st.emptyCell, CC_NCOLS + 1);
        cell(r2, 3, st.hdrBorderL).setCellValue("Revision: " + nvl(cfg.getRevisionNumber(), DEF_REV));
        merge(sh, 2, 2, 3, 11);

        // Row 3 — Gray title bar (bordered, gray fill)
        XSSFRow r3 = row(sh, 3, CLASS_HDR_HT[3]);
        fillRow(r3, st.titleBarCell, CC_NCOLS + 1);
        cell(r3, 2, st.titleBarCell).setCellValue("Timetable");
        merge(sh, 3, 3, 2, 12);

        // Row 4 — Dept | Academic Year | Semester (plain, outside bordered table)
        XSSFRow r4 = row(sh, 4, CLASS_HDR_HT[4]);
        cell(r4, 2, st.ay24b).setCellValue("Department: " + dept);
        cell(r4, 7, st.ay24b).setCellValue("Academic Year: " + ay);
        cell(r4, 12, st.ay24b).setCellValue("Semester: " + sem);
        merge(sh, 4, 4, 2, 6); merge(sh, 4, 4, 7, 11);

        // Row 5 — W.E.F.
        XSSFRow r5 = row(sh, 5, CLASS_HDR_HT[5]);
        cell(r5, 2, st.wef26b).setCellValue("W. E. F.: " + wef);
        merge(sh, 5, 5, 2, 12);

        // Row 6 — column headers
        colHeaderRow(sh, 6, CLASS_HDR_HT[6], st, CC_SCOL, CC_BRK, CC_LNG, CC_NCOLS, true);
        addLogos(sh, wb, clgLogo, ownerLogo, CLASS_COL_WIDTHS[CC_DAY], 12, CLASS_COL_WIDTHS[12]);
        return 7;  // data starts at row 7
    }

    /** FACULTY/LAB header — consistent with sample_header.png. Data starts at row 8. */
    private int facRoomHeader(XSSFSheet sh, XSSFWorkbook wb, Styles st,
                               String name, String type,
                               byte[] clgLogo, byte[] ownerLogo,
                               double dayColW, double rightColW, AcademicSetting cfg) {
        boolean isFac = "Individual Time Table".equals(type);
        String recNo  = "Record No.: " + (isFac ? nvl(cfg.getRecordNoFaculty(), DEF_REC_F)
                                                 : nvl(cfg.getRecordNoLab(), DEF_REC_L));
        String inst   = nvl(cfg.getInstitutionNameShort(), DEF_INST_SHORT);
        String dept   = nvl(cfg.getDepartmentName(), DEF_DEPT);
        String sem    = nvl(cfg.getSemesterDisplay(), DEF_SEM);
        String wef    = nvl(cfg.getWefDate(), DEF_WEF);

        // Row 0 — bordered institution name (logos overlaid as floating images)
        XSSFRow r0 = row(sh, 0, FAC_HDR_HT[0]);
        fillRow(r0, st.emptyCell, FC_NCOLS);
        cell(r0, 3, st.hdrBorderC).setCellValue(inst);
        merge(sh, 0, 0, 3, 10);     // D:K — institution name
        merge(sh, 0, 2, 11, 11);    // L (right logo col, rows 0-2)

        // Row 1 — Record No. | DoI (bordered)
        XSSFRow r1 = row(sh, 1, FAC_HDR_HT[1]);
        fillRow(r1, st.emptyCell, FC_NCOLS);
        cell(r1, 3, st.hdrBorderL).setCellValue(recNo);
        cell(r1, 7, st.hdrBorderL).setCellValue("DoI: " + nvl(cfg.getDoiDate(), DEF_DOI));
        merge(sh, 1, 1, 3, 6); merge(sh, 1, 1, 7, 10);

        // Row 2 — Revision (bordered)
        XSSFRow r2 = row(sh, 2, FAC_HDR_HT[2]);
        fillRow(r2, st.emptyCell, FC_NCOLS);
        cell(r2, 3, st.hdrBorderL).setCellValue("Revision: " + nvl(cfg.getRevisionNumber(), DEF_REV));
        merge(sh, 2, 2, 3, 10);

        // Row 3 — Gray title bar (full width, bordered, gray fill)
        XSSFRow r3 = row(sh, 3, FAC_HDR_HT[3]);
        fillRow(r3, st.titleBarCell, FC_NCOLS);
        cell(r3, 2, st.titleBarCell).setCellValue(type);
        merge(sh, 3, 3, 2, 11);

        // Row 4 — Dept | Sem-II (plain, outside bordered table)
        XSSFRow r4 = row(sh, 4, FAC_HDR_HT[4]);
        cell(r4, 2, st.facMetaL).setCellValue("Department: " + dept);
        cell(r4, 9, st.facMetaL).setCellValue("Sem-" + sem);
        merge(sh, 4, 4, 2, 8);

        // Row 5 — W.E.F.
        XSSFRow r5 = row(sh, 5, FAC_HDR_HT[5]);
        cell(r5, 2, st.facMetaL).setCellValue("W. E. F.: " + wef);
        merge(sh, 5, 5, 2, 11);

        // Row 6 — Faculty / Lab name
        XSSFRow r6 = row(sh, 6, FAC_HDR_HT[6]);
        String cleanName = cleanFacultyName(name);
        String nameLabel = isFac ? "Name of the Faculty: Prof. " + cleanName : "Lab Name: " + name;
        cell(r6, 2, st.facMetaL).setCellValue(nameLabel);
        merge(sh, 6, 6, 2, 11);

        // Row 7 — Column headers
        colHeaderRow(sh, 7, FAC_HDR_HT[7], st, FC_SCOL, FC_BRK, FC_LNG, FC_NCOLS, false);
        addLogos(sh, wb, clgLogo, ownerLogo, dayColW, 11, rightColW);

        return 8;  // data starts at row 8
    }

    /** Strip leading honorific so we don't double up "Prof. Prof." */
    private String cleanFacultyName(String name) {
        if (name == null) return "";
        return name.replaceAll("(?i)^(prof\\.?|dr\\.?)\\s*", "").trim();
    }

    /** SE(IT) → SE, TE(IT) → TE, BE(IT) → BE; strips the (IT) suffix. */
    private String abbrevClass(String cls) {
        if (cls == null) return "—";
        return cls.replaceAll("\\(.*?\\)", "").trim();
    }

    private void colHeaderRow(XSSFSheet sh, int ri, float height, Styles st,
                               int[] scol, int brkCol, int lngCol, int ncols, boolean hasCls) {
        XSSFRow row = row(sh, ri, height);
        int ncol = ncols > 12 ? CC_NCOLS : FC_NCOLS;
        for (int c = 0; c < ncol; c++) row.createCell(c).setCellStyle(st.hdrCell);

        String[] labels = {
            "9.00 AM to 10.00 AM","10.00 AM to 11.00 AM",
            "11.15 AM to 12.15 PM","12.15 PM to 1.15 PM",
            "2.00 PM to 3.00 PM","3.00 PM to 4.00 PM","4.00 PM to 5.00 PM"
        };
        if (hasCls) {
            row.getCell(CC_DAY).setCellValue("Day/ Time");
            row.getCell(CC_CLS).setCellValue("Class");
        } else {
            row.getCell(FC_DAY).setCellValue("Day/ Time");
        }
        // SHORT BREAK header has no bottom border (matches IT.xlsx G8 border)
        row.getCell(brkCol).setCellStyle(st.brkHdrCell);
        row.getCell(brkCol).setCellValue("SHORT\nBREAK");
        row.getCell(lngCol).setCellStyle(st.lngHdrCell);
        row.getCell(lngCol).setCellValue("LONG\nBREAK");
        for (int si = 0; si < SLOTS.length; si++)
            row.getCell(scol[si]).setCellValue(labels[si]);
    }

    /* ═══════════════════════════ DATA ROW WRITERS ═══════════════════════════ */

    private void writeClassRows(XSSFSheet sh, Styles st, int startRow, String cls,
                                  Map<String,List<Timetable>[]> grid,
                                  Map<String,String> nc, float[] heights,
                                  int[] scol) {
        String clsLabel = "SE(IT)".equals(cls) ? "S.E(IT)" : cls;

        for (int di = 0; di < DAYS.length; di++) {
            int ri = startRow + di;
            List<Timetable>[] slots = grid.getOrDefault(DAYS[di], emptyArr());
            boolean[] lbFirst=new boolean[7], lbSecond=new boolean[7], lb3=new boolean[7];
            detectMerges(slots, lbFirst, lbSecond, lb3);

            float ht = (heights != null && di < heights.length) ? heights[di] : 70f;
            XSSFRow row = row(sh, ri, ht);

            // All cells default empty bordered
            int ncol = (scol == CC_SCOL) ? CC_NCOLS : FC_NCOLS;
            for (int c = 0; c < ncol; c++) row.createCell(c).setCellStyle(st.emptyCell);

            // Day label
            cell(row, CC_DAY, st.dayCell).setCellValue(DAYS[di]);

            // Class label (only first row of section has value; merge added after loop)
            cell(row, CC_CLS, st.clsCell);
            if (di == 0) row.getCell(CC_CLS).setCellValue(clsLabel);

            // Break columns (text only in first row)
            row.getCell(CC_BRK).setCellStyle(st.brkData);
            row.getCell(CC_LNG).setCellStyle(st.lngData);
            if (di == 0) {
                row.getCell(CC_BRK).setCellValue("SHORT BREAK");
                row.getCell(CC_LNG).setCellValue("LONG  BREAK");
            }

            // Data slots
            for (int si = 0; si < SLOTS.length; si++) {
                int col = scol[si];
                if (lbSecond[si]) continue; // merged second slot — keep empty
                List<Timetable> entries = slots[si];
                if (entries == null || entries.isEmpty()) continue;

                boolean isLab = isLab(entries);
                XSSFCellStyle cst = isLab ? st.labCell : st.thCell;
                row.getCell(col).setCellStyle(cst);
                row.getCell(col).setCellValue(classText(entries, nc));

                if (lbFirst[si])
                    sh.addMergedRegion(new CellRangeAddress(ri, ri, col, scol[si+1]));
                else if (lb3[si])
                    sh.addMergedRegion(new CellRangeAddress(ri, ri, col, scol[si+2]));
            }
        }
        // Class label vertical merge
        sh.addMergedRegion(
            new CellRangeAddress(startRow, startRow+DAYS.length-1, CC_CLS, CC_CLS));
    }

    private void writeFacRoomRows(XSSFSheet sh, Styles st, int startRow,
                                   Map<String,List<Timetable>[]> grid,
                                   Map<String,String> nc, int[] scol, String mode) {
        float[] avgHt = FAC_DATA_HT;
        for (int di = 0; di < DAYS.length; di++) {
            int ri = startRow + di;
            List<Timetable>[] slots = grid.getOrDefault(DAYS[di], emptyArr());
            boolean[] lbFirst=new boolean[7], lbSecond=new boolean[7], lb3=new boolean[7];
            detectMerges(slots, lbFirst, lbSecond, lb3);

            XSSFRow row = row(sh, ri, avgHt[di]);
            for (int c = 0; c < FC_NCOLS; c++) row.createCell(c).setCellStyle(st.emptyCell);
            cell(row, FC_DAY, st.dayCell).setCellValue(DAYS[di]);
            row.getCell(FC_BRK).setCellStyle(st.brkData);
            row.getCell(FC_LNG).setCellStyle(st.lngData);
            if (di == 0) {
                row.getCell(FC_BRK).setCellValue("SHORT BREAK");
                row.getCell(FC_LNG).setCellValue("LONG  BREAK");
            }

            for (int si = 0; si < SLOTS.length; si++) {
                int col = scol[si];
                if (lbSecond[si]) continue;
                List<Timetable> entries = slots[si];
                if (entries == null || entries.isEmpty()) continue;

                String txt = "fac".equals(mode) ? facText(entries, nc) : roomText(entries, nc);
                row.getCell(col).setCellStyle(isLab(entries) ? st.labCell : st.thCell);
                row.getCell(col).setCellValue(txt);

                if (lbFirst[si])
                    sh.addMergedRegion(new CellRangeAddress(ri, ri, col, scol[si+1]));
                else if (lb3[si])
                    sh.addMergedRegion(new CellRangeAddress(ri, ri, col, scol[si+2]));
            }
        }
    }

    /** Blank separator row with decorative horizontal merges matching IT.xlsx. */
    private void writeSeparatorRow(XSSFSheet sh, Styles st, int ri, float ht) {
        XSSFRow row = row(sh, ri, ht);
        for (int c = 0; c < CC_NCOLS; c++) row.createCell(c).setCellStyle(st.emptyCell);
        // Break column cells get break style (they're part of the vertical merge)
        row.getCell(CC_BRK).setCellStyle(st.brkData);
        row.getCell(CC_LNG).setCellStyle(st.lngData);
        // Decorative merges: E+F, H+I, K+L+M (matching IT.xlsx rows 14 and 20)
        sh.addMergedRegion(new CellRangeAddress(ri, ri, 4, 5));   // E:F
        sh.addMergedRegion(new CellRangeAddress(ri, ri, 7, 8));   // H:I
        sh.addMergedRegion(new CellRangeAddress(ri, ri, 10, 12)); // K:M
    }

    /** Adds break column vertical merges. */
    private void mergeBreaks(XSSFSheet sh, int first, int last, int brkCol, int lngCol) {
        if (first >= last) return;
        sh.addMergedRegion(new CellRangeAddress(first, last, brkCol, brkCol));
        sh.addMergedRegion(new CellRangeAddress(first, last, lngCol, lngCol));
    }

    /* ═══════════════════════════ FOOTER — FACULTY (Template A) ══════════════ */
    // Table cols: D(3)=SUBJECT NAME  E(4)=CLASS  G(6)=LOAD IN HOURS
    // All break columns and hidden columns avoided.
    // Signature block has NO grid border — plain text only.

    private void facFooter(XSSFSheet sh, Styles st, String facName,
                            List<Timetable> rows, Map<String,String> nc, int afterRow,
                            AcademicSetting cfg) {
        int r = afterRow;
        row(sh, r++, 10f);  // plain gap — no borders

        // Table: SUBJECT NAME(3-5) | CLASS(6-8) | LOAD IN HOURS(9-11)
        // Break cols 5(SHORT BREAK) and 8(LONG BREAK) absorbed inside merges — no visible gap
        XSSFRow hdr = row(sh, r, 22f);
        cell(hdr, 3,  st.tblHdrCell).setCellValue("SUBJECT NAME");
        cell(hdr, 4,  st.tblHdrCell); cell(hdr, 5, st.tblHdrCell);
        cell(hdr, 6,  st.tblHdrCell).setCellValue("CLASS");
        cell(hdr, 7,  st.tblHdrCell); cell(hdr, 8, st.tblHdrCell);
        cell(hdr, 9,  st.tblHdrCell).setCellValue("LOAD IN HOURS");
        cell(hdr, 10, st.tblHdrCell); cell(hdr, 11, st.tblHdrCell);
        merge(sh, r, r, 3, 5); merge(sh, r, r, 6, 8); merge(sh, r, r, 9, 11);
        r++;

        // subject → class → hour count (dedup: day+slot+subject+batch)
        Map<String, Map<String, Long>> subjClassHours = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (Timetable t : rows) {
            String dedup = s(t.getDay()) + "|" + s(t.getTimeSlot()) + "|" + s(t.getSubject()) + "|" + s(t.getBatch());
            if (!seen.add(dedup)) continue;
            String subj  = subjDisp(t, nc);
            String clazz = nb(t.getClassName()) ? t.getClassName() : "—";
            subjClassHours.computeIfAbsent(subj, k -> new LinkedHashMap<>())
                          .merge(clazz, 1L, Long::sum);
        }

        long total = 0;
        for (Map.Entry<String, Map<String, Long>> e : subjClassHours.entrySet()) {
            String subj = e.getKey();
            if (NO_FAC.contains(subj)) continue;
            for (Map.Entry<String, Long> ce : e.getValue().entrySet()) {
                XSSFRow dr = row(sh, r, 20f);
                cell(dr, 3,  st.tblDataCell).setCellValue(subj);
                cell(dr, 4,  st.tblDataCell); cell(dr, 5, st.tblDataCell);
                cell(dr, 6,  st.tblDataCell).setCellValue(abbrevClass(ce.getKey()));
                cell(dr, 7,  st.tblDataCell); cell(dr, 8, st.tblDataCell);
                cell(dr, 9,  st.tblDataCell).setCellValue(String.valueOf(ce.getValue()));
                cell(dr, 10, st.tblDataCell); cell(dr, 11, st.tblDataCell);
                merge(sh, r, r, 3, 5); merge(sh, r, r, 6, 8); merge(sh, r, r, 9, 11);
                total += ce.getValue();
                r++;
            }
        }

        // TOTAL row
        XSSFRow totRow = row(sh, r, 22f);
        cell(totRow, 3,  st.tblTotCell).setCellValue("TOTAL");
        cell(totRow, 4,  st.tblTotCell); cell(totRow, 5, st.tblTotCell);
        cell(totRow, 6,  st.tblTotCell); cell(totRow, 7, st.tblTotCell); cell(totRow, 8, st.tblTotCell);
        merge(sh, r, r, 3, 8);
        cell(totRow, 9,  st.tblTotCell).setCellValue(String.valueOf(total));
        cell(totRow, 10, st.tblTotCell); cell(totRow, 11, st.tblTotCell);
        merge(sh, r, r, 9, 11);
        r++;

        // Date — with today's date
        row(sh, r++, 8f);
        String facToday = java.time.LocalDate.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        XSSFRow dv = row(sh, r, 16f);
        cell(dv, 2, st.footSm).setCellValue("Date: " + facToday);
        r++;

        // Gap rows — plain empty rows (no cell borders)
        while (r < afterRow + 14) row(sh, r++, 12f);

        // Signature block — NO fillRow; only the 3 name cells created (no grid box)
        XSSFRow names = row(sh, r, 22f);
        cell(names, 2,  st.footHdr).setCellValue(nvl(cfg.getTtCoordinatorName(), DEF_TTC));
        cell(names, 5,  st.footHdr).setCellValue(nvl(cfg.getHodSignatureName(), DEF_HOD));
        cell(names, 9,  st.footHdr).setCellValue(nvl(cfg.getPrincipalName(), DEF_PRINCIPAL));
        merge(sh, r, r, 2, 4); merge(sh, r, r, 5, 8); merge(sh, r, r, 9, 11);
        r++;
        XSSFRow titles = row(sh, r, 16f);
        cell(titles, 2, st.footSmC).setCellValue("TIME TABLE COORDINATOR");
        cell(titles, 5, st.footSmC).setCellValue("HOD");
        cell(titles, 9, st.footSmC).setCellValue("PRINCIPAL");
        merge(sh, r, r, 2, 4); merge(sh, r, r, 5, 8); merge(sh, r, r, 9, 11);
    }

    /* ═══════════════════════════ FOOTER — CLASS (Template D) ════════════════ */
    // Uses only visible, wide columns. Col D(3) is hidden on class sheets, cols G(6)
    // and J(9) are narrow break columns — all avoided.
    //   Theory: C(2)=Subject  E(4)=Staff
    //   Lab:    F(5)=Subject  H(7)=Batch  I(8)=Staff  L(11)=LAB room

    private void classFooter(XSSFSheet sh, Styles st, String cls,
                              List<Timetable> rows, Map<String,String> nc, int afterRow,
                              List<Division> divisions, AcademicSetting cfg) {
        int r = afterRow;

        // Batch roll-no legend — plain text, no borders
        String legend = batchLegend(cls, divisions);
        if (!legend.isEmpty()) {
            XSSFRow lr = row(sh, r, 16f);
            cell(lr, 2, st.footSm).setCellValue(legend);
            merge(sh, r, r, 2, 12);
            r++;
        }
        row(sh, r++, 6f);  // small gap

        // ── Column header row (single level) ──
        // Theory: Subject(2-3) | Faculty(4-5)   [col 6 = SHORT BREAK spacer]
        // Practical: Subject(7-8) | Batch(9-10) | Faculty(11) | LAB(12)
        XSSFRow hdr = row(sh, r, 22f);
        cell(hdr, 2,  st.tblHdrCell).setCellValue("Subject");
        cell(hdr, 3,  st.tblHdrCell);
        cell(hdr, 4,  st.tblHdrCell).setCellValue("Faculty");
        cell(hdr, 5,  st.tblHdrCell);
        cell(hdr, 7,  st.tblHdrCell).setCellValue("Subject");
        cell(hdr, 8,  st.tblHdrCell);
        cell(hdr, 9,  st.tblHdrCell).setCellValue("Batch");
        cell(hdr, 10, st.tblHdrCell);
        cell(hdr, 11, st.tblHdrCell).setCellValue("Faculty");
        cell(hdr, 12, st.tblHdrCell).setCellValue("LAB");
        merge(sh, r, r, 2, 3);
        merge(sh, r, r, 4, 5);
        merge(sh, r, r, 7, 8);
        merge(sh, r, r, 9, 10);
        r++;

        // Theory subjects (unique, deduplicated; skip VL/Audit/no-faculty entries)
        Map<String, String> theorySubjFac = new LinkedHashMap<>();
        for (Timetable t : rows) {
            if ("Practical".equalsIgnoreCase(t.getLectureType()) ||
                "Lab".equalsIgnoreCase(t.getLectureType())) continue;
            String subj = subjDisp(t, nc);
            if (NO_FAC.contains(subj)) continue;
            if (!theorySubjFac.containsKey(subj))
                theorySubjFac.put(subj, nb(t.getFaculty()) ? t.getFaculty() : "—");
        }

        // Lab subjects: group by subject → collect all batches (sorted), skip Library
        Map<String, String[]> labMap = new LinkedHashMap<>();
        Map<String, Set<String>> labBatches = new LinkedHashMap<>();
        Set<String> labSeen = new HashSet<>();
        for (Timetable t : rows) {
            if (!"Practical".equalsIgnoreCase(t.getLectureType()) &&
                !"Lab".equalsIgnoreCase(t.getLectureType())) continue;
            String subj = subjDisp(t, nc);
            if ("LIBRARY".equals(subj) || NO_FAC.contains(subj)) continue;
            String batch = s(t.getBatch()).trim();
            if (!labSeen.add(subj + "|" + batch)) continue;
            labBatches.computeIfAbsent(subj, k -> new TreeSet<>()).add(batch);
            labMap.putIfAbsent(subj, new String[]{
                subj,
                nb(t.getFaculty()) ? t.getFaculty() : "—",
                nb(t.getRoom()) ? t.getRoom() : "—"});
        }

        List<Map.Entry<String,String>> tList = new ArrayList<>(theorySubjFac.entrySet());
        List<String[]> lList = new ArrayList<>(labMap.values());
        int nRows = Math.max(tList.size(), lList.size());
        for (int i = 0; i < nRows; i++) {
            XSSFRow dr = row(sh, r, 20f);
            if (i < tList.size()) {
                cell(dr, 2,  st.tblDataCell).setCellValue(tList.get(i).getKey());
                cell(dr, 3,  st.tblDataCell);
                cell(dr, 4,  st.tblDataCell).setCellValue(tList.get(i).getValue());
                cell(dr, 5,  st.tblDataCell);
                merge(sh, r, r, 2, 3);
                merge(sh, r, r, 4, 5);
            }
            if (i < lList.size()) {
                String[] la = lList.get(i);
                String batches = String.join(",", labBatches.getOrDefault(la[0], new LinkedHashSet<>()));
                cell(dr, 7,  st.tblDataCell).setCellValue(la[0]);
                cell(dr, 8,  st.tblDataCell);
                cell(dr, 9,  st.tblDataCell).setCellValue(batches);
                cell(dr, 10, st.tblDataCell);
                cell(dr, 11, st.tblDataCell).setCellValue(la[1]);
                cell(dr, 12, st.tblDataCell).setCellValue(roomAbbrev(la[2]));
                merge(sh, r, r, 7, 8);
                merge(sh, r, r, 9, 10);
            }
            r++;
        }

        row(sh, r++, 8f);  // gap after table

        // ── Class Teacher + Date on same row ──
        String ctPrefix = cls.startsWith("SE") ? "S" : cls.startsWith("TE") ? "T" : "B";
        String classTeacher = divisions.stream()
            .filter(dv -> ctPrefix.equals(dv.getBatchPrefix()))
            .findFirst()
            .map(dv -> nb(dv.getClassTeacher()) ? dv.getClassTeacher() : "")
            .orElse("");
        String today = java.time.LocalDate.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        // Class Teacher row
        XSSFRow ctd = row(sh, r, 22f);
        cell(ctd, 2, st.footSmB).setCellValue("Class Teacher:");
        if (nb(classTeacher)) {
            XSSFCell ctLine = ctd.createCell(3);
            ctLine.setCellStyle(st.footSm);
            ctLine.setCellValue(classTeacher);
            for (int c = 4; c <= 11; c++) ctd.createCell(c).setCellStyle(st.footSm);
        } else {
            for (int c = 3; c <= 11; c++) ctd.createCell(c).setCellStyle(st.signLine);
        }
        merge(sh, r, r, 3, 11);
        r++;

        // Date — left-aligned at col 2, same position as all other sheets
        XSSFRow ctDate = row(sh, r, 18f);
        cell(ctDate, 2, st.footSm).setCellValue("Date: " + today);
        r++;

        // Gap rows for signing space (at least 3 rows)
        int sigStart = Math.max(r + 3, afterRow + 16);
        while (r < sigStart) row(sh, r++, 14f);

        // ── Signature block — no grid box ──
        XSSFRow names = row(sh, r, 22f);
        cell(names, 2, st.footHdr).setCellValue(nvl(cfg.getTtCoordinatorName(), DEF_TTC));
        cell(names, 5, st.footHdr).setCellValue(nvl(cfg.getHodSignatureName(), DEF_HOD));
        cell(names, 9, st.footHdr).setCellValue(nvl(cfg.getPrincipalName(), DEF_PRINCIPAL));
        merge(sh, r, r, 2, 4); merge(sh, r, r, 5, 8); merge(sh, r, r, 9, 12);
        r++;
        XSSFRow titles = row(sh, r, 16f);
        cell(titles, 2, st.footSmC).setCellValue("TIME TABLE COORDINATOR");
        cell(titles, 5, st.footSmC).setCellValue("HOD");
        cell(titles, 9, st.footSmC).setCellValue("PRINCIPAL");
        merge(sh, r, r, 2, 4); merge(sh, r, r, 5, 8); merge(sh, r, r, 9, 12);
    }

    /* ═══════════════════════════ FOOTER — LAB (Template B) ══════════════════ */

    private void labFooter(XSSFSheet sh, Styles st, String labName,
                            List<Timetable> rows, Map<String,String> nc, int afterRow,
                            AcademicSetting cfg) {
        int r = afterRow;
        row(sh, r++, 10f);

        // Occupancy header
        XSSFRow hdr = row(sh, r, 22f);
        cell(hdr, 2,  st.tblHdrCell).setCellValue("SUBJECT NAME");
        cell(hdr, 4,  st.tblHdrCell);   // right-border for merge 2-4
        cell(hdr, 5,  st.tblHdrCell).setCellValue("BATCH");
        cell(hdr, 6,  st.tblHdrCell);   // right-border for merge 5-6
        cell(hdr, 7,  st.tblHdrCell).setCellValue("OCCUPANCY IN HOURS");
        cell(hdr, 11, st.tblHdrCell);   // right-border for merge 7-11
        merge(sh, r, r, 2, 4); merge(sh, r, r, 5, 6); merge(sh, r, r, 7, 11);
        r++;

        Map<String, Long> subjHours = rows.stream()
            .collect(Collectors.groupingBy(t -> subjDisp(t, nc) + "|" + s(t.getBatch()),
                Collectors.counting()));
        long total = 0;
        for (Map.Entry<String, Long> e : new TreeMap<>(subjHours).entrySet()) {
            String[] parts = e.getKey().split("\\|", 2);
            XSSFRow dr = row(sh, r, 22f);
            cell(dr, 2,  st.tblDataCell).setCellValue(parts[0]);
            cell(dr, 4,  st.tblDataCell);   // right-border for merge 2-4
            cell(dr, 5,  st.tblDataCell).setCellValue(parts.length > 1 ? parts[1] : "");
            cell(dr, 6,  st.tblDataCell);   // right-border for merge 5-6
            cell(dr, 7,  st.tblDataCell).setCellValue(e.getValue());
            cell(dr, 11, st.tblDataCell);   // right-border for merge 7-11
            merge(sh, r, r, 2, 4); merge(sh, r, r, 5, 6); merge(sh, r, r, 7, 11);
            total += e.getValue();
            r++;
        }
        XSSFRow totRow = row(sh, r, 22f);
        cell(totRow, 2,  st.tblTotCell).setCellValue("TOTAL");
        cell(totRow, 6,  st.tblTotCell);   // right-border for merge 2-6
        cell(totRow, 7,  st.tblTotCell).setCellValue(total);
        cell(totRow, 11, st.tblTotCell);   // right-border for merge 7-11
        merge(sh, r, r, 2, 6); merge(sh, r, r, 7, 11);
        r++;

        int labSigStart = Math.max(r + 3, afterRow + 10);
        while (r < labSigStart) row(sh, r++, 12f);
        String labToday = java.time.LocalDate.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        XSSFRow dv = row(sh, r, 16f);
        cell(dv, 2, st.footSm).setCellValue("Date: " + labToday);
        r++;

        while (r < afterRow + 16) row(sh, r++, 12f);
        XSSFRow names = row(sh, r, 22f);
        cell(names, 2, st.footHdr).setCellValue(nvl(cfg.getTtCoordinatorName(), DEF_TTC));
        cell(names, 5, st.footHdr).setCellValue(nvl(cfg.getHodSignatureName(), DEF_HOD));
        cell(names, 9, st.footHdr).setCellValue(nvl(cfg.getPrincipalName(), DEF_PRINCIPAL));
        merge(sh, r, r, 2, 4); merge(sh, r, r, 5, 8); merge(sh, r, r, 9, 11);
        r++;
        XSSFRow titles = row(sh, r, 16f);
        cell(titles, 2, st.footSmC).setCellValue("TIME TABLE COORDINATOR");
        cell(titles, 5, st.footSmC).setCellValue("HOD");
        cell(titles, 9, st.footSmC).setCellValue("PRINCIPAL");
        merge(sh, r, r, 2, 4); merge(sh, r, r, 5, 8); merge(sh, r, r, 9, 11);
    }

    /** Batch roll-number legend string per class — built from Division.studentCount + rollNumberStart. */
    private String batchLegend(String cls, List<Division> divisions) {
        String prefix = cls.startsWith("SE") ? "S" : cls.startsWith("TE") ? "T" : "B";
        return divisions.stream()
            .filter(d -> prefix.equals(d.getBatchPrefix()))
            .findFirst()
            .map(d -> buildBatchLegend(d, prefix))
            .orElse("");
    }

    private String buildBatchLegend(Division d, String prefix) {
        Integer total   = d.getStudentCount();
        Integer base    = d.getRollNumberStart();
        Integer batches = d.getBatchCount();
        if (total == null || total <= 0 || base == null || batches == null || batches <= 0) return "";
        int perBatch = (total + batches - 1) / batches; // ceiling distribution
        StringBuilder sb = new StringBuilder("BATCHES:");
        for (int i = 0; i < batches; i++) {
            int bStart = base + i * perBatch;
            int bEnd   = Math.min(bStart + perBatch - 1, base + total - 1);
            sb.append("  ").append(prefix).append(i + 1)
              .append(" ROLL NO. ").append(bStart).append("-").append(bEnd);
        }
        return sb.toString();
    }

    /* ═══════════════════════════ FOOTER (MASTER only) ═══════════════════════ */

    private void masterFooter(XSSFSheet sh, Styles st, int startRow,
                               List<Division> divisions, AcademicSetting cfg) {
        // Compact footer — removed SE/TE/BE faculty lists so MASTER fits on one A4 page.
        int r = startRow;

        // Batch roll numbers (all 3 classes, one line each)
        row(sh, r++, 8f);
        for (String cls : CLASSES) {
            String leg = batchLegend(cls, divisions);
            if (!leg.isEmpty()) {
                XSSFRow rb = row(sh, r, 14f);
                cell(rb, 2, st.footSm).setCellValue(leg);
                merge(sh, r, r, 2, 12);
                r++;
            }
        }

        // Date — with today's date
        row(sh, r++, 8f);
        String mToday = java.time.LocalDate.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        XSSFRow dv = row(sh, r, 14f);
        cell(dv, 2, st.footSm).setCellValue("Date: " + mToday);
        r++;

        // Gap — plain rows, no borders
        while (r < startRow + 10) row(sh, r++, 12f);

        // Signature block — NO borders, centered in three equal zones
        XSSFRow rNames = row(sh, r, 20f);
        cell(rNames, 2, st.footHdr).setCellValue(nvl(cfg.getTtCoordinatorName(), DEF_TTC));
        cell(rNames, 6, st.footHdr).setCellValue(nvl(cfg.getHodSignatureName(), DEF_HOD));
        cell(rNames, 10, st.footHdr).setCellValue(nvl(cfg.getPrincipalName(), DEF_PRINCIPAL));
        merge(sh, r, r, 2, 4); merge(sh, r, r, 6, 8); merge(sh, r, r, 10, 12);
        r++;
        XSSFRow rTitles = row(sh, r, 14f);
        cell(rTitles, 2, st.footSmC).setCellValue("TIME TABLE COORDINATOR");
        cell(rTitles, 6, st.footSmC).setCellValue("HOD");
        cell(rTitles, 10, st.footSmC).setCellValue("PRINCIPAL");
        merge(sh, r, r, 2, 4); merge(sh, r, r, 6, 8); merge(sh, r, r, 10, 12);
    }

    /* ═══════════════════════════ LOGO EMBEDDING ═════════════════════════════ */

    /**
     * Embeds both logos scaled to fit within their respective columns.
     * dayColW / rightColW are in Excel character-width units.
     * rightLogoCol is the 0-based column index of the owner/right logo.
     */
    private void addLogos(XSSFSheet sh, XSSFWorkbook wb, byte[] clgLogo, byte[] ownerLogo,
                          double dayColW, int rightLogoCol, double rightColW) {
        if (clgLogo == null && ownerLogo == null) return;
        XSSFDrawing drawing = sh.createDrawingPatriarch();
        final double EPU = 66675.0; // EMUs per Excel character-width unit

        if (clgLogo != null) {
            int picIdx = wb.addPicture(clgLogo, Workbook.PICTURE_TYPE_PNG);
            int cw = (int)(dayColW * EPU);
            // 3% left padding, 97% right — stays within day column
            XSSFClientAnchor a = new XSSFClientAnchor(
                (int)(cw * 0.03), 20000,
                (int)(cw * 0.97), 0,  // dy2=0 at start of row3 = bottom of bordered header rows 0-2
                2, 0, 2, 3);
            a.setAnchorType(ClientAnchor.AnchorType.MOVE_AND_RESIZE);
            drawing.createPicture(a, picIdx);
        }
        if (ownerLogo != null) {
            int picIdx = wb.addPicture(ownerLogo, Workbook.PICTURE_TYPE_PNG);
            int cw = (int)(rightColW * EPU);
            // Limit to 85% width (15% right margin) to prevent page-edge overflow
            // dy2=0 at start of row3 = image ends at bottom of bordered header (rows 0-2)
            XSSFClientAnchor a = new XSSFClientAnchor(
                (int)(cw * 0.05), 20000,
                (int)(cw * 0.85), 0,
                rightLogoCol, 0, rightLogoCol, 3);
            a.setAnchorType(ClientAnchor.AnchorType.MOVE_AND_RESIZE);
            drawing.createPicture(a, picIdx);
        }
    }

    /* ═══════════════════════════ CELL TEXT BUILDERS ═════════════════════════ */

    private String classText(List<Timetable> entries, Map<String,String> nc) {
        if (entries.size() == 1) {
            Timetable t = entries.get(0);
            String d = subjDisp(t, nc);
            String bat = nb(t.getBatch()) ? t.getBatch()+"-" : "";
            return bat + d + facSuffix(t, d);
        }
        // 2 batches per line: "S1-X, S2-Y\nS3-Z, S4-W" — halves the row height needed
        List<String> parts = entries.stream()
            .sorted(Comparator.comparing(t -> s(t.getBatch())))
            .map(t -> { String d = subjDisp(t,nc); return s(t.getBatch())+"-"+d+facSuffix(t,d); })
            .collect(Collectors.toList());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(i % 2 == 0 ? "\n" : ", ");
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    private String facText(List<Timetable> entries, Map<String,String> nc) {
        if (entries.size() == 1) {
            Timetable t = entries.get(0);
            String d = subjDisp(t, nc);
            return nb(t.getBatch()) ? t.getBatch()+"-"+d : d;
        }
        return entries.stream()
            .sorted(Comparator.comparing(t -> s(t.getBatch())))
            .map(t -> s(t.getBatch())+"-"+subjDisp(t,nc))
            .collect(Collectors.joining(", "));
    }

    private String roomText(List<Timetable> entries, Map<String,String> nc) {
        if (entries.size() == 1) {
            Timetable t = entries.get(0);
            String d = subjDisp(t, nc);
            return (nb(t.getBatch()) ? t.getBatch()+"-" : "") + d + facSuffix(t, d);
        }
        return entries.stream()
            .sorted(Comparator.comparing(t -> s(t.getBatch())))
            .map(t -> { String d = subjDisp(t,nc); return s(t.getBatch())+"-"+d+facSuffix(t,d); })
            .collect(Collectors.joining(", "));
    }

    private String subjDisp(Timetable t, Map<String,String> nc) {
        String code = nc.getOrDefault(s(t.getSubject()).toLowerCase(), s(t.getSubject()));
        String fac  = abbrev(t.getFaculty());
        if ("LP2".equals(code)) {
            if ("ANK".equals(fac)) return "LP-II(WAD)";
            if ("ARD".equals(fac)) return "LP-II(E-II)";
            return "LP-II";
        }
        return CODE_DISP.getOrDefault(code, code);
    }

    private String facSuffix(Timetable t, String disp) {
        if (NO_FAC.contains(disp)) return "";
        if (!nb(t.getFaculty())) return "";
        String a = abbrev(t.getFaculty());
        return (a.isEmpty() || "LC".equals(a)) ? "" : "("+a+")";
    }

    /* ═══════════════════════════ MERGE DETECTION ════════════════════════════ */

    private void detectMerges(List<Timetable>[] sl,
                               boolean[] first, boolean[] second, boolean[] three) {
        for (int[] p : WIN_PAIRS) {
            if (sameWin(sl[p[0]], sl[p[1]])) { first[p[0]]=true; second[p[1]]=true; }
        }
        if (!first[4] && sameWin(sl[4],sl[5]) && sameWin(sl[5],sl[6]) && sameWin(sl[4],sl[6])) {
            three[4]=true; second[5]=true; second[6]=true;
        }
    }

    private boolean sameWin(List<Timetable> a, List<Timetable> b) {
        if (a==null||b==null||a.isEmpty()||b.isEmpty()||a.size()!=b.size()) return false;
        Set<String> ka = a.stream().map(t->s(t.getBatch())+"|"+s(t.getSubject())).collect(Collectors.toSet());
        Set<String> kb = b.stream().map(t->s(t.getBatch())+"|"+s(t.getSubject())).collect(Collectors.toSet());
        return ka.equals(kb);
    }

    /* ═══════════════════════════ GRID / HELPERS ═════════════════════════════ */

    @SuppressWarnings("unchecked")
    private Map<String,List<Timetable>[]> buildGrid(List<Timetable> rows) {
        Map<String,List<Timetable>[]> g = new LinkedHashMap<>();
        for (String d:DAYS) { List<Timetable>[] a=new List[7]; for(int i=0;i<7;i++) a[i]=new ArrayList<>(); g.put(d,a); }
        for (Timetable t : rows) {
            String day = normDay(t.getDay());
            if (day==null||t.getTimeSlot()==null) continue;
            List<Timetable>[] ds = g.get(day); if (ds==null) continue;
            for (int si=0;si<SLOTS.length;si++) if(SLOTS[si].equals(t.getTimeSlot())) { ds[si].add(t); break; }
        }
        return g;
    }

    @SuppressWarnings("unchecked")
    private List<Timetable>[] emptyArr() {
        List<Timetable>[] a=new List[7]; for(int i=0;i<7;i++) a[i]=new ArrayList<>(); return a;
    }

    private Map<String,String> nameToCode(List<Subject> subjects) {
        Map<String,String> m = new HashMap<>();
        for (Subject s : subjects) if(nb(s.getName())&&nb(s.getCode())) m.put(s.getName().toLowerCase().trim(),s.getCode());
        return m;
    }

    private boolean isLab(List<Timetable> e) {
        return e.size()>1 || e.stream().anyMatch(t->isPrac(t.getLectureType()));
    }
    private boolean isPrac(String t) { return t!=null&&(t.equalsIgnoreCase("Practical")||t.equalsIgnoreCase("Lab")); }
    private String normDay(String d) { if(d==null)return null; for(String x:DAYS) if(x.equalsIgnoreCase(d.trim()))return x; return null; }
    String abbrev(String n) {
        if(n==null||n.isBlank())return "";
        String c=n.replaceAll("(?i)(prof|dr|mrs|mr)\\.","").trim();
        StringBuilder sb=new StringBuilder();
        for(String p:c.split("\\s+")) if(!p.isEmpty()&&!p.equals(".")) sb.append(p.charAt(0));
        return sb.toString().toUpperCase();
    }
    private String safe(String n) { String r=n.replaceAll("[\\[\\]:\\*?/\\\\]"," ").trim(); return r.length()>31?r.substring(0,31):r; }
    private String roomAbbrev(String r) {
        Map<String,String> m=Map.of("Computer Center Lab","CCL","Distributed Lab","DS","Multimedia Lab","ML",
            "Network Lab","NL","Operating System Lab","OS","Project Lab","Project Lab",
            "Software Testing And Design Lab","SDTL","Web Engineering & Technology Lab","WET");
        return m.getOrDefault(r, r);
    }
    /** Row heights for MASTER stacked layout (larger cells). */
    private float[] classRowHeights(String cls) {
        if("SE(IT)".equals(cls)) return SE_HT;
        if("TE(IT)".equals(cls)) return TE_HT;
        return BE_HT;
    }

    /** Row heights for individual CLASS sheets (smaller, matching IT.xlsx). */
    private float[] classSheetRowHeights(String cls) {
        if("SE(IT)".equals(cls)) return SE_CLASS_HT;
        if("TE(IT)".equals(cls)) return TE_CLASS_HT;
        return BE_CLASS_HT;
    }
    private List<Timetable> filter(List<Timetable> all, String cls) {
        return all.stream().filter(t->cls.equals(t.getClassName())).collect(Collectors.toList());
    }
    private byte[] readLogo(String path) {
        // Try user-supplied filesystem path first
        if (path != null && !path.isBlank()) {
            try {
                java.nio.file.Path p = Paths.get(path);
                if (java.nio.file.Files.exists(p)) return Files.readAllBytes(p);
            } catch (Exception ignored) {}
        }
        // Fall back to bundled images in classpath (src/main/resources/static/images/)
        boolean isOwner = path != null && path.toLowerCase().contains("owner");
        String res = "/static/images/" + (isOwner ? "ownerlogo.png" : "clglogo.png");
        try (var is = getClass().getResourceAsStream(res)) {
            return is != null ? is.readAllBytes() : null;
        } catch (Exception ignored) { return null; }
    }

    private byte[] readOwnerLogo(String path) {
        if (path != null && !path.isBlank()) {
            try {
                java.nio.file.Path p = Paths.get(path);
                if (java.nio.file.Files.exists(p)) return Files.readAllBytes(p);
            } catch (Exception ignored) {}
        }
        try (var is = getClass().getResourceAsStream("/static/images/ownerlogo.png")) {
            return is != null ? is.readAllBytes() : null;
        } catch (Exception ignored) { return null; }
    }
    private boolean nb(String s) { return s!=null&&!s.isBlank(); }
    private String   s(String v)  { return v!=null?v:""; }
    /** Returns val if non-blank, otherwise def. Used for AcademicSetting null-safe fallback. */
    private String nvl(String val, String def) { return (val != null && !val.isBlank()) ? val : def; }

    /* ── Cell / Row creation shortcuts ── */
    private XSSFRow row(XSSFSheet sh, int ri, float ht) {
        XSSFRow r = sh.createRow(ri); r.setHeightInPoints(ht); return r;
    }
    private XSSFCell cell(XSSFRow row, int col, XSSFCellStyle style) {
        XSSFCell c = row.createCell(col); c.setCellStyle(style); return c;
    }
    private void merge(XSSFSheet sh, int r1, int r2, int c1, int c2) {
        sh.addMergedRegion(new CellRangeAddress(r1, r2, c1, c2));
    }

    /** Fill every cell in [0..ncols) with the given style (ensures no unstyled/red cells). */
    private void fillRow(XSSFRow row, XSSFCellStyle style, int ncols) {
        for (int c = 0; c < ncols; c++) {
            XSSFCell cell = row.getCell(c);
            if (cell == null) cell = row.createCell(c);
            cell.setCellStyle(style);
        }
    }

    /** Create a blank row with all cells set to emptyCell style. */
    private void emptyRow(XSSFSheet sh, Styles st, int ri, float ht, int ncols) {
        XSSFRow r = row(sh, ri, ht);
        fillRow(r, st.emptyCell, ncols);
    }

    /* ── Column widths ── */
    private void applyColWidths(XSSFSheet sh, double[] widths) {
        for (int i = 0; i < widths.length; i++)
            sh.setColumnWidth(i, (int) Math.round(widths[i] * 256));
    }

    /** A4 landscape, fit-to-page, narrow margins. Applied to every sheet. */
    private void applyPageSetup(XSSFSheet sh) {
        sh.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE);
        sh.getPrintSetup().setLandscape(true);
        sh.getPrintSetup().setFitWidth((short)1);
        sh.getPrintSetup().setFitHeight((short)1);
        sh.getPrintSetup().setHResolution((short)300);
        sh.getPrintSetup().setVResolution((short)300);
        sh.setFitToPage(true);
        sh.setMargin(org.apache.poi.ss.usermodel.PageMargin.LEFT,   0.25);
        sh.setMargin(org.apache.poi.ss.usermodel.PageMargin.RIGHT,  0.25);
        sh.setMargin(org.apache.poi.ss.usermodel.PageMargin.TOP,    0.25);
        sh.setMargin(org.apache.poi.ss.usermodel.PageMargin.BOTTOM, 0.25);
        sh.setMargin(org.apache.poi.ss.usermodel.PageMargin.HEADER, 0.10);
        sh.setMargin(org.apache.poi.ss.usermodel.PageMargin.FOOTER, 0.10);
    }

    /* ═══════════════════════════ STYLES ═════════════════════════════════════ */

    private static final class Styles {
        /* Header / metadata */
        final XSSFCellStyle inst22, meta, masterTitle, ay24b, dept36b, wef26b;
        /* Faculty/class header rows (bold 18pt, left-aligned, no border) */
        final XSSFCellStyle facMetaL;
        /* Grid */
        final XSSFCellStyle hdrCell, brkHdrCell, lngHdrCell;
        final XSSFCellStyle dayCell, clsCell;
        final XSSFCellStyle thCell, labCell, emptyCell, brkData, lngData;
        /* Footer */
        final XSSFCellStyle footSm, footHdr, footSmC, footSmB, signLine;
        final XSSFCellStyle titleBarCell, hdrBorderL, hdrBorderC;
        /* Sub-tables (faculty load / class subject-staff / lab occupancy) */
        final XSSFCellStyle tblHdrCell, tblDataCell, tblTotCell;

        Styles(XSSFWorkbook wb) {
            /* ── Fonts ── */
            XSSFFont f11b  = tnr(wb,11,true);
            XSSFFont f11   = tnr(wb,11,false);
            XSSFFont f10b  = tnr(wb,10,true);
            XSSFFont f10   = tnr(wb,10,false);
            XSSFFont f9    = tnr(wb, 9,false);
            XSSFFont f9b   = tnr(wb, 9,true);
            XSSFFont f14   = tnr(wb,14,false);
            XSSFFont f16b  = tnr(wb,16,true);
            XSSFFont f18b  = tnr(wb,18,true);
            XSSFFont f20   = tnr(wb,20,false);
            XSSFFont f20b  = tnr(wb,20,true);
            XSSFFont f22b  = tnr(wb,22,true);
            XSSFFont f24b  = tnr(wb,24,true);

            /* ── No-border styles (header/footer rows) ── */
            inst22      = nb(wb, f14, CENTER, true);
            meta        = nb(wb, f9,  LEFT,   false);
            masterTitle = nb(wb, f11, CENTER, false);
            ay24b       = nb(wb, f16b,CENTER, false);
            dept36b     = nb(wb, f20b,CENTER, false);
            wef26b      = nb(wb, f16b,CENTER, false);
            facMetaL    = nb(wb, f11b,LEFT,   false);

            /* ── Grid header row (thin borders, white fill, 11pt bold) ── */
            hdrCell    = bordered(wb,255,255,255, f11b,  CENTER, true,  true, true, true, true);
            brkHdrCell = bordered(wb,255,255,255, f9,    CENTER, true,  true, false,true, true);
            lngHdrCell = bordered(wb,255,255,255, f9,    CENTER, true,  true, true, true, true);

            /* ── Day and Class labels ── */
            dayCell    = bordered(wb,255,255,255, f11b,  CENTER, false, true, true, true, true);
            clsCell    = bordered(wb,255,255,255, f11b,  CENTER, false, true, true, true, true);

            /* ── Data cells: white fill, 11pt — wrap so all batch lines show ── */
            thCell     = bordered(wb,255,255,255, f11,   CENTER, true,  true, true, true, true);
            labCell    = bordered(wb,255,255,255, f11,   CENTER, true,  true, true, true, true);
            emptyCell  = bordered(wb,255,255,255, f9,    CENTER, false, true, true, true, true);

            /* ── Break columns: gray fill #D8D8D8 ── */
            brkData    = bordered(wb,0xD8,0xD8,0xD8, f9b, CENTER, true,  true, true, true, true);
            lngData    = bordered(wb,0xD8,0xD8,0xD8, f9b, CENTER, true,  true, true, true, true);

            /* ── Footer ── */
            footSm     = nb(wb, f10,  LEFT,   false);
            footSmC    = nb(wb, f10,  CENTER, false);
            footSmB    = nb(wb, f10b, LEFT,   false);
            footHdr    = nb(wb, f11b, CENTER, false);
            XSSFCellStyle sl = wb.createCellStyle();
            sl.setFont(f10);
            sl.setVerticalAlignment(VerticalAlignment.BOTTOM);
            sl.setBorderBottom(BorderStyle.THIN);
            signLine = sl;
            // Header bordered-table styles (match sample_header.png)
            titleBarCell = bordered(wb,0xA0,0xA0,0xA0, f11b, CENTER, false, true, true, true, true);
            hdrBorderL   = bordered(wb,255,255,255,     f9,   LEFT,   false, true, true, true, true);
            hdrBorderC   = bordered(wb,255,255,255,     f11b, CENTER, true,  true, true, true, true);

            /* ── Sub-table cells (thin borders, white fill) ── */
            tblHdrCell  = bordered(wb,255,255,255, f11b, CENTER, true,  true,true,true,true);
            tblDataCell = bordered(wb,255,255,255, f11,  CENTER, false, true,true,true,true);
            tblTotCell  = bordered(wb,255,255,255, f11b, CENTER, false, true,true,true,true);
        }

        /** Times New Roman font */
        private static XSSFFont tnr(XSSFWorkbook wb, int pt, boolean bold) {
            XSSFFont f = wb.createFont(); f.setFontName("Times New Roman");
            f.setFontHeightInPoints((short)pt); f.setBold(bold); return f;
        }

        /** No-border style (for header/footer rows outside the grid) */
        private static XSSFCellStyle nb(XSSFWorkbook wb, XSSFFont font,
                                         HorizontalAlignment align, boolean wrap) {
            XSSFCellStyle s = wb.createCellStyle();
            s.setFont(font); s.setAlignment(align);
            s.setVerticalAlignment(VerticalAlignment.CENTER);
            s.setWrapText(wrap); return s;
        }

        /** Bordered style for grid cells */
        private static XSSFCellStyle bordered(XSSFWorkbook wb, int r, int g, int b,
                                               XSSFFont font, HorizontalAlignment align, boolean wrap,
                                               boolean top, boolean bot, boolean left, boolean right) {
            XSSFCellStyle s = wb.createCellStyle();
            s.setFillForegroundColor(new XSSFColor(new byte[]{(byte)r,(byte)g,(byte)b}, null));
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            s.setFont(font); s.setAlignment(align);
            s.setVerticalAlignment(VerticalAlignment.CENTER);
            s.setWrapText(wrap);
            if (top)   s.setBorderTop(BorderStyle.THIN);
            if (bot)   s.setBorderBottom(BorderStyle.THIN);
            if (left)  s.setBorderLeft(BorderStyle.THIN);
            if (right) s.setBorderRight(BorderStyle.THIN);
            return s;
        }

        static final HorizontalAlignment CENTER = HorizontalAlignment.CENTER;
        static final HorizontalAlignment LEFT   = HorizontalAlignment.LEFT;
    }
}
