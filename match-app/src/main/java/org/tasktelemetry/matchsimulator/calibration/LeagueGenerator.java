package org.tasktelemetry.matchsimulator.calibration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.MatchRandom;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

public final class LeagueGenerator {

    private static final List<Role> STARTING_ROLES = List.of(
            Role.GOALKEEPER,
            Role.DEFENDER, Role.DEFENDER, Role.DEFENDER, Role.DEFENDER,
            Role.MIDFIELDER, Role.MIDFIELDER, Role.MIDFIELDER, Role.MIDFIELDER,
            Role.FORWARD, Role.FORWARD);
    private static final List<Role> BENCH_ROLES = List.of(
            Role.GOALKEEPER,
            Role.DEFENDER, Role.DEFENDER,
            Role.MIDFIELDER, Role.MIDFIELDER,
            Role.FORWARD, Role.FORWARD);

    private final BenchConfig config;
    private final RoleProfiles profiles;

    public LeagueGenerator(BenchConfig config, RoleProfiles profiles) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.profiles = Objects.requireNonNull(profiles, "role profiles must not be null");
    }

    public List<Team> generate(long seed) {
        MatchRandom random = new MatchRandom(seed);
        List<Team> teams = new ArrayList<>();
        for (int index = 0; index < config.teamCount(); index++) {
            Team team = generateTeam(index, random);
            teams.add(team);
        }
        return teams;
    }

    public int strengthOf(int teamIndex) {
        int span = config.strongestStrength() - config.weakestStrength();
        int steps = config.teamCount() - 1;
        int scaledNumerator = 2 * teamIndex * span + steps;
        int increment = scaledNumerator / (2 * steps);
        return config.weakestStrength() + increment;
    }

    public String nameOf(int teamIndex) {
        return String.format(Locale.ROOT, "Team %02d", teamIndex);
    }

    private Team generateTeam(int teamIndex, MatchRandom random) {
        String name = nameOf(teamIndex);
        int strength = strengthOf(teamIndex);
        PlayStyle style = rotatingStyle(teamIndex);
        List<Player> starters = generatePlayers(name, "S", STARTING_ROLES, strength, random);
        List<Player> bench = generatePlayers(name, "B", BENCH_ROLES, strength, random);
        return new Team(name, style, starters, bench);
    }

    private PlayStyle rotatingStyle(int teamIndex) {
        PlayStyle[] styles = PlayStyle.values();
        return styles[teamIndex % styles.length];
    }

    private List<Player> generatePlayers(String teamName, String squadPart, List<Role> roles, int strength,
            MatchRandom random) {
        List<Player> players = new ArrayList<>();
        for (int position = 0; position < roles.size(); position++) {
            Role role = roles.get(position);
            String playerName = teamName + " " + squadPart + (position + 1);
            PlayerAttributes attributes = generateAttributes(role, strength, random);
            Player player = new Player(playerName, role, attributes);
            players.add(player);
        }
        return players;
    }

    private PlayerAttributes generateAttributes(Role role, int strength, MatchRandom random) {
        AttributeOffsets offsets = profiles.offsetsOf(role);
        int passing = rate(strength, offsets.passing(), random);
        int dribbling = rate(strength, offsets.dribbling(), random);
        int shooting = rate(strength, offsets.shooting(), random);
        int tackling = rate(strength, offsets.tackling(), random);
        int positioning = rate(strength, offsets.positioning(), random);
        int pace = rate(strength, offsets.pace(), random);
        int aerial = rate(strength, offsets.aerial(), random);
        int goalkeeping = rate(strength, offsets.goalkeeping(), random);
        return new PlayerAttributes(passing, dribbling, shooting, tackling, positioning, pace, aerial, goalkeeping);
    }

    private int rate(int strength, int offset, MatchRandom random) {
        int variation = drawVariation(random);
        return strength + offset + variation;
    }

    private int drawVariation(MatchRandom random) {
        int bound = config.individualVariation();
        return random.nextIntInclusive(-bound, bound);
    }
}
