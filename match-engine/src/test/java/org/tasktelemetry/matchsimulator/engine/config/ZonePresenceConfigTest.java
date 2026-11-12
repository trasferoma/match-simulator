package org.tasktelemetry.matchsimulator.engine.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.team.Role;

class ZonePresenceConfigTest {

    private final ZonePresenceConfig defaults = ZonePresenceConfig.defaults();

    @Test
    void defaultsCarryTheSpecifiedPresenceTable() {
        assertThat(defaults.presenceByZoneRole().get(ZoneRole.OWN_BOX))
                .containsEntry(Role.DEFENDER, 1.0).containsEntry(Role.MIDFIELDER, 0.3)
                .containsEntry(Role.FORWARD, 0.0);
        assertThat(defaults.presenceByZoneRole().get(ZoneRole.DEFENCE))
                .containsEntry(Role.DEFENDER, 0.9).containsEntry(Role.MIDFIELDER, 0.5)
                .containsEntry(Role.FORWARD, 0.1);
        assertThat(defaults.presenceByZoneRole().get(ZoneRole.MIDFIELD))
                .containsEntry(Role.DEFENDER, 0.3).containsEntry(Role.MIDFIELDER, 1.0)
                .containsEntry(Role.FORWARD, 0.4);
        assertThat(defaults.presenceByZoneRole().get(ZoneRole.ATTACKING_THIRD))
                .containsEntry(Role.DEFENDER, 0.1).containsEntry(Role.MIDFIELDER, 0.5)
                .containsEntry(Role.FORWARD, 0.9);
        assertThat(defaults.presenceByZoneRole().get(ZoneRole.OPPONENT_BOX))
                .containsEntry(Role.DEFENDER, 0.05).containsEntry(Role.MIDFIELDER, 0.3)
                .containsEntry(Role.FORWARD, 1.0);
    }

    @Test
    void defaultsCarryTheFourFourTwoReferenceAndTheScalarParameters() {
        assertThat(defaults.referenceFormation())
                .containsEntry(Role.DEFENDER, 4).containsEntry(Role.MIDFIELDER, 4).containsEntry(Role.FORWARD, 2)
                .doesNotContainKey(Role.GOALKEEPER);
        assertThat(defaults.advantageFactorPerMan()).isEqualTo(1.25);
        assertThat(defaults.advantageCap()).isEqualTo(4.0);
        assertThat(defaults.representativeWeight()).isEqualTo(0.5);
        assertThat(defaults.passMinimumPlayers()).isEqualTo(1.0);
    }

    @Test
    void presenceTableIsImmutableAndDetachedFromTheInput() {
        Map<ZoneRole, Map<Role, Double>> input = new EnumMap<>(defaults.presenceByZoneRole());
        ZonePresenceConfig config = withPresence(input);
        input.clear();

        assertThat(config.presenceByZoneRole()).hasSize(ZoneRole.values().length);
        assertThatThrownBy(() -> config.presenceByZoneRole().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> config.presenceByZoneRole().get(ZoneRole.MIDFIELD).put(Role.FORWARD, 0.5))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsAMissingZoneRole() {
        Map<ZoneRole, Map<Role, Double>> presence = new EnumMap<>(defaults.presenceByZoneRole());
        presence.remove(ZoneRole.DEFENCE);

        assertThatThrownBy(() -> withPresence(presence))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("presence by zone role needs a row for DEFENCE");
    }

    @Test
    void rejectsARowWithAMissingDepartment() {
        Map<ZoneRole, Map<Role, Double>> presence = new EnumMap<>(defaults.presenceByZoneRole());
        Map<Role, Double> row = new EnumMap<>(presence.get(ZoneRole.MIDFIELD));
        row.remove(Role.FORWARD);
        presence.put(ZoneRole.MIDFIELD, row);

        assertThatThrownBy(() -> withPresence(presence))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("presence for MIDFIELD needs a value for FORWARD");
    }

    @Test
    void rejectsAPresenceForTheGoalkeeper() {
        Map<ZoneRole, Map<Role, Double>> presence = new EnumMap<>(defaults.presenceByZoneRole());
        Map<Role, Double> row = new EnumMap<>(presence.get(ZoneRole.OWN_BOX));
        row.put(Role.GOALKEEPER, 1.0);
        presence.put(ZoneRole.OWN_BOX, row);

        assertThatThrownBy(() -> withPresence(presence))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("presence for OWN_BOX must not include the goalkeeper");
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.01, 1.01, Double.NaN})
    void rejectsAPresenceOutsideZeroAndOne(double value) {
        Map<ZoneRole, Map<Role, Double>> presence = new EnumMap<>(defaults.presenceByZoneRole());
        Map<Role, Double> row = new EnumMap<>(presence.get(ZoneRole.MIDFIELD));
        row.put(Role.MIDFIELDER, value);
        presence.put(ZoneRole.MIDFIELD, row);

        assertThatThrownBy(() -> withPresence(presence))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("presence for MIDFIELD of MIDFIELDER must be between 0.0 and 1.0, was " + value);
    }

    @Test
    void rejectsARowWithoutAPositivePresence() {
        Map<ZoneRole, Map<Role, Double>> presence = new EnumMap<>(defaults.presenceByZoneRole());
        Map<Role, Double> row = new EnumMap<>(Role.class);
        row.put(Role.DEFENDER, 0.0);
        row.put(Role.MIDFIELDER, 0.0);
        row.put(Role.FORWARD, 0.0);
        presence.put(ZoneRole.ATTACKING_THIRD, row);

        assertThatThrownBy(() -> withPresence(presence))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("presence for ATTACKING_THIRD needs at least one positive value");
    }

    @Test
    void rejectsAReferenceWithoutAnyPlayerInADepartment() {
        Map<Role, Integer> reference = reference(5, 5, 0);

        assertThatThrownBy(() -> withReference(reference))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reference formation needs at least one player for FORWARD, was 0");
    }

    @Test
    void rejectsAReferenceMissingADepartment() {
        Map<Role, Integer> reference = reference(4, 4, 2);
        reference.remove(Role.MIDFIELDER);

        assertThatThrownBy(() -> withReference(reference))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reference formation needs at least one player for MIDFIELDER, was null");
    }

    @Test
    void rejectsAFourFourThreeReference() {
        Map<Role, Integer> reference = reference(4, 4, 3);

        assertThatThrownBy(() -> withReference(reference))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reference formation must have exactly 10 movement players, was 11");
    }

    @Test
    void rejectsAFourFourOneReference() {
        Map<Role, Integer> reference = reference(4, 4, 1);

        assertThatThrownBy(() -> withReference(reference))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reference formation must have exactly 10 movement players, was 9");
    }

    @Test
    void rejectsAReferenceWithTheGoalkeeper() {
        Map<Role, Integer> reference = reference(4, 4, 2);
        reference.put(Role.GOALKEEPER, 1);

        assertThatThrownBy(() -> withReference(reference))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reference formation must not include the goalkeeper");
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.99, 0.0, -1.25, Double.NaN, Double.POSITIVE_INFINITY})
    void rejectsAnAdvantageFactorBelowOne(double factor) {
        assertThatThrownBy(() -> withScalars(factor, 4.0, 0.5, 1.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("advantage factor per man must be at least 1.0 and finite, was " + factor);
    }

    @Test
    void acceptsANeutralAdvantageFactorOfOne() {
        assertThat(withScalars(1.0, 4.0, 0.5, 1.0).advantageFactorPerMan()).isEqualTo(1.0);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -4.0, Double.NaN, Double.POSITIVE_INFINITY})
    void rejectsAnAdvantageCapThatIsNotPositiveAndFinite(double cap) {
        assertThatThrownBy(() -> withScalars(1.25, cap, 0.5, 1.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("advantage cap must be positive and finite, was " + cap);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 1.1, Double.NaN})
    void rejectsARepresentativeWeightOutsideZeroAndOne(double weight) {
        assertThatThrownBy(() -> withScalars(1.25, 4.0, weight, 1.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("representative weight must be between 0.0 and 1.0, was " + weight);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, Double.NaN, Double.POSITIVE_INFINITY})
    void rejectsANegativePassMinimum(double minimum) {
        assertThatThrownBy(() -> withScalars(1.25, 4.0, 0.5, minimum))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pass minimum players must not be negative and must be finite, was " + minimum);
    }

    @Test
    void acceptsAZeroPassMinimum() {
        assertThat(withScalars(1.25, 4.0, 0.5, 0.0).passMinimumPlayers()).isZero();
    }

    private Map<Role, Integer> reference(int defenders, int midfielders, int forwards) {
        Map<Role, Integer> reference = new EnumMap<>(Role.class);
        reference.put(Role.DEFENDER, defenders);
        reference.put(Role.MIDFIELDER, midfielders);
        reference.put(Role.FORWARD, forwards);
        return reference;
    }

    private ZonePresenceConfig withPresence(Map<ZoneRole, Map<Role, Double>> presence) {
        return new ZonePresenceConfig(presence, defaults.referenceFormation(), defaults.advantageFactorPerMan(),
                defaults.advantageCap(), defaults.representativeWeight(), defaults.passMinimumPlayers());
    }

    private ZonePresenceConfig withReference(Map<Role, Integer> reference) {
        return new ZonePresenceConfig(defaults.presenceByZoneRole(), reference, defaults.advantageFactorPerMan(),
                defaults.advantageCap(), defaults.representativeWeight(), defaults.passMinimumPlayers());
    }

    private ZonePresenceConfig withScalars(double factor, double cap, double weight, double passMinimum) {
        return new ZonePresenceConfig(defaults.presenceByZoneRole(), defaults.referenceFormation(), factor, cap,
                weight, passMinimum);
    }
}
