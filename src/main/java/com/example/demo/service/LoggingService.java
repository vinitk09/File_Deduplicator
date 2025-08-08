package com.example.demo.service;

import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class LoggingService {
    private static final String LOG_DIR = "logs";
    private static final String LOG_FILE = "deduplicator.log";
    private static final String REPORT_DIR = "reports";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final List<String> activityLog = new ArrayList<>();

    public LoggingService() {
        try {
            Files.createDirectories(Paths.get(LOG_DIR));
            Files.createDirectories(Paths.get(REPORT_DIR));
        } catch (IOException e) {
            System.err.println("Failed to create log directories: " + e.getMessage());
        }
    }

    public void log(String message) {
        String timestamp = LocalDateTime.now().format(DATE_FORMAT);
        String logEntry = String.format("[%s] %s", timestamp, message);

        // Add to in-memory log
        activityLog.add(logEntry);

        // Write to file
        try {
            Path logPath = Paths.get(LOG_DIR, LOG_FILE);
            Files.write(logPath, (logEntry + System.lineSeparator()).getBytes(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Failed to write to log file: " + e.getMessage());
        }

        System.out.println(logEntry);
    }

    public String generateReport() {
        StringBuilder reportContent = new StringBuilder();
        reportContent.append("<!DOCTYPE html><html><head><title>Deduplication Report</title>");
        reportContent.append("<style>body{font-family:Arial,sans-serif;margin:20px;}");
        reportContent.append("h1{color:#333;}table{border-collapse:collapse;width:100%;}");
        reportContent.append("th,td{padding:8px;text-align:left;border-bottom:1px solid #ddd;}</style></head><body>");

        reportContent.append("<h1>File Deduplicator Report</h1>");
        reportContent.append("<p>Generated on: ").append(LocalDateTime.now().format(DATE_FORMAT)).append("</p>");

        // Activity Log
        reportContent.append("<h2>Activity Log</h2>");
        reportContent.append("<table><tr><th>Timestamp</th><th>Activity</th></tr>");
        for (String entry : activityLog) {
            String[] parts = entry.split("] ", 2);
            String timestamp = parts[0].substring(1);
            String message = parts.length > 1 ? parts[1] : "";
            reportContent.append("<tr><td>").append(timestamp).append("</td><td>").append(message).append("</td></tr>");
        }
        reportContent.append("</table>");

        reportContent.append("</body></html>");

        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String reportFilename = String.format("report_%s.html", timestamp);
            Path reportPath = Paths.get(REPORT_DIR, reportFilename);

            Files.write(reportPath, reportContent.toString().getBytes(), StandardOpenOption.CREATE);
            log("Generated report: " + reportPath);
            return reportPath.toString();
        } catch (IOException e) {
            log("Failed to generate report: " + e.getMessage());
            return null;
        }
    }

    public List<String> getActivityLog() {
        return new ArrayList<>(activityLog);
    }
}