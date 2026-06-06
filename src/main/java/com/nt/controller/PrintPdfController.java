package com.nt.controller;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import com.nt.service.TimetableXlsxExportService;

import jakarta.servlet.http.HttpServletResponse;

@Controller
public class PrintPdfController {

    @Autowired
    private TimetableXlsxExportService xlsxService;

    private static final String[] LIBREOFFICE_PATHS = {
        // Windows (standard install locations)
        "C:\\Program Files\\LibreOffice\\program\\soffice.exe",
        "C:\\Program Files (x86)\\LibreOffice\\program\\soffice.exe",
        // Linux / Mac (on PATH)
        "libreoffice",
        "soffice"
    };

    @GetMapping("/print-timetable")
    public void printTimetable(HttpServletResponse response) throws Exception {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("atg-print-");
            Path xlsxFile = tempDir.resolve("timetable.xlsx");
            Files.write(xlsxFile, xlsxService.generate());

            Path pdfFile = tempDir.resolve("timetable.pdf");
            boolean converted = false;

            if (isWindows()) {
                converted = convertWithLibreOffice(xlsxFile, pdfFile);
                if (!converted) converted = convertWithExcel(xlsxFile, pdfFile); 
            } else {
                converted = convertWithLibreOffice(xlsxFile, pdfFile);
            }


            if (!converted) {
                showInstallPage(response);
                return ;
            }

            byte[] pdfBytes = Files.readAllBytes(pdfFile);
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"IT_Timetable_AUTO_GENERATED.pdf\"");
            response.setContentLength(pdfBytes.length);
            response.getOutputStream().write(pdfBytes);

        } finally {
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

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private boolean convertWithExcel(Path xlsxFile, Path pdfFile) {
        try {
            // Use PowerShell to automate Excel COM — works if MS Excel is installed
            String psScript = String.format(
                "$ErrorActionPreference='Stop';" +
                "$xl = New-Object -ComObject Excel.Application;" +
                "$xl.Visible = $false;" +
                "$xl.DisplayAlerts = $false;" +
                "$wb = $xl.Workbooks.Open('%s');" +
                "$wb.ExportAsFixedFormat(0, '%s');" +
                "$wb.Close($false);" +
                "$xl.Quit();" +
                "[System.Runtime.Interopservices.Marshal]::ReleaseComObject($xl) | Out-Null",
                xlsxFile.toAbsolutePath().toString().replace("'", "''"),
                pdfFile.toAbsolutePath().toString().replace("'", "''")
            );
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell", "-NonInteractive", "-NoProfile", "-Command", psScript
            );
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            try (InputStream is = proc.getInputStream()) {
                is.transferTo(java.io.OutputStream.nullOutputStream());
            }
            proc.waitFor();
            return pdfFile.toFile().exists() && pdfFile.toFile().length() > 0;
        } catch (Exception e) {
            System.err.println("[PrintPdf] Excel conversion failed: " + e.getMessage());
            return false;
        }
    }

    private boolean convertWithLibreOffice(Path xlsxFile, Path pdfFile) {
        String loPath = findLibreOffice();
        if (loPath == null) return false;
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    loPath, "--headless", "--norestore", "--convert-to", "pdf",
                    "--outdir", pdfFile.getParent().toString(),
                    xlsxFile.toString()
            );
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            try (InputStream is = proc.getInputStream()) {
                is.transferTo(java.io.OutputStream.nullOutputStream());
            }
            proc.waitFor();
            return pdfFile.toFile().exists() && pdfFile.toFile().length() > 0;
        } catch (Exception e) {
            System.err.println("[PrintPdf] LibreOffice conversion failed: " + e.getMessage());
            return false;
        }
    }

    private void showInstallPage(HttpServletResponse response) throws Exception {
        response.setStatus(200);
        response.setContentType("text/html;charset=UTF-8");
        response.getWriter().write("""
            <!DOCTYPE html>
            <html><head><meta charset="UTF-8"><title>LibreOffice Required</title>
            <style>
              body{font-family:Arial,sans-serif;display:flex;align-items:center;justify-content:center;
                   min-height:100vh;margin:0;background:#f8fafc;}
              .box{background:#fff;border:1px solid #e2e8f0;border-radius:12px;padding:40px;
                   max-width:540px;text-align:center;box-shadow:0 4px 20px rgba(0,0,0,0.08);}
              h2{color:#dc2626;margin:0 0 14px;font-size:20px;}
              p{color:#475569;font-size:14px;line-height:1.7;margin:0 0 18px;}
              .steps{background:#f1f5f9;border-radius:8px;padding:16px 20px;text-align:left;
                     font-size:13px;color:#334155;margin-bottom:22px;line-height:1.8;}
              a.btn{display:inline-block;padding:10px 22px;background:#4f46e5;color:#fff;
                    border-radius:8px;text-decoration:none;font-weight:700;font-size:14px;margin-right:8px;}
              a.back{display:inline-block;padding:10px 18px;background:#f1f5f9;color:#475569;
                     border-radius:8px;text-decoration:none;font-weight:600;font-size:14px;}
            </style></head>
            <body><div class="box">
              <h2>&#9888; PDF Converter Not Found</h2>
              <p>The <strong>Print</strong> feature requires either <strong>Microsoft Excel</strong>
                 or <strong>LibreOffice</strong> to convert the timetable to PDF.
                 Neither was found on this computer.</p>
              <div class="steps">
                <strong>Windows:</strong> Install Microsoft Excel <em>or</em> LibreOffice (free)<br><br>
                <strong>Linux / Mac:</strong> Install LibreOffice
                <ol style="margin-top:8px;">
                  <li>Download &amp; install <strong>LibreOffice</strong> (free)</li>
                  <li>Use the <strong>default install path</strong> during setup</li>
                  <li>Restart ATG, then click <strong>Print</strong> again</li>
                </ol>
              </div>
              <a class="btn" href="https://www.libreoffice.org/download/download-libreoffice/" target="_blank">
                Download LibreOffice</a>
              <a class="back" href="/view-timetable">&#8592; Go Back</a>
            </div></body></html>
            """);
    }

    private String findLibreOffice() {
        for (String path : LIBREOFFICE_PATHS) {
            try {
                if (path.contains("\\") || path.contains("/")) {
                    if (!new File(path).exists()) continue;
                }
                // Verify the binary actually works (not just a broken wrapper)
                Process test = new ProcessBuilder(path, "--version")
                        .redirectErrorStream(true).start();
                String out = new String(test.getInputStream().readAllBytes()).trim();
                int code = test.waitFor();
                if (code == 0 && out.toLowerCase().contains("libreoffice")) return path;
            } catch (Exception ignored) {}
        }
        return null;
    }
}
