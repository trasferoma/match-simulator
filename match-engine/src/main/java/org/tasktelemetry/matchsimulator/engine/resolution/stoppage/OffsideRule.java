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
import org.tasktelemetry.matchsimulator.engine.resolution.OutOfPositionPenalty;
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
    private final OutOfPositionPenalty outOfPosition;

    public OffsideRule(OffsideConfig config, PitchLayout layout, MatchRandom random, PlayerPicker picker,
            OutOfPositionPenalty outOfPosition) {
        this.config = config;
        this.layout = layout;
        this.random = random;
        this.picker = picker;
        this.outOfPosition = outOfPosition;
    }

    public double offsideProbability(TeamOnPitch attackers, Player receiver, TeamOnPitch defenders,
            Player defender) {
        double receiverPositioning = effectivePositioningOf(attackers, receiver);
        double defenderPositioning = effectivePositioningOf(defenders, defender);
        double defenderPositioningLead = defenderPositioning - receiverPositioning;
        double positioningShift = config.positioningSlope() * defenderPositioningLead;
        return shiftOdds(config.baseProbability(), positioningShift);
    }

    public ActionOutcome callOffsideIfCaught(ActionOutcome outcome, TeamOnPitch attackers, TeamOnPitch defenders) {
        if (!isAdvanceTowardsTheOpponentBox(outcome)) {
            return outcome;
        }
        Player passer = passerOf(outcome);
        Player receiver = pickReceiver(outcome, attackers, passer);
        Player defender = pickDefender(outcome, defenders);
        if (!isCaughtOffside(attackers, receiver, defenders, defender)) {
            return outcome;
        }
        return offsideAgainst(receiver, passer, outcome);
    }

    private Player passerOf(ActionOutcome pass) {
        return pass.participants().primary()
                .orElseThrow(() -> new IllegalArgumentException("a completed pass must name the passer"));
    }

    private ActionOutcome offsideAgainst(Player receiver, Player passer, ActionOutcome pass) {
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

    private Player pickReceiver(ActionOutcome pass, TeamOnPitch attackers, Player passer) {
        ZoneRole arrivalRole = arrivalRoleOf(pass);
        return picker.pickOtherThan(attackers, arrivalRole, passer);
    }

    private Player pickDefender(ActionOutcome pass, TeamOnPitch defenders) {
        MatchState arrival = pass.next();
        MatchSide defendingSide = arrival.possession().opposite();
        ZoneRole defendingRole = layout.roleOf(arrival.zone(), defendingSide);
        return picker.pickFrom(defenders, defendingRole);
    }

    private ZoneRole arrivalRoleOf(ActionOutcome outcome) {
        MatchState arrival = outcome.next();
        return layout.roleOf(arrival.zone(), arrival.possession());
    }

    private double effectivePositioningOf(TeamOnPitch team, Player player) {
        double factor = outOfPosition.factorOf(team, player);
        return player.attributes().positioning() * factor;
    }

    private boolean isCaughtOffside(TeamOnPitch attackers, Player receiver, TeamOnPitch defenders, Player defender) {
        double probability = offsideProbability(attackers, receiver, defenders, defender);
        return random.occurs(probability);
    }

    private static double shiftOdds(double probability, double shift) {
        double odds = probability / (1.0 - probability);
        double shiftedOdds = odds * StrictMath.exp(shift);
        return shiftedOdds / (1.0 + shiftedOdds);
    }
}
