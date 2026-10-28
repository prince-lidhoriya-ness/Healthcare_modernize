package com.ivirma.clinic.auth;

public class LoginResponse {

    private final String token;
    private final AuthenticatedUser user;

    public LoginResponse(String token, AuthenticatedUser user) {
        this.token = token;
        this.user = user;
    }

    public String getToken() { return token; }
    public AuthenticatedUser getUser() { return user; }
}
