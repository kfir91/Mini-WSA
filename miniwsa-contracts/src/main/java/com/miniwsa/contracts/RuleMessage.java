package com.miniwsa.contracts;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class RuleMessage {

    @NotBlank
    private String id;

    @NotBlank
    private String name;

    @NotBlank
    private String message;

    @NotNull
    private Severity severity;

    @NotNull
    private AttackCategory category;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public AttackCategory getCategory() {
        return category;
    }

    public void setCategory(AttackCategory category) {
        this.category = category;
    }
}
