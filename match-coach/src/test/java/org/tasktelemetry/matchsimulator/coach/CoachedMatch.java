package org.tasktelemetry.matchsimulator.coach;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.tasktelemetry.matchsimulator.coach.config.CoachConfig;
import org.tasktelemetry.matchsimulator.coach.decision.AutomaticCoach;
import org.tasktelemetry.matchsimulator.coach.lineup.LineupChoice;
import org.tasktelemetry.matchsimulator.coach.lineup.PreMatchDecision;
import org.tasktelemetry.matchsimulator.coach.squad.CoachAttributes;
import org.tasktelemetry.matchsimulator.coach.squad.CoachProfile;
import org.tasktelemetry.matchsimulator.coach.squad.Squad;
import org.tasktelemetry.matchsimulator.engine.Match;
import org.tasktelemetry.matchsimulator.engine.MatchEngine;
import org.tasktelemetry.matchsimulator.engine.MatchRandom;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.intervention.InterventionResult;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

public final class CoachedMatch {

    public record Outcome(List<MatchEvent> events, int pendingRequests) {
    }

    public record Lineups(PreMatchDecision home, PreMatchDecision away) {
    }

    public static final int HOME_RATING = 66;
    public static final int AWAY_RATING = 58;

    private static final int COACH_READING = 50;
    private static final long TIE_BREAK_SEED_STEP = 1_000_003L;

    private final CoachConfig config = CoachConfig.defaults();
    private final MatchEngine engine = new MatchEngine(EngineConfig.defaults());
    private final Squad homeSquad = squad("Home", HOME_RATING);
    private final Squad awaySquad = squad("Away", AWAY_RATING);

    public Lineups chooseLineups() {
        LineupChoice choice = new LineupChoice(config);
        PreMatchDecision home = choice.decide(homeSquad, awaySquad, MatchSide.HOME);
        PreMatchDecision away = choice.decide(awaySquad, homeSquad, MatchSide.AWAY);
        return new Lineups(home, away);
    }

    public Outcome playWithAutomaticCoaches(long seed, boolean askTheAwayCoachFirst) {
        Lineups lineups = chooseLineups();
        MatchCoaches coaches = automaticCoaches();
        return play(lineups, seed, coaches, askTheAwayCoachFirst);
    }

    public MatchCoaches automaticCoaches() {
        Coach homeCoach = new AutomaticCoach(config, homeSquad.coach().attributes());
        Coach awayCoach = new AutomaticCoach(config, awaySquad.coach().attributes());
        return new MatchCoaches(homeCoach, awayCoach);
    }

    public Outcome play(Lineups lineups, long seed, MatchCoaches coaches, boolean askTheAwayCoachFirst) {
        TieBreakDraw homeDraw = drawOf(seed, 1L);
        TieBreakDraw awayDraw = drawOf(seed, 2L);
        coaches.open(lineups.home(), lineups.away(), homeDraw, awayDraw);
        try {
            return playOpen(lineups.home().team(), lineups.away().team(), seed, coaches, askTheAwayCoachFirst);
        } finally {
            coaches.close();
        }
    }

    private TieBreakDraw drawOf(long seed, long sideNumber) {
        MatchRandom random = new MatchRandom(seed * TIE_BREAK_SEED_STEP + sideNumber);
        return random::nextDouble;
    }

    private Outcome playOpen(Team home, Team away, long seed, MatchCoaches coaches,
            boolean askTheAwayCoachFirst) {
        Match match = engine.start(home, away, seed);
        List<MatchEvent> delivered = new ArrayList<>();
        int pendingRequests = 0;
        while (!match.isFinished()) {
            match.step();
            List<MatchEvent> allEvents = match.events();
            List<MatchEvent> newEvents = List.copyOf(allEvents.subList(delivered.size(), allEvents.size()));
            delivered.addAll(newEvents);
            if (newEvents.isEmpty() || match.isFinished()) {
                continue;
            }
            List<Intervention> requests = askCoaches(coaches, newEvents, askTheAwayCoachFirst);
            pendingRequests += applyInOrder(match, requests);
        }
        return new Outcome(match.events(), pendingRequests);
    }

    private List<Intervention> askCoaches(MatchCoaches coaches, List<MatchEvent> newEvents,
            boolean askTheAwayCoachFirst) {
        if (!askTheAwayCoachFirst) {
            return coaches.decide(newEvents);
        }
        Optional<Intervention> awayRequest = coaches.away().decide(newEvents);
        Optional<Intervention> homeRequest = coaches.home().decide(newEvents);
        List<Intervention> requests = new ArrayList<>();
        homeRequest.ifPresent(requests::add);
        awayRequest.ifPresent(requests::add);
        return requests;
    }

    private int applyInOrder(Match match, List<Intervention> requests) {
        int pending = 0;
        for (Intervention request : requests) {
            InterventionResult result = match.apply(request);
            if (result instanceof InterventionResult.Pending) {
                pending++;
            }
        }
        return pending;
    }

    private static Squad squad(String name, int rating) {
        List<Player> players = new ArrayList<>();
        players.addAll(group(name, Role.GOALKEEPER, 2, rating));
        players.addAll(group(name, Role.DEFENDER, 6, rating));
        players.addAll(group(name, Role.MIDFIELDER, 6, rating));
        players.addAll(group(name, Role.FORWARD, 4, rating));
        CoachProfile coach = new CoachProfile("Coach " + name, new CoachAttributes(COACH_READING));
        return new Squad(name, coach, players);
    }

    private static List<Player> group(String squadName, Role role, int count, int rating) {
        List<Player> players = new ArrayList<>();
        for (int number = 1; number <= count; number++) {
            PlayerAttributes attributes = new PlayerAttributes(rating, rating, rating, rating, rating, rating,
                    rating, rating);
            players.add(new Player(squadName + " " + role + " " + number, role, attributes));
        }
        return players;
    }
}
