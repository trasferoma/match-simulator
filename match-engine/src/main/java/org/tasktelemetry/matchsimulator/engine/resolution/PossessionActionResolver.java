package org.tasktelemetry.matchsimulator.engine.resolution;

import java.util.List;
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
import org.tasktelemetry.matchsimulator.engine.team.Role;

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
            return attemptCross(state, attackers, defenders);
        }
        Player carrier = pickCarrier(state, attackers);
        Player opponent = pickOpponent(state, defenders);
        if (decidesToDribble()) {
            return resolveDribble(state, score, carrier, opponent, attackers, defenders);
        }
        if (hasNoTeammateToPassTo(state, attackers, carrier)) {
            return resolveInsteadOfPassing(state, score, carrier, opponent, attackers, defenders);
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
        Duel duel = applyStyleAndVenue(attributeDuel, state, attackers, passer, defenders, interceptor);
        if (attackerWins(duel)) {
            return completeAction(state, score, EventType.PASS_COMPLETED, attackers, passer, defenders);
        }
        return turnOverPossession(state, EventType.INTERCEPTION, interceptor);
    }

    private ActionOutcome resolveDribble(MatchState state, Score score, Player dribbler, Player tackler,
            TeamOnPitch attackers, TeamOnPitch defenders) {
        Duel duel = openPlayDribbleDuel(state, dribbler, tackler, attackers, defenders);
        if (attackerWins(duel)) {
            return completeDribble(state, score, attackers, dribbler, defenders);
        }
        return loseDribble(state, defenders, dribbler, tackler);
    }

    private ActionOutcome resolveInsteadOfPassing(MatchState state, Score score, Player carrier, Player opponent,
            TeamOnPitch attackers, TeamOnPitch defenders) {
        Duel duel = openPlayDribbleDuel(state, carrier, opponent, attackers, defenders);
        if (!isWorthDribblingInsteadOfPassing(duel)) {
            return circulateBall(state, carrier);
        }
        if (attackerWins(duel)) {
            return completeDribble(state, score, attackers, carrier, defenders);
        }
        return loseDribble(state, defenders, carrier, opponent);
    }

    private ActionOutcome completeDribble(MatchState state, Score score, TeamOnPitch attackers, Player dribbler,
            TeamOnPitch defenders) {
        return completeAction(state, score, EventType.DRIBBLE_COMPLETED, attackers, dribbler, defenders);
    }

    private ActionOutcome loseDribble(MatchState state, TeamOnPitch defenders, Player dribbler, Player tackler) {
        return loseDuel(state, EventType.TACKLE, defenders, dribbler, tackler);
    }

    private ActionOutcome circulateBall(MatchState state, Player carrier) {
        int duration = drawDuration(state);
        return ActionOutcome.byPlayer(EventType.BALL_CIRCULATED, carrier, state.possession(), state, duration);
    }

    private ActionOutcome completeAction(MatchState state, Score score, EventType type, TeamOnPitch attackers,
            Player attacker, TeamOnPitch defenders) {
        if (isInOpponentBox(state)) {
            return shotResolver.resolveShot(state, score, attackers, attacker, ShotKind.FOOT, defenders);
        }
        return advancePossession(state, type, attacker);
    }

    private ActionOutcome advancePossession(MatchState state, EventType type, Player attacker) {
        MatchState next = stateAfterAdvance(state, type, attacker);
        int duration = drawDuration(state);
        return ActionOutcome.byPlayer(type, attacker, state.possession(), next, duration);
    }

    private MatchState stateAfterAdvance(MatchState state, EventType type, Player attacker) {
        Zone target = forwardZone(state);
        MatchState moved = state.movedTo(target);
        if (type == EventType.PASS_COMPLETED) {
            return moved.withLastPasser(attacker);
        }
        return moved;
    }

    private ActionOutcome loseDuel(MatchState state, EventType recovery, TeamOnPitch defenders, Player attacker,
            Player defender) {
        Optional<ActionOutcome> foul = foulResolver.resolveFoul(state, defenders, defender, attacker);
        return foul.orElseGet(() -> turnOverPossession(state, recovery, defender));
    }

    private ActionOutcome turnOverPossession(MatchState state, EventType type, Player recoverer) {
        MatchSide recoveringSide = state.possession().opposite();
        MatchState next = new MatchState(recoveringSide, state.zone(), state.phase());
        int duration = drawDuration(state);
        return ActionOutcome.byPlayer(type, recoverer, recoveringSide, next, duration);
    }

    private ActionOutcome attemptCross(MatchState state, TeamOnPitch attackers, TeamOnPitch defenders) {
        return crossResolver.resolveCross(state, attackers, defenders);
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

    private Duel openPlayDribbleDuel(MatchState state, Player dribbler, Player tackler, TeamOnPitch attackers,
            TeamOnPitch defenders) {
        Duel attributeDuel = dribblingDuel(dribbler, tackler);
        return applyStyleAndVenue(attributeDuel, state, attackers, dribbler, defenders, tackler);
    }

    private Duel applyStyleAndVenue(Duel attributeDuel, MatchState state, TeamOnPitch attackers, Player attacker,
            TeamOnPitch defenders, Player defender) {
        return matchups.openPlayDuel(attributeDuel, state, attackers, attacker, defenders, defender);
    }

    private boolean hasNoTeammateToPassTo(MatchState state, TeamOnPitch attackers, Player carrier) {
        if (isInOpponentBox(state)) {
            return false;
        }
        Zone zoneAhead = forwardZone(state);
        ZoneRole arrivalRole = layout.roleOf(zoneAhead, state.possession());
        Role arrivalSector = PlayerPicker.sectorOf(arrivalRole);
        List<Player> sectorPlayers = attackers.playersIn(arrivalSector);
        return sectorPlayers.stream().allMatch(player -> player.equals(carrier));
    }

    private boolean isWorthDribblingInsteadOfPassing(Duel duel) {
        double probability = duel.attackerWinProbability();
        return probability >= config.noTeammateDribbleThreshold();
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
