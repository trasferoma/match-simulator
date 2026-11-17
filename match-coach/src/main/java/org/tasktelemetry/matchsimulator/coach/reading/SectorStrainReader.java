package org.tasktelemetry.matchsimulator.coach.reading;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.tasktelemetry.matchsimulator.coach.config.SectorStrainConfig;
import org.tasktelemetry.matchsimulator.coach.config.SectorThresholds;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.team.Role;

public final class SectorStrainReader {

    private static final List<Role> SECTORS = List.of(Role.DEFENDER, Role.MIDFIELDER, Role.FORWARD);

    private final SectorStrainConfig config;
    private final DuelReader duelReader;

    public SectorStrainReader(SectorStrainConfig config) {
        this.config = config;
        this.duelReader = new DuelReader();
    }

    public Map<Role, SectorStrain> strainsOf(MatchSide side, EventWindow window) {
        List<MatchEvent> events = window.within(config.windowMinutes());
        SectorDuels duels = duelReader.read(side, events);
        Map<Role, SectorStrain> strains = new EnumMap<>(Role.class);
        for (Role sector : SECTORS) {
            DuelCount sectorDuels = duels.countOf(sector);
            SectorStrain strain = strainOf(sector, sectorDuels);
            strains.put(sector, strain);
        }
        return Collections.unmodifiableMap(strains);
    }

    public SectorStrain strainOf(Role sector, DuelCount duels) {
        if (duels.total() < config.minimumDuels()) {
            return SectorStrain.NONE;
        }
        SectorThresholds thresholds = thresholdsOf(sector);
        double lostShare = duels.lostShare();
        if (lostShare >= thresholds.severeCrisis()) {
            return SectorStrain.SEVERE_CRISIS;
        }
        if (lostShare >= thresholds.difficulty()) {
            return SectorStrain.DIFFICULTY;
        }
        return SectorStrain.NONE;
    }

    private SectorThresholds thresholdsOf(Role sector) {
        return switch (sector) {
            case DEFENDER -> config.defence();
            case MIDFIELDER -> config.midfield();
            case FORWARD -> config.attack();
            case GOALKEEPER -> throw new IllegalArgumentException("the goalkeeper is not a sector with thresholds");
        };
    }
}
