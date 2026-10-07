package com.loginfeature.dto;

public class OperationResponse {

    private Boolean success;
    private String message;

    public OperationResponse() {
    }

    public OperationResponse(Boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public static OperationResponse ok(String message) {
        return new OperationResponse(Boolean.TRUE, message);
    }

    public static OperationResponse fail(String message) {
        return new OperationResponse(Boolean.FALSE, message);
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
