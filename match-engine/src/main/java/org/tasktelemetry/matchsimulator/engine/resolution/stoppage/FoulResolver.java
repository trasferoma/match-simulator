package org.tasktelemetry.matchsimulator.engine.resolution.stoppage;

import java.util.Optional;

import org.tasktelemetry.matchsimulator.engine.MatchRandom;
import org.tasktelemetry.matchsimulator.engine.config.DurationRange;
import org.tasktelemetry.matchsimulator.engine.config.FoulConfig;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.FoulSeverity;
import org.tasktelemetry.matchsimulator.engine.event.Participants;
import org.tasktelemetry.matchsimulator.engine.pitch.PitchLayout;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.resolution.ActionOutcome;
import org.tasktelemetry.matchsimulator.engine.state.MatchState;
import org.tasktelemetry.matchsimulator.engine.state.SetPieceKind;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.Player;

public final class FoulResolver {

    private final FoulConfig config;
    private final PitchLayout layout;
    private final MatchRandom random;

    public FoulResolver(FoulConfig config, PitchLayout layout, MatchRandom random) {
        this.config = config;
        this.layout = layout;
        this.random = random;
    }

    public double foulProbability(Player defender) {
        return foulProbabilityFrom(config.baseFoulProbability(), defender);
    }

    public double penaltyAreaFoulProbability(Player defender) {
        double base = config.baseFoulProbability() * config.penaltyAreaFoulFactor();
        return foulProbabilityFrom(base, defender);
    }

    public Optional<ActionOutcome> resolveFoul(MatchState state, Player fouler, Player fouled) {
        double probability = foulProbabilityIn(state, fouler);
        if (!random.occurs(probability)) {
            return Optional.empty();
        }
        ActionOutcome foul = concedeFoul(state, fouler, fouled);
        return Optional.of(foul);
    }

    public Optional<ActionOutcome> sanctionFor(ActionOutcome foul, TeamOnPitch foulingTeam) {
        EventDetail.Foul detail = foulDetailOf(foul);
        Player fouler = foul.participants().primary()
                .orElseThrow(() -> new IllegalArgumentException("a foul must name the player who committed it"));
        FoulSeverity severity = detail.severity();
        double red = config.redProbability().get(severity);
        double yellow = config.yellowProbability().get(severity);
        double draw = random.nextDouble();
        if (draw < red) {
            ActionOutcome redCard = showCard(EventType.RED_CARD, fouler, foul);
            return Optional.of(redCard);
        }
        if (draw < red + yellow) {
            EventType yellowType = yellowTypeFor(foulingTeam, fouler);
            ActionOutcome yellowCard = showCard(yellowType, fouler, foul);
            return Optional.of(yellowCard);
        }
        return Optional.empty();
    }

    private double foulProbabilityIn(MatchState state, Player defender) {
        if (isInsideTheBoxOfTheFouler(state)) {
            return penaltyAreaFoulProbability(defender);
        }
        return foulProbability(defender);
    }

    private double foulProbabilityFrom(double baseProbability, Player defender) {
        int tacklingBelowNeutral = config.neutralTackling() - defender.attributes().tackling();
        double tacklingShift = config.tacklingSlope() * tacklingBelowNeutral;
        return shiftOdds(baseProbability, tacklingShift);
    }

    private ActionOutcome concedeFoul(MatchState state, Player fouler, Player fouled) {
        FoulSeverity severity = drawSeverity();
        boolean penalty = isInsideTheBoxOfTheFouler(state);
        SetPieceKind restart = penalty ? SetPieceKind.PENALTY : SetPieceKind.FREE_KICK;
        MatchState next = MatchState.forSetPiece(state.possession(), state.zone(), restart);
        int duration = drawDuration(config.foulDuration());
        Participants participants = Participants.of(fouler, fouled);
        EventDetail detail = EventDetail.foul(severity, penalty);
        return new ActionOutcome(EventType.FOUL, participants, next, duration, detail);
    }

    private ActionOutcome showCard(EventType card, Player carded, ActionOutcome foul) {
        Participants participants = Participants.of(carded);
        EventDetail detail = EventDetail.none();
        return new ActionOutcome(card, participants, foul.next(), 0, detail);
    }

    private EventType yellowTypeFor(TeamOnPitch foulingTeam, Player fouler) {
        return foulingTeam.isCautioned(fouler) ? EventType.SECOND_YELLOW_CARD : EventType.YELLOW_CARD;
    }

    private FoulSeverity drawSeverity() {
        double draw = random.nextDouble();
        if (draw < config.lightShare()) {
            return FoulSeverity.LIGHT;
        }
        if (draw < config.lightShare() + config.hardShare()) {
            return FoulSeverity.HARD;
        }
        return FoulSeverity.RECKLESS;
    }

    private boolean isInsideTheBoxOfTheFouler(MatchState state) {
        ZoneRole zoneRole = layout.roleOf(state.zone(), state.possession());
        return zoneRole == ZoneRole.OPPONENT_BOX;
    }

    private EventDetail.Foul foulDetailOf(ActionOutcome foul) {
        if (foul.detail() instanceof EventDetail.Foul detail) {
            return detail;
        }
        throw new IllegalArgumentException("a sanction needs a foul outcome, but the detail is " + foul.detail());
    }

    private int drawDuration(DurationRange range) {
        return random.nextIntInclusive(range.minSeconds(), range.maxSeconds());
    }

    private static double shiftOdds(double probability, double shift) {
        double odds = probability / (1.0 - probability);
        double shiftedOdds = odds * StrictMath.exp(shift);
        return shiftedOdds / (1.0 + shiftedOdds);
    }
}
