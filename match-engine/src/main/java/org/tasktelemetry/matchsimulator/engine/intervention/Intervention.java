package org.tasktelemetry.matchsimulator.engine.intervention;

import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

public sealed interface Intervention {

    MatchSide side();

    ChangeReason reason();

    private static void requireVoluntary(ChangeReason reason) {
        if (reason.isForced()) {
            throw new IllegalArgumentException("an intervention cannot carry the forced reason " + reason);
        }
    }

    record StyleChange(MatchSide side, PlayStyle style, ChangeReason reason) implements Intervention {

        public StyleChange {
            Objects.requireNonNull(side, "side must not be null");
            Objects.requireNonNull(style, "style must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
            requireVoluntary(reason);
        }
    }

    record VoluntarySubstitution(MatchSide side, Player leaving, Player entering, ChangeReason reason)
            implements Intervention {

        public VoluntarySubstitution {
            Objects.requireNonNull(side, "side must not be null");
            Objects.requireNonNull(leaving, "leaving player must not be null");
            Objects.requireNonNull(entering, "entering player must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
            requireVoluntary(reason);
        }
    }

    record SectorMove(MatchSide side, Player player, Role sector, ChangeReason reason) implements Intervention {

        public SectorMove {
            Objects.requireNonNull(side, "side must not be null");
            Objects.requireNonNull(player, "player must not be null");
            Objects.requireNonNull(sector, "sector must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
            requireVoluntary(reason);
        }
    }
}
