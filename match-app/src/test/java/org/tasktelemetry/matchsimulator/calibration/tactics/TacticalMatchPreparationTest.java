package org.tasktelemetry.matchsimulator.calibration.tactics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.calibration.BenchConfig;
import org.tasktelemetry.matchsimulator.calibration.LeagueGenerator;
import org.tasktelemetry.matchsimulator.calibration.RoleProfiles;
import org.tasktelemetry.matchsimulator.calibration.VoluntaryInterventions;
import org.tasktelemetry.matchsimulator.coach.config.CoachConfig;
import org.tasktelemetry.matchsimulator.coach.squad.Squad;
import org.tasktelemetry.matchsimulator.engine.MatchEngine;
import org.tasktelemetry.matchsimulator.engine.MatchRandom;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;
import org.tasktelemetry.matchsimulator.live.LiveMatch;
import org.tasktelemetry.matchsimulator.live.PreparedMatch;
import org.tasktelemetry.matchsimulator.live.SilentObserver;

class TacticalMatchPreparationTest {

    private final CoachConfig coachConfig = CoachConfig.defaults();
    private final TacticVariants variants = TacticVariants.from(coachConfig.formations());
    private final TacticalMatchPreparation preparation = new TacticalMatchPreparation(coachConfig);
    private final LiveMatch liveMatch = new LiveMatch(new MatchEngine(EngineConfig.defaults()));

    @Test
    void thereAreNineVariantsAndTheReferenceAppearsOnlyOnceAndFirst() {
        List<TacticVariant> all = variants.all();

        assertThat(all).hasSize(9).doesNotHaveDuplicates();
        assertThat(all.get(0)).isEqualTo(variants.reference());
        assertThat(variants.reference().style()).isEqualTo(PlayStyle.BALANCED);
        assertThat(variants.reference().formation().label()).isEqualTo("4-4-2");
    }

    @Test
    void everyVariantKeepsItsStyleAndFormationForTheWholeMatch() {
        for (TacticVariant variant : variants.all()) {
            Squad tested = squad(0, 1L);
            Squad opponent = squad(1, 1L);

            PreparedMatch prepared = preparation.prepare(tested, variant, opponent, variants.reference());
            Team team = prepared.home();
            List<MatchEvent> events = liveMatch.play(prepared, 7L, new SilentObserver());

            assertThat(team.style()).isEqualTo(variant.style());
            assertThat(playersIn(team, Role.DEFENDER)).isEqualTo(variant.formation().defenders());
            assertThat(playersIn(team, Role.MIDFIELDER)).isEqualTo(variant.formation().midfielders());
            assertThat(playersIn(team, Role.FORWARD)).isEqualTo(variant.formation().forwards());
            assertThat(VoluntaryInterventions.from(events).total()).isZero();
            assertThat(events).noneMatch(event -> event.type() == EventType.STYLE_CHANGED);
            assertThat(events).anyMatch(event -> event.type() == EventType.MATCH_END);
        }
    }

    private Squad squad(int teamIndex, long seed) {
        LeagueGenerator generator = new LeagueGenerator(BenchConfig.defaults(), RoleProfiles.defaults());
        return generator.generateSquad(teamIndex, 68, new MatchRandom(seed));
    }

    private long playersIn(Team team, Role role) {
        return team.starters().stream().filter(player -> player.role() == role).count();
    }
}
