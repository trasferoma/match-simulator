package org.tasktelemetry.matchsimulator.engine.resolution.shot;

import org.tasktelemetry.matchsimulator.engine.MatchRandom;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.config.CrossConfig;
import org.tasktelemetry.matchsimulator.engine.config.DurationRange;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.Participants;
import org.tasktelemetry.matchsimulator.engine.event.ShotKind;
import org.tasktelemetry.matchsimulator.engine.pitch.PitchLayout;
import org.tasktelemetry.matchsimulator.engine.pitch.Zone;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.resolution.ActionOutcome;
import org.tasktelemetry.matchsimulator.engine.resolution.Duel;
import org.tasktelemetry.matchsimulator.engine.resolution.PlayerPicker;
import org.tasktelemetry.matchsimulator.engine.state.MatchState;
import org.tasktelemetry.matchsimulator.engine.state.Score;
import org.tasktelemetry.matchsimulator.engine.state.SetPieceKind;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.Player;

public final class CrossResolver {

    private static final ZoneRole CROSSER_ZONE_ROLE = ZoneRole.MIDFIELD;

    private final CrossConfig config;
    private final PitchLayout layout;
    private final MatchRandom random;
    private final PlayerPicker picker;
    private final ShotResolver shotResolver;

    public CrossResolver(CrossConfig config, PitchLayout layout, MatchRandom random, PlayerPicker picker,
            ShotResolver shotResolver) {
        this.config = config;
        this.layout = layout;
        this.random = random;
        this.picker = picker;
        this.shotResolver = shotResolver;
    }

    public double crossProbability() {
        return config.crossProbability() * config.styleMultiplier();
    }

    public Duel crossDuel(Player crosser) {
        return new Duel(crosser.attributes().passing(), config.crossDifficulty());
    }

    public Duel aerialDuel(Player attacker, Player defender) {
        return Duel.between(attacker.attributes().aerial(), defender.attributes().aerial());
    }

    public Player pickCrosser(TeamOnPitch attackers) {
        return picker.pickFrom(attackers, CROSSER_ZONE_ROLE);
    }

    public ActionOutcome resolveCross(MatchState state, TeamOnPitch attackers) {
        Player crosser = pickCrosser(attackers);
        if (crossReachesTheBox(crosser)) {
            return deliverCross(state, crosser);
        }
        return loseCross(state, crosser);
    }

    public ActionOutcome resolveAerialDuel(MatchState state, Score score, TeamOnPitch attackers,
            TeamOnPitch defenders) {
        Player attacker = pickAttackerInTheBox(state, attackers);
        Player defender = pickDefenderInTheBox(state, defenders);
        Duel duel = aerialDuel(attacker, defender);
        if (attackerWins(duel)) {
            return shotResolver.resolveShot(state, score, attacker, ShotKind.HEADER, defenders);
        }
        return clearBall(state, defender);
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

    private Player pickAttackerInTheBox(MatchState state, TeamOnPitch attackers) {
        ZoneRole zoneRole = layout.roleOf(state.zone(), state.possession());
        return picker.pickFrom(attackers, zoneRole);
    }

    private Player pickDefenderInTheBox(MatchState state, TeamOnPitch defenders) {
        MatchSide defendingSide = state.possession().opposite();
        ZoneRole zoneRole = layout.roleOf(state.zone(), defendingSide);
        return picker.pickFrom(defenders, zoneRole);
    }

    private boolean crossReachesTheBox(Player crosser) {
        Duel duel = crossDuel(crosser);
        return attackerWins(duel);
    }

    private boolean attackerWins(Duel duel) {
        double probability = duel.attackerWinProbability();
        return random.occurs(probability);
    }

    private int drawDuration(DurationRange range) {
        return random.nextIntInclusive(range.minSeconds(), range.maxSeconds());
    }
}
