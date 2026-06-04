package com.nt.controller;

import com.nt.service.TimetableXlsxExportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

@Controller
public class PrintPdfController {

    @Autowired
    private TimetableXlsxExportService xlsxService;

    @GetMapping("/print-timetable")
    public ResponseEntity<byte[]> printTimetable() {
        Path tempDir = null;
        try {
            // Write XLSX to a temp directory
            tempDir = Files.createTempDirectory("atg-print-");
            Path xlsxFile = tempDir.resolve("timetable.xlsx");
            Files.write(xlsxFile, xlsxService.generate());

            // Convert to PDF using LibreOffice headless
            ProcessBuilder pb = new ProcessBuilder(
                    "libreoffice", "--headless", "--convert-to", "pdf",
                    "--outdir", tempDir.toString(),
                    xlsxFile.toString()
            );
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            int exit = proc.waitFor();

            Path pdfFile = tempDir.resolve("timetable.pdf");
            if (exit != 0 || !pdfFile.toFile().exists()) {
                byte[] errBytes = proc.getInputStream().readAllBytes();
                System.err.println("LibreOffice conversion failed: " + new String(errBytes));
                return ResponseEntity.internalServerError().build();
            }

            byte[] pdfBytes = Files.readAllBytes(pdfFile);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"IT_Timetable_AY2025-26_SemII.pdf\"")
                    .contentLength(pdfBytes.length)
                    .body(pdfBytes);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        } finally {
            // Clean up temp files
            if (tempDir != null) {
                try {
                    Files.walk(tempDir)
                         .sorted(java.util.Comparator.reverseOrder())
                         .map(Path::toFile)
                         .forEach(File::delete);
                } catch (Exception ignored) {}
            }
        }
    }
}
