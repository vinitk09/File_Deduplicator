package com.example.demo.web;

public class Rule {
    public enum RuleType {
        PATH_CONTAINS, FILE_EXTENSION, FILE_NAME_REGEX
    }

    private RuleType type;
    private String pattern;
    private String category;

    // Constructors
    public Rule() {}
    public Rule(RuleType type, String pattern, String category) {
        this.type = type;
        this.pattern = pattern;
        this.category = category;
    }

    // Getters and Setters
    public RuleType getType() { return type; }
    public void setType(RuleType type) { this.type = type; }
    public String getPattern() { return pattern; }
    public void setPattern(String pattern) { this.pattern = pattern; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}