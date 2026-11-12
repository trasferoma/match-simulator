package org.tasktelemetry.matchsimulator.narration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.AWAY_FORWARD;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.HOME_FORWARD;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.HOME_MIDFIELDER;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.firstHalf;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.secondHalf;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.GoalContext;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.state.Score;

class NarrationContextTest {

    private final NarrationEvents fixture = new NarrationEvents();

    @Test
    void aFirstGoalThatTakesTheLeadIsHighlightedAsTheLead() {
        MatchEvent goal = fixture.addGoal(firstHalf(600), MatchSide.HOME, HOME_FORWARD, GoalContext.TAKES_THE_LEAD);

        assertThat(contextOfTheMatch().highlightOf(goal)).contains(GoalHighlight.TAKES_THE_LEAD);
    }

    @Test
    void aGoalThatLevelsTheScoreIsHighlightedAsTheEqualiser() {
        MatchEvent goal = fixture.addGoal(firstHalf(600), MatchSide.AWAY, AWAY_FORWARD, GoalContext.EQUALISER);

        assertThat(contextOfTheMatch().highlightOf(goal)).contains(GoalHighlight.EQUALISER);
    }

    @Test
    void aGoalThatExtendsTheLeadOrOnlyReducesTheGapHasNoHighlight() {
        MatchEvent extension = fixture.addGoal(firstHalf(600), MatchSide.HOME, HOME_FORWARD,
                GoalContext.EXTENDS_THE_LEAD);
        MatchEvent consolation = fixture.addGoal(firstHalf(1200), MatchSide.AWAY, AWAY_FORWARD,
                GoalContext.CONSOLATION);
        NarrationContext context = contextOfTheMatch();

        assertThat(context.highlightOf(consolation)).isEmpty();
        assertThat(context.highlightOf(extension)).isEmpty();
    }

    @Test
    void theSecondGoalOfTheSamePlayerIsABraceAndTheThirdAHatTrick() {
        MatchEvent first = fixture.addGoal(firstHalf(600), MatchSide.HOME, HOME_FORWARD, GoalContext.TAKES_THE_LEAD);
        MatchEvent second = fixture.addGoal(secondHalf(300), MatchSide.HOME, HOME_FORWARD,
                GoalContext.EXTENDS_THE_LEAD);
        MatchEvent third = fixture.addGoal(secondHalf(2000), MatchSide.HOME, HOME_FORWARD,
                GoalContext.EXTENDS_THE_LEAD);
        NarrationContext context = contextOfTheMatch();

        assertThat(context.highlightOf(first)).contains(GoalHighlight.TAKES_THE_LEAD);
        assertThat(context.highlightOf(second)).contains(GoalHighlight.BRACE);
        assertThat(context.highlightOf(third)).contains(GoalHighlight.HAT_TRICK);
    }

    @Test
    void aBraceTakesPrecedenceOverTheLeadAndAnEqualiser() {
        fixture.addGoal(firstHalf(600), MatchSide.HOME, HOME_FORWARD, GoalContext.TAKES_THE_LEAD);
        fixture.addGoal(firstHalf(900), MatchSide.AWAY, AWAY_FORWARD, GoalContext.EQUALISER);
        MatchEvent second = fixture.addGoal(secondHalf(300), MatchSide.HOME, HOME_FORWARD, GoalContext.TAKES_THE_LEAD);

        assertThat(contextOfTheMatch().highlightOf(second)).contains(GoalHighlight.BRACE);
    }

    @Test
    void theGoalsOfOtherPlayersAreNotCounted() {
        fixture.addGoal(firstHalf(600), MatchSide.HOME, HOME_FORWARD, GoalContext.TAKES_THE_LEAD);
        MatchEvent midfielderGoal = fixture.addGoal(firstHalf(900), MatchSide.HOME, HOME_MIDFIELDER,
                GoalContext.EXTENDS_THE_LEAD);

        assertThat(contextOfTheMatch().highlightOf(midfielderGoal)).isEmpty();
    }

    @Test
    void theGoalsAfterTheOneNarratedAreNotCounted() {
        MatchEvent first = fixture.addGoal(firstHalf(600), MatchSide.HOME, HOME_FORWARD, GoalContext.TAKES_THE_LEAD);
        fixture.addGoal(secondHalf(300), MatchSide.HOME, HOME_FORWARD, GoalContext.EXTENDS_THE_LEAD);

        assertThat(contextOfTheMatch().highlightOf(first)).contains(GoalHighlight.TAKES_THE_LEAD);
    }

    @Test
    void anEventThatIsNotAGoalHasNoHighlight() {
        MatchEvent pass = fixture.add(EventType.PASS_COMPLETED, MatchSide.HOME, HOME_FORWARD);

        assertThat(contextOfTheMatch().highlightOf(pass)).isEmpty();
    }

    @Test
    void theScoreAfterAnEventCountsTheGoalsOfBothSidesUpToThatEventIncluded() {
        MatchEvent kickOff = fixture.add(EventType.KICK_OFF, MatchSide.HOME, HOME_FORWARD);
        MatchEvent homeGoal = fixture.addGoal(firstHalf(600), MatchSide.HOME, HOME_FORWARD,
                GoalContext.TAKES_THE_LEAD);
        MatchEvent pass = fixture.add(EventType.PASS_COMPLETED, MatchSide.HOME, HOME_MIDFIELDER);
        MatchEvent awayGoal = fixture.addGoal(firstHalf(900), MatchSide.AWAY, AWAY_FORWARD, GoalContext.EQUALISER);
        NarrationContext context = contextOfTheMatch();

        assertThat(context.scoreAfter(kickOff)).isEqualTo(new Score(0, 0));
        assertThat(context.scoreAfter(homeGoal)).isEqualTo(new Score(1, 0));
        assertThat(context.scoreAfter(pass)).isEqualTo(new Score(1, 0));
        assertThat(context.scoreAfter(awayGoal)).isEqualTo(new Score(1, 1));
    }

    private NarrationContext contextOfTheMatch() {
        return new NarrationContext(fixture.list(), NarrationEvents.TEAM_NAMES);
    }

    @Test
    void thePlayersOfTheSameNameInTheTwoTeamsAreCountedSeparately() {
        MatchEvent homeFirst = fixture.addGoal(firstHalf(600), MatchSide.HOME, HOME_FORWARD,
                GoalContext.TAKES_THE_LEAD);
        MatchEvent awayFirst = fixture.addGoal(firstHalf(900), MatchSide.AWAY, HOME_FORWARD, GoalContext.EQUALISER);
        MatchEvent homeSecond = fixture.addGoal(secondHalf(300), MatchSide.HOME, HOME_FORWARD,
                GoalContext.EXTENDS_THE_LEAD);
        MatchEvent awaySecond = fixture.addGoal(secondHalf(900), MatchSide.AWAY, HOME_FORWARD,
                GoalContext.EQUALISER);
        NarrationContext context = contextOfTheMatch();

        assertThat(context.highlightOf(homeFirst)).contains(GoalHighlight.TAKES_THE_LEAD);
        assertThat(context.highlightOf(awayFirst)).contains(GoalHighlight.EQUALISER);
        assertThat(context.highlightOf(homeSecond)).contains(GoalHighlight.BRACE);
        assertThat(context.highlightOf(awaySecond)).contains(GoalHighlight.BRACE);
    }
}
