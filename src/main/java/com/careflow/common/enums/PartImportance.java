package com.careflow.common.enums;

public enum PartImportance {
    CRITICAL(1),
    MAJOR(2),
    NORMAL(3),
    MINOR(4);

    private final int severity; // 🎯 낮을수록 심각함

    PartImportance(int severity) {
        this.severity = severity;
    }

    public int getSeverity() {
        return severity;
    }
}