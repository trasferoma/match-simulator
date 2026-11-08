package org.tasktelemetry.matchsimulator.calibration;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.team.Role;

public record RoleProfiles(Map<Role, AttributeOffsets> offsetsByRole) {

    public RoleProfiles {
        Objects.requireNonNull(offsetsByRole, "offsets by role must not be null");
        Map<Role, AttributeOffsets> copy = new EnumMap<>(Role.class);
        copy.putAll(offsetsByRole);
        for (Role role : Role.values()) {
            if (copy.get(role) == null) {
                throw new IllegalArgumentException("role profiles need offsets for " + role);
            }
        }
        offsetsByRole = Collections.unmodifiableMap(copy);
    }

    public static RoleProfiles defaults() {
        Map<Role, AttributeOffsets> offsetsByRole = new EnumMap<>(Role.class);
        offsetsByRole.put(Role.GOALKEEPER, new AttributeOffsets(-4, -6, -7, -6, 8, -6, 4, 17));
        offsetsByRole.put(Role.DEFENDER, new AttributeOffsets(-2, -6, -8, 10, 10, 0, 8, -12));
        offsetsByRole.put(Role.MIDFIELDER, new AttributeOffsets(12, 6, 0, 4, 4, 2, -4, -24));
        offsetsByRole.put(Role.FORWARD, new AttributeOffsets(2, 10, 12, -14, 2, 8, 4, -24));
        return new RoleProfiles(offsetsByRole);
    }

    public AttributeOffsets offsetsOf(Role role) {
        return offsetsByRole.get(role);
    }
}
