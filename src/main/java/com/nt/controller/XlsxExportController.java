package com.nt.controller;

import com.nt.service.TimetableXlsxExportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class XlsxExportController {

    @Autowired
    private TimetableXlsxExportService xlsxService;

    @GetMapping("/export-xlsx")
    public ResponseEntity<byte[]> exportXlsx() {
        try {
            byte[] data = xlsxService.generate();
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"IT_Timetable_AY2025-26_SemII.xlsx\"")
                .contentLength(data.length)
                .body(data);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
}
