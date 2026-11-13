package org.tasktelemetry.matchsimulator.narration;

import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.team.Player;

public final class PhraseVariantPicker {

    private static final String FIELD_SEPARATOR = "|";
    private static final String NO_PLAYER = "";

    public int variantIndex(MatchEvent event, int variantCount) {
        int fingerprint = fingerprintOf(event);
        return Math.floorMod(fingerprint, variantCount);
    }

    public int variantIndexAfterRepetitions(MatchEvent firstOfRun, int repetitions, int variantCount) {
        int firstIndex = variantIndex(firstOfRun, variantCount);
        return Math.floorMod(firstIndex + repetitions, variantCount);
    }

    public int fingerprintOf(MatchEvent event) {
        int minute = event.time().minute();
        String typeName = event.type().name();
        String playerName = event.participants().primary().map(Player::name).orElse(NO_PLAYER);
        String text = minute + FIELD_SEPARATOR + typeName + FIELD_SEPARATOR + playerName;
        return text.hashCode();
    }
}
