package com.example.demo.web;

import com.example.demo.service.LoggingService;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.*;
import java.util.stream.*;

@Service
public class FileService {
    private static final List<String> TARGET_DIRS = List.of("C:\\Users\\asus\\Downloads");
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024; // 100MB
    private static final int BUFFER_SIZE = 8192; // 8KB buffer for hashing

    private final List<Rule> userRules = new ArrayList<>();
    private final LoggingService loggingService;
    private final Map<String, List<String>> duplicateFilesCache = new ConcurrentHashMap<>();
    private final Map<String, String> fileCategoriesCache = new ConcurrentHashMap<>();

    public FileService(LoggingService loggingService) {
        this.loggingService = loggingService;
        loggingService.log("FileService initialized with target directories: " + TARGET_DIRS);
    }

    public synchronized void addRule(Rule rule) {
        if (rule == null || rule.getType() == null || rule.getPattern() == null || rule.getCategory() == null) {
            loggingService.log("Attempted to add invalid rule: " + rule);
            throw new IllegalArgumentException("Rule must have type, pattern, and category");
        }

        userRules.add(rule);
        loggingService.log(String.format("Added new rule - Type: %s, Pattern: %s, Category: %s",
                rule.getType(), rule.getPattern(), rule.getCategory()));

        // Clear cache since rules affect categorization
        duplicateFilesCache.clear();
        fileCategoriesCache.clear();
    }

    public List<Rule> getRules() {
        return Collections.unmodifiableList(userRules);
    }

    public Map<String, List<FileInfo>> findDuplicates() throws IOException {
        loggingService.log("Starting duplicate file scan in directories: " + TARGET_DIRS);
        long startTime = System.currentTimeMillis();

        // Clear previous results
        duplicateFilesCache.clear();
        fileCategoriesCache.clear();

        for (String dir : TARGET_DIRS) {
            try (Stream<Path> stream = Files.walk(Paths.get(dir))) {
                stream.parallel()
                        .filter(Files::isRegularFile)
                        .filter(Files::isReadable)
                        .filter(this::isWithinSizeLimit)
                        .forEach(file -> processFile(file, duplicateFilesCache, fileCategoriesCache));
            } catch (IOException e) {
                loggingService.log("Error scanning directory " + dir + ": " + e.getMessage());
                throw e;
            }
        }

        Map<String, List<FileInfo>> results = filterAndConvertResults(duplicateFilesCache, fileCategoriesCache);

        long duration = System.currentTimeMillis() - startTime;
        loggingService.log(String.format(
                "Scan completed in %d ms. Found %d duplicate groups containing %d files total",
                duration, results.size(), results.values().stream().mapToInt(List::size).sum()
        ));

        return results;
    }

    public List<String> deleteFiles(List<String> filePaths) {
        if (filePaths == null || filePaths.isEmpty()) {
            loggingService.log("Delete operation called with empty file list");
            return Collections.emptyList();
        }

        loggingService.log(String.format("Attempting to delete %d files", filePaths.size()));
        List<String> successfullyDeleted = new ArrayList<>();
        List<String> failedDeletions = new ArrayList<>();

        for (String path : filePaths) {
            try {
                if (Files.deleteIfExists(Paths.get(path))) {
                    successfullyDeleted.add(path);
                    loggingService.log("Deleted file: " + path);

                    // Remove from caches
                    removeFileFromCaches(path);
                } else {
                    loggingService.log("File not found for deletion: " + path);
                    failedDeletions.add(path);
                }
            } catch (IOException e) {
                loggingService.log("Failed to delete " + path + ": " + e.getMessage());
                failedDeletions.add(path);
            }
        }

        if (!failedDeletions.isEmpty()) {
            loggingService.log(String.format(
                    "Failed to delete %d files: %s",
                    failedDeletions.size(),
                    String.join(", ", failedDeletions)
            ));
        }

        loggingService.log(String.format(
                "Delete operation completed. Successfully deleted %d of %d files",
                successfullyDeleted.size(), filePaths.size()
        ));

        return successfullyDeleted;
    }

    private void removeFileFromCaches(String filePath) {
        // Remove from file categories cache
        fileCategoriesCache.remove(filePath);

        // Remove from duplicate files cache
        for (Map.Entry<String, List<String>> entry : duplicateFilesCache.entrySet()) {
            entry.getValue().remove(filePath);

            // Remove the group if it now has only one file
            if (entry.getValue().size() < 2) {
                duplicateFilesCache.remove(entry.getKey());
            }
        }
    }

