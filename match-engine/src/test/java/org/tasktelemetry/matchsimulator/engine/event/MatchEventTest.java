package org.tasktelemetry.matchsimulator.engine.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.state.MatchPeriod;
import org.tasktelemetry.matchsimulator.engine.state.PlayPhase;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Role;

class MatchEventTest {

    private final MatchTime time = new MatchTime(MatchPeriod.FIRST_HALF, 90);
    private final BallPosition midfieldBall = new BallPosition(MatchSide.HOME, ZoneRole.MIDFIELD);

    @Test
    void stoppageAnnouncementCarriesItsMinutes() {
        EventDetail detail = EventDetail.stoppageAnnounced(3);

        assertThat(detail).isEqualTo(new EventDetail.StoppageAnnounced(3));
        assertThat(((EventDetail.StoppageAnnounced) detail).minutes()).isEqualTo(3);
    }

    @Test
    void stoppageAnnouncementRejectsLessThanOneMinute() {
        assertThatThrownBy(() -> EventDetail.stoppageAnnounced(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 1 minute");
    }

    @Test
    void styleChangeCarriesPreviousStyleNewStyleAndReason() {
        EventDetail detail = EventDetail.styleChange(PlayStyle.ATTACKING, PlayStyle.DEFENSIVE,
                ChangeReason.PROTECT_LEAD);

        assertThat(detail).isEqualTo(new EventDetail.StyleChange(PlayStyle.ATTACKING, PlayStyle.DEFENSIVE,
                ChangeReason.PROTECT_LEAD));
    }

    @Test
    void styleChangeRejectsTheSameStyleAndMissingValues() {
        assertThatThrownBy(() -> EventDetail.styleChange(PlayStyle.ATTACKING, PlayStyle.ATTACKING,
                ChangeReason.CHASE_GAME)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EventDetail.styleChange(null, PlayStyle.ATTACKING, ChangeReason.CHASE_GAME))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> EventDetail.styleChange(PlayStyle.BALANCED, null, ChangeReason.CHASE_GAME))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> EventDetail.styleChange(PlayStyle.BALANCED, PlayStyle.ATTACKING, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsAnOrderBelowOneAndANegativeDuration() {
        assertThatThrownBy(() -> eventWith(0, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> eventWith(1, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsAPeriodEventWithZeroDurationAndNoPlayers() {
        MatchEvent event = eventWith(1, 0);

        assertThat(event.participants().primary()).isEmpty();
        assertThat(event.detail()).isEqualTo(EventDetail.none());
    }

    @Test
    void rejectsMissingValues() {
        assertThatThrownBy(() -> new MatchEvent(1, null, EventType.KICK_OFF, MatchSide.HOME, ZoneRole.MIDFIELD,
                midfieldBall, PlayPhase.SET_PIECE, Participants.nobody(), 0, EventDetail.none()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new MatchEvent(1, time, EventType.KICK_OFF, MatchSide.HOME, ZoneRole.MIDFIELD,
                midfieldBall, PlayPhase.SET_PIECE, null, 0, EventDetail.none()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new MatchEvent(1, time, EventType.KICK_OFF, MatchSide.HOME, ZoneRole.MIDFIELD,
                null, PlayPhase.SET_PIECE, Participants.nobody(), 0, EventDetail.none()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void aStyleChangeIsAVoluntaryIntervention() {
        EventDetail detail = EventDetail.styleChange(PlayStyle.BALANCED, PlayStyle.ATTACKING,
                ChangeReason.CHASE_GAME);

        assertThat(interventionEvent(EventType.STYLE_CHANGED, detail).isVoluntaryIntervention()).isTrue();
    }

    @Test
    void aSubstitutionOrAMoveIsVoluntaryOnlyWithAVoluntaryReason() {
        Deployment deployment = new Deployment(4, 4, 2);
        EventDetail voluntary = EventDetail.lineupChange(Role.FORWARD, ChangeReason.CHASE_GAME, deployment,
                deployment);
        EventDetail forced = EventDetail.lineupChange(Role.FORWARD, ChangeReason.INJURY, deployment, deployment);

        assertThat(interventionEvent(EventType.SUBSTITUTION, voluntary).isVoluntaryIntervention()).isTrue();
        assertThat(interventionEvent(EventType.PLAYER_MOVED, voluntary).isVoluntaryIntervention()).isTrue();
        assertThat(interventionEvent(EventType.SUBSTITUTION, forced).isVoluntaryIntervention()).isFalse();
        assertThat(interventionEvent(EventType.PLAYER_MOVED, forced).isVoluntaryIntervention()).isFalse();
    }

    @Test
    void anyOtherEventIsNotAnIntervention() {
        assertThat(eventWith(1, 0).isVoluntaryIntervention()).isFalse();
    }

    private MatchEvent interventionEvent(EventType type, EventDetail detail) {
        return new MatchEvent(1, time, type, MatchSide.HOME, ZoneRole.MIDFIELD, midfieldBall, PlayPhase.BUILD_UP,
                Participants.nobody(), 0, detail);
    }

    private MatchEvent eventWith(int order, int durationSeconds) {
        return new MatchEvent(order, time, EventType.KICK_OFF, MatchSide.HOME, ZoneRole.MIDFIELD,
                midfieldBall, PlayPhase.SET_PIECE, Participants.nobody(), durationSeconds, EventDetail.none());
    }
}
