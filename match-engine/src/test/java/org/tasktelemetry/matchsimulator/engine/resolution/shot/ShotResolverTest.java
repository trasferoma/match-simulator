package org.tasktelemetry.matchsimulator.engine.resolution.shot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.tasktelemetry.matchsimulator.engine.MatchRandom;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.TestResolvers;
import org.tasktelemetry.matchsimulator.engine.TestShotConfig;
import org.tasktelemetry.matchsimulator.engine.TestTeams;
import org.tasktelemetry.matchsimulator.engine.config.DurationRange;
import org.tasktelemetry.matchsimulator.engine.config.ShotConfig;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.GoalContext;
import org.tasktelemetry.matchsimulator.engine.event.ShotKind;
import org.tasktelemetry.matchsimulator.engine.event.ShotProfile;
import org.tasktelemetry.matchsimulator.engine.event.ShotQuality;
import org.tasktelemetry.matchsimulator.engine.pitch.PitchLayout;
import org.tasktelemetry.matchsimulator.engine.pitch.Zone;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.resolution.ActionOutcome;
import org.tasktelemetry.matchsimulator.engine.state.MatchState;
import org.tasktelemetry.matchsimulator.engine.state.PlayPhase;
import org.tasktelemetry.matchsimulator.engine.state.Score;
import org.tasktelemetry.matchsimulator.engine.state.SetPieceKind;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;
import org.tasktelemetry.matchsimulator.engine.team.Role;

class ShotResolverTest {

    private static final long SEED = 21L;
    private static final int TRIALS = 20_000;
    private static final int MANY_TRIALS = 100_000;
    private static final double ONE_POINT = 0.01;
    private static final int NEUTRAL_SHOOTING = ShotConfig.defaults().expectedGoals().neutralShooting();
    private static final int NEUTRAL_GOALKEEPING = ShotConfig.defaults().neutralGoalkeeping();
    private static final int PENALTY_NEUTRAL_SHOOTING =
            ShotConfig.defaults().expectedGoals().penaltyNeutralShooting();

    private final PitchLayout layout = PitchLayout.standard();
    private final ShotConfig config = ShotConfig.defaults();
    private final ExpectedGoalsModel model = new ExpectedGoalsModel(config);
    private final ShotQualityScale scale = new ShotQualityScale(config);
    private final TeamOnPitch attackers = teamOnPitch("Home", NEUTRAL_SHOOTING, NEUTRAL_GOALKEEPING);
    private final TeamOnPitch defenders = teamOnPitch("Away", NEUTRAL_SHOOTING, NEUTRAL_GOALKEEPING);
    private final TeamOnPitch penaltyTakers = teamOnPitch("Home", PENALTY_NEUTRAL_SHOOTING, NEUTRAL_GOALKEEPING);
    private final Zone box = layout.ownBoxZone(MatchSide.AWAY);
    private final MatchState state = MatchState.openPlay(MatchSide.HOME, box);

    @Test
    void theFrequencyOfGoalsIsTheProbabilityOfAGoal() {
        double expected = goalProbabilityOf(attackers.playersIn(Role.FORWARD).get(0), defenders);

        List<ActionOutcome> outcomes = shots(resolver(config, SEED), attackers, defenders, TRIALS);

        assertThat(share(outcomes, EventType.GOAL)).isCloseTo(expected, within(ONE_POINT));
    }

    @Test
    void whatIsNotAGoalSplitsIntoBlockedOffTargetAndSavedAsConfigured() {
        double goal = goalProbabilityOf(attackers.playersIn(Role.FORWARD).get(0), defenders);
        double noGoal = 1.0 - goal;

        List<ActionOutcome> outcomes = shots(resolver(config, SEED), attackers, defenders, TRIALS);

        assertThat(share(outcomes, EventType.SHOT_BLOCKED)).isCloseTo(noGoal * config.blockedShare(),
                within(ONE_POINT));
        assertThat(share(outcomes, EventType.SHOT_OFF_TARGET)).isCloseTo(noGoal * config.offTargetShare(),
                within(ONE_POINT));
        double savedShare = 1.0 - config.blockedShare() - config.offTargetShare();
        assertThat(share(outcomes, EventType.SHOT_SAVED)).isCloseTo(noGoal * savedShare, within(ONE_POINT));
    }

