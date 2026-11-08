package org.tasktelemetry.matchsimulator.engine.resolution.stoppage;

import org.tasktelemetry.matchsimulator.engine.MatchRandom;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.config.OffsideConfig;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.Participants;
import org.tasktelemetry.matchsimulator.engine.pitch.PitchLayout;
import org.tasktelemetry.matchsimulator.engine.pitch.Zone;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.resolution.ActionOutcome;
import org.tasktelemetry.matchsimulator.engine.resolution.PlayerPicker;
import org.tasktelemetry.matchsimulator.engine.state.MatchState;
import org.tasktelemetry.matchsimulator.engine.state.SetPieceKind;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.Player;

public final class OffsideRule {

    private final OffsideConfig config;
    private final PitchLayout layout;
    private final MatchRandom random;
    private final PlayerPicker picker;

    public OffsideRule(OffsideConfig config, PitchLayout layout, MatchRandom random, PlayerPicker picker) {
        this.config = config;
        this.layout = layout;
        this.random = random;
        this.picker = picker;
    }

    public double offsideProbability(Player receiver) {
        int positioningBelowNeutral = config.neutralPositioning() - receiver.attributes().positioning();
        double positioningShift = config.positioningSlope() * positioningBelowNeutral;
        return shiftOdds(config.baseProbability(), positioningShift);
    }

    public ActionOutcome callOffsideIfCaught(ActionOutcome outcome, TeamOnPitch attackers) {
        if (!isAdvanceTowardsTheOpponentBox(outcome)) {
            return outcome;
        }
        Player receiver = pickReceiver(outcome, attackers);
        if (!isCaughtOffside(receiver)) {
            return outcome;
        }
        return offsideAgainst(receiver, outcome);
    }

    private ActionOutcome offsideAgainst(Player receiver, ActionOutcome pass) {
        Player passer = pass.participants().primary()
                .orElseThrow(() -> new IllegalArgumentException("a completed pass must name the passer"));
        MatchSide defendingSide = pass.next().possession().opposite();
        Zone defenceZone = layout.defenceZone(defendingSide);
        MatchState next = MatchState.forSetPiece(defendingSide, defenceZone, SetPieceKind.OFFSIDE_FREE_KICK);
        Participants participants = Participants.of(receiver, passer);
        EventDetail detail = EventDetail.none();
        return new ActionOutcome(EventType.OFFSIDE, participants, next, pass.durationSeconds(), detail);
    }

    private boolean isAdvanceTowardsTheOpponentBox(ActionOutcome outcome) {
        if (outcome.type() != EventType.PASS_COMPLETED) {
            return false;
        }
        ZoneRole arrivalRole = arrivalRoleOf(outcome);
        return arrivalRole == ZoneRole.ATTACKING_THIRD || arrivalRole == ZoneRole.OPPONENT_BOX;
    }

    private Player pickReceiver(ActionOutcome pass, TeamOnPitch attackers) {
        ZoneRole arrivalRole = arrivalRoleOf(pass);
        return picker.pickFrom(attackers, arrivalRole);
    }

    private ZoneRole arrivalRoleOf(ActionOutcome outcome) {
        MatchState arrival = outcome.next();
        return layout.roleOf(arrival.zone(), arrival.possession());
    }

    private boolean isCaughtOffside(Player receiver) {
        double probability = offsideProbability(receiver);
        return random.occurs(probability);
    }

    private static double shiftOdds(double probability, double shift) {
        double odds = probability / (1.0 - probability);
        double shiftedOdds = odds * StrictMath.exp(shift);
        return shiftedOdds / (1.0 + shiftedOdds);
    }
}
