package com.example.demo.web;

import com.example.demo.service.LoggingService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Controller
@RequestMapping("/duplicates")
public class FileController {
    private final FileService fileService;
    private final LoggingService loggingService;

    public FileController(FileService fileService, LoggingService loggingService) {
        this.fileService = fileService;
        this.loggingService = loggingService;
    }

    @GetMapping
    public String showDuplicates(Model model) throws IOException {
        model.addAttribute("duplicateGroups", fileService.findDuplicates());
        return "files";
    }

    @PostMapping("/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteFiles(@RequestBody Map<String, List<String>> request) {
        List<String> filesToDelete = request.get("filesToDelete");
        if (filesToDelete == null || filesToDelete.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "No files specified for deletion"
            ));
        }

        List<String> deletedFiles = fileService.deleteFiles(filesToDelete);
        return ResponseEntity.ok(Map.of(
                "success", !deletedFiles.isEmpty(),
                "message", deletedFiles.isEmpty() ? "No files were deleted" :
                        deletedFiles.size() + " files deleted successfully",
                "deletedFiles", deletedFiles
        ));
    }

    @PostMapping("/rules")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addRule(@RequestBody Rule rule) {
        try {
            fileService.addRule(rule);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Rule added successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Error adding rule: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/rules")
    @ResponseBody
    public List<Rule> getRules() {
        return fileService.getRules();
    }

    @GetMapping("/report")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> generateReport(HttpServletResponse response) {
        try {
            String reportPath = loggingService.generateReport();
            if (reportPath != null) {
                // Set headers to force download
                Path path = Paths.get(reportPath);
                String filename = path.getFileName().toString();

                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                        .body(Map.of(
                                "success", true,
                                "message", "Report generated successfully",
                                "reportPath", reportPath
                        ));
            } else {
                throw new Exception("Failed to generate report file");
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Error generating report: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/logs")
    @ResponseBody
    public ResponseEntity<List<String>> getActivityLogs() {
        return ResponseEntity.ok(loggingService.getActivityLog());
    }
}