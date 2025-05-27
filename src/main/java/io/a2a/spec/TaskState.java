package io.a2a.spec;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Represents the state of a task.
 */
public enum TaskState {
    SUBMITTED("submitted"),
    WORKING("working"),
    INPUT_REQUIRED("input-required"),
    AUTH_REQUIRED("auth-required"),
    COMPLETED("completed"),
    CANCELED("canceled"),
    FAILED("failed"),
    REJECTED("rejected"),
    UNKNOWN("unknown");

    private final String state;

    TaskState(String state) {
        this.state = state;
    }

    @JsonValue
    public String asString() {
        return state;
    }

    @JsonCreator
    public static TaskState fromString(String state) {
        switch (state) {
            case "submitted":
                return SUBMITTED;
            case "working":
                return WORKING;
            case "input-required":
                return INPUT_REQUIRED;
            case "auth-required":
                return AUTH_REQUIRED;
            case "completed":
                return COMPLETED;
            case "canceled":
                return CANCELED;
            case "failed":
                return FAILED;
            case "rejected":
                return REJECTED;
            case "unknown":
                return UNKNOWN;
            default:
                throw new IllegalArgumentException("Invalid TaskState: " + state);
        }
    }
}