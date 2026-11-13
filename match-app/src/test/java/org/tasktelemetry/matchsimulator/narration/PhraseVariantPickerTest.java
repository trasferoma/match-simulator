package org.tasktelemetry.matchsimulator.narration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.AWAY_DEFENDER;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.HOME_FORWARD;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.firstHalf;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.secondHalf;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.BallPosition;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.GoalContext;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.event.MatchTime;
import org.tasktelemetry.matchsimulator.engine.event.Participants;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.state.PlayPhase;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

class PhraseVariantPickerTest {

    private static final int FINGERPRINT_OF_12_GOAL_ROSSI = -1260362466;
    private static final int FINGERPRINT_OF_47_SHOT_SAVED_VERDI = 1321059335;
    private static final int FINGERPRINT_OF_0_KICK_OFF_NOBODY = -1420604774;
    private static final BallPosition MIDFIELD_BALL = new BallPosition(MatchSide.HOME, ZoneRole.MIDFIELD);

    private final PhraseVariantPicker picker = new PhraseVariantPicker();

    @Test
    void theFingerprintIsTheHashCodeOfMinuteTypeNameAndPlayerName() {
        MatchEvent goal = goalByRossiAt(firstHalfSeconds(754));

        assertThat(picker.fingerprintOf(goal)).isEqualTo(FINGERPRINT_OF_12_GOAL_ROSSI);
        assertThat(picker.fingerprintOf(goal)).isEqualTo("12|GOAL|Rossi".hashCode());
    }

    @Test
    void theMinuteIsTheMinuteOfTheMatchEvenInTheSecondHalf() {
        MatchEvent save = eventAt(EventType.SHOT_SAVED, secondHalfSeconds(120), AWAY_DEFENDER.name());

        assertThat(picker.fingerprintOf(save)).isEqualTo("47|SHOT_SAVED|Neri".hashCode());
    }

    @Test
    void anEventWithoutPlayersUsesAnEmptyPlayerName() {
        MatchEvent kickOff = new MatchEvent(1, firstHalf(0), EventType.KICK_OFF, MatchSide.HOME, ZoneRole.MIDFIELD,
                MIDFIELD_BALL, PlayPhase.SET_PIECE, Participants.nobody(), 0, EventDetail.none());

        assertThat(picker.fingerprintOf(kickOff)).isEqualTo(FINGERPRINT_OF_0_KICK_OFF_NOBODY);
    }

    @Test
    void theIndexIsTheFloorModuloOfTheFingerprintByTheNumberOfVariants() {
        MatchEvent goal = goalByRossiAt(firstHalfSeconds(754));

        assertThat(picker.variantIndex(goal, 3)).isZero();
        assertThat(picker.variantIndex(goal, 5)).isEqualTo(4);
        assertThat(picker.variantIndex(goal, 1)).isZero();
    }

    @Test
    void eachRepetitionMovesToTheNextVariantAndWrapsAround() {
        MatchEvent goal = goalByRossiAt(firstHalfSeconds(754));

        assertThat(picker.variantIndexAfterRepetitions(goal, 0, 5)).isEqualTo(picker.variantIndex(goal, 5));
        assertThat(picker.variantIndexAfterRepetitions(goal, 1, 3)).isEqualTo(1);
        assertThat(picker.variantIndexAfterRepetitions(goal, 2, 5)).isEqualTo(1);
        assertThat(picker.variantIndexAfterRepetitions(goal, 3, 1)).isZero();
    }

    @Test
    void aNegativeFingerprintStillGivesAnIndexInsideTheVariants() {
        MatchEvent kickOff = new MatchEvent(1, firstHalf(0), EventType.KICK_OFF, MatchSide.HOME, ZoneRole.MIDFIELD,
                MIDFIELD_BALL, PlayPhase.SET_PIECE, Participants.nobody(), 0, EventDetail.none());

        assertThat(FINGERPRINT_OF_0_KICK_OFF_NOBODY % 3).isEqualTo(-2);
        assertThat(picker.variantIndex(kickOff, 3)).isEqualTo(1);
        assertThat(picker.variantIndex(kickOff, 5)).isEqualTo(1);
    }

    @Test
    void aPositiveFingerprintGivesTheIndexComputedByHand() {
        MatchEvent save = eventAt(EventType.SHOT_SAVED, secondHalfSeconds(120), "Verdi");

        assertThat(picker.fingerprintOf(save)).isEqualTo(FINGERPRINT_OF_47_SHOT_SAVED_VERDI);
        assertThat(picker.variantIndex(save, 3)).isEqualTo(2);
        assertThat(picker.variantIndex(save, 2)).isEqualTo(1);
    }

    @Test
    void theSameEventAlwaysGivesTheSameIndex() {
        MatchEvent goal = goalByRossiAt(firstHalfSeconds(754));

        assertThat(picker.variantIndex(goal, 3)).isEqualTo(picker.variantIndex(goal, 3));
    }

    @Test
    void theSecondsAndTheOrderOfTheEventDoNotChangeTheIndex() {
        MatchEvent early = goalByRossiAt(firstHalfSeconds(720));
        MatchEvent late = goalByRossiAt(firstHalfSeconds(779));

        assertThat(picker.fingerprintOf(late)).isEqualTo(picker.fingerprintOf(early));
    }

    private MatchEvent goalByRossiAt(MatchTime time) {
        Participants participants = NarrationEvents.participantsOf(HOME_FORWARD);
        EventDetail detail = NarrationEvents.goalDetail(GoalContext.TAKES_THE_LEAD);
        return new MatchEvent(7, time, EventType.GOAL, MatchSide.HOME, ZoneRole.OPPONENT_BOX,
                new BallPosition(MatchSide.HOME, ZoneRole.OPPONENT_BOX), PlayPhase.BUILD_UP, participants, 20, detail);
    }

    private MatchEvent eventAt(EventType type, MatchTime time,
            String playerName) {
        Player player = NarrationEvents.player(playerName, Role.FORWARD);
        Participants participants = Participants.of(player, MatchSide.AWAY);
        return new MatchEvent(1, time, type, MatchSide.AWAY, ZoneRole.MIDFIELD,
                new BallPosition(MatchSide.AWAY, ZoneRole.MIDFIELD), PlayPhase.BUILD_UP, participants, 5,
                EventDetail.none());
    }

    private MatchTime firstHalfSeconds(int seconds) {
        return firstHalf(seconds);
    }

    private MatchTime secondHalfSeconds(int seconds) {
        return secondHalf(seconds);
    }
}
