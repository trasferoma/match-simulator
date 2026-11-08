package org.tasktelemetry.matchsimulator.engine.resolution.shot;

import org.tasktelemetry.matchsimulator.engine.config.ShotConfig;

record MissShares(double blocked, double offTarget, double saved) {

    enum Miss {
        BLOCKED,
        OFF_TARGET,
        SAVED
    }

    static MissShares ofShot(ShotConfig config) {
        double blocked = config.blockedShare();
        double offTarget = config.offTargetShare();
        double saved = 1.0 - blocked - offTarget;
        return new MissShares(blocked, offTarget, saved);
    }

    static MissShares ofPenalty(ShotConfig config) {
        MissShares shot = ofShot(config);
        double unblocked = shot.offTarget + shot.saved;
        double offTarget = shot.offTarget / unblocked;
        double saved = shot.saved / unblocked;
        return new MissShares(0.0, offTarget, saved);
    }

    Miss missAt(double draw) {
        if (draw < blocked) {
            return Miss.BLOCKED;
        }
        if (draw < blocked + offTarget) {
            return Miss.OFF_TARGET;
        }
        return Miss.SAVED;
    }
}
