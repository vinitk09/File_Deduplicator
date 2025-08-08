package com.example.demo.web;

public class FileInfo {
    private final String path;
    private final String category;
    private boolean selected;

    public FileInfo(String path, String category) {
        this.path = path;
        this.category = category;
        this.selected = false;
    }

    // Getters and Setters
    public String getPath() { return path; }
    public String getCategory() { return category; }
    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }
}