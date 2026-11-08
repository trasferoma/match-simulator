package org.tasktelemetry.matchsimulator.engine;

import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.config.FoulConfig;
import org.tasktelemetry.matchsimulator.engine.config.LineupConfig;
import org.tasktelemetry.matchsimulator.engine.config.SetPieceConfig;

public final class TestConfigs {

    private static final double NO_HOME_ADVANTAGE = 1.0;
    private static final double NO_FOULS = 0.0;

    private TestConfigs() {
    }

    public static EngineConfig withHomeAdvantage(EngineConfig base, double homeAdvantage) {
        return new EngineConfig(base.buildUpAndMidfieldDuration(), base.attackingThirdDuration(),
                base.dribbleProbability(), base.minimumStoppageMinutes(), base.shot(), base.setPiece(), base.cross(),
                base.counterAttack(), base.playStyles(), base.foul(), base.offside(), base.lineup(), homeAdvantage);
    }

    public static EngineConfig withoutHomeAdvantage(EngineConfig base) {
        return withHomeAdvantage(base, NO_HOME_ADVANTAGE);
    }

    public static EngineConfig withFoul(EngineConfig base, FoulConfig foul) {
        return new EngineConfig(base.buildUpAndMidfieldDuration(), base.attackingThirdDuration(),
                base.dribbleProbability(), base.minimumStoppageMinutes(), base.shot(), base.setPiece(), base.cross(),
                base.counterAttack(), base.playStyles(), foul, base.offside(), base.lineup(), base.homeAdvantage());
    }

    public static EngineConfig withLineup(EngineConfig base, LineupConfig lineup) {
        return new EngineConfig(base.buildUpAndMidfieldDuration(), base.attackingThirdDuration(),
                base.dribbleProbability(), base.minimumStoppageMinutes(), base.shot(), base.setPiece(), base.cross(),
                base.counterAttack(), base.playStyles(), base.foul(), base.offside(), lineup, base.homeAdvantage());
    }

    public static EngineConfig withSetPiece(EngineConfig base, SetPieceConfig setPiece) {
        return new EngineConfig(base.buildUpAndMidfieldDuration(), base.attackingThirdDuration(),
                base.dribbleProbability(), base.minimumStoppageMinutes(), base.shot(), setPiece, base.cross(),
                base.counterAttack(), base.playStyles(), base.foul(), base.offside(), base.lineup(), base.homeAdvantage());
    }

    public static EngineConfig withoutFouls(EngineConfig base) {
        return withFoul(base, withBaseFoulProbability(base.foul(), NO_FOULS));
    }

    public static FoulConfig withBaseFoulProbability(FoulConfig base, double probability) {
        return new FoulConfig(probability, base.neutralTackling(), base.tacklingSlope(), base.penaltyAreaFoulFactor(),
                base.lightShare(),
                base.hardShare(), base.yellowProbability(), base.redProbability(), base.foulDuration());
    }
}
