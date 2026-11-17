package org.tasktelemetry.matchsimulator.coach.reading;

import java.util.List;

import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;

public final class DuelReader {

    public SectorDuels read(MatchSide side, List<MatchEvent> events) {
        Tally defence = new Tally();
        Tally midfield = new Tally();
        Tally attack = new Tally();
        for (MatchEvent event : events) {
            if (!isDuel(event)) {
                continue;
            }
            boolean ballCarrierWon = isWonByTheBallCarrier(event);
            boolean sideHadTheBall = event.side() == side;
            ZoneRole zone = event.zoneRole();
            if (zone == ZoneRole.MIDFIELD) {
                boolean sideWon = sideHadTheBall == ballCarrierWon;
                midfield.record(!sideWon);
            } else if (zone.isInAttackingArea() && sideHadTheBall) {
                attack.record(!ballCarrierWon);
            } else if (zone.isInAttackingArea()) {
                defence.record(ballCarrierWon);
            }
        }
        DuelCount defenceDuels = defence.toCount();
        DuelCount midfieldDuels = midfield.toCount();
        DuelCount attackDuels = attack.toCount();
        return new SectorDuels(defenceDuels, midfieldDuels, attackDuels);
    }

    private boolean isDuel(MatchEvent event) {
        EventType type = event.type();
        return isWonByTheBallCarrier(event) || type.isBallRecovery();
    }

    private boolean isWonByTheBallCarrier(MatchEvent event) {
        EventType type = event.type();
        return type == EventType.PASS_COMPLETED || type == EventType.DRIBBLE_COMPLETED;
    }

    private static final class Tally {

        private int lost;
        private int total;

        void record(boolean wasLost) {
            total++;
            if (wasLost) {
                lost++;
            }
        }

        DuelCount toCount() {
            return new DuelCount(lost, total);
        }
    }
}
