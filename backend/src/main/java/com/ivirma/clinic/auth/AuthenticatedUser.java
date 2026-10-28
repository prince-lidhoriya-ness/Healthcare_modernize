package com.ivirma.clinic.auth;

/**
 * The signed-in user, as returned to the frontend and kept as the security principal.
 * patientId is set only for CUSTOMER accounts.
 */
public class AuthenticatedUser {

    private final Long id;
    private final String username;
    private final Role role;
    private final String displayName;
    private final Long patientId;

    public AuthenticatedUser(Long id, String username, Role role, String displayName, Long patientId) {
        this.id = id;
        this.username = username;
        this.role = role;
        this.displayName = displayName;
        this.patientId = patientId;
    }

    public static AuthenticatedUser from(UserAccount account) {
        Long patientId = account.getPatient() != null ? account.getPatient().getId() : null;
        return new AuthenticatedUser(account.getId(), account.getUsername(), account.getRole(),
                account.getDisplayName(), patientId);
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public Role getRole() { return role; }
    public String getDisplayName() { return displayName; }
    public Long getPatientId() { return patientId; }
}
