package dk.easv.eventTicketSystem.be;

public enum Role {
    ADMIN(1, "Admin", "A"),
    COORDINATOR(2, "Event Coordinator", "EC");

    private final int id;
    private final String roleName;
    private final String roleCode;

    Role(int id, String roleName, String roleCode) {
        this.id = id;
        this.roleName = roleName;
        this.roleCode = roleCode;
    }

    public int getId() {
        return id;
    }

    public String getRoleName() {
        return roleName;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public static Role fromId(int id) {
        for (Role value : values()) {
            if (value.id == id) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown role id: " + id);
    }
}
