package org.tasktelemetry.matchsimulator.teamfile;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Role;

public final class TeamFileTerms {

    private final Map<String, PlayStyle> styles = createStyles();
    private final Map<String, Role> roles = createRoles();

    public Optional<PlayStyle> style(String term) {
        return Optional.ofNullable(styles.get(term));
    }

    public Optional<Role> role(String term) {
        return Optional.ofNullable(roles.get(term));
    }

    public String termOf(Role role) {
        return roles.entrySet().stream()
                .filter(entry -> entry.getValue() == role)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no term for role " + role));
    }

    public String allowedStyles() {
        return String.join(", ", styles.keySet());
    }

    public String allowedRoles() {
        return String.join(", ", roles.keySet());
    }

    private static Map<String, PlayStyle> createStyles() {
        Map<String, PlayStyle> styles = new LinkedHashMap<>();
        styles.put("equilibrato", PlayStyle.BALANCED);
        styles.put("offensivo", PlayStyle.ATTACKING);
        styles.put("difensivo", PlayStyle.DEFENSIVE);
        styles.put("possesso", PlayStyle.POSSESSION);
        styles.put("contropiede", PlayStyle.COUNTER_ATTACK);
        return styles;
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