    private void processFile(Path file, Map<String, List<String>> hashMap, Map<String, String> fileCategories) {
        try {
            String filePath = file.toString();
            String hash = calculateFileHash(file);
            String category = determineCategory(file);

            hashMap.computeIfAbsent(hash, k -> new ArrayList<>()).add(filePath);
            fileCategories.put(filePath, category);

            loggingService.log("Processed file: " + filePath + " (Hash: " + hash + ", Category: " + category + ")");
        } catch (IOException e) {
            loggingService.log("Error processing file: " + file + " - " + e.getMessage());
        }
    }

    private String determineCategory(Path file) throws IOException {
        String filePath = file.toString();
        String fileName = file.getFileName().toString();

        // Check user-defined rules first
        for (Rule rule : userRules) {
            if (matchesRule(filePath, fileName, rule)) {
                return rule.getCategory();
            }
        }

        // Fall back to default categorization
        return categorizeFile(filePath);
    }

    private boolean matchesRule(String filePath, String fileName, Rule rule) {
        try {
            switch (rule.getType()) {
                case PATH_CONTAINS:
                    return filePath.contains(rule.getPattern());
                case FILE_EXTENSION:
                    return fileName.toLowerCase().endsWith(rule.getPattern().toLowerCase());
                case FILE_NAME_REGEX:
                    return Pattern.compile(rule.getPattern()).matcher(fileName).find();
                default:
                    return false;
            }
        } catch (Exception e) {
            loggingService.log("Error applying rule " + rule + " to file " + fileName + ": " + e.getMessage());
            return false;
        }
    }

    private String calculateFileHash(Path file) throws IOException {
        try (InputStream is = Files.newInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(is, BUFFER_SIZE)) {
            return DigestUtils.md5DigestAsHex(bis);
        }
    }

    private boolean isWithinSizeLimit(Path file) {
        try {
            long size = Files.size(file);
            if (size > MAX_FILE_SIZE) {
                loggingService.log("Skipping large file: " + file + " (" + size + " bytes)");
                return false;
            }
            return true;
        } catch (IOException e) {
            loggingService.log("Error checking size of file " + file + ": " + e.getMessage());
            return false;
        }
    }

    private Map<String, List<FileInfo>> filterAndConvertResults(
            Map<String, List<String>> hashMap, Map<String, String> fileCategories) {
        return hashMap.entrySet().parallelStream()
                .filter(e -> e.getValue().size() > 1) // Only keep groups with duplicates
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream()
                                .map(path -> new FileInfo(path, fileCategories.get(path)))
                                .collect(Collectors.toList())
                ));
    }

    private String categorizeFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return "Uncategorized";
        }

        File file = new File(filePath);
        String fileName = file.getName().toLowerCase();
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1);

        // Web Development
        if (fileName.matches("(package\\.json|dockerfile|webpack\\.config\\.js|\\.env|pom\\.xml)")) {
            return "Build Config";
        }
        if (fileName.matches(".*\\.(html|css|js|jsx|ts|tsx|vue)$")) {
            return "Web Frontend";
        }
        if (fileName.matches(".*\\.(py|java|php|rb|go|cs)$")) {
            return "Web Backend";
        }
        if (fileName.matches(".*\\.(json|xml|csv|yaml|yml)$")) {
            return "Data Files";
        }
        if (fileName.endsWith(".sql")) {
            return "Database";
        }

        // General Categories
        if (fileName.matches(".*\\.(pdf|docx?|txt|rtf|odt|md)$")) {
            return "Documents";
        }
        if (fileName.matches(".*\\.(jpg|jpeg|png|gif|svg|webp)$")) {
            return "Images";
        }
        if (fileName.matches(".*\\.(mp4|mov|avi|mkv|wmv|flv)$")) {
            return "Videos";
        }
        if (fileName.matches(".*\\.(mp3|wav|aac|flac|ogg)$")) {
            return "Audio";
        }
        if (fileName.matches(".*\\.(zip|rar|7z|tar|gz)$")) {
            return "Archives";
        }
        if (fileName.matches(".*\\.(exe|msi|bat|sh|dll)$")) {
            return "Executables";
        }

        return "Uncategorized";
    }

    // Cache management methods
    public synchronized void clearCache() {
        duplicateFilesCache.clear();
        fileCategoriesCache.clear();
        loggingService.log("Cleared file service caches");
    }

    public int getCacheSize() {
        return duplicateFilesCache.size();
    }
}