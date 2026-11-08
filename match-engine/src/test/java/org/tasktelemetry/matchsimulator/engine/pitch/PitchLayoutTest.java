package org.tasktelemetry.matchsimulator.engine.pitch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.tasktelemetry.matchsimulator.engine.MatchSide;

class PitchLayoutTest {

    private final PitchLayout standard = PitchLayout.standard();

    @Test
    void standardLayoutHasFiveZonesInARow() {
        assertThat(standard.zones()).hasSize(5);
    }

    @Test
    void homePossessionReadsRolesFromItsOwnBoxToTheOpponentBox() {
        assertThat(rolesAlongThePitch(standard, MatchSide.HOME)).containsExactly(
                ZoneRole.OWN_BOX, ZoneRole.DEFENCE, ZoneRole.MIDFIELD, ZoneRole.ATTACKING_THIRD,
                ZoneRole.OPPONENT_BOX);
    }

    @Test
    void awayPossessionReadsTheSameZonesInReverse() {
        assertThat(rolesAlongThePitch(standard, MatchSide.AWAY)).containsExactly(
                ZoneRole.OPPONENT_BOX, ZoneRole.ATTACKING_THIRD, ZoneRole.MIDFIELD, ZoneRole.DEFENCE,
                ZoneRole.OWN_BOX);
    }

    @Test
    void theSameZoneHasOppositeRolesForTheTwoSides() {
        Zone homeBox = standard.zones().get(0);

        assertThat(standard.roleOf(homeBox, MatchSide.HOME)).isEqualTo(ZoneRole.OWN_BOX);
        assertThat(standard.roleOf(homeBox, MatchSide.AWAY)).isEqualTo(ZoneRole.OPPONENT_BOX);
    }

    @Test
    void sevenZonesAddMidfieldZonesAndKeepTheOtherRoles() {
        PitchLayout seven = new PitchLayout(7);

        assertThat(rolesAlongThePitch(seven, MatchSide.HOME)).containsExactly(
                ZoneRole.OWN_BOX, ZoneRole.DEFENCE, ZoneRole.MIDFIELD, ZoneRole.MIDFIELD, ZoneRole.MIDFIELD,
                ZoneRole.ATTACKING_THIRD, ZoneRole.OPPONENT_BOX);
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 6, 7, 9, 12})
    void anyNumberOfZonesReusesTheSameRolesForBothSides(int zoneCount) {
        PitchLayout layout = new PitchLayout(zoneCount);

        assertThat(layout.zones()).hasSize(zoneCount);
        assertThat(rolesAlongThePitch(layout, MatchSide.HOME)).containsAll(EnumSet.allOf(ZoneRole.class));
        assertThat(rolesAlongThePitch(layout, MatchSide.AWAY)).containsAll(EnumSet.allOf(ZoneRole.class));
        assertThat(rolesAlongThePitch(layout, MatchSide.HOME)).isSorted();
        assertThat(rolesAlongThePitch(layout, MatchSide.AWAY)).isSortedAccordingTo(
                Comparator.<ZoneRole>naturalOrder().reversed());
    }

    @ParameterizedTest
    @EnumSource(MatchSide.class)
    void advancingFromTheOwnBoxCrossesEveryRoleUpToTheOpponentBox(MatchSide side) {
        for (int zoneCount : new int[] {5, 9}) {
            PitchLayout layout = new PitchLayout(zoneCount);

            List<ZoneRole> roles = rolesWalkedByAdvancing(layout, side);

            assertThat(roles).hasSize(zoneCount).isSorted();
            assertThat(roles.get(0)).isEqualTo(ZoneRole.OWN_BOX);
            assertThat(roles.get(zoneCount - 1)).isEqualTo(ZoneRole.OPPONENT_BOX);
        }
    }

    @Test
    void homeAdvancesTowardHigherZonesAndAwayTowardLowerOnes() {
        Zone middle = standard.zones().get(2);

        assertThat(standard.advance(middle, MatchSide.HOME)).contains(standard.zones().get(3));
        assertThat(standard.advance(middle, MatchSide.AWAY)).contains(standard.zones().get(1));
    }

    @Test
    void retreatingGoesBackTowardTheOwnBox() {
        Zone middle = standard.zones().get(2);

        assertThat(standard.retreat(middle, MatchSide.HOME)).contains(standard.zones().get(1));
        assertThat(standard.retreat(middle, MatchSide.AWAY)).contains(standard.zones().get(3));
    }

    @Test
    void thereIsNothingBeyondTheOpponentBoxNorBehindTheOwnBox() {
        Zone homeBox = standard.zones().get(0);
        Zone awayBox = standard.zones().get(4);

        assertThat(standard.advance(awayBox, MatchSide.HOME)).isEmpty();
        assertThat(standard.advance(homeBox, MatchSide.AWAY)).isEmpty();
        assertThat(standard.retreat(homeBox, MatchSide.HOME)).isEmpty();
        assertThat(standard.retreat(awayBox, MatchSide.AWAY)).isEmpty();
    }

    @Test
    void advancingThenRetreatingReturnsToTheStartingZone() {
        Zone start = standard.zones().get(1);

        Optional<Zone> forward = standard.advance(start, MatchSide.HOME);
        Optional<Zone> back = standard.retreat(forward.orElseThrow(), MatchSide.HOME);

        assertThat(back).contains(start);
    }

    @Test
    void rejectsALayoutWithFewerZonesThanRoles() {
        assertThatThrownBy(() -> new PitchLayout(4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 5 zones");
    }

    @Test
    void rejectsAZoneOfAnotherLayout() {
        Zone farZone = new PitchLayout(9).zones().get(8);

        assertThatThrownBy(() -> standard.roleOf(farZone, MatchSide.HOME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong to this layout");
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 6, 7, 9, 12})
    void kickOffZoneIsMidfieldForBothSides(int zoneCount) {
        PitchLayout layout = new PitchLayout(zoneCount);

        Zone kickOffZone = layout.kickOffZone();

        assertThat(layout.roleOf(kickOffZone, MatchSide.HOME)).isEqualTo(ZoneRole.MIDFIELD);
        assertThat(layout.roleOf(kickOffZone, MatchSide.AWAY)).isEqualTo(ZoneRole.MIDFIELD);
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 7, 9})
    void theOwnBoxZoneHasTheOwnBoxRoleForItsSideAndTheOpponentBoxRoleForTheOther(int zoneCount) {
        PitchLayout layout = new PitchLayout(zoneCount);

        for (MatchSide side : MatchSide.values()) {
            Zone ownBox = layout.ownBoxZone(side);

            assertThat(layout.roleOf(ownBox, side)).isEqualTo(ZoneRole.OWN_BOX);
            assertThat(layout.roleOf(ownBox, side.opposite())).isEqualTo(ZoneRole.OPPONENT_BOX);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 7, 9})
    void theDefenceZoneHasTheDefenceRoleForItsSideAndIsNextToTheOwnBox(int zoneCount) {
        PitchLayout layout = new PitchLayout(zoneCount);

        for (MatchSide side : MatchSide.values()) {
            Zone defence = layout.defenceZone(side);

            assertThat(layout.roleOf(defence, side)).isEqualTo(ZoneRole.DEFENCE);
            assertThat(layout.advance(layout.ownBoxZone(side), side)).contains(defence);
        }
    }

    private List<ZoneRole> rolesAlongThePitch(PitchLayout layout, MatchSide side) {
        return layout.zones().stream()
                .map(zone -> layout.roleOf(zone, side))
                .toList();
    }

    private List<ZoneRole> rolesWalkedByAdvancing(PitchLayout layout, MatchSide side) {
        Zone ownBox = layout.zones().stream()
                .filter(zone -> layout.roleOf(zone, side) == ZoneRole.OWN_BOX)
                .findFirst()
                .orElseThrow();
        List<ZoneRole> roles = new ArrayList<>();
        Optional<Zone> current = Optional.of(ownBox);
        while (current.isPresent()) {
            roles.add(layout.roleOf(current.get(), side));
            current = layout.advance(current.get(), side);
        }
        return roles;
    }
}
