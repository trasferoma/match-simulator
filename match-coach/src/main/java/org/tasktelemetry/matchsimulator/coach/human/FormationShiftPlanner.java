package org.tasktelemetry.matchsimulator.coach.human;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.IntStream;

import org.tasktelemetry.matchsimulator.engine.event.Deployment;
import org.tasktelemetry.matchsimulator.engine.event.FatigueBand;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention.SectorShift;
import org.tasktelemetry.matchsimulator.engine.team.Role;

public final class FormationShiftPlanner {

    private static final List<Role> MOVEMENT_SECTORS = List.of(Role.DEFENDER, Role.MIDFIELDER, Role.FORWARD);

    private record SectorTransfer(Role from, Role to) {
    }

    public List<SectorShift> shiftsFor(ProjectedLineup lineup, Deployment target) {
        Deployment current = lineup.deployment();
        List<SectorTransfer> transfers = pairSectors(current, target);
        return choosePlayersFor(lineup, transfers);
    }

    private List<SectorTransfer> pairSectors(Deployment current, Deployment target) {
        List<Role> leaving = sectorsWithSurplus(current, target);
        List<Role> joining = sectorsWithSurplus(target, current);
        return IntStream.range(0, leaving.size())
                .mapToObj(index -> new SectorTransfer(leaving.get(index), joining.get(index)))
                .toList();
    }

    private List<Role> sectorsWithSurplus(Deployment larger, Deployment smaller) {
        List<Role> sectors = new ArrayList<>();
        for (Role sector : MOVEMENT_SECTORS) {
            int surplus = playersIn(larger, sector) - playersIn(smaller, sector);
            int menInSurplus = Math.max(surplus, 0);
            List<Role> repeated = Collections.nCopies(menInSurplus, sector);
            sectors.addAll(repeated);
        }
        return sectors;
    }

    private List<SectorShift> choosePlayersFor(ProjectedLineup lineup, List<SectorTransfer> transfers) {
        Map<Integer, SheetPlayer> moverOfTransfer = new TreeMap<>();
        assignPlayersGoingBackToBaseRole(lineup, transfers, moverOfTransfer);
        assignRemainingTransfers(lineup, transfers, moverOfTransfer);
        return IntStream.range(0, transfers.size())
                .mapToObj(index -> shiftOf(moverOfTransfer.get(index), transfers.get(index)))
                .toList();
    }

    private void assignPlayersGoingBackToBaseRole(ProjectedLineup lineup, List<SectorTransfer> transfers,
            Map<Integer, SheetPlayer> moverOfTransfer) {
        Comparator<SheetPlayer> orderOfChoice = orderOfChoice();
        for (int index = 0; index < transfers.size(); index++) {
            SectorTransfer transfer = transfers.get(index);
            Optional<SheetPlayer> returning = lineup.playersIn(transfer.from()).stream()
                    .filter(candidate -> !moverOfTransfer.containsValue(candidate))
                    .filter(candidate -> isGoingBackToBaseRole(candidate, transfer.to()))
                    .min(orderOfChoice);
            if (returning.isPresent()) {
                moverOfTransfer.put(index, returning.get());
            }
        }
    }

    private void assignRemainingTransfers(ProjectedLineup lineup, List<SectorTransfer> transfers,
            Map<Integer, SheetPlayer> moverOfTransfer) {
        Comparator<SheetPlayer> orderOfChoice = orderOfChoice();
        for (int index = 0; index < transfers.size(); index++) {
            if (moverOfTransfer.containsKey(index)) {
                continue;
            }
            SectorTransfer transfer = transfers.get(index);
            SheetPlayer mover = lineup.playersIn(transfer.from()).stream()
                    .filter(candidate -> !moverOfTransfer.containsValue(candidate))
                    .min(orderOfChoice)
                    .orElseThrow(() -> new IllegalStateException("nobody left in " + transfer.from() + " to move"));
            moverOfTransfer.put(index, mover);
        }
    }

    private SectorShift shiftOf(SheetPlayer mover, SectorTransfer transfer) {
        return new SectorShift(mover.player(), transfer.to());
    }

    private Comparator<SheetPlayer> orderOfChoice() {
        Comparator<SheetPlayer> highestMovementAverageFirst = Comparator
                .comparingDouble(SheetPlayer::movementAverage)
                .reversed();
        return leastTiredFirst()
                .thenComparing(highestMovementAverageFirst)
                .thenComparingInt(SheetPlayer::number);
    }

    private Comparator<SheetPlayer> leastTiredFirst() {
        List<FatigueBand> freshestToMostTired = List.of(FatigueBand.FRESH, FatigueBand.TIRED, FatigueBand.EXHAUSTED);
        return Comparator.comparingInt(candidate -> freshestToMostTired.indexOf(candidate.fatigue()));
    }

    private boolean isGoingBackToBaseRole(SheetPlayer candidate, Role destination) {
        return candidate.isOutOfPosition() && candidate.player().role() == destination;
    }

    private int playersIn(Deployment deployment, Role sector) {
        return switch (sector) {
            case DEFENDER -> deployment.defenders();
            case MIDFIELDER -> deployment.midfielders();
            case FORWARD -> deployment.forwards();
            case GOALKEEPER -> throw new IllegalArgumentException("a deployment does not count the goalkeeper");
        };
    }
}
