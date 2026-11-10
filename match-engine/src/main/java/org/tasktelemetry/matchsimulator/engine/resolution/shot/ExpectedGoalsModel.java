package org.tasktelemetry.matchsimulator.engine.resolution.shot;

import org.tasktelemetry.matchsimulator.engine.config.ExpectedGoalsConfig;
import org.tasktelemetry.matchsimulator.engine.config.ShotConfig;
import org.tasktelemetry.matchsimulator.engine.event.ShotKind;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.state.PlayPhase;
import org.tasktelemetry.matchsimulator.engine.team.Player;

public final class ExpectedGoalsModel {

    private static final double NEUTRAL_MULTIPLIER = 1.0;

    private final ShotConfig config;

    public ExpectedGoalsModel(ShotConfig config) {
        this.config = config;
    }

    public double expectedGoals(ZoneRole zoneRole, PlayPhase phase, ShotKind kind, Player shooter,
            double shootingMultiplier) {
        ExpectedGoalsConfig expectedGoals = config.expectedGoals();
        double base = expectedGoals.baseByZoneRole().get(zoneRole);
        double phaseMultiplier = expectedGoals.multiplierByPhase().get(phase);
        double kindMultiplier = expectedGoals.multiplierByKind().get(kind);
        double situationalChance = base * phaseMultiplier * kindMultiplier;
        double boundedChance = boundedToConfiguredLimits(situationalChance);
        double shootingShift = shootingShift(shooter, shootingMultiplier, expectedGoals.neutralShooting(),
                expectedGoals.shootingSlope());
        return shiftOdds(boundedChance, shootingShift);
    }

    public double penaltyExpectedGoals(Player taker) {
        ExpectedGoalsConfig expectedGoals = config.expectedGoals();
        double shootingShift = shootingShift(taker, NEUTRAL_MULTIPLIER, expectedGoals.penaltyNeutralShooting(),
                expectedGoals.penaltyShootingSlope());
        return shiftOdds(expectedGoals.penaltyBase(), shootingShift);
    }

    public double goalProbability(double expectedGoals, Player goalkeeper, double goalkeepingMultiplier) {
        double goalkeeping = goalkeeper.attributes().goalkeeping() * goalkeepingMultiplier;
        double goalkeepingBelowNeutral = config.neutralGoalkeeping() - goalkeeping;
        double goalkeeperShift = config.goalkeepingSlope() * goalkeepingBelowNeutral;
        return shiftOdds(expectedGoals, goalkeeperShift);
    }

    private double boundedToConfiguredLimits(double chance) {
        ExpectedGoalsConfig expectedGoals = config.expectedGoals();
        double atLeastMinimum = Math.max(chance, expectedGoals.minimumExpectedGoals());
        return Math.min(atLeastMinimum, expectedGoals.maximumExpectedGoals());
    }

    private static double shootingShift(Player shooter, double multiplier, int neutralShooting, double slope) {
        double shooting = shooter.attributes().shooting() * multiplier;
        double shootingAboveNeutral = shooting - neutralShooting;
        return slope * shootingAboveNeutral;
    }

    private static double shiftOdds(double probability, double shift) {
        double odds = probability / (1.0 - probability);
        double shiftedOdds = odds * StrictMath.exp(shift);
        return shiftedOdds / (1.0 + shiftedOdds);
    }
}
