package org.tasktelemetry.matchsimulator.engine.resolution.shot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.config.ShotConfig;

class MissSharesTest {

    private static final double EXACT = 1e-9;

    private final ShotConfig config = ShotConfig.defaults();

    @Test
    void aShotSplitsTheMissesAsConfiguredAndTheRestIsSaved() {
        MissShares shares = MissShares.ofShot(config);

        assertThat(shares.blocked()).isEqualTo(config.blockedShare());
        assertThat(shares.offTarget()).isEqualTo(config.offTargetShare());
        assertThat(shares.saved()).isCloseTo(1.0 - config.blockedShare() - config.offTargetShare(), within(EXACT));
        assertThat(shares.blocked() + shares.offTarget() + shares.saved()).isCloseTo(1.0, within(EXACT));
    }

    @Test
    void aPenaltyIsNeverBlockedAndKeepsTheProportionBetweenOffTargetAndSaved() {
        MissShares shot = MissShares.ofShot(config);
        MissShares penalty = MissShares.ofPenalty(config);

        assertThat(penalty.blocked()).isZero();
        assertThat(penalty.offTarget() + penalty.saved()).isCloseTo(1.0, within(EXACT));
        assertThat(penalty.offTarget() / penalty.saved()).isCloseTo(shot.offTarget() / shot.saved(), within(EXACT));
    }

    @Test
    void theDrawFallsInTheBlockedThenOffTargetThenSavedBand() {
        MissShares shares = MissShares.ofShot(config);
        double blockedEnd = config.blockedShare();
        double offTargetEnd = config.blockedShare() + config.offTargetShare();

        assertThat(shares.missAt(0.0)).isEqualTo(MissShares.Miss.BLOCKED);
        assertThat(shares.missAt(blockedEnd - EXACT)).isEqualTo(MissShares.Miss.BLOCKED);
        assertThat(shares.missAt(blockedEnd)).isEqualTo(MissShares.Miss.OFF_TARGET);
        assertThat(shares.missAt(offTargetEnd - EXACT)).isEqualTo(MissShares.Miss.OFF_TARGET);
        assertThat(shares.missAt(offTargetEnd)).isEqualTo(MissShares.Miss.SAVED);
        assertThat(shares.missAt(Math.nextDown(1.0))).isEqualTo(MissShares.Miss.SAVED);
    }

    @Test
    void aPenaltyDrawNeverFallsInTheBlockedBand() {
        MissShares penalty = MissShares.ofPenalty(config);

        assertThat(penalty.missAt(0.0)).isEqualTo(MissShares.Miss.OFF_TARGET);
        assertThat(penalty.missAt(Math.nextDown(1.0))).isEqualTo(MissShares.Miss.SAVED);
    }
}
