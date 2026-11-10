package org.tasktelemetry.matchsimulator.engine.resolution.shot;

import java.util.List;

import org.tasktelemetry.matchsimulator.engine.MatchRandom;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.config.CrossConfig;
import org.tasktelemetry.matchsimulator.engine.config.DurationRange;
import org.tasktelemetry.matchsimulator.engine.config.PlayStyleModifiers;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.Participants;
import org.tasktelemetry.matchsimulator.engine.event.ShotKind;
import org.tasktelemetry.matchsimulator.engine.pitch.PitchLayout;
import org.tasktelemetry.matchsimulator.engine.pitch.Zone;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.resolution.ActionOutcome;
import org.tasktelemetry.matchsimulator.engine.resolution.Duel;
import org.tasktelemetry.matchsimulator.engine.resolution.Matchups;
import org.tasktelemetry.matchsimulator.engine.resolution.PlayerPicker;
import org.tasktelemetry.matchsimulator.engine.state.MatchState;
import org.tasktelemetry.matchsimulator.engine.state.PlayPhase;
import org.tasktelemetry.matchsimulator.engine.state.Score;
import org.tasktelemetry.matchsimulator.engine.state.SetPieceKind;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

public final class CrossResolver {

    private static final ZoneRole HEADER_ZONE_ROLE = ZoneRole.OPPONENT_BOX;

    private final CrossConfig config;
    private final PlayStyleModifiers playStyles;
    private final PitchLayout layout;
    private final MatchRandom random;
    private final PlayerPicker picker;
    private final ShotResolver shotResolver;
    private final Matchups matchups;

    public CrossResolver(CrossConfig config, PlayStyleModifiers playStyles, PitchLayout layout, MatchRandom random,
            PlayerPicker picker, ShotResolver shotResolver, Matchups matchups) {
        this.config = config;
        this.playStyles = playStyles;
        this.layout = layout;
        this.random = random;
        this.picker = picker;
        this.shotResolver = shotResolver;
        this.matchups = matchups;
    }

    public double crossProbability(PlayStyle style) {
        double propensity = playStyles.effectOf(style).crossPropensity();
        return config.crossProbability() * propensity;
    }

    public Duel crossDuel(Player crosser, Player defender) {
        return Duel.between(crosser.attributes().passing(), defender.attributes().positioning());
    }

    public Duel aerialDuel(Player attacker, Player defender) {
        return Duel.between(attacker.attributes().aerial(), defender.attributes().aerial());
    }

    public Player pickCrosser(TeamOnPitch attackers) {
        List<Player> candidates = listCrosserCandidates(attackers);
        return picker.pickByPassing(candidates);
    }

    public ActionOutcome resolveCross(MatchState state, TeamOnPitch attackers, TeamOnPitch defenders) {
        Player crosser = pickCrosser(attackers);
        Player defender = pickDefender(state, defenders);
        if (crossReachesTheBox(state, attackers, crosser, defenders, defender)) {
            return deliverCross(state, crosser);
        }
        return failCross(state, crosser, defender);
    }

    public ActionOutcome resolveCrossInFlight(MatchState state, Score score, TeamOnPitch attackers,
            TeamOnPitch defenders) {
        if (!isCrossFromSetPiece(state)) {
            return resolveAerialDuel(state, score, attackers, defenders);
        }
        Player crosser = state.crosser();
        Player defender = pickDefender(state, defenders);
        if (crossReachesTheBox(state, attackers, crosser, defenders, defender)) {
            return resolveAerialDuel(state, score, attackers, defenders);
        }
        return failCross(state, crosser, defender);
    }

    public ActionOutcome resolveAerialDuel(MatchState state, Score score, TeamOnPitch attackers,
            TeamOnPitch defenders) {
        Player attacker = pickHeader(state, attackers);
        Player defender = pickDefender(state, defenders);
        Duel attributeDuel = aerialDuel(attacker, defender);
        Duel duel = matchups.openPlayDuel(attributeDuel, state, attackers, attacker, defenders, defender);
        if (attackerWins(duel)) {
            return shootHeader(state, score, attackers, attacker, defenders);
        }
        return clearBall(state, defender);
    }

    private ActionOutcome shootHeader(MatchState state, Score score, TeamOnPitch attackers, Player attacker,
            TeamOnPitch defenders) {
        return shotResolver.resolveShot(state, score, attackers, attacker, ShotKind.HEADER, defenders);
    }

