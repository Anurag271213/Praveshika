package com.loginfeature.dto;

public class LoginResponse {

    private Boolean success;
    private String message;
    private String token;
    private String tokenType = "Bearer";
    private Long expiresIn;
    private UserResponse user;

    public LoginResponse() {
    }

    public LoginResponse(Boolean success, String message, String token, UserResponse user) {
        this.success = success;
        this.message = message;
        this.token = token;
        this.tokenType = "Bearer";
        this.user = user;
    }

    public LoginResponse(Boolean success, String message, String token, Long expiresIn, UserResponse user) {
        this.success = success;
        this.message = message;
        this.token = token;
        this.tokenType = "Bearer";
        this.expiresIn = expiresIn;
        this.user = user;
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

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public Long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(Long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public UserResponse getUser() {
        return user;
    }

    public void setUser(UserResponse user) {
        this.user = user;
    }
}
