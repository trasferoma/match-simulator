package org.tasktelemetry.matchsimulator.engine.resolution;

import java.util.Optional;

import org.tasktelemetry.matchsimulator.engine.MatchRandom;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.config.DurationRange;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.ShotKind;
import org.tasktelemetry.matchsimulator.engine.pitch.PitchLayout;
import org.tasktelemetry.matchsimulator.engine.pitch.Zone;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.resolution.shot.CrossResolver;
import org.tasktelemetry.matchsimulator.engine.resolution.shot.ShotResolver;
import org.tasktelemetry.matchsimulator.engine.resolution.stoppage.FoulResolver;
import org.tasktelemetry.matchsimulator.engine.state.MatchState;
import org.tasktelemetry.matchsimulator.engine.state.Score;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;

public final class PossessionActionResolver {

    private final EngineConfig config;
    private final PitchLayout layout;
    private final MatchRandom random;
    private final Matchups matchups;
    private final ShotResolver shotResolver;
    private final CrossResolver crossResolver;
    private final FoulResolver foulResolver;

    public PossessionActionResolver(EngineConfig config, PitchLayout layout, MatchRandom random,
            Matchups matchups, ShotResolver shotResolver, CrossResolver crossResolver, FoulResolver foulResolver) {
        this.config = config;
        this.layout = layout;
        this.random = random;
        this.matchups = matchups;
        this.shotResolver = shotResolver;
        this.crossResolver = crossResolver;
        this.foulResolver = foulResolver;
    }

    public ActionOutcome resolve(MatchState state, Score score, TeamOnPitch attackers, TeamOnPitch defenders) {
        if (isOnAttackingThird(state) && decidesToCross(attackers)) {
            return attemptCross(state, attackers);
        }
        Player carrier = pickCarrier(state, attackers);
        Player opponent = pickOpponent(state, defenders);
        if (decidesToDribble()) {
            return resolveDribble(state, score, carrier, opponent, attackers, defenders);
        }
        return resolvePass(state, score, carrier, opponent, attackers, defenders);
    }

    public Duel passingDuel(Player passer, Player interceptor) {
        return Duel.between(passer.attributes().passing(), interceptor.attributes().positioning());
    }

    public Duel dribblingDuel(Player dribbler, Player tackler) {
        return Duel.between(dribbler.attributes().dribbling(), tackler.attributes().tackling());
    }

    private ActionOutcome resolvePass(MatchState state, Score score, Player passer, Player interceptor,
            TeamOnPitch attackers, TeamOnPitch defenders) {
        Duel attributeDuel = passingDuel(passer, interceptor);
        Duel duel = applyStyleAndVenue(attributeDuel, state, attackers, defenders);
        if (attackerWins(duel)) {
            return completeAction(state, score, EventType.PASS_COMPLETED, passer, defenders);
        }
        return turnOverPossession(state, EventType.INTERCEPTION, interceptor);
    }

    private ActionOutcome resolveDribble(MatchState state, Score score, Player dribbler, Player tackler,
            TeamOnPitch attackers, TeamOnPitch defenders) {
        Duel attributeDuel = dribblingDuel(dribbler, tackler);
        Duel duel = applyStyleAndVenue(attributeDuel, state, attackers, defenders);
        if (attackerWins(duel)) {
            return completeAction(state, score, EventType.DRIBBLE_COMPLETED, dribbler, defenders);
        }
        return loseDuel(state, EventType.TACKLE, dribbler, tackler);
    }

    private ActionOutcome completeAction(MatchState state, Score score, EventType type, Player attacker,
            TeamOnPitch defenders) {
        if (isInOpponentBox(state)) {
            return shotResolver.resolveShot(state, score, attacker, ShotKind.FOOT, defenders);
        }
        return advancePossession(state, type, attacker);
    }

    private ActionOutcome advancePossession(MatchState state, EventType type, Player attacker) {
        Zone target = forwardZone(state);
        MatchState next = new MatchState(state.possession(), target, state.phase());
        int duration = drawDuration(state);
        return ActionOutcome.byPlayer(type, attacker, next, duration);
    }

    private ActionOutcome loseDuel(MatchState state, EventType recovery, Player attacker, Player defender) {
        Optional<ActionOutcome> foul = foulResolver.resolveFoul(state, defender, attacker);
        return foul.orElseGet(() -> turnOverPossession(state, recovery, defender));
    }

    private ActionOutcome turnOverPossession(MatchState state, EventType type, Player recoverer) {
        MatchSide recoveringSide = state.possession().opposite();
        MatchState next = new MatchState(recoveringSide, state.zone(), state.phase());
        int duration = drawDuration(state);
        return ActionOutcome.byPlayer(type, recoverer, next, duration);
    }

    private ActionOutcome attemptCross(MatchState state, TeamOnPitch attackers) {
        return crossResolver.resolveCross(state, attackers);
    }

    private boolean isOnAttackingThird(MatchState state) {
        ZoneRole zoneRole = layout.roleOf(state.zone(), state.possession());
        return zoneRole == ZoneRole.ATTACKING_THIRD;
    }

    private boolean isInOpponentBox(MatchState state) {
        ZoneRole zoneRole = layout.roleOf(state.zone(), state.possession());
        return zoneRole == ZoneRole.OPPONENT_BOX;
    }

    private Zone forwardZone(MatchState state) {
        return layout.advance(state.zone(), state.possession())
                .orElseThrow(() -> new IllegalStateException("no zone ahead of " + state.zone()));
    }

    private int drawDuration(MatchState state) {
        ZoneRole zoneRole = layout.roleOf(state.zone(), state.possession());
        DurationRange range = durationFor(zoneRole);
        return random.nextIntInclusive(range.minSeconds(), range.maxSeconds());
    }

    private DurationRange durationFor(ZoneRole zoneRole) {
        return switch (zoneRole) {
            case OWN_BOX, DEFENCE, MIDFIELD -> config.buildUpAndMidfieldDuration();
            case ATTACKING_THIRD, OPPONENT_BOX -> config.attackingThirdDuration();
        };
    }

    private Player pickCarrier(MatchState state, TeamOnPitch attackers) {
        return matchups.pickAttacker(state, attackers);
    }

    private Player pickOpponent(MatchState state, TeamOnPitch defenders) {
        return matchups.pickDefender(state, defenders);
    }

    private Duel applyStyleAndVenue(Duel attributeDuel, MatchState state, TeamOnPitch attackers,
            TeamOnPitch defenders) {
        return matchups.openPlayDuel(attributeDuel, state, attackers, defenders);
    }

    private boolean decidesToCross(TeamOnPitch attackers) {
        PlayStyle style = attackers.team().style();
        double probability = crossResolver.crossProbability(style);
        return random.occurs(probability);
    }

    private boolean decidesToDribble() {
        return random.occurs(config.dribbleProbability());
    }

    private boolean attackerWins(Duel duel) {
        double probability = duel.attackerWinProbability();
        return random.occurs(probability);
    }
}
