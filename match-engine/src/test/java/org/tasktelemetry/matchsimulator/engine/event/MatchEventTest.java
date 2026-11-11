package org.tasktelemetry.matchsimulator.engine.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.state.MatchPeriod;
import org.tasktelemetry.matchsimulator.engine.state.PlayPhase;

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

    private MatchEvent eventWith(int order, int durationSeconds) {
        return new MatchEvent(order, time, EventType.KICK_OFF, MatchSide.HOME, ZoneRole.MIDFIELD,
                midfieldBall, PlayPhase.SET_PIECE, Participants.nobody(), durationSeconds, EventDetail.none());
    }
}
