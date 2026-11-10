package org.tasktelemetry.matchsimulator.engine.resolution;

import org.tasktelemetry.matchsimulator.engine.MatchRandom;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.config.CounterAttackConfig;
import org.tasktelemetry.matchsimulator.engine.config.DurationRange;
import org.tasktelemetry.matchsimulator.engine.config.PlayStyleModifiers;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.ShotKind;
import org.tasktelemetry.matchsimulator.engine.pitch.PitchLayout;
import org.tasktelemetry.matchsimulator.engine.pitch.Zone;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.resolution.shot.ShotResolver;
import org.tasktelemetry.matchsimulator.engine.state.MatchState;
import org.tasktelemetry.matchsimulator.engine.state.Score;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;

public final class CounterAttackResolver {

    private final CounterAttackConfig config;
    private final PlayStyleModifiers playStyles;
    private final PitchLayout layout;
    private final MatchRandom random;
    private final Matchups matchups;
    private final ShotResolver shotResolver;

    public CounterAttackResolver(CounterAttackConfig config, PlayStyleModifiers playStyles, PitchLayout layout,
            MatchRandom random, Matchups matchups, ShotResolver shotResolver) {
        this.config = config;
        this.playStyles = playStyles;
        this.layout = layout;
        this.random = random;
        this.matchups = matchups;
        this.shotResolver = shotResolver;
    }

    public double startProbability(ZoneRole recoveryZoneRole, PlayStyle recoveringStyle) {
        if (!canStartFrom(recoveryZoneRole)) {
            return 0.0;
        }
        double styleMultiplier = playStyles.effectOf(recoveringStyle).counterAttackStart();
        return config.startProbability() * styleMultiplier;
    }

    public Duel paceDuel(Player runner, Player chaser) {
        return Duel.between(runner.attributes().pace(), chaser.attributes().pace());
    }

    public ActionOutcome openCounterAttackIfBallRecovered(ActionOutcome outcome, TeamOnPitch recoverers) {
        if (!isBallRecovered(outcome)) {
            return outcome;
        }
        if (!decidesToCounterAttack(outcome.next(), recoverers)) {
            return outcome;
        }
        return inCounterAttackPhase(outcome);
    }

    public ActionOutcome resolve(MatchState state, Score score, TeamOnPitch attackers, TeamOnPitch defenders) {
        Player runner = pickRunner(state, attackers);
        Player chaser = pickChaser(state, defenders);
        Duel attributeDuel = paceDuel(runner, chaser);
        Duel duel = applyStyleAndVenue(attributeDuel, state, attackers, runner, defenders, chaser);
        if (attackerWins(duel)) {
            return completeRun(state, score, attackers, runner, defenders);
        }
        return loseRun(state, chaser);
    }

    private ActionOutcome completeRun(MatchState state, Score score, TeamOnPitch attackers, Player runner,
            TeamOnPitch defenders) {
        if (isInOpponentBox(state)) {
            return shotResolver.resolveShot(state, score, attackers, runner, ShotKind.FOOT, defenders);
        }
        Zone target = forwardZone(state);
        MatchState next = new MatchState(state.possession(), target, state.phase());
        int duration = drawDuration();
        return ActionOutcome.byPlayer(EventType.DRIBBLE_COMPLETED, runner, next, duration);
    }

    private ActionOutcome loseRun(MatchState state, Player chaser) {
        MatchSide recoveringSide = state.possession().opposite();
        MatchState next = MatchState.openPlay(recoveringSide, state.zone());
        int duration = drawDuration();
        return ActionOutcome.byPlayer(EventType.TACKLE, chaser, next, duration);
    }

    private Player pickRunner(MatchState state, TeamOnPitch attackers) {
        return matchups.pickAttacker(state, attackers);
    }

    private Player pickChaser(MatchState state, TeamOnPitch defenders) {
        return matchups.pickDefender(state, defenders);
    }

    private Duel applyStyleAndVenue(Duel attributeDuel, MatchState state, TeamOnPitch attackers, Player attacker,
            TeamOnPitch defenders, Player defender) {
        return matchups.counterAttackDuel(attributeDuel, state, attackers, attacker, defenders, defender);
    }

    private boolean isBallRecovered(ActionOutcome outcome) {
        return outcome.type().isBallRecovery();
    }

    private boolean decidesToCounterAttack(MatchState recovered, TeamOnPitch recoverers) {
        ZoneRole recoveryZoneRole = layout.roleOf(recovered.zone(), recovered.possession());
        double probability = startProbability(recoveryZoneRole, recoverers.team().style());
        return random.occurs(probability);
    }

    private ActionOutcome inCounterAttackPhase(ActionOutcome outcome) {
        MatchState counterAttack = outcome.next().inCounterAttack();
        return new ActionOutcome(outcome.type(), outcome.participants(), counterAttack, outcome.durationSeconds(),
                outcome.detail());
    }

    private boolean canStartFrom(ZoneRole recoveryZoneRole) {
        return recoveryZoneRole == ZoneRole.DEFENCE || recoveryZoneRole == ZoneRole.MIDFIELD;
    }

    private boolean isInOpponentBox(MatchState state) {
        ZoneRole zoneRole = layout.roleOf(state.zone(), state.possession());
        return zoneRole == ZoneRole.OPPONENT_BOX;
    }

    private Zone forwardZone(MatchState state) {
        return layout.advance(state.zone(), state.possession())
                .orElseThrow(() -> new IllegalStateException("no zone ahead of " + state.zone()));
    }

    private int drawDuration() {
        DurationRange range = config.advanceDuration();
        return random.nextIntInclusive(range.minSeconds(), range.maxSeconds());
    }

    private boolean attackerWins(Duel duel) {
        double probability = duel.attackerWinProbability();
        return random.occurs(probability);
    }
}
