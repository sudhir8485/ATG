package com.nt.service;

import com.nt.entity.Subject;
import com.nt.entity.Timetable;
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

    @Autowired private TimetableRepository timetableRepo;
    @Autowired private SubjectRepository    subjectRepo;

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

    /* ── MASTER: header rows 0-7 (Excel 1-8) and per-class data row heights ── */
    static final float[] HDR_HT   = {19.5f,42.0f,27.0f,27.0f,24.75f,70.5f,39.75f,96.0f};
    static final float[] SE_HT    = {88.5f,69.75f,70.5f,77.25f,64.5f};
    static final float[] TE_HT    = {73.5f,68.25f,62.25f,66.0f,83.25f};
    static final float[] BE_HT    = {63.0f,68.25f,66.75f,62.25f,51.0f};
    static final float   SEP1_HT  = 51.75f;
    static final float   SEP2_HT  = 50.25f;

    /* ── CLASS sheet (Template D): 10 header rows + per-class data heights ── */
    static final float[] CLASS_HDR_HT = {15.0f,27.0f,25.5f,19.5f,19.5f,30.0f,30.0f,30.0f,25.5f,96.75f};
    static final float[] SE_CLASS_HT  = {73.5f,60.0f,60.0f,87.75f,60.0f};
    static final float[] TE_CLASS_HT  = {108.0f,60.0f,60.0f,84.75f,60.0f};
    static final float[] BE_CLASS_HT  = {60.0f,60.0f,60.0f,60.0f,49.5f};

    /* ── FACULTY sheet (Template E): 9 header rows + data row heights ── */
    static final float[] FAC_HDR_HT   = {15.0f,54.0f,24.75f,24.75f,24.75f,24.75f,28.5f,26.25f,75.75f};
    static final float[] FAC_DATA_HT  = {40.0f,40.0f,40.0f,50.0f,50.0f};

    /* ── MASTER column widths (14 cols A-N) — exact match IT.xlsx MASTER ── */
    static final double[] COL_WIDTHS = {
        8.0,   // A
        5.42,  // B
        28.42, // C  Day
        16.57, // D  Class
        35.0,  // E  09-10
        50.86, // F  10-11
        20.29, // G  SHORT BREAK
        36.15, // H  11:15-12:15
        65.71, // I  12:15-13:15
        29.14, // J  LONG BREAK
        32.29, // K  14-15
        63.14, // L  15-16
        47.86, // M  16-17
        5.14,  // N  (margin)
    };

    /* ── CLASS sheet column widths (from IT.xlsx CLASS_SE) ── */
    static final double[] CLASS_COL_WIDTHS = {
        1.14,  // A
        6.29,  // B
        20.85, // C  Day
        17.15, // D  Class
        25.42, // E  09-10
        41.29, // F  10-11
        21.0,  // G  SHORT BREAK
        28.29, // H  11:15-12:15
        44.71, // I  12:15-13:15
        16.85, // J  LONG BREAK
        16.85, // K  14-15
        34.14, // L  15-16
        23.0,  // M  16-17
        25.29, // N  (margin)
    };

    /* ── FACULTY sheet column widths (from IT.xlsx RSL faculty sheet) ── */
    static final double[] FAC_COL_WIDTHS = {
        1.14,  // A
        11.0,  // B
        22.0,  // C  Day
        19.14, // D  09-10
        22.0,  // E  10-11
        20.42, // F  SHORT BREAK
        22.42, // G  11:15-12:15
        23.71, // H  12:15-13:15
        15.0,  // I  LONG BREAK
        21.0,  // J  14-15
        25.0,  // K  15-16
        30.29, // L  16-17
        9.14,  // M  (margin)
    };

    /* ── LAB/ROOM sheet column widths (from IT.xlsx CCL/WET lab sheets) ── */
    static final double[] LAB_COL_WIDTHS = {
        1.14,  // A
        1.14,  // B
        17.0,  // C  Day
        19.0,  // D  09-10
        21.43, // E  10-11
        20.42, // F  SHORT BREAK
        18.14, // G  11:15-12:15
        18.71, // H  12:15-13:15
        21.43, // I  LONG BREAK
        17.15, // J  14-15
        24.0,  // K  15-16
        14.0,  // L  16-17
        9.14,  // M  (margin)
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

    /* Logo paths */
    static final String CLG_LOGO   = "/home/sudhir/Desktop/ATG/clglogo.png";
    static final String OWNER_LOGO = "/home/sudhir/Desktop/ATG/ownerlogo.png";

    /* ═══════════════════════════ PUBLIC ENTRY ═══════════════════════════════ */

    public byte[] generate() throws IOException {
        List<Timetable> all = timetableRepo.findByDeletedFalse();
        Map<String,String> nc = nameToCode(subjectRepo.findAll());

        XSSFWorkbook wb = new XSSFWorkbook();
        Styles st = new Styles(wb);

        byte[] clgBytes   = readLogo(CLG_LOGO);
        byte[] ownerBytes = readLogo(OWNER_LOGO);

        /* Class sheets */
        for (String cls : CLASSES)
            writeClassSheet(wb, st, cls, filter(all,cls), nc, clgBytes, ownerBytes);

        /* Master sheet */
        writeMasterSheet(wb, st, all, nc, clgBytes, ownerBytes);

        /* Faculty sheets (skip Library Coordinator) */
        all.stream()
            .filter(t -> nb(t.getFaculty()) && !"Library Coordinator".equalsIgnoreCase(t.getFaculty()))
            .collect(Collectors.groupingBy(Timetable::getFaculty))
            .entrySet().stream().sorted(Map.Entry.comparingByKey())
            .forEach(e -> writeFacSheet(wb, st, e.getKey(), e.getValue(), nc, clgBytes, ownerBytes));

        /* Room sheets */
        all.stream()
            .filter(t -> nb(t.getRoom()))
            .collect(Collectors.groupingBy(Timetable::getRoom))
            .entrySet().stream().sorted(Map.Entry.comparingByKey())
            .forEach(e -> writeRoomSheet(wb, st, e.getKey(), e.getValue(), nc, clgBytes, ownerBytes));

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        wb.write(bos); wb.close();
        return bos.toByteArray();
    }

    /* ═══════════════════════════ SHEET WRITERS ══════════════════════════════ */

    private void writeClassSheet(XSSFWorkbook wb, Styles st, String cls,
                                  List<Timetable> rows, Map<String,String> nc,
                                  byte[] clgLogo, byte[] ownerLogo) {
        XSSFSheet sh = wb.createSheet(cls.replace("(","_").replace(")",""));
        int hi = classHeader(sh, wb, st, cls, clgLogo, ownerLogo);
        float[] heights = classSheetRowHeights(cls);
        int dataEnd = hi + DAYS.length - 1;
        writeClassRows(sh, st, hi, cls, buildGrid(rows), nc, heights, CC_SCOL);
        mergeBreaks(sh, hi, dataEnd, CC_BRK, CC_LNG);
        applyColWidths(sh, CLASS_COL_WIDTHS);
        applyPageSetup(sh);
        classFooter(sh, st, cls, rows, nc, hi + DAYS.length);
        sh.createFreezePane(0, hi + 1);
    }

    private void writeMasterSheet(XSSFWorkbook wb, Styles st, List<Timetable> all,
                                   Map<String,String> nc, byte[] clgLogo, byte[] ownerLogo) {
        XSSFSheet sh = wb.createSheet("MASTER");

        /* ── header rows 0-7 ── */
        int hi = masterHeader(sh, wb, st, clgLogo, ownerLogo);
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
        masterFooter(sh, st, beEnd + 1);

        applyColWidths(sh, COL_WIDTHS);
        applyPageSetup(sh);
        sh.createFreezePane(0, hi + 1);
    }

    private void writeFacSheet(XSSFWorkbook wb, Styles st, String fac,
                                List<Timetable> rows, Map<String,String> nc,
                                byte[] clgLogo, byte[] ownerLogo) {
        XSSFSheet sh = wb.createSheet(safe("Fac_" + abbrev(fac)));
        int hi = facRoomHeader(sh, wb, st, fac, "Individual Time Table", clgLogo, ownerLogo);
        writeFacRoomRows(sh, st, hi, buildGrid(rows), nc, FC_SCOL, "fac");
        mergeBreaks(sh, hi, hi + DAYS.length - 1, FC_BRK, FC_LNG);
        applyColWidths(sh, FAC_COL_WIDTHS);
        applyPageSetup(sh);
        facFooter(sh, st, fac, rows, nc, hi + DAYS.length);
        sh.createFreezePane(0, hi + 1);
    }

    private void writeRoomSheet(XSSFWorkbook wb, Styles st, String room,
                                 List<Timetable> rows, Map<String,String> nc,
                                 byte[] clgLogo, byte[] ownerLogo) {
        XSSFSheet sh = wb.createSheet(safe(roomAbbrev(room)));
        int hi = facRoomHeader(sh, wb, st, room, "Laboratory Timetable", clgLogo, ownerLogo);
        writeFacRoomRows(sh, st, hi, buildGrid(rows), nc, FC_SCOL, "room");
        mergeBreaks(sh, hi, hi + DAYS.length - 1, FC_BRK, FC_LNG);
        applyColWidths(sh, LAB_COL_WIDTHS);
        applyPageSetup(sh);
        labFooter(sh, st, room, rows, nc, hi + DAYS.length);
        sh.createFreezePane(0, hi + 1);
    }

    /* ═══════════════════════════ HEADER BUILDERS ════════════════════════════ */

    /** Returns POI row index of first data row (=8 for MASTER). */
    private int masterHeader(XSSFSheet sh, XSSFWorkbook wb, Styles st,
                              byte[] clgLogo, byte[] ownerLogo) {
        /* Row 0 (Excel 1, 19.5pt): empty — logo row */
        row(sh, 0, HDR_HT[0]);

        /* Row 1 (Excel 2, 42.0pt): E1:L2 institution name; M1:M4 right logo */
        XSSFRow r1 = row(sh, 1, HDR_HT[1]);
        cell(r1, 4, st.inst22).setCellValue(
            "Akhil Bharatiya Maratha Shikshan Parishad's\n" +
            "Anantrao Pawar college of Engineering & Research, Parvati, Pune");
        merge(sh, 0, 1, 4, 11);   // E1:L2
        merge(sh, 0, 3, 12, 12);  // M1:M4 (right logo column)

        /* Row 2 (Excel 3, 27.0pt): Record No | DoI */
        XSSFRow r2 = row(sh, 2, HDR_HT[2]);
        cell(r2, 4, st.meta).setCellValue("Record No.: ACA/R/003A");
        cell(r2, 7, st.meta).setCellValue("DoI: 01/02/2025");
        merge(sh, 2, 2, 4, 6); merge(sh, 2, 2, 7, 11);

        /* Row 3 (Excel 4, 27.0pt): Revision | Version */
        XSSFRow r3 = row(sh, 3, HDR_HT[3]);
        cell(r3, 4, st.meta).setCellValue("Revision: 00");
        cell(r3, 7, st.meta).setCellValue("Version: 3A.0");
        merge(sh, 3, 3, 4, 6); merge(sh, 3, 3, 7, 11);

        /* Row 4 (Excel 5, 24.75pt): C5:M5 Master Timetable */
        XSSFRow r4 = row(sh, 4, HDR_HT[4]);
        cell(r4, 2, st.masterTitle).setCellValue("Master Timetable");
        merge(sh, 4, 4, 2, 12);

        /* Row 5 (Excel 6, 70.5pt): C6:D6 A.Y. | E6:L6 DEPT | M6 SEM-II */
        XSSFRow r5 = row(sh, 5, HDR_HT[5]);
        cell(r5, 2, st.ay24b).setCellValue("A.Y. 2025-26");
        cell(r5, 4, st.dept36b).setCellValue("Department of Information Technology");
        cell(r5, 12, st.ay24b).setCellValue("SEM-II");
        merge(sh, 5, 5, 2, 3); merge(sh, 5, 5, 4, 11);

        /* Row 6 (Excel 7, 39.75pt): C7:M7 W.E.F. */
        XSSFRow r6 = row(sh, 6, HDR_HT[6]);
        cell(r6, 2, st.wef26b).setCellValue("W. E. F.: 01/01/2026");
        merge(sh, 6, 6, 2, 12);

        /* Row 7 (Excel 8, 96.0pt): column headers — MASTER uses "Days" not "Day/ Time" */
        colHeaderRow(sh, 7, HDR_HT[7], st, CC_SCOL, CC_BRK, CC_LNG, CC_NCOLS, true);
        // Override Day label for MASTER
        sh.getRow(7).getCell(CC_DAY).setCellValue("Days");

        /* Logos */
        addLogos(sh, wb, clgLogo, ownerLogo);

        return 8;  // data starts at row 8
    }

    /**
     * CLASS sheet header — 10 rows matching IT.xlsx Template D (ACA/R/003B).
     * Returns 10 (data starts at POI row 10 = Excel row 11).
     */
    private int classHeader(XSSFSheet sh, XSSFWorkbook wb, Styles st, String cls,
                             byte[] clgLogo, byte[] ownerLogo) {
        // Row 0 (Excel 1, 15pt): empty logo space
        row(sh, 0, CLASS_HDR_HT[0]);

        // Rows 1-2 (Excel 2-3): institution name — D2:L3 merged (2-row tall block)
        XSSFRow r1 = row(sh, 1, CLASS_HDR_HT[1]);
        cell(r1, 3, st.inst22).setCellValue(
            "Akhil Bharatiya Maratha Shikshan Parishad's" +
            "                                        " +
            "Anantrao Pawar college of Engineering & Research, Parvati, Pune");
        row(sh, 2, CLASS_HDR_HT[2]);
        merge(sh, 1, 2, 3, 11);  // D2:L3
        merge(sh, 1, 4, 12, 12); // M2:M5 (right logo column)

        // Row 3 (Excel 4, 19.5pt): Record No. | DoI
        XSSFRow r3 = row(sh, 3, CLASS_HDR_HT[3]);
        cell(r3, 3, st.meta).setCellValue("Record No.: ACA/R/003B");
        cell(r3, 7, st.meta).setCellValue("DoI: 01/02/2025");
        merge(sh, 3, 3, 3, 6); merge(sh, 3, 3, 7, 11);

        // Row 4 (Excel 5, 19.5pt): Revision
        XSSFRow r4 = row(sh, 4, CLASS_HDR_HT[4]);
        cell(r4, 3, st.meta).setCellValue("Revision: 00");
        merge(sh, 4, 4, 3, 11);

        // Row 5 (Excel 6, 30pt): "Timetable" grey title — B6:M6
        XSSFRow r5 = row(sh, 5, CLASS_HDR_HT[5]);
        cell(r5, 1, st.masterTitle).setCellValue("Timetable");
        merge(sh, 5, 5, 1, 12);

        // Row 6 (Excel 7, 30pt): Dept | Acadamic Year | Semester (spaces to separate)
        XSSFRow r6 = row(sh, 6, CLASS_HDR_HT[6]);
        cell(r6, 2, st.ay24b).setCellValue("Department: Information Technology");
        cell(r6, 6, st.ay24b).setCellValue("Acadamic Year- 2025-26");
        cell(r6, 12, st.ay24b).setCellValue("Semester: II");
        merge(sh, 6, 6, 2, 5); merge(sh, 6, 6, 6, 11);

        // Row 7 (Excel 8, 30pt): empty row
        row(sh, 7, CLASS_HDR_HT[7]);

        // Row 8 (Excel 9, 25.5pt): W.E.F.
        XSSFRow r8 = row(sh, 8, CLASS_HDR_HT[8]);
        cell(r8, 2, st.wef26b).setCellValue("W. E. F.: 01 / 01 /2026");
        merge(sh, 8, 8, 2, 12);

        // Row 9 (Excel 10, 96.75pt): column headers
        colHeaderRow(sh, 9, CLASS_HDR_HT[9], st, CC_SCOL, CC_BRK, CC_LNG, CC_NCOLS, true);
        addLogos(sh, wb, clgLogo, ownerLogo);

        return 10;  // data starts at POI row 10
    }

    /**
     * FACULTY/LAB header — 9 rows matching IT.xlsx Template E/B.
     * Returns 9 (data starts at POI row 9 = Excel row 10).
     */
    private int facRoomHeader(XSSFSheet sh, XSSFWorkbook wb, Styles st,
                               String name, String type,
                               byte[] clgLogo, byte[] ownerLogo) {
        boolean isFac = "Individual Time Table".equals(type);
        String recNo   = isFac ? "Record No.: ACA/R/003E" : "Record No.: ACA/R/003D";

        // Row 0 (Excel 1, 15pt): empty logo space
        row(sh, 0, FAC_HDR_HT[0]);

        // Row 1 (Excel 2, 54pt): institution name — E2:K2 (single tall row)
        // Faculty sheets: no ", Parvati, Pune"; lab sheets: keep it
        String instName = isFac
            ? "Akhil Bharatiya Maratha Shikshan Parishad's" +
              "                                                                                 " +
              "Anantrao Pawar college of Engineering & Research"
            : "Akhil Bharatiya Maratha Shikshan Parishad's" +
              "                                        " +
              "Anantrao Pawar college of Engineering & Research, Parvati, Pune";
        XSSFRow r1 = row(sh, 1, FAC_HDR_HT[1]);
        cell(r1, 4, st.inst22).setCellValue(instName);
        merge(sh, 1, 1, 4, 10);  // E2:K2
        merge(sh, 1, 3, 11, 11); // L2:L4 (right logo column — stop at row 4, not 5)

        // Row 2 (Excel 3, 24.75pt): Record No. | DoI
        XSSFRow r2 = row(sh, 2, FAC_HDR_HT[2]);
        cell(r2, 4, st.meta).setCellValue(recNo);
        cell(r2, 7, st.meta).setCellValue("DoI: 01/02/2025");
        merge(sh, 2, 2, 4, 6); merge(sh, 2, 2, 7, 10);

        // Row 3 (Excel 4, 24.75pt): Revision
        XSSFRow r3 = row(sh, 3, FAC_HDR_HT[3]);
        cell(r3, 4, st.meta).setCellValue("Revision: 00");
        merge(sh, 3, 3, 4, 10);

        // Row 4 (Excel 5, 24.75pt): title bar (grey)
        XSSFRow r4 = row(sh, 4, FAC_HDR_HT[4]);
        cell(r4, 2, st.masterTitle).setCellValue(type);
        merge(sh, 4, 4, 2, 11);

        // Row 5 (Excel 6, 24.75pt): Dept (with spaces so Sem-II appears right-aligned)
        XSSFRow r5 = row(sh, 5, FAC_HDR_HT[5]);
        cell(r5, 2, st.facMetaL).setCellValue(
            "Department: Information Technology" +
            "                                                                                         " +
            "Sem-II");
        merge(sh, 5, 5, 2, 11);

        // Row 6 (Excel 7, 28.5pt): W.E.F.
        XSSFRow r6 = row(sh, 6, FAC_HDR_HT[6]);
        cell(r6, 2, st.facMetaL).setCellValue("W. E. F.: 01/01/2026");
        merge(sh, 6, 6, 2, 11);

        // Row 7 (Excel 8, 26.25pt): Name
        XSSFRow r7 = row(sh, 7, FAC_HDR_HT[7]);
        String cleanName = cleanFacultyName(name);
        String nameLabel = isFac ? "Name of the Faculty: Prof. " + cleanName : "Lab Name: " + name;
        cell(r7, 2, st.facMetaL).setCellValue(nameLabel);
        merge(sh, 7, 7, 2, 11);

        // Row 8 (Excel 9, 75.75pt): column headers
        colHeaderRow(sh, 8, FAC_HDR_HT[8], st, FC_SCOL, FC_BRK, FC_LNG, FC_NCOLS, false);
        addLogos(sh, wb, clgLogo, ownerLogo);

        return 9;  // data starts at POI row 9
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
            "9.00 AM to 10.00 AM","10.00AM to 11.00 AM",
            "11.15 AM to 12.15 PM","12.15 AM to 1.15 AM",
            "2. 00 PM to 3.00 PM","3.00 PM to 4.00 PM","4.00 PM to 5.00 PM"
        };
        if (hasCls) {
            row.getCell(CC_DAY).setCellValue("Day/ Time");
            row.getCell(CC_CLS).setCellValue("Class");
        } else {
            row.getCell(FC_DAY).setCellValue("Day/ Time");
        }
        // SHORT BREAK header has no bottom border (matches IT.xlsx G8 border)
        row.getCell(brkCol).setCellStyle(st.brkHdrCell);
        row.getCell(brkCol).setCellValue("11.00 AM to 11.15 AM");
        row.getCell(lngCol).setCellValue("1.15 PM to 2.00 PM");
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
    // Columns (0-based, Faculty FC layout — no Class col):
    //   D(3)=SUBECT NAME  E(4)=CLASS  F(5)=LOAD IN HOURS

    private void facFooter(XSSFSheet sh, Styles st, String facName,
                            List<Timetable> rows, Map<String,String> nc, int afterRow) {
        int r = afterRow;
        emptyRow(sh, st, r++, 15f, FC_NCOLS);   // blank gap

        // Header row: SUBECT NAME | CLASS | LOAD IN HOURS  (matching IT.xlsx RSL layout)
        XSSFRow hdr = row(sh, r, 27.75f);
        fillRow(hdr, st.emptyCell, FC_NCOLS);
        cell(hdr, 4, st.tblHdrCell).setCellValue("SUBECT NAME");
        cell(hdr, 5, st.tblHdrCell).setCellValue("CLASS");
        cell(hdr, 6, st.tblHdrCell).setCellValue("LOAD IN HOURS");
        r++;

        // subject → class → count (deduplicated by slot, not double-counting lab windows)
        Map<String, Map<String, Long>> subjClassHours = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (Timetable t : rows) {
            // unique key: day + timeSlot + subject + batch (avoids counting merged lab window twice)
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
            if (NO_FAC.contains(subj)) continue;  // skip VL/Library/Audit in load table
            for (Map.Entry<String, Long> ce : e.getValue().entrySet()) {
                XSSFRow dr = row(sh, r, 27.75f);
                fillRow(dr, st.emptyCell, FC_NCOLS);
                cell(dr, 4, st.tblDataCell).setCellValue(subj);
                cell(dr, 5, st.tblDataCell).setCellValue(abbrevClass(ce.getKey()));
                cell(dr, 6, st.tblDataCell).setCellValue(String.valueOf(ce.getValue()));
                total += ce.getValue();
                r++;
            }
        }

        // TOTAL row
        XSSFRow totRow = row(sh, r, 30f);
        fillRow(totRow, st.emptyCell, FC_NCOLS);
        cell(totRow, 4, st.tblTotCell).setCellValue("TOTAL");
        cell(totRow, 6, st.tblTotCell).setCellValue(String.valueOf(total));
        r++;

        emptyRow(sh, st, r++, 15f, FC_NCOLS);   // gap before Date
        XSSFRow dv = row(sh, r, 24.75f);
        fillRow(dv, st.emptyCell, FC_NCOLS);
        cell(dv, 2, st.footSm).setCellValue("Date:");
        cell(dv, 9, st.footSm).setCellValue("Version 3E.0");
        r++;

        while (r < afterRow + 24) emptyRow(sh, st, r++, 15f, FC_NCOLS);

        XSSFRow names = row(sh, r, 28.5f);
        fillRow(names, st.emptyCell, FC_NCOLS);
        cell(names, 2, st.footHdr).setCellValue("PROF. R. A. NIKAM");
        cell(names, 5, st.footHdr).setCellValue("DR. A. A.  KADAM");
        cell(names, 9, st.footHdr).setCellValue("DR. S. B. THAKARE");
        merge(sh, r, r, 2, 3); merge(sh, r, r, 5, 7); merge(sh, r, r, 9, 11);
        r++;
        XSSFRow titles = row(sh, r, 20.25f);
        fillRow(titles, st.emptyCell, FC_NCOLS);
        cell(titles, 2, st.footSm).setCellValue("TIME TABLE COORDINATOR");
        cell(titles, 5, st.footSm).setCellValue("HOD");
        cell(titles, 9, st.footSm).setCellValue("PRINCIPAL");
        merge(sh, r, r, 2, 3); merge(sh, r, r, 5, 7); merge(sh, r, r, 9, 11);
    }

    /* ═══════════════════════════ FOOTER — CLASS (Template D) ════════════════ */
    // Columns (0-based, CC layout with Day=C(2) and Class=D(3)):
    //   Theory: C(2)=Subject  D(3)=Staff(full name)
    //   Lab:    G(6)=Subject  H(7)=Batch(comma list)  I(8)=Staff  J(9)=LAB room

    private void classFooter(XSSFSheet sh, Styles st, String cls,
                              List<Timetable> rows, Map<String,String> nc, int afterRow) {
        int r = afterRow;

        // Batch roll-no legend
        String legend = batchLegend(cls);
        if (!legend.isEmpty()) {
            XSSFRow lr = row(sh, r, 24.75f);
            fillRow(lr, st.emptyCell, CC_NCOLS);
            cell(lr, 2, st.footSm).setCellValue(legend);
            merge(sh, r, r, 2, 12);
            r++;
        }
        emptyRow(sh, st, r++, 15.75f, CC_NCOLS);

        // Header row — matches IT.xlsx CLASS_SE row 18
        XSSFRow hdr = row(sh, r, 29.25f);
        fillRow(hdr, st.emptyCell, CC_NCOLS);
        cell(hdr, 2, st.tblHdrCell).setCellValue("Subject");
        cell(hdr, 3, st.tblHdrCell).setCellValue("Staff");
        cell(hdr, 6, st.tblHdrCell).setCellValue("Subject");
        cell(hdr, 7, st.tblHdrCell).setCellValue("Batch");
        cell(hdr, 8, st.tblHdrCell).setCellValue("Staff");
        cell(hdr, 9, st.tblHdrCell).setCellValue("LAB");
        r++;

        // Theory subjects (unique, deduplicated; skip VL/Audit/no-faculty entries)
        Map<String, String> theorySubjFac = new LinkedHashMap<>();
        for (Timetable t : rows) {
            if ("Practical".equalsIgnoreCase(t.getLectureType()) ||
                "Lab".equalsIgnoreCase(t.getLectureType())) continue;
            String subj = subjDisp(t, nc);
            if (NO_FAC.contains(subj)) continue;  // skip VL, Audit, Internship
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
            if ("LIBRARY".equals(subj) || NO_FAC.contains(subj)) continue; // skip Library
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
            XSSFRow dr = row(sh, r, 29.25f);
            fillRow(dr, st.emptyCell, CC_NCOLS);
            if (i < tList.size()) {
                cell(dr, 2, st.tblDataCell).setCellValue(tList.get(i).getKey());
                cell(dr, 3, st.tblDataCell).setCellValue(tList.get(i).getValue());
            }
            if (i < lList.size()) {
                String[] la = lList.get(i);
                String batches = String.join(",", labBatches.getOrDefault(la[0], new LinkedHashSet<>()));
                cell(dr, 6, st.tblDataCell).setCellValue(la[0]);
                cell(dr, 7, st.tblDataCell).setCellValue(batches);
                cell(dr, 8, st.tblDataCell).setCellValue(la[1]);
                cell(dr, 9, st.tblDataCell).setCellValue(la[2]);
            }
            r++;
        }

        // Class Teacher block (right side, cols G:I)
        emptyRow(sh, st, r++, 15.75f, CC_NCOLS);
        XSSFRow ct = row(sh, r, 29.25f);
        fillRow(ct, st.emptyCell, CC_NCOLS);
        cell(ct, 6, st.tblHdrCell).setCellValue("Class Teacher:");
        merge(sh, r, r, 6, 9);
        r++;

        while (r < afterRow + 15) emptyRow(sh, st, r++, 15.75f, CC_NCOLS);
        XSSFRow dv = row(sh, r, 29.25f);
        fillRow(dv, st.emptyCell, CC_NCOLS);
        cell(dv, 2, st.footSm).setCellValue("Date:");
        cell(dv, 9, st.footSm).setCellValue("Version: 3B.0");
        r++;

        while (r < afterRow + 22) emptyRow(sh, st, r++, 15.75f, CC_NCOLS);

        XSSFRow names = row(sh, r, 38.25f);
        fillRow(names, st.emptyCell, CC_NCOLS);
        cell(names, 2, st.footHdr).setCellValue("PROF.R. A. NIKAM");
        cell(names, 6, st.footHdr).setCellValue("DR. A. A KADAM");
        cell(names, 10, st.footHdr).setCellValue("DR. S. B. THAKARE");
        merge(sh, r, r, 2, 4); merge(sh, r, r, 6, 8); merge(sh, r, r, 10, 12);
        r++;
        XSSFRow titles = row(sh, r, 15.75f);
        fillRow(titles, st.emptyCell, CC_NCOLS);
        cell(titles, 2, st.footSm).setCellValue("TIME TABLE COORDINATOR");
        cell(titles, 6, st.footSm).setCellValue("HOD");
        cell(titles, 10, st.footSm).setCellValue("PRINCIPAL");
        merge(sh, r, r, 2, 4); merge(sh, r, r, 6, 8); merge(sh, r, r, 10, 12);
    }

    /* ═══════════════════════════ FOOTER — LAB (Template B) ══════════════════ */

    private void labFooter(XSSFSheet sh, Styles st, String labName,
                            List<Timetable> rows, Map<String,String> nc, int afterRow) {
        int r = afterRow;
        row(sh, r++, 18f);

        // Lab name header row
        XSSFRow labTitle = row(sh, r, 20f);
        cell(labTitle, 2, st.tblHdrCell).setCellValue("LAB NAME: " + labName);
        merge(sh, r, r, 2, 11);
        r++;

        // Occupancy header
        XSSFRow hdr = row(sh, r, 22f);
        cell(hdr, 2, st.tblHdrCell).setCellValue("SUBECT NAME");
        cell(hdr, 5, st.tblHdrCell).setCellValue("BATCH");
        cell(hdr, 7, st.tblHdrCell).setCellValue("OCCUPANCY IN HOURS");
        merge(sh, r, r, 2, 4); merge(sh, r, r, 5, 6); merge(sh, r, r, 7, 11);
        r++;

        Map<String, Long> subjHours = rows.stream()
            .collect(Collectors.groupingBy(t -> subjDisp(t, nc) + "|" + s(t.getBatch()),
                Collectors.counting()));
        long total = 0;
        for (Map.Entry<String, Long> e : new TreeMap<>(subjHours).entrySet()) {
            String[] parts = e.getKey().split("\\|", 2);
            XSSFRow dr = row(sh, r, 22f);
            cell(dr, 2, st.tblDataCell).setCellValue(parts[0]);
            cell(dr, 5, st.tblDataCell).setCellValue(parts.length > 1 ? parts[1] : "");
            cell(dr, 7, st.tblDataCell).setCellValue(e.getValue());
            merge(sh, r, r, 2, 4); merge(sh, r, r, 5, 6); merge(sh, r, r, 7, 11);
            total += e.getValue();
            r++;
        }
        XSSFRow totRow = row(sh, r, 22f);
        cell(totRow, 2, st.tblTotCell).setCellValue("TOTAL");
        cell(totRow, 7, st.tblTotCell).setCellValue(total);
        merge(sh, r, r, 2, 6); merge(sh, r, r, 7, 11);
        r++;

        while (r < afterRow + 14) row(sh, r++, 16f);
        XSSFRow dv = row(sh, r, 26f);
        cell(dv, 2, st.footSm).setCellValue("Date:");
        cell(dv, 9, st.footSm).setCellValue("Version: 3D.0");
        r++;

        while (r < afterRow + 22) row(sh, r++, 16f);
        XSSFRow names = row(sh, r, 28f);
        cell(names, 2, st.footHdr).setCellValue("PROF. R. A. NIKAM");
        cell(names, 6, st.footHdr).setCellValue("DR. A. A.  KADAM");
        cell(names, 10, st.footHdr).setCellValue("DR. S. B. THAKARE");
        merge(sh, r, r, 2, 4); merge(sh, r, r, 6, 8); merge(sh, r, r, 10, 11);
        r++;
        XSSFRow titles = row(sh, r, 22f);
        cell(titles, 2, st.footSm).setCellValue("TIME TABLE COORDINATOR");
        cell(titles, 6, st.footSm).setCellValue("HOD");
        cell(titles, 10, st.footSm).setCellValue("PRINCIPAL");
        merge(sh, r, r, 2, 4); merge(sh, r, r, 6, 8); merge(sh, r, r, 10, 11);
    }

    /** Batch roll-number legend string per class. */
    private String batchLegend(String cls) {
        if ("SE(IT)".equals(cls))
            return "BATCHES:  S1 ROLL NO. 52201-5220   S2 ROLL NO. 5221-5240   S3 ROLL NO. 5241-5260   S4 ROLL NO. 5261-5275";
        if ("TE(IT)".equals(cls))
            return "BATCHES:  T1 ROLL NO. 5301-5320   T2 ROLL NO. 5321-5340   T3 ROLL NO. 5341-5360   T4 ROLL NO. 5361-5374";
        if ("BE(IT)".equals(cls))
            return "BATCHES:  B1 ROLL NO. 5401-5420   B2 ROLL NO. 5421-5440   B3 ROLL NO. 5441-5460   B4 ROLL NO. 5461-5477";
        return "";
    }

    /* ═══════════════════════════ FOOTER (MASTER only) ═══════════════════════ */

    private void masterFooter(XSSFSheet sh, Styles st, int startRow) {
        // ri = startRow = row 25 (0-indexed) = Excel row 26

        /* Row 25 (Excel 26): blank gap 34.5pt */
        row(sh, startRow, 34.5f);

        /* Row 26 (Excel 27, 30.75pt): BATCHES row 1 — B27:M27 */
        int r27 = startRow + 1;
        XSSFRow rb1 = row(sh, r27, 30.75f);
        cell(rb1, 1, st.footSm).setCellValue(
            " BATCHES -  S1 ROLL NO. 52201-5220       S2 ROLL NO.5221-5240    " +
            "S3 ROLL NO.5241-5260    S4 ROLL NO. 5261-5275       " +
            "T1 ROLL NO 5301-5320   T2 ROLL NO. 5321-5340   T3 ROLL NO. 5341-5360        ");
        merge(sh, r27, r27, 1, 12);  // B:M

        /* Row 27 (Excel 28, 29.25pt): BATCHES row 2 — E28:M28 */
        int r28 = r27 + 1;
        XSSFRow rb2 = row(sh, r28, 29.25f);
        cell(rb2, 4, st.footSm).setCellValue(
            " T4 ROLL NO. 5361-5374                                 " +
            "B1 ROLL NO.   5401-5420   B2 ROLL NO 5421-5440     " +
            "B3 ROLL NO 5441-5460    B4 ROLL NO. 5461-54774");
        merge(sh, r28, r28, 4, 12);  // E:M

        /* Row 28 (Excel 29, 29.25pt): blank */
        row(sh, r28 + 1, 29.25f);

        /* Row 29 (Excel 30, 29.25pt): SE | TE | BE headers */
        int r30 = r28 + 2;
        XSSFRow rHdr = row(sh, r30, 29.25f);
        cell(rHdr, 2, st.footHdr).setCellValue("SE");
        cell(rHdr, 6, st.footHdr).setCellValue("TE");
        cell(rHdr, 11, st.footHdr).setCellValue("BE");

        /* Faculty lines rows 30-37 (Excel 31-38) */
        String[][] seFac = {
            {"DR. S. R. KOKANE  -Open Elective-II, MIL"},
            {"PROF. R. A. NIKAM- DBMS,DBMSL"},
            {"PROF. S. S. KHOTE & PROF. A.R. DODKE - PA,DM & SM(S1,S2)"},
            {"PROF.P. G. KHAIRE-CG,CGL"},
            {"PROF. R. S. LAVHE - MIL"},
            {"PROF. A. N. KALAL & DR. A. A. KADAM-EC"},
            {"PROF. D. P. RANKHAMBE-ES"},
            {"PROF. RASHMI KENVAT- P & S"}
        };
        String[][] teFac = {
            {"PROF. D. P. RANKHAMBE- DSBDA, DSBDL"},
            {"PROF. R. S. LAVHE- CNS,CNSL(T1,T2)"},
            {"PROF. A. N. KALAL -  WAD, LP-II(WAD),INTERNSHIP"},
            {"PROF. A. R.  DODKE- EL-II,LP-II"},
            {"PROF. S. S. KHOTE- CNSL(T3 & T4)"},
            {"PROF.P. G. KHAIRE- EAC(Hon)"},
            {},{}
        };
        String[][] beFac = {
            {"DR. A. A. KADAM- SE, LP-V(DS)"},
            {"PROF. S. R. KOKANE- DS, LP-V(DS)"},
            {"PROF. S. S. KHOTE- EL-V"},
            {"PROF. A.N.KALAL- ISM(hon)"},
            {"PROF. R. A. NIKAM- EL-VI, LP-VI(El-VI)(B3,B4)"},
            {"PROF. D. P. RANKHAMBE-SEMINAR(Hon)"},
            {"PROF.P. G. KHAIRE-LP-VI(El-VI)(B1,B2)"},
            {}
        };
        float[] facHts = {29.25f,29.25f,29.25f,29.25f,36.0f,27.75f,32.25f,28.5f};

        for (int i = 0; i < 8; i++) {
            int fr = r30 + 1 + i;
            XSSFRow fRow = row(sh, fr, facHts[i]);
            if (seFac[i].length > 0) {
                cell(fRow, 2, st.footSm).setCellValue(seFac[i][0]);
                // C:E merges for rows 31,32,35 (POI 30,31,34)
                if (i == 0 || i == 1 || i == 4)
                    merge(sh, fr, fr, 2, 4);
            }
            if (teFac[i].length > 0) {
                cell(fRow, 6, st.footSm).setCellValue(teFac[i][0]);
                // G:I merge for row 34 only (POI 33, i=3)
                if (i == 3) merge(sh, fr, fr, 6, 8);
            }
            if (beFac[i].length > 0) {
                cell(fRow, 11, st.footSm).setCellValue(beFac[i][0]);
                // L:N merges for rows 35,36 (POI 34,35, i=4,5)
                if (i == 4 || i == 5) merge(sh, fr, fr, 11, 13);
            }
        }

        /* Version row (Excel 39, 28.5pt): Version at J */
        int r39 = r30 + 9;
        XSSFRow rVer = row(sh, r39, 28.5f);
        cell(rVer, 9, st.footSm).setCellValue("Version: 3A.0");

        /* Date row (Excel 40, 36.0pt) */
        int r40 = r39 + 1;
        XSSFRow rDate = row(sh, r40, 36.0f);
        cell(rDate, 2, st.footSm).setCellValue("Date");

        /* Blank rows 41-46 (5 rows × 15.75pt for signatures) */
        int r41 = r40 + 1;
        row(sh, r41, 21.0f);
        for (int i = 1; i <= 5; i++) row(sh, r41 + i, 15.75f);

        /* Row 47 (Excel 47, 36.0pt): Names — D47:F47, H47:J47, M47 standalone */
        int r47 = r41 + 6;
        XSSFRow rNames = row(sh, r47, 36.0f);
        cell(rNames, 3, st.footHdr).setCellValue("PROF.R. A. NIKAM");
        cell(rNames, 7, st.footHdr).setCellValue("DR. A. A. KADAM");
        cell(rNames, 12, st.footHdr).setCellValue("DR. S. B. THAKARE");
        merge(sh, r47, r47, 3, 5);  // D:F
        merge(sh, r47, r47, 7, 9);  // H:J

        /* Row 48 (Excel 48, 28.5pt): Titles — D48:F48, H48:J48, M48 standalone */
        int r48 = r47 + 1;
        XSSFRow rTitles = row(sh, r48, 28.5f);
        cell(rTitles, 3, st.footSm).setCellValue("TIME TABLE COORDINATOR");
        cell(rTitles, 7, st.footSm).setCellValue("HOD");
        cell(rTitles, 12, st.footSm).setCellValue("PRINCIPAL");
        merge(sh, r48, r48, 3, 5);
        merge(sh, r48, r48, 7, 9);

        /* Row 49 (Excel 49, 15.75pt): blank */
        row(sh, r48 + 1, 15.75f);
    }

    /* ═══════════════════════════ LOGO EMBEDDING ═════════════════════════════ */

    private void addLogos(XSSFSheet sh, XSSFWorkbook wb, byte[] clgLogo, byte[] ownerLogo) {
        if (clgLogo == null && ownerLogo == null) return;
        XSSFDrawing drawing = sh.createDrawingPatriarch();

        if (clgLogo != null) {
            // College crest: from(col=2,row=0,dx=85680,dy=38160) to(col=2,row=3,dx=1437840,dy=161640)
            int picIdx = wb.addPicture(clgLogo, Workbook.PICTURE_TYPE_PNG);
            XSSFClientAnchor a = new XSSFClientAnchor(85680, 38160, 1437840, 161640, 2, 0, 2, 3);
            a.setAnchorType(ClientAnchor.AnchorType.MOVE_AND_RESIZE);
            drawing.createPicture(a, picIdx);
        }
        if (ownerLogo != null) {
            // Person photo: from(col=12,row=0,dx=673560,dy=23400) to(col=12,row=3,dx=2531880,dy=301680)
            int picIdx = wb.addPicture(ownerLogo, Workbook.PICTURE_TYPE_PNG);
            XSSFClientAnchor a = new XSSFClientAnchor(673560, 23400, 2531880, 301680, 12, 0, 12, 3);
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
        return entries.stream()
            .sorted(Comparator.comparing(t -> s(t.getBatch())))
            .map(t -> { String d = subjDisp(t,nc); return s(t.getBatch())+"-"+d+facSuffix(t,d); })
            .collect(Collectors.joining(", "));
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
        try { return Files.readAllBytes(Paths.get(path)); }
        catch (Exception e) { return null; }
    }
    private boolean nb(String s) { return s!=null&&!s.isBlank(); }
    private String   s(String v)  { return v!=null?v:""; }

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

    /** A4 landscape, scale 50%, fit-to-page. Applied to every sheet. */
    private void applyPageSetup(XSSFSheet sh) {
        sh.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE);
        sh.getPrintSetup().setLandscape(true);
        sh.getPrintSetup().setScale((short)50);
        sh.getPrintSetup().setFitWidth((short)1);
        sh.getPrintSetup().setFitHeight((short)1);
        sh.getPrintSetup().setHResolution((short)300);
        sh.getPrintSetup().setVResolution((short)300);
        sh.setFitToPage(true);
        sh.setMargin(org.apache.poi.ss.usermodel.PageMargin.LEFT,   1.06);
        sh.setMargin(org.apache.poi.ss.usermodel.PageMargin.RIGHT,  0.70);
        sh.setMargin(org.apache.poi.ss.usermodel.PageMargin.TOP,    0.58);
        sh.setMargin(org.apache.poi.ss.usermodel.PageMargin.BOTTOM, 0.75);
    }

    /* ═══════════════════════════ STYLES ═════════════════════════════════════ */

    private static final class Styles {
        /* Header / metadata */
        final XSSFCellStyle inst22, meta, masterTitle, ay24b, dept36b, wef26b;
        /* Faculty/class header rows (bold 18pt, left-aligned, no border) */
        final XSSFCellStyle facMetaL;
        /* Grid */
        final XSSFCellStyle hdrCell, brkHdrCell;
        final XSSFCellStyle dayCell, clsCell;
        final XSSFCellStyle thCell, labCell, emptyCell, brkData, lngData;
        /* Footer */
        final XSSFCellStyle footSm, footHdr;
        /* Sub-tables (faculty load / class subject-staff / lab occupancy) */
        final XSSFCellStyle tblHdrCell, tblDataCell, tblTotCell;

        Styles(XSSFWorkbook wb) {
            /* ── Fonts ── */
            XSSFFont f22   = tnr(wb,22,false);
            XSSFFont f18   = tnr(wb,18,false);
            XSSFFont f24b  = tnr(wb,24,true);
            XSSFFont f36b  = tnr(wb,36,true);
            XSSFFont f26b  = tnr(wb,26,true);
            XSSFFont f26   = tnr(wb,26,false);
            XSSFFont f9    = tnr(wb, 9,false);
            XSSFFont f10   = tnr(wb,10,false);
            XSSFFont f22b  = tnr(wb,22,true);

            XSSFFont f18b  = tnr(wb,18,true);

            /* ── No-border styles (header/footer rows) ── */
            inst22      = nb(wb, f22, CENTER, true);
            meta        = nb(wb, f9,  LEFT,   false);
            masterTitle = nb(wb, f18, CENTER, false);
            ay24b       = nb(wb, f24b,CENTER, false);
            dept36b     = nb(wb, f36b,CENTER, false);
            wef26b      = nb(wb, f26b,CENTER, false);
            facMetaL    = nb(wb, f18b,LEFT,   false);

            /* ── Grid header row (thin borders, white fill, bold 26pt) ── */
            hdrCell    = bordered(wb,255,255,255, f26b,  CENTER, true,  true, true, true, true);
            brkHdrCell = bordered(wb,255,255,255, f26b,  CENTER, true,  true, false,true, true);
            // brkHdrCell has no bottom border (matches IT.xlsx G8)

            /* ── Day and Class labels ── */
            dayCell    = bordered(wb,255,255,255, f26b,  CENTER, false, true, true, true, true);
            clsCell    = bordered(wb,255,255,255, f26b,  CENTER, false, true, true, true, true);

            /* ── Data cells: white fill, 26pt Times New Roman ── */
            // Theory: wrap=false (matches IT.xlsx E9)
            thCell     = bordered(wb,255,255,255, f26,   CENTER, false, true, true, true, true);
            // Lab: wrap=true (matches IT.xlsx K9)
            labCell    = bordered(wb,255,255,255, f26,   CENTER, true,  true, true, true, true);
            emptyCell  = bordered(wb,255,255,255, f10,   CENTER, false, true, true, true, true);

            /* ── Break columns: gray fill #D8D8D8 ── */
            brkData    = bordered(wb,0xD8,0xD8,0xD8, f9, CENTER, true,  true, true, true, true);
            lngData    = bordered(wb,0xD8,0xD8,0xD8, f9, CENTER, true,  true, true, true, true);

            /* ── Footer ── */
            footSm     = nb(wb, f9,  LEFT,   false);
            footHdr    = nb(wb, f22b,CENTER, false);

            /* ── Sub-table cells (thin borders, white fill) ── */
            XSSFFont f16b = tnr(wb, 16, true);
            XSSFFont f16  = tnr(wb, 16, false);
            tblHdrCell  = bordered(wb,255,255,255, f16b, CENTER, true,  true,true,true,true);
            tblDataCell = bordered(wb,255,255,255, f16,  CENTER, false, true,true,true,true);
            tblTotCell  = bordered(wb,255,255,255, f16b, CENTER, false, true,true,true,true);
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
