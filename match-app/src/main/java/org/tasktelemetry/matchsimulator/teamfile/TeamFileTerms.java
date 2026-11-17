package org.tasktelemetry.matchsimulator.teamfile;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.tasktelemetry.matchsimulator.engine.team.Role;

public final class TeamFileTerms {

    private final Map<String, Role> roles = createRoles();

    public Optional<Role> role(String term) {
        return Optional.ofNullable(roles.get(term));
    }

    public String allowedRoles() {
        return String.join(", ", roles.keySet());
    }

    private static Map<String, Role> createRoles() {
        Map<String, Role> roles = new LinkedHashMap<>();
        roles.put("portiere", Role.GOALKEEPER);
        roles.put("difensore", Role.DEFENDER);
        roles.put("centrocampista", Role.MIDFIELDER);
        roles.put("attaccante", Role.FORWARD);
        return roles;
    }
}