    @Test
    void aGoalGivesTheKickOffToTheConcedingSideInTheCentreWithTheDurationOfAGoal() {
        List<ActionOutcome> goals = outcomesOfType(EventType.GOAL, MANY_TRIALS / 5);

        assertThat(goals).isNotEmpty().allSatisfy(outcome -> {
            assertThat(outcome.next()).isEqualTo(
                    MatchState.forSetPiece(MatchSide.AWAY, layout.kickOffZone(), SetPieceKind.KICK_OFF));
            assertThat(config.goalWithRestartDuration().contains(outcome.durationSeconds())).isTrue();
            assertThat(outcome.participants().secondary()).isEmpty();
        });
    }

    @Test
    void aSavedShotIsHeldByTheGoalkeeperWhoIsTheSecondaryPlayerOrPushedOutForACorner() {
        List<ActionOutcome> saves = outcomesOfType(EventType.SHOT_SAVED, MANY_TRIALS / 5);
        MatchState held = MatchState.openPlay(MatchSide.AWAY, box);

        assertThat(saves).isNotEmpty().allSatisfy(outcome -> {
            assertThat(outcome.next()).isIn(held, cornerForTheAttackers());
            assertThat(outcome.participants().secondary()).contains(defenders.goalkeeper());
            assertThat(config.shotDuration().contains(outcome.durationSeconds())).isTrue();
        });
    }

    @Test
    void aSavedShotGivesACornerToTheAttackersAtTheConfiguredShare() {
        List<ActionOutcome> saves = outcomesOfType(EventType.SHOT_SAVED, MANY_TRIALS);
        MatchState corner = cornerForTheAttackers();

        long cornerCount = saves.stream().filter(outcome -> outcome.next().equals(corner)).count();

        assertThat((double) cornerCount / saves.size()).isCloseTo(config.savedToCornerShare(), within(ONE_POINT));
    }

    @Test
    void aShotOffTargetGivesTheGoalKickToTheDefendersInTheirBox() {
        List<ActionOutcome> misses = outcomesOfType(EventType.SHOT_OFF_TARGET, MANY_TRIALS / 5);

        assertThat(misses).isNotEmpty().allSatisfy(outcome -> {
            assertThat(outcome.next()).isEqualTo(
                    MatchState.forSetPiece(MatchSide.AWAY, box, SetPieceKind.GOAL_KICK));
            assertThat(outcome.participants().secondary()).isEmpty();
            assertThat(config.shotDuration().contains(outcome.durationSeconds())).isTrue();
        });
    }

    @Test
    void aBlockedShotStaysInTheZoneAndIsSplitBetweenCornerAttackersAndDefendersAsConfigured() {
        List<ActionOutcome> blocks = outcomesOfType(EventType.SHOT_BLOCKED, MANY_TRIALS);
        MatchState corner = cornerForTheAttackers();

        long cornerCount = blocks.stream().filter(outcome -> outcome.next().equals(corner)).count();
        long keptByAttack = blocks.stream()
                .filter(outcome -> outcome.next().possession() == MatchSide.HOME)
                .filter(outcome -> outcome.next().phase() == PlayPhase.BUILD_UP)
                .count();
        long wonByDefence = blocks.stream()
                .filter(outcome -> outcome.next().possession() == MatchSide.AWAY)
                .count();
        assertThat(blocks).allSatisfy(outcome -> {
            assertThat(outcome.next().zone()).isEqualTo(box);
            assertThat(config.shotDuration().contains(outcome.durationSeconds())).isTrue();
        });
        double defenceShare = 1.0 - config.blockedToCornerShare() - config.blockedRecoveredByAttackShare();
        assertThat((double) cornerCount / blocks.size()).isCloseTo(config.blockedToCornerShare(), within(ONE_POINT));
        assertThat((double) keptByAttack / blocks.size()).isCloseTo(config.blockedRecoveredByAttackShare(),
                within(ONE_POINT));
        assertThat((double) wonByDefence / blocks.size()).isCloseTo(defenceShare, within(ONE_POINT));
    }

    @Test
    void aHeaderFromACrossNamesTheCrosserAsSecondaryPlayerOfEveryOutcomeButTheSave() {
        Player crosser = attackers.playersIn(Role.MIDFIELDER).get(0);
        Player header = attackers.playersIn(Role.FORWARD).get(0);
        MatchState crossInFlight = state.withCrossInFlightFrom(crosser);
        ShotResolver resolver = resolver(config, SEED);

        List<ActionOutcome> outcomes = IntStream.range(0, 5_000)
                .mapToObj(index -> resolver.resolveShot(crossInFlight, Score.goalless(), header, ShotKind.HEADER,
                        defenders))
                .toList();

        assertThat(outcomes).filteredOn(outcome -> outcome.type() != EventType.SHOT_SAVED).isNotEmpty()
                .allSatisfy(outcome -> assertThat(outcome.participants().secondary()).contains(crosser));
        assertThat(outcomes).filteredOn(outcome -> outcome.type() == EventType.SHOT_SAVED).isNotEmpty()
                .allSatisfy(outcome -> assertThat(outcome.participants().secondary())
                        .contains(defenders.goalkeeper()));
    }

