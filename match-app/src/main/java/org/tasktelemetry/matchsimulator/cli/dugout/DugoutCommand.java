package org.tasktelemetry.matchsimulator.cli.dugout;

import org.tasktelemetry.matchsimulator.engine.event.Deployment;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;

sealed interface DugoutCommand {

    record Substitute(int leavingNumber, int enteringNumber) implements DugoutCommand {
    }

    record ChangeFormation(Deployment target) implements DugoutCommand {
    }

    record ChangeStyle(PlayStyle style) implements DugoutCommand {
    }

    record Undo() implements DugoutCommand {
    }

    record Help() implements DugoutCommand {
    }

    record Redraw() implements DugoutCommand {
    }

    record Resume() implements DugoutCommand {
    }
}
