package org.tasktelemetry.matchsimulator.teamfile;

import java.util.List;

record TeamFileDocument(String name, CoachEntry coach, List<PlayerEntry> players) {

    record CoachEntry(String name, CoachAttributesEntry attributes) {
    }

    record CoachAttributesEntry(Integer tacticalReading) {
    }

    record PlayerEntry(String name, String role, AttributesEntry attributes) {
    }

    record AttributesEntry(
            Integer passing,
            Integer dribbling,
            Integer shooting,
            Integer tackling,
            Integer positioning,
            Integer pace,
            Integer aerial,
            Integer goalkeeping) {
    }
}
