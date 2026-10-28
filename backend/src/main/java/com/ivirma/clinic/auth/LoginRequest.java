package com.ivirma.clinic.auth;

import jakarta.validation.constraints.NotBlank;

public class LoginRequest {

    @NotBlank
    private String username;

    @NotBlank
    private String password;

    /** The portal being signed in to. When set, accounts for the other portal are refused. */
    private Role portal;

    public LoginRequest() {
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Role getPortal() { return portal; }
    public void setPortal(Role portal) { this.portal = portal; }
}
