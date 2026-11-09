package org.tasktelemetry.matchsimulator.teamfile;

import java.util.List;

record TeamFileDocument(String name, String style, List<PlayerEntry> starters, List<PlayerEntry> bench) {

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
