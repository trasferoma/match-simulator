package org.tasktelemetry.matchsimulator.calibration.tactics;

import java.util.Objects;

import org.tasktelemetry.matchsimulator.coach.squad.Squad;
import org.tasktelemetry.matchsimulator.engine.MatchSide;

record TacticSample(Squad tested, Squad opponent, MatchSide testedSide, long matchSeed) {

    TacticSample {
        Objects.requireNonNull(tested, "tested squad must not be null");
        Objects.requireNonNull(opponent, "opponent squad must not be null");
        Objects.requireNonNull(testedSide, "tested side must not be null");
    }
}
