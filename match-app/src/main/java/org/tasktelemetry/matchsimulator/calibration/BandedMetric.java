package org.tasktelemetry.matchsimulator.calibration;

public enum BandedMetric {
    GOALS_PER_GAME("goals_per_game", "gol a partita"),
    HOME_GOALS_PER_GAME("home_goals_per_game", "gol in casa"),
    AWAY_GOALS_PER_GAME("away_goals_per_game", "gol in trasferta"),
    HOME_WIN_PCT("home_win_pct", "vittorie in casa (%)"),
    DRAW_PCT("draw_pct", "pareggi (%)"),
    AWAY_WIN_PCT("away_win_pct", "vittorie in trasferta (%)"),
    SHOTS_PER_GAME("shots_per_game", "tiri a partita"),
    HOME_SHOTS_PER_GAME("home_shots_per_game", "tiri in casa"),
    AWAY_SHOTS_PER_GAME("away_shots_per_game", "tiri in trasferta");

    private final String fileKey;
    private final String label;

    BandedMetric(String fileKey, String label) {
        this.fileKey = fileKey;
        this.label = label;
    }

    public String fileKey() {
        return fileKey;
    }

    public String label() {
        return label;
    }
}
