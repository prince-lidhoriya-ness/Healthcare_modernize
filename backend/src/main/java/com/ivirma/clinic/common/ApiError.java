package com.ivirma.clinic.common;

import java.util.Date;

public class ApiError {

    private int status;
    private String error;
    private String message;
    private String detail;
    private Date timestamp;

    public ApiError() {
    }

    public ApiError(int status, String error, String message, String detail) {
        this.status = status;
        this.error = error;
        this.message = message;
        this.detail = detail;
        this.timestamp = new Date();
    }

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public Date getTimestamp() { return timestamp; }
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }
}
