package org.tasktelemetry.matchsimulator.coach.reading;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.team.Role;

public final class SituationReader {

    private final GameFlowReader flowReader;
    private final SectorStrainReader strainReader;

    public SituationReader(GameFlowReader flowReader, SectorStrainReader strainReader) {
        this.flowReader = flowReader;
        this.strainReader = strainReader;
    }

    public Set<Situation> holdingFor(MatchSide side, Posture opponentPosture, EventWindow window) {
        List<Situation> fromFlow = situationsOfFlow(side, window);
        List<Situation> fromOpponent = situationsOfOpponent(opponentPosture);
        List<Situation> fromStrains = situationsOfStrains(side, window);
        return Stream.of(fromFlow, fromOpponent, fromStrains)
                .flatMap(List::stream)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Situation.class)));
    }

    private List<Situation> situationsOfFlow(MatchSide side, EventWindow window) {
        GameFlow flow = flowReader.flowOf(side, window);
        return switch (flow) {
            case DOMINATED -> List.of(Situation.DOMINATED);
            case DOMINANT -> List.of(Situation.DOMINANT);
            case EVEN -> List.of();
        };
    }

    private List<Situation> situationsOfOpponent(Posture opponentPosture) {
        return opponentPosture == Posture.UNBALANCED ? List.of(Situation.OPPONENT_UNBALANCED) : List.of();
    }

    private List<Situation> situationsOfStrains(MatchSide side, EventWindow window) {
        Map<Role, SectorStrain> strains = strainReader.strainsOf(side, window);
        return strains.entrySet().stream()
                .flatMap(entry -> situationsOf(entry.getKey(), entry.getValue()).stream())
                .toList();
    }

    private List<Situation> situationsOf(Role sector, SectorStrain strain) {
        return switch (strain) {
            case NONE -> List.of();
            case DIFFICULTY -> struggling(sector);
            case SEVERE_CRISIS -> strugglingAndInCrisis(sector);
        };
    }

    private List<Situation> struggling(Role sector) {
        Situation struggling = Situation.strugglingOf(sector);
        return List.of(struggling);
    }

    private List<Situation> strugglingAndInCrisis(Role sector) {
        Situation struggling = Situation.strugglingOf(sector);
        Situation inCrisis = Situation.crisisOf(sector);
        return List.of(struggling, inCrisis);
    }
}
