package org.tasktelemetry.matchsimulator.engine;

import org.tasktelemetry.matchsimulator.engine.resolution.CounterAttackResolver;
import org.tasktelemetry.matchsimulator.engine.resolution.PossessionActionResolver;
import org.tasktelemetry.matchsimulator.engine.resolution.lineup.DismissalResolver;
import org.tasktelemetry.matchsimulator.engine.resolution.lineup.MinimumPlayersRule;
import org.tasktelemetry.matchsimulator.engine.resolution.shot.CrossResolver;
import org.tasktelemetry.matchsimulator.engine.resolution.stoppage.FoulResolver;
import org.tasktelemetry.matchsimulator.engine.resolution.stoppage.OffsideRule;
import org.tasktelemetry.matchsimulator.engine.resolution.stoppage.SetPieceResolver;

record ActionResolvers(
        PossessionActionResolver possession,
        CounterAttackResolver counterAttack,
        SetPieceResolver setPiece,
        CrossResolver cross,
        FoulResolver foul,
        OffsideRule offside,
        DismissalResolver dismissal,
        MinimumPlayersRule minimumPlayers) {
}