    private ActionOutcome deliverCross(MatchState state, Player crosser) {
        Zone opponentBox = opponentBoxOf(state);
        MatchState inTheBox = MatchState.openPlay(state.possession(), opponentBox);
        MatchState next = inTheBox.withCrossInFlightFrom(crosser);
        int duration = drawDuration(config.crossDuration());
        Participants participants = Participants.of(crosser);
        EventDetail detail = EventDetail.none();
        return new ActionOutcome(EventType.CROSS_COMPLETED, participants, next, duration, detail);
    }

    private ActionOutcome failCross(MatchState state, Player crosser, Player defender) {
        if (decidesToDeflectToCorner()) {
            return deflectToCorner(state, crosser, defender);
        }
        return loseCross(state, crosser);
    }

    private ActionOutcome deflectToCorner(MatchState state, Player crosser, Player defender) {
        Zone opponentBox = opponentBoxOf(state);
        MatchState next = MatchState.forSetPiece(state.possession(), opponentBox, SetPieceKind.CORNER);
        int duration = drawDuration(config.crossDuration());
        Participants participants = Participants.of(defender, crosser);
        EventDetail detail = EventDetail.none();
        return new ActionOutcome(EventType.CROSS_DEFLECTED_TO_CORNER, participants, next, duration, detail);
    }

    private ActionOutcome loseCross(MatchState state, Player crosser) {
        MatchSide defendingSide = state.possession().opposite();
        Zone defendingBox = layout.ownBoxZone(defendingSide);
        MatchState next = MatchState.openPlay(defendingSide, defendingBox);
        int duration = drawDuration(config.crossDuration());
        Participants participants = Participants.of(crosser);
        EventDetail detail = EventDetail.none();
        return new ActionOutcome(EventType.CROSS_FAILED, participants, next, duration, detail);
    }

    private ActionOutcome clearBall(MatchState state, Player defender) {
        MatchState next = stateAfterClearance(state);
        int duration = drawDuration(config.clearanceDuration());
        Participants participants = Participants.of(defender, state.crosser());
        EventDetail detail = EventDetail.none();
        return new ActionOutcome(EventType.CLEARANCE, participants, next, duration, detail);
    }

    private MatchState stateAfterClearance(MatchState state) {
        boolean clearedForCorner = random.occurs(config.clearedToCornerShare());
        if (clearedForCorner) {
            return MatchState.forSetPiece(state.possession(), state.zone(), SetPieceKind.CORNER);
        }
        MatchSide defendingSide = state.possession().opposite();
        Zone inFrontOfTheBox = layout.retreat(state.zone(), state.possession())
                .orElseThrow(() -> new IllegalStateException("no zone in front of " + state.zone()));
        return MatchState.openPlay(defendingSide, inFrontOfTheBox);
    }

    private Zone opponentBoxOf(MatchState state) {
        MatchSide defendingSide = state.possession().opposite();
        return layout.ownBoxZone(defendingSide);
    }

    private Player pickHeader(MatchState state, TeamOnPitch attackers) {
        return picker.pickOtherThan(attackers, HEADER_ZONE_ROLE, state.crosser());
    }

    private Player pickDefender(MatchState state, TeamOnPitch defenders) {
        MatchSide defendingSide = state.possession().opposite();
        ZoneRole zoneRole = layout.roleOf(state.zone(), defendingSide);
        return picker.pickFrom(defenders, zoneRole);
    }

    private List<Player> listCrosserCandidates(TeamOnPitch attackers) {
        Role headerSector = PlayerPicker.sectorOf(HEADER_ZONE_ROLE);
        List<Player> headers = attackers.playersIn(headerSector);
        List<Player> soleHeader = headers.size() == 1 ? headers : List.of();
        return attackers.players().stream()
                .filter(player -> attackers.roleOf(player) != Role.GOALKEEPER)
                .filter(player -> !soleHeader.contains(player))
                .toList();
    }

    private boolean isCrossFromSetPiece(MatchState state) {
        return state.phase() == PlayPhase.SET_PIECE;
    }

    private boolean crossReachesTheBox(MatchState state, TeamOnPitch attackers, Player crosser,
            TeamOnPitch defenders, Player defender) {
        Duel attributeDuel = crossDuel(crosser, defender);
        Duel duel = matchups.openPlayDuel(attributeDuel, state, attackers, crosser, defenders, defender);
        return attackerWins(duel);
    }

    private boolean decidesToDeflectToCorner() {
        return random.occurs(config.deflectedToCornerShare());
    }

    private boolean attackerWins(Duel duel) {
        double probability = duel.attackerWinProbability();
        return random.occurs(probability);
    }

    private int drawDuration(DurationRange range) {
        return random.nextIntInclusive(range.minSeconds(), range.maxSeconds());
    }
}
