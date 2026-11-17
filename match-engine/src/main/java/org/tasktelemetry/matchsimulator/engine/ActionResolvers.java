package org.tasktelemetry.matchsimulator.engine;

import org.tasktelemetry.matchsimulator.engine.resolution.CounterAttackResolver;
import org.tasktelemetry.matchsimulator.engine.resolution.PossessionActionResolver;
import org.tasktelemetry.matchsimulator.engine.resolution.fatigue.EnergyDrain;
import org.tasktelemetry.matchsimulator.engine.resolution.fatigue.HalfTimeRecovery;
import org.tasktelemetry.matchsimulator.engine.resolution.lineup.DismissalResolver;
import org.tasktelemetry.matchsimulator.engine.resolution.lineup.InjuryResolver;
import org.tasktelemetry.matchsimulator.engine.resolution.lineup.MinimumPlayersRule;
import org.tasktelemetry.matchsimulator.engine.resolution.lineup.SectorMoveRule;
import org.tasktelemetry.matchsimulator.engine.resolution.lineup.SubstitutionTimer;
import org.tasktelemetry.matchsimulator.engine.resolution.lineup.VoluntarySubstitutionRule;
import org.tasktelemetry.matchsimulator.engine.resolution.shot.CrossResolver;
import org.tasktelemetry.matchsimulator.engine.resolution.stoppage.FoulResolver;
import org.tasktelemetry.matchsimulator.engine.resolution.stoppage.OffsideRule;
import org.tasktelemetry.matchsimulator.engine.resolution.stoppage.SetPieceResolver;
import org.tasktelemetry.matchsimulator.engine.resolution.stoppage.StoppageTime;

record ActionResolvers(
        PossessionActionResolver possession,
        CounterAttackResolver counterAttack,
        SetPieceResolver setPiece,
        CrossResolver cross,
        FoulResolver foul,
        OffsideRule offside,
        DismissalResolver dismissal,
        MinimumPlayersRule minimumPlayers,
        InjuryResolver injury,
        SubstitutionTimer substitutionTimer,
        VoluntarySubstitutionRule voluntarySubstitution,
        SectorMoveRule sectorMove,
        StoppageTime stoppage,
        EnergyDrain energyDrain,
        HalfTimeRecovery halfTimeRecovery) {
}
