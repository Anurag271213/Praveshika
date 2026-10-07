package com.loginfeature.dto;

import java.time.Instant;

public class HealthResponse {

    private String status;
    private Instant timestamp;

    public HealthResponse() {
    }

    public HealthResponse(String status, Instant timestamp) {
        this.status = status;
        this.timestamp = timestamp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