    @Test
    void aHeaderCarriesTheHeaderKindAndTheExpectedGoalsOfAHeader() {
        Player header = attackers.playersIn(Role.FORWARD).get(0);
        double expected = model.expectedGoals(ZoneRole.OPPONENT_BOX, PlayPhase.BUILD_UP, ShotKind.HEADER, header);
        double expectedWithTheFoot = model.expectedGoals(ZoneRole.OPPONENT_BOX, PlayPhase.BUILD_UP, ShotKind.FOOT,
                header);

        ActionOutcome outcome = resolver(config, SEED).resolveShot(state, Score.goalless(), header,
                ShotKind.HEADER, defenders);

        ShotProfile profile = profileOf(outcome.detail());
        assertThat(profile.kind()).isEqualTo(ShotKind.HEADER);
        assertThat(profile.expectedGoals()).isCloseTo(expected, within(1e-9));
        assertThat(expected).isLessThan(expectedWithTheFoot);
    }

    @Test
    void theShooterIsThePrimaryPlayerOfEveryOutcome() {
        Player shooter = attackers.playersIn(Role.FORWARD).get(1);

        List<ActionOutcome> outcomes = IntStream.range(0, 500)
                .mapToObj(index -> resolver(config, index).resolveShot(state, Score.goalless(), shooter,
                        ShotKind.FOOT, defenders))
                .toList();

        assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.participants().primary()).contains(shooter));
    }

    @Test
    void theDetailCarriesTheExpectedGoalsBeforeTheGoalkeeperAndTheDangerOfTheShot() {
        Player shooter = attackers.playersIn(Role.FORWARD).get(0);
        double expectedGoals = model.expectedGoals(ZoneRole.OPPONENT_BOX, PlayPhase.BUILD_UP, ShotKind.FOOT,
                shooter);
        ShotQuality danger = scale.dangerOf(expectedGoals);
        TeamOnPitch greatKeeper = teamOnPitch("Wall", NEUTRAL_SHOOTING, 99);

        List<ActionOutcome> outcomes = shots(resolver(config, SEED), attackers, greatKeeper, 2_000);

        assertThat(outcomes).allSatisfy(outcome -> {
            ShotProfile profile = profileOf(outcome);
            assertThat(profile.expectedGoals()).isCloseTo(expectedGoals, within(1e-9));
            assertThat(profile.kind()).isEqualTo(ShotKind.FOOT);
            assertThat(profile.quality()).isEqualTo(danger);
            assertThat(profile.penalty()).isFalse();
        });
    }

    @Test
    void theDetailOfASaveCarriesTheDifficultyOfTheSave() {
        Player shooter = attackers.playersIn(Role.FORWARD).get(0);
        double goalProbability = goalProbabilityOf(shooter, defenders);

        List<ActionOutcome> saves = outcomesOfType(EventType.SHOT_SAVED, 2_000);

        assertThat(saves).isNotEmpty().allSatisfy(outcome ->
                assertThat(outcome.detail()).isInstanceOfSatisfying(EventDetail.Save.class, save ->
                        assertThat(save.saveQuality()).isEqualTo(scale.difficultyOfSaving(goalProbability))));
    }

    @Test
    void theDetailOfAGoalCarriesTheContextFromTheScoreBeforeTheGoal() {
        Player shooter = attackers.playersIn(Role.FORWARD).get(0);
        ShotResolver resolver = resolver(config, SEED);

        List<ActionOutcome> goals = IntStream.range(0, TRIALS)
                .mapToObj(index -> resolver.resolveShot(state, new Score(1, 3), shooter, ShotKind.FOOT, defenders))
                .filter(outcome -> outcome.type() == EventType.GOAL)
                .toList();

        assertThat(goals).isNotEmpty().allSatisfy(outcome ->
                assertThat(outcome.detail()).isInstanceOfSatisfying(EventDetail.Goal.class, goal ->
                        assertThat(goal.context()).isEqualTo(GoalContext.CONSOLATION)));
    }

    @Test
    void theExpectedGoalsDependOnTheZoneRoleOfTheShot() {
        Player shooter = attackers.playersIn(Role.FORWARD).get(0);
        Zone midfield = layout.kickOffZone();
        MatchState longRange = MatchState.openPlay(MatchSide.HOME, midfield);

        ActionOutcome outcome = resolver(config, SEED).resolveShot(longRange, Score.goalless(), shooter,
                ShotKind.FOOT, defenders);

        double expected = model.expectedGoals(ZoneRole.MIDFIELD, PlayPhase.BUILD_UP, ShotKind.FOOT, shooter);
        assertThat(profileOf(outcome).expectedGoals()).isCloseTo(expected, within(1e-9));
    }

    @Test
    void theShotKindAndPhaseOfTheStateReachTheExpectedGoals() {
        Player shooter = attackers.playersIn(Role.FORWARD).get(0);
        MatchState setPiece = MatchState.forSetPiece(MatchSide.HOME, box, SetPieceKind.GOAL_KICK);

        ActionOutcome outcome = resolver(config, SEED).resolveShot(setPiece, Score.goalless(), shooter,
                ShotKind.HEADER, defenders);

        double expected = model.expectedGoals(ZoneRole.OPPONENT_BOX, PlayPhase.SET_PIECE, ShotKind.HEADER, shooter);
        assertThat(profileOf(outcome).expectedGoals()).isCloseTo(expected, within(1e-9));
        assertThat(profileOf(outcome).kind()).isEqualTo(ShotKind.HEADER);
    }

    @ParameterizedTest
    @ValueSource(ints = {50, 90})
    void shootingMovesTheFrequencyOfGoalsOfAShotAsTheModelSays(int shooting) {
        TeamOnPitch tuned = teamOnPitch("Tuned", shooting, NEUTRAL_GOALKEEPING);
        double expected = goalProbabilityOf(tuned.playersIn(Role.FORWARD).get(0), defenders);

        List<ActionOutcome> outcomes = shots(resolver(config, SEED), tuned, defenders, TRIALS);

        assertThat(share(outcomes, EventType.GOAL)).isCloseTo(expected, within(ONE_POINT));
    }

    @ParameterizedTest
    @ValueSource(ints = {50, 95})
    void goalkeepingMovesTheFrequencyOfGoalsOfAShotAsTheModelSays(int goalkeeping) {
        TeamOnPitch tuned = teamOnPitch("Tuned", NEUTRAL_SHOOTING, goalkeeping);
        double expected = goalProbabilityOf(attackers.playersIn(Role.FORWARD).get(0), tuned);

        List<ActionOutcome> outcomes = shots(resolver(config, SEED), attackers, tuned, TRIALS);

        assertThat(share(outcomes, EventType.GOAL)).isCloseTo(expected, within(ONE_POINT));
    }

    @Test
    void aBetterGoalkeeperConcedesFewerGoalsToTheSameShooter() {
        TeamOnPitch weakKeeper = teamOnPitch("Weak", NEUTRAL_SHOOTING, 40);
        TeamOnPitch strongKeeper = teamOnPitch("Strong", NEUTRAL_SHOOTING, 95);

        double weak = share(shots(resolver(config, SEED), attackers, weakKeeper, TRIALS), EventType.GOAL);
        double strong = share(shots(resolver(config, SEED), attackers, strongKeeper, TRIALS), EventType.GOAL);

        assertThat(strong).isLessThan(weak);
    }

    @Test
    void aPenaltyGoesInWithTheProbabilityOfThePenaltyModel() {
        double expectedGoals = model.penaltyExpectedGoals(bestShooterOf(penaltyTakers));
        double expected = model.goalProbability(expectedGoals, defenders.goalkeeper());

        List<ActionOutcome> outcomes = penalties(resolver(config, SEED), penaltyTakers, defenders, TRIALS);

        assertThat(expected).isCloseTo(0.79, within(1e-9));
        assertThat(share(outcomes, EventType.GOAL)).isCloseTo(expected, within(ONE_POINT));
    }

    @Test
    void aPenaltyIsNeverBlockedAndSplitsTheMissesBetweenOffTargetAndSaved() {
        List<ActionOutcome> outcomes = penalties(resolver(config, SEED), penaltyTakers, defenders, MANY_TRIALS);

        MissShares shares = MissShares.ofPenalty(config);
        double miss = 1.0 - 0.79;
        assertThat(share(outcomes, EventType.SHOT_BLOCKED)).isZero();
        assertThat(share(outcomes, EventType.SHOT_OFF_TARGET)).isCloseTo(miss * shares.offTarget(),
                within(ONE_POINT));
        assertThat(share(outcomes, EventType.SHOT_SAVED)).isCloseTo(miss * shares.saved(), within(ONE_POINT));
    }

    @Test
    void aPenaltyIsNeverBlockedEvenWhenBlockingIsTheOnlyMissOfAShot() {
        ShotConfig mostlyBlocked = TestShotConfig.withMissShares(0.9, 0.05, 0.4);

        List<ActionOutcome> outcomes = penalties(resolver(mostlyBlocked, SEED), attackers, defenders, TRIALS);

        assertThat(outcomes).extracting(ActionOutcome::type).doesNotContain(EventType.SHOT_BLOCKED);
        assertThat(share(shots(resolver(mostlyBlocked, SEED), attackers, defenders, TRIALS),
                EventType.SHOT_BLOCKED)).isGreaterThan(0.5);
    }

    @Test
    void thePenaltyDetailMarksThePenaltyAndTheDurationIsThatOfAPenaltyOrOfAGoal() {
        List<ActionOutcome> outcomes = penalties(resolver(config, SEED), penaltyTakers, defenders, 2_000);

        assertThat(outcomes).allSatisfy(outcome -> {
            assertThat(profileOf(outcome).penalty()).isTrue();
            assertThat(profileOf(outcome).quality()).isEqualTo(ShotQuality.BIG_CHANCE);
            assertThat(profileOf(outcome).expectedGoals()).isCloseTo(0.79, within(1e-9));
            DurationRange expectedRange = outcome.type() == EventType.GOAL
                    ? config.goalWithRestartDuration()
                    : config.penaltyDuration();
            assertThat(expectedRange.contains(outcome.durationSeconds())).isTrue();
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {50, 95})
    void shootingMovesTheFrequencyOfGoalsOfAPenaltyAsTheModelSays(int shooting) {
        TeamOnPitch tuned = teamOnPitch("Tuned", shooting, NEUTRAL_GOALKEEPING);
        double expectedGoals = model.penaltyExpectedGoals(bestShooterOf(tuned));
        double expected = model.goalProbability(expectedGoals, defenders.goalkeeper());

        List<ActionOutcome> outcomes = penalties(resolver(config, SEED), tuned, defenders, TRIALS);

        assertThat(share(outcomes, EventType.GOAL)).isCloseTo(expected, within(ONE_POINT));
    }

    @Test
    void goalkeepingMovesTheFrequencyOfGoalsOfAPenaltyAsTheModelSays() {
        TeamOnPitch strongKeeper = teamOnPitch("Wall", NEUTRAL_SHOOTING, 99);
        double expectedGoals = model.penaltyExpectedGoals(bestShooterOf(attackers));
        double expected = model.goalProbability(expectedGoals, strongKeeper.goalkeeper());

        List<ActionOutcome> outcomes = penalties(resolver(config, SEED), attackers, strongKeeper, TRIALS);

        assertThat(share(outcomes, EventType.GOAL)).isCloseTo(expected, within(ONE_POINT));
        assertThat(expected).isLessThan(0.79);
    }

    @Test
    void thePenaltyTakerIsTheBestShooterOnThePitch() {
        TeamOnPitch team = teamWithShooters(List.of(60, 50, 90, 50, 70));

        Player taker = resolver(config, SEED).penaltyTakerOf(team);

        assertThat(taker.attributes().shooting()).isEqualTo(90);
    }

    @Test
    void atTheSameShootingThePenaltyTakerIsTheFirstInTheOrderOnThePitch() {
        TeamOnPitch team = teamWithShooters(List.of(60, 80, 50, 80, 70));

        Player taker = resolver(config, SEED).penaltyTakerOf(team);

        assertThat(taker).isEqualTo(team.players().get(1));
    }

    @Test
    void thePenaltyIsTakenByTheBestShooterAndTheParticipantsAreTakerAndKeeperOnSaves() {
        TeamOnPitch team = teamWithShooters(List.of(60, 50, 90, 50, 70));
        Player expectedTaker = team.players().get(2);

        List<ActionOutcome> outcomes = penalties(resolver(config, SEED), team, defenders, 2_000);

        assertThat(outcomes).allSatisfy(outcome ->
                assertThat(outcome.participants().primary()).contains(expectedTaker));
        assertThat(outcomes).filteredOn(outcome -> outcome.type() == EventType.SHOT_SAVED).isNotEmpty()
                .allSatisfy(outcome ->
                        assertThat(outcome.participants().secondary()).contains(defenders.goalkeeper()));
    }

    @Test
    void theSameSeedGivesTheSameOutcomes() {
        List<ActionOutcome> first = shots(resolver(config, SEED), attackers, defenders, 300);
        List<ActionOutcome> second = shots(resolver(config, SEED), attackers, defenders, 300);

        assertThat(first).containsExactlyElementsOf(second);
    }

private MatchState cornerForTheAttackers() {
    return MatchState.forSetPiece(MatchSide.HOME, box, SetPieceKind.CORNER);
}

private ShotProfile profileOf(EventDetail detail) {
    return switch (detail) {
        case EventDetail.Shot shot -> shot.profile();
        case EventDetail.Save save -> save.profile();
        case EventDetail.Goal goal -> goal.profile();
        default -> throw new AssertionError("not a shot detail: " + detail);
    };
}

private List<ActionOutcome> outcomesOfType(EventType type, int trials) {
        return shots(resolver(config, SEED), attackers, defenders, trials).stream()
                .filter(outcome -> outcome.type() == type)
                .toList();
    }

    private List<ActionOutcome> shots(ShotResolver resolver, TeamOnPitch shootingTeam, TeamOnPitch keepingTeam,
            int count) {
        Player shooter = shootingTeam.playersIn(Role.FORWARD).get(0);
        return IntStream.range(0, count)
                .mapToObj(index -> resolver.resolveShot(state, Score.goalless(), shooter, ShotKind.FOOT, keepingTeam))
                .toList();
    }

    private List<ActionOutcome> penalties(ShotResolver resolver, TeamOnPitch shootingTeam, TeamOnPitch keepingTeam,
            int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> resolver.resolvePenalty(state, Score.goalless(), shootingTeam, keepingTeam))
                .toList();
    }

    private double share(List<ActionOutcome> outcomes, EventType type) {
        long matching = outcomes.stream().filter(outcome -> outcome.type() == type).count();
        return (double) matching / outcomes.size();
    }

    private double goalProbabilityOf(Player shooter, TeamOnPitch keepingTeam) {
        double expectedGoals = model.expectedGoals(ZoneRole.OPPONENT_BOX, PlayPhase.BUILD_UP, ShotKind.FOOT, shooter);
        return model.goalProbability(expectedGoals, keepingTeam.goalkeeper());
    }

    private Player bestShooterOf(TeamOnPitch team) {
        return resolver(config, SEED).penaltyTakerOf(team);
    }

    private ShotProfile profileOf(ActionOutcome outcome) {
        return switch (outcome.detail()) {
            case EventDetail.Goal goal -> goal.profile();
            case EventDetail.Save save -> save.profile();
            case EventDetail.Shot shot -> shot.profile();
            default -> throw new AssertionError("not a shot detail: " + outcome.detail());
        };
    }

    private ShotResolver resolver(ShotConfig shotConfig, long seed) {
        MatchRandom random = new MatchRandom(seed);
        return TestResolvers.shotResolver(shotConfig, layout, random);
    }

    private TeamOnPitch teamOnPitch(String name, int forwardShooting, int goalkeeperGoalkeeping) {
        PlayerAttributes keeper = TestTeams.withShootingAndGoalkeeping(NEUTRAL_SHOOTING, goalkeeperGoalkeeping);
        PlayerAttributes forwards = TestTeams.withShootingAndGoalkeeping(forwardShooting, NEUTRAL_GOALKEEPING);
        return TeamOnPitch.startingWith(TestTeams.teamWithKeeperAndForwards(name, keeper, forwards));
    }

    private TeamOnPitch teamWithShooters(List<Integer> shootings) {
        List<Player> players = new ArrayList<>();
        for (int index = 0; index < shootings.size(); index++) {
            PlayerAttributes attributes = TestTeams.withShootingAndGoalkeeping(shootings.get(index), 50);
            players.add(TestTeams.player("Taker-" + index, Role.FORWARD, attributes));
        }
        TeamOnPitch complete = teamOnPitch("Rest", NEUTRAL_SHOOTING, NEUTRAL_GOALKEEPING);
        return new TeamOnPitch(complete.team(), players);
    }
}
