package org.tasktelemetry.matchsimulator.narration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.AWAY_DEFENDER;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.AWAY_FORWARD;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.AWAY_GOALKEEPER;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.HOME_FORWARD;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.HOME_MIDFIELDER;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.FoulSeverity;
import org.tasktelemetry.matchsimulator.engine.event.GoalContext;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.event.Participants;
import org.tasktelemetry.matchsimulator.engine.event.SaveQuality;
import org.tasktelemetry.matchsimulator.engine.event.ShotKind;
import org.tasktelemetry.matchsimulator.engine.event.ShotProfile;
import org.tasktelemetry.matchsimulator.engine.event.ShotQuality;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.team.Player;

class PhraseKeysTest {

    private final PhraseKeys keys = new PhraseKeys();
    private final NarrationEvents fixture = new NarrationEvents();

    @Test
    void anEventWithoutDetailNorSecondaryPlayerHasTheNameOfItsTypeAsKey() {
        MatchEvent pass = fixture.add(EventType.PASS_COMPLETED, MatchSide.HOME, HOME_MIDFIELDER);

        assertThat(keys.keyOf(pass)).isEqualTo("PASS_COMPLETED");
    }

    @Test
    void theSecondaryPlayerAddsASegment() {
        Participants participants = Participants.of(AWAY_DEFENDER, HOME_FORWARD);
        MatchEvent offside = fixture.add(EventType.OFFSIDE, MatchSide.HOME, ZoneRole.ATTACKING_THIRD, participants,
                EventDetail.none());

        assertThat(keys.keyOf(offside)).isEqualTo("OFFSIDE.WITH_SECONDARY");
    }

    @Test
    void aGoalIsKeyedByTheFormOfTheShot() {
        MatchEvent foot = goal(ShotProfile.ofShot(0.2, ShotKind.FOOT, ShotQuality.BIG_CHANCE), HOME_FORWARD, null);
        MatchEvent freeKick = goal(ShotProfile.ofShot(0.1, ShotKind.DIRECT_FREE_KICK, ShotQuality.ATTEMPT),
                HOME_FORWARD, null);
        MatchEvent header = goal(ShotProfile.ofShot(0.3, ShotKind.HEADER, ShotQuality.HALF_CHANCE), HOME_FORWARD,
                HOME_MIDFIELDER);
        MatchEvent penalty = goal(ShotProfile.ofPenalty(0.79, ShotQuality.BIG_CHANCE), HOME_FORWARD, null);

        assertThat(keys.keyOf(foot)).isEqualTo("GOAL.FOOT");
        assertThat(keys.keyOf(freeKick)).isEqualTo("GOAL.DIRECT_FREE_KICK");
        assertThat(keys.keyOf(header)).isEqualTo("GOAL.HEADER.WITH_SECONDARY");
        assertThat(keys.keyOf(penalty)).isEqualTo("GOAL.PENALTY");
    }

    @Test
    void aSaveIsKeyedByTheDifficultyOfTheSaveOrByThePenalty() {
        ShotProfile profile = ShotProfile.ofShot(0.2, ShotKind.FOOT, ShotQuality.BIG_CHANCE);
        EventDetail difficult = EventDetail.save(profile, SaveQuality.DIFFICULT);
        EventDetail penaltySave = EventDetail.save(ShotProfile.ofPenalty(0.79, ShotQuality.BIG_CHANCE),
                SaveQuality.SPECTACULAR);
        Participants participants = Participants.of(HOME_FORWARD, AWAY_GOALKEEPER);

        MatchEvent save = fixture.add(EventType.SHOT_SAVED, MatchSide.HOME, ZoneRole.OPPONENT_BOX, participants,
                difficult);
        MatchEvent penalty = fixture.add(EventType.SHOT_SAVED, MatchSide.HOME, ZoneRole.OPPONENT_BOX, participants,
                penaltySave);

        assertThat(keys.keyOf(save)).isEqualTo("SHOT_SAVED.DIFFICULT.WITH_SECONDARY");
        assertThat(keys.keyOf(penalty)).isEqualTo("SHOT_SAVED.PENALTY.WITH_SECONDARY");
    }

    @Test
    void aMissedShotIsKeyedByItsDanger() {
        EventDetail detail = EventDetail.shot(ShotProfile.ofShot(0.4, ShotKind.FOOT, ShotQuality.BIG_CHANCE));
        Participants participants = Participants.of(AWAY_FORWARD);

        MatchEvent miss = fixture.add(EventType.SHOT_OFF_TARGET, MatchSide.AWAY, ZoneRole.OPPONENT_BOX, participants,
                detail);

        assertThat(keys.keyOf(miss)).isEqualTo("SHOT_OFF_TARGET.BIG_CHANCE");
    }

    @Test
    void aFoulIsKeyedByItsSeverityAndByTheCallOfAPenalty() {
        Participants participants = Participants.of(AWAY_DEFENDER, HOME_FORWARD);

        MatchEvent hard = fixture.add(EventType.FOUL, MatchSide.HOME, ZoneRole.MIDFIELD, participants,
                EventDetail.foul(FoulSeverity.HARD, false));
        MatchEvent reckless = fixture.add(EventType.FOUL, MatchSide.HOME, ZoneRole.OPPONENT_BOX, participants,
                EventDetail.foul(FoulSeverity.RECKLESS, true));

        assertThat(keys.keyOf(hard)).isEqualTo("FOUL.HARD.WITH_SECONDARY");
        assertThat(keys.keyOf(reckless)).isEqualTo("FOUL.RECKLESS.PENALTY.WITH_SECONDARY");
    }

    @Test
    void theEventsWithoutAKeySegmentFromTheirDetailKeepTheNameOfTheType() {
        MatchEvent stoppage = fixture.add(EventType.STOPPAGE_ANNOUNCED, MatchSide.HOME, ZoneRole.MIDFIELD,
                Participants.nobody(), EventDetail.stoppageAnnounced(4));

        assertThat(keys.keyOf(stoppage)).isEqualTo("STOPPAGE_ANNOUNCED");
    }

    @Test
    void aGoalHighlightHasItsOwnKey() {
        assertThat(keys.keyOf(GoalHighlight.BRACE)).isEqualTo("GOAL_CONTEXT.BRACE");
        assertThat(keys.keyOf(GoalHighlight.TAKES_THE_LEAD)).isEqualTo("GOAL_CONTEXT.TAKES_THE_LEAD");
    }

    private MatchEvent goal(ShotProfile profile, Player scorer,
            Player crosser) {
        Participants participants = crosser == null ? Participants.of(scorer) : Participants.of(scorer, crosser);
        EventDetail detail = EventDetail.goal(profile, GoalContext.TAKES_THE_LEAD);
        return fixture.add(EventType.GOAL, MatchSide.HOME, ZoneRole.OPPONENT_BOX, participants, detail);
    }
}
