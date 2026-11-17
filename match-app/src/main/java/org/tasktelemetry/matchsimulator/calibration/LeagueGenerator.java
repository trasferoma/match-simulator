package org.tasktelemetry.matchsimulator.calibration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.tasktelemetry.matchsimulator.coach.squad.CoachAttributes;
import org.tasktelemetry.matchsimulator.coach.squad.CoachProfile;
import org.tasktelemetry.matchsimulator.coach.squad.Squad;
import org.tasktelemetry.matchsimulator.engine.MatchRandom;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;
import org.tasktelemetry.matchsimulator.engine.team.Role;

public final class LeagueGenerator {

    private static final List<Role> ROSTER_ROLES = List.of(
            Role.GOALKEEPER,
            Role.DEFENDER, Role.DEFENDER, Role.DEFENDER, Role.DEFENDER,
            Role.MIDFIELDER, Role.MIDFIELDER, Role.MIDFIELDER, Role.MIDFIELDER,
            Role.FORWARD, Role.FORWARD,
            Role.GOALKEEPER,
            Role.DEFENDER, Role.DEFENDER,
            Role.MIDFIELDER, Role.MIDFIELDER,
            Role.FORWARD, Role.FORWARD);

    private final BenchConfig config;
    private final RoleProfiles profiles;

    public LeagueGenerator(BenchConfig config, RoleProfiles profiles) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.profiles = Objects.requireNonNull(profiles, "role profiles must not be null");
        requireStaminaInScaleForEveryBenchStrength();
    }

    public List<Squad> generate(long seed) {
        MatchRandom random = new MatchRandom(seed);
        List<Squad> squads = new ArrayList<>();
        for (int index = 0; index < config.teamCount(); index++) {
            int strength = strengthOf(index);
            Squad squad = generateSquad(index, strength, random);
            squads.add(squad);
        }
        return squads;
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

    public String coachNameOf(int teamIndex) {
        return String.format(Locale.ROOT, "Coach %02d", teamIndex);
    }

    public Squad generateSquad(int teamIndex, int strength, MatchRandom random) {
        String name = nameOf(teamIndex);
        List<Player> players = generatePlayers(name, strength, random);
        CoachProfile coach = generateCoach(teamIndex);
        return new Squad(name, coach, players);
    }

    private CoachProfile generateCoach(int teamIndex) {
        String coachName = coachNameOf(teamIndex);
        CoachAttributes attributes = new CoachAttributes(config.coachTacticalReading());
        return new CoachProfile(coachName, attributes);
    }

    private List<Player> generatePlayers(String teamName, int strength, MatchRandom random) {
        List<Player> players = new ArrayList<>();
        for (int position = 0; position < ROSTER_ROLES.size(); position++) {
            Role role = ROSTER_ROLES.get(position);
            String playerName = teamName + " P" + (position + 1);
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
        int stamina = staminaOf(role, strength);
        return new PlayerAttributes(passing, dribbling, shooting, tackling, positioning, pace, aerial, goalkeeping,
                stamina);
    }

    private int staminaOf(Role role, int strength) {
        return strength + profiles.staminaOffsetOf(role);
    }

    private void requireStaminaInScaleForEveryBenchStrength() {
        for (Role role : Role.values()) {
            requireStaminaInScale(role, config.weakestStrength());
            requireStaminaInScale(role, config.strongestStrength());
        }
    }

    private void requireStaminaInScale(Role role, int strength) {
        int stamina = staminaOf(role, strength);
        boolean isInScale = stamina >= PlayerAttributes.MIN_VALUE && stamina <= PlayerAttributes.MAX_VALUE;
        if (!isInScale) {
            throw new IllegalArgumentException("stamina of " + role + " at strength " + strength + " must be between "
                    + PlayerAttributes.MIN_VALUE + " and " + PlayerAttributes.MAX_VALUE + ", was " + stamina);
        }
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
