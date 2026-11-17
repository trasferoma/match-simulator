package org.tasktelemetry.matchsimulator.coach;

import java.util.ArrayList;
import java.util.List;

import org.tasktelemetry.matchsimulator.coach.TieBreakDraw;
import org.tasktelemetry.matchsimulator.coach.config.AssessmentBand;
import org.tasktelemetry.matchsimulator.coach.lineup.PreMatchDecision;
import org.tasktelemetry.matchsimulator.coach.squad.CoachAttributes;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

public final class ScenarioTeams {

    public static final TieBreakDraw ALWAYS_THE_FIRST = () -> 0.0;
    public static final CoachAttributes NEUTRAL_COACH = new CoachAttributes(50);

    private static final int RATING = 60;

    private ScenarioTeams() {
    }

    public static Team fourFourTwo(String name, PlayStyle style) {
        return formation(name, style, 4, 4, 2);
    }

    public static Team formation(String name, PlayStyle style, int defenders, int midfielders, int forwards) {
        List<Player> starters = new ArrayList<>();
        starters.add(player(name, "GK", Role.GOALKEEPER));
        starters.addAll(group(name, "D", Role.DEFENDER, defenders));
        starters.addAll(group(name, "M", Role.MIDFIELDER, midfielders));
        starters.addAll(group(name, "F", Role.FORWARD, forwards));
        List<Player> bench = new ArrayList<>();
        bench.add(player(name, "BGK", Role.GOALKEEPER));
        bench.addAll(group(name, "BD", Role.DEFENDER, 2));
        bench.addAll(group(name, "BM", Role.MIDFIELDER, 2));
        bench.addAll(group(name, "BF", Role.FORWARD, 2));
        return new Team(name, style, starters, bench);
    }

    public static PreMatchDecision preMatch(Team team, AssessmentBand band) {
        List<Player> rosterOrder = new ArrayList<>(team.starters());
        rosterOrder.addAll(team.bench());
        return new PreMatchDecision(team, 0.0, band, null, rosterOrder);
    }

    public static Team custom(String name, PlayStyle style, List<Player> starters, List<Player> bench) {
        return new Team(name, style, starters, bench);
    }

    public static Team lineup(String name, PlayStyle style, List<Player> defenders, List<Player> midfielders,
            List<Player> forwards, List<Player> bench) {
        List<Player> starters = new ArrayList<>();
        starters.add(rated(name + "-GK", Role.GOALKEEPER, RATING));
        starters.addAll(defenders);
        starters.addAll(midfielders);
        starters.addAll(forwards);
        return new Team(name, style, starters, bench);
    }

    public static List<Player> ratedGroup(String teamName, String prefix, Role role, int count, int rating) {
        List<Player> players = new ArrayList<>();
        for (int number = 1; number <= count; number++) {
            players.add(rated(teamName + "-" + prefix + number, role, rating));
        }
        return players;
    }

    public static Player rated(String name, Role role, int rating) {
        PlayerAttributes attributes = new PlayerAttributes(rating, rating, rating, rating, rating, rating, rating,
                rating, 50);
        return new Player(name, role, attributes);
    }

    public static Player starter(Team team, Role role, int number) {
        return team.starters().stream()
                .filter(player -> player.role() == role)
                .skip(number - 1L)
                .findFirst()
                .orElseThrow();
    }

    public static Player benchPlayer(Team team, Role role, int number) {
        return team.bench().stream()
                .filter(player -> player.role() == role)
                .skip(number - 1L)
                .findFirst()
                .orElseThrow();
    }

    private static List<Player> group(String teamName, String prefix, Role role, int count) {
        List<Player> players = new ArrayList<>();
        for (int number = 1; number <= count; number++) {
            players.add(player(teamName, prefix + number, role));
        }
        return players;
    }

    private static Player player(String teamName, String label, Role role) {
        PlayerAttributes attributes = new PlayerAttributes(RATING, RATING, RATING, RATING, RATING, RATING, RATING,
                RATING, 50);
        return new Player(teamName + "-" + label, role, attributes);
    }
}
