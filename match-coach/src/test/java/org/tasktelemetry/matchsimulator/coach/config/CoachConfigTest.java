package org.tasktelemetry.matchsimulator.coach.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Role;

class CoachConfigTest {

    private final CoachConfig defaults = CoachConfig.defaults();

    @Test
    void defaultsAreTheSameEveryTime() {
        assertThat(CoachConfig.defaults()).isEqualTo(CoachConfig.defaults());
    }

    @Test
    void defaultsDeclareTheAssessmentValues() {
        assertThat(defaults.assessment()).isEqualTo(new AssessmentConfig(2.0, -6.0, -2.0, 2.0, 6.0));
    }

    @Test
    void defaultsDeclareTheFormationCatalogInOrderWithTheirGrade() {
        assertThat(defaults.formations().formations()).containsExactly(
                new Formation(5, 3, 2, -2),
                new Formation(4, 5, 1, -1),
                new Formation(4, 4, 2, 0),
                new Formation(3, 5, 2, 1),
                new Formation(4, 3, 3, 2));
        assertThat(defaults.formations().formations()).extracting(Formation::label)
                .containsExactly("5-3-2", "4-5-1", "4-4-2", "3-5-2", "4-3-3");
    }

    @Test
    void defaultsDeclareThePrudenceOfEveryStyle() {
        assertThat(defaults.styleScale().prudenceByStyle())
                .containsOnlyKeys(PlayStyle.values())
                .containsEntry(PlayStyle.DEFENSIVE, -2)
                .containsEntry(PlayStyle.COUNTER_ATTACK, -1)
                .containsEntry(PlayStyle.BALANCED, 0)
                .containsEntry(PlayStyle.POSSESSION, 1)
                .containsEntry(PlayStyle.ATTACKING, 2);
    }

    @Test
    void defaultsDeclareTheLimitsPerSectorAndTheBench() {
        assertThat(defaults.sectorLimits().defenders()).isEqualTo(new SectorLimit(3, 5));
        assertThat(defaults.sectorLimits().midfielders()).isEqualTo(new SectorLimit(2, 5));
        assertThat(defaults.sectorLimits().forwards()).isEqualTo(new SectorLimit(1, 4));
        assertThat(defaults.maximumBenchSize()).isEqualTo(9);
    }

    @Test
    void defaultsDeclareTheTimings() {
        assertThat(defaults.timing()).isEqualTo(new TimingConfig(1, 75, 10, 3, 15));
        assertThat(defaults.resultTiming()).isEqualTo(new ResultTimingConfig(85, 5, 85, 80, 80));
    }

    @Test
    void defaultsDeclareFlowSectorStrainAndTacticalReading() {
        assertThat(defaults.flow()).isEqualTo(new FlowConfig(30, 0.30, 0.70));
        assertThat(defaults.sectorStrain().windowMinutes()).isEqualTo(30);
        assertThat(defaults.sectorStrain().minimumDuels()).isEqualTo(8);
        assertThat(defaults.sectorStrain().defence()).isEqualTo(new SectorThresholds(0.60, 0.75));
        assertThat(defaults.sectorStrain().midfield()).isEqualTo(new SectorThresholds(0.75, 0.85));
        assertThat(defaults.sectorStrain().attack()).isEqualTo(new SectorThresholds(0.85, 0.90));
        assertThat(defaults.tacticalReading()).isEqualTo(new TacticalReadingConfig(50, 4, 0.5));
    }

    @Test
    void defaultsDeclareTheSubstitutionsSharedWithTheEngineAndOneKeptInReserve() {
        assertThat(defaults.substitutions()).isEqualTo(new SubstitutionConfig(5, 1));
    }

    @Test
    void rejectsSubstitutionsWithoutAMaximumOrWithoutAReserveOrWithAReserveAboveTheMaximum() {
        assertThatThrownBy(() -> new SubstitutionConfig(0, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maximum substitutions must be at least 1, was 0");
        assertThatThrownBy(() -> new SubstitutionConfig(5, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("reserved substitutions must be at least 1, was 0");
        assertThatThrownBy(() -> new SubstitutionConfig(2, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("reserved substitutions 3 must not exceed the maximum 2");
    }

    @Test
    void rejectsAMissingSubstitutionGroup() {
        assertThatThrownBy(() -> new CoachConfig(defaults.assessment(), defaults.bandResponses(),
                defaults.formations(), defaults.styleScale(), defaults.sectorLimits(), 9, defaults.timing(),
                defaults.resultTiming(), defaults.flow(), defaults.sectorStrain(), defaults.tacticalReading(),
                null, defaults.fatigueResponse()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("substitution configuration must not be null");
    }

    @Test
    void rejectsAMissingFatigueResponseGroup() {
        assertThatThrownBy(() -> new CoachConfig(defaults.assessment(), defaults.bandResponses(),
                defaults.formations(), defaults.styleScale(), defaults.sectorLimits(), 9, defaults.timing(),
                defaults.resultTiming(), defaults.flow(), defaults.sectorStrain(), defaults.tacticalReading(),
                defaults.substitutions(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("fatigue response configuration must not be null");
    }

    @Test
    void aSectorLimitHasRoomForOneMoreBelowTheMaximumAndCanSpareOneAboveTheMinimum() {
        SectorLimit defenders = defaults.sectorLimits().defenders();

        assertThat(defenders.allowsAnother(4)).isTrue();
        assertThat(defenders.allowsAnother(5)).isFalse();
        assertThat(defenders.canSpareOne(4)).isTrue();
        assertThat(defenders.canSpareOne(3)).isFalse();
    }

    @Test
    void theLimitOfASectorIsFoundByItsRoleAndTheGoalkeeperHasNone() {
        SectorLimits limits = defaults.sectorLimits();

        assertThat(limits.limitOf(Role.DEFENDER)).isEqualTo(limits.defenders());
        assertThat(limits.limitOf(Role.MIDFIELDER)).isEqualTo(limits.midfielders());
        assertThat(limits.limitOf(Role.FORWARD)).isEqualTo(limits.forwards());
        assertThatThrownBy(() -> limits.limitOf(Role.GOALKEEPER)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyDefaultFormationHasTenOutfieldPlayers() {
        assertThat(defaults.formations().formations())
                .allSatisfy(formation -> assertThat(formation.defenders() + formation.midfielders()
                        + formation.forwards()).isEqualTo(10));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("configsWithOneMissingGroup")
    void rejectsAMissingGroup(String expectedMessage, ThrowingCallable construction) {
        assertThatThrownBy(construction)
                .isInstanceOf(NullPointerException.class)
                .hasMessage(expectedMessage);
    }

    private static Stream<Arguments> configsWithOneMissingGroup() {
        CoachConfig base = CoachConfig.defaults();
        return Stream.of(
                missingGroup("assessment configuration must not be null",
                        () -> configWith(null, base.bandResponses(), base.formations(), base.styleScale(), base.sectorLimits(), base.timing(), base.resultTiming(), base.flow(), base.sectorStrain(), base.tacticalReading())),
                missingGroup("band responses must not be null",
                        () -> configWith(base.assessment(), null, base.formations(), base.styleScale(), base.sectorLimits(), base.timing(), base.resultTiming(), base.flow(), base.sectorStrain(), base.tacticalReading())),
                missingGroup("formation catalog must not be null",
                        () -> configWith(base.assessment(), base.bandResponses(), null, base.styleScale(), base.sectorLimits(), base.timing(), base.resultTiming(), base.flow(), base.sectorStrain(), base.tacticalReading())),
                missingGroup("style prudence scale must not be null",
                        () -> configWith(base.assessment(), base.bandResponses(), base.formations(), null, base.sectorLimits(), base.timing(), base.resultTiming(), base.flow(), base.sectorStrain(), base.tacticalReading())),
                missingGroup("sector limits must not be null",
                        () -> configWith(base.assessment(), base.bandResponses(), base.formations(), base.styleScale(), null, base.timing(), base.resultTiming(), base.flow(), base.sectorStrain(), base.tacticalReading())),
                missingGroup("timing configuration must not be null",
                        () -> configWith(base.assessment(), base.bandResponses(), base.formations(), base.styleScale(), base.sectorLimits(), null, base.resultTiming(), base.flow(), base.sectorStrain(), base.tacticalReading())),
                missingGroup("result timing configuration must not be null",
                        () -> configWith(base.assessment(), base.bandResponses(), base.formations(), base.styleScale(), base.sectorLimits(), base.timing(), null, base.flow(), base.sectorStrain(), base.tacticalReading())),
                missingGroup("flow configuration must not be null",
                        () -> configWith(base.assessment(), base.bandResponses(), base.formations(), base.styleScale(), base.sectorLimits(), base.timing(), base.resultTiming(), null, base.sectorStrain(), base.tacticalReading())),
                missingGroup("sector strain configuration must not be null",
                        () -> configWith(base.assessment(), base.bandResponses(), base.formations(), base.styleScale(), base.sectorLimits(), base.timing(), base.resultTiming(), base.flow(), null, base.tacticalReading())),
                missingGroup("tactical reading configuration must not be null",
                        () -> configWith(base.assessment(), base.bandResponses(), base.formations(), base.styleScale(), base.sectorLimits(), base.timing(), base.resultTiming(), base.flow(), base.sectorStrain(), null)));
    }

    private static Arguments missingGroup(String expectedMessage, ThrowingCallable construction) {
        return Arguments.of(expectedMessage, construction);
    }

    private static CoachConfig configWith(AssessmentConfig assessment, BandResponses bandResponses,
            FormationCatalog formations, StylePrudenceScale styleScale, SectorLimits sectorLimits,
            TimingConfig timing, ResultTimingConfig resultTiming, FlowConfig flow,
            SectorStrainConfig sectorStrain, TacticalReadingConfig tacticalReading) {
        return new CoachConfig(assessment, bandResponses, formations, styleScale, sectorLimits, 9, timing,
                resultTiming, flow, sectorStrain, tacticalReading, SubstitutionConfig.defaults(),
                FatigueResponseConfig.defaults());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsABenchWithoutPlayers(int size) {
        assertThatThrownBy(() -> new CoachConfig(defaults.assessment(), defaults.bandResponses(),
                defaults.formations(), defaults.styleScale(), defaults.sectorLimits(), size, defaults.timing(),
                defaults.resultTiming(), defaults.flow(), defaults.sectorStrain(), defaults.tacticalReading(),
                defaults.substitutions(), defaults.fatigueResponse()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maximum bench size must be at least 1, was " + size);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -2.0, Double.NaN, Double.POSITIVE_INFINITY})
    void rejectsAHomeFieldWeightThatIsNotPositiveAndFinite(double weight) {
        assertThatThrownBy(() -> new AssessmentConfig(weight, -6.0, -2.0, 2.0, 6.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("home field weight must be positive and finite, was " + weight);
    }

    @ParameterizedTest
    @CsvSource({
            "-6.0, MUCH_WEAKER", "-2.0, WEAKER", "2.0, STRONGER", "6.0, MUCH_STRONGER"})
    void aBoundaryBelongsToTheOuterBandEvenWithARoundingErrorInTheEvaluation(double boundary,
            AssessmentBand expectedBand) {
        AssessmentConfig assessment = AssessmentConfig.defaults();
        double justInsideTheInnerBand = boundary < 0 ? Math.nextUp(boundary) : Math.nextDown(boundary);

        assertThat(assessment.bandOf(boundary)).isEqualTo(expectedBand);
        assertThat(assessment.bandOf(justInsideTheInnerBand)).isEqualTo(expectedBand);
    }

    @Test
    void rejectsAnInfiniteOrMissingBoundary() {
        assertThatThrownBy(() -> new AssessmentConfig(2.0, Double.NEGATIVE_INFINITY, -2.0, 2.0, 6.0))
                .hasMessageContaining("much weaker boundary must be finite");
        assertThatThrownBy(() -> new AssessmentConfig(2.0, -6.0, -2.0, 2.0, Double.NaN))
                .hasMessageContaining("much stronger boundary must be finite");
    }

    @Test
    void rejectsBoundariesNotStrictlyIncreasing() {
        assertThatThrownBy(() -> new AssessmentConfig(2.0, -6.0, 2.0, 2.0, 6.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("band boundaries must be in strictly increasing order")
                .hasMessageContaining("weaker boundary 2.0")
                .hasMessageContaining("stronger boundary 2.0");
        assertThatThrownBy(() -> new AssessmentConfig(2.0, 6.0, 2.0, -2.0, -6.0))
                .hasMessageContaining("band boundaries must be in strictly increasing order");
    }

    @Test
    void rejectsAFormationWithoutTenOutfieldPlayers() {
        assertThatThrownBy(() -> new Formation(4, 4, 3, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("a formation must have 10 outfield players, 4-4-3 has 11");
        assertThatThrownBy(() -> new Formation(4, 4, 1, 0))
                .hasMessage("a formation must have 10 outfield players, 4-4-1 has 9");
    }

    @Test
    void rejectsAFormationWithAnEmptySector() {
        assertThatThrownBy(() -> new Formation(0, 5, 5, 0))
                .hasMessage("formation defenders must be at least 1, was 0");
        assertThatThrownBy(() -> new Formation(5, 0, 5, 0))
                .hasMessage("formation midfielders must be at least 1, was 0");
        assertThatThrownBy(() -> new Formation(5, 5, 0, 0))
                .hasMessage("formation forwards must be at least 1, was 0");
    }

    @Test
    void rejectsAMissingOrEmptyCatalog() {
        assertThatThrownBy(() -> new FormationCatalog(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("formations must not be null");
        assertThatThrownBy(() -> new FormationCatalog(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("the formation catalog must not be empty");
    }

    @Test
    void theCatalogIsCopiedAndNotModifiable() {
        List<Formation> formations = new ArrayList<>(defaults.formations().formations());
        FormationCatalog catalog = new FormationCatalog(formations);
        formations.clear();

        assertThat(catalog.formations()).hasSize(5);
        assertThatThrownBy(() -> catalog.formations().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsAStyleScaleMissingAStyle() {
        Map<PlayStyle, Integer> prudence = new EnumMap<>(defaults.styleScale().prudenceByStyle());
        prudence.remove(PlayStyle.POSSESSION);

        assertThatThrownBy(() -> new StylePrudenceScale(prudence))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("style prudence needs a value for POSSESSION");
        assertThatThrownBy(() -> new StylePrudenceScale(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("prudence by style must not be null");
    }

    @Test
    void theStyleScaleIsCopiedAndNotModifiable() {
        Map<PlayStyle, Integer> prudence = new EnumMap<>(defaults.styleScale().prudenceByStyle());
        StylePrudenceScale scale = new StylePrudenceScale(prudence);
        prudence.put(PlayStyle.ATTACKING, 99);

        assertThat(scale.prudenceByStyle()).containsEntry(PlayStyle.ATTACKING, 2);
        assertThatThrownBy(() -> scale.prudenceByStyle().put(PlayStyle.ATTACKING, 5))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsASectorLimitWithMinimumAboveMaximumOrBelowOne() {
        assertThatThrownBy(() -> new SectorLimit(4, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sector minimum 4 must not exceed the maximum 3");
        assertThatThrownBy(() -> new SectorLimit(0, 3))
                .hasMessage("sector minimum must be at least 1, was 0");
    }

    @Test
    void acceptsASectorLimitWithEqualBounds() {
        assertThat(new SectorLimit(3, 3).maximum()).isEqualTo(3);
    }

    @Test
    void rejectsAMissingSectorLimit() {
        SectorLimit limit = new SectorLimit(1, 2);

        assertThatThrownBy(() -> new SectorLimits(null, limit, limit))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("defenders limit");
        assertThatThrownBy(() -> new SectorLimits(limit, null, limit))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("midfielders limit");
        assertThatThrownBy(() -> new SectorLimits(limit, limit, null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("forwards limit");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -3, 91})
    void rejectsTimingsOutsideTheMatch(int minutes) {
        assertThatThrownBy(() -> new TimingConfig(minutes, 75, 10, 3, 15))
                .hasMessage("check interval must be between 1 and 90 minutes, was " + minutes);
        assertThatThrownBy(() -> new TimingConfig(1, minutes, 10, 3, 15))
                .hasMessage("final phase start minute must be between 1 and 90 minutes, was " + minutes);
        assertThatThrownBy(() -> new TimingConfig(1, 75, minutes, 1, 15))
                .hasMessage("pause must be between 1 and 90 minutes, was " + minutes);
        assertThatThrownBy(() -> new TimingConfig(1, 75, 10, minutes, 15))
                .hasMessage("final phase pause must be between 1 and 90 minutes, was " + minutes);
    }

    @Test
    void rejectsAFinalPhasePauseLongerThanThePause() {
        assertThatThrownBy(() -> new TimingConfig(1, 75, 3, 10, 15))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("final phase pause 10 must not exceed the pause 3");
    }

    @Test
    void acceptsAFinalPhasePauseEqualToThePause() {
        assertThat(new TimingConfig(1, 75, 5, 5, 15).finalPhasePauseMinutes()).isEqualTo(5);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 91})
    void rejectsResultMinutesOutsideTheMatch(int minutes) {
        assertThatThrownBy(() -> new ResultTimingConfig(minutes, 5, 85, 80, 80))
                .hasMessage("risk base minute must be between 1 and 90 minutes, was " + minutes);
        assertThatThrownBy(() -> new ResultTimingConfig(85, minutes, 85, 80, 80))
                .hasMessage("risk minutes per goal behind must be between 1 and 90 minutes, was " + minutes);
        assertThatThrownBy(() -> new ResultTimingConfig(85, 5, minutes, 80, 80))
                .hasMessage("protect lead minute must be between 1 and 90 minutes, was " + minutes);
        assertThatThrownBy(() -> new ResultTimingConfig(85, 5, 85, minutes, 80))
                .hasMessage("risk in draw minute must be between 1 and 90 minutes, was " + minutes);
        assertThatThrownBy(() -> new ResultTimingConfig(85, 5, 85, 80, minutes))
                .hasMessage("reserve hold minute must be between 1 and 90 minutes, was " + minutes);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 1.0, -0.1, 1.1, Double.NaN})
    void rejectsFlowIndexesOutsideTheOpenUnitInterval(double index) {
        assertThatThrownBy(() -> new FlowConfig(30, index, 0.9))
                .hasMessageContaining("dominated index must be strictly between 0.0 and 1.0, was " + index);
        assertThatThrownBy(() -> new FlowConfig(30, 0.1, index))
                .hasMessageContaining("dominant index must be strictly between 0.0 and 1.0, was " + index);
    }

    @Test
    void rejectsADominatedIndexNotBelowTheDominantOne() {
        assertThatThrownBy(() -> new FlowConfig(30, 0.7, 0.3))
                .hasMessage("dominated index 0.7 must be below the dominant index 0.3");
        assertThatThrownBy(() -> new FlowConfig(30, 0.5, 0.5))
                .hasMessage("dominated index 0.5 must be below the dominant index 0.5");
    }

    @Test
    void rejectsAFlowWindowOutsideTheMatch() {
        assertThatThrownBy(() -> new FlowConfig(0, 0.3, 0.7))
                .hasMessage("flow window must be between 1 and 90 minutes, was 0");
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 1.0, -0.2, 1.5, Double.NaN})
    void rejectsSectorThresholdsOutsideTheOpenUnitInterval(double threshold) {
        assertThatThrownBy(() -> new SectorThresholds(threshold, 0.9))
                .hasMessageContaining("difficulty threshold must be strictly between 0.0 and 1.0, was " + threshold);
        assertThatThrownBy(() -> new SectorThresholds(0.5, threshold))
                .hasMessageContaining("severe crisis threshold must be strictly between 0.0 and 1.0, was "
                        + threshold);
    }

    @Test
    void rejectsASevereCrisisBelowTheDifficultyThreshold() {
        assertThatThrownBy(() -> new SectorThresholds(0.8, 0.7))
                .hasMessage("severe crisis threshold 0.7 must not be below the difficulty threshold 0.8");
    }

    @Test
    void acceptsASevereCrisisEqualToTheDifficultyThreshold() {
        assertThat(new SectorThresholds(0.8, 0.8).severeCrisis()).isEqualTo(0.8);
    }

    @Test
    void rejectsInvalidSectorStrainValues() {
        SectorThresholds thresholds = new SectorThresholds(0.6, 0.7);

        assertThatThrownBy(() -> new SectorStrainConfig(0, 8, thresholds, thresholds, thresholds))
                .hasMessage("sector window must be between 1 and 90 minutes, was 0");
        assertThatThrownBy(() -> new SectorStrainConfig(30, 0, thresholds, thresholds, thresholds))
                .hasMessage("minimum duels must be at least 1, was 0");
        assertThatThrownBy(() -> new SectorStrainConfig(30, 8, null, thresholds, thresholds))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("defence thresholds");
        assertThatThrownBy(() -> new SectorStrainConfig(30, 8, thresholds, null, thresholds))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("midfield thresholds");
        assertThatThrownBy(() -> new SectorStrainConfig(30, 8, thresholds, thresholds, null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("attack thresholds");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 101})
    void rejectsANeutralReadingOutsideTheAttributeScale(int reading) {
        assertThatThrownBy(() -> new TacticalReadingConfig(reading, 4, 0.5))
                .hasMessage("neutral reading must be between 1 and 100, was " + reading);
    }

    @Test
    void rejectsAnInvalidReadingDelayOrSensitivity() {
        assertThatThrownBy(() -> new TacticalReadingConfig(50, 0, 0.5))
                .hasMessage("base delay must be between 1 and 90 minutes, was 0");
        assertThatThrownBy(() -> new TacticalReadingConfig(50, 4, 0.0))
                .hasMessage("reading sensitivity must be positive and finite, was 0.0");
        assertThatThrownBy(() -> new TacticalReadingConfig(50, 4, Double.NaN))
                .hasMessage("reading sensitivity must be positive and finite, was NaN");
    }

    @Test
    void defaultsDeclareTheGradeAndStyleOfEveryAssessmentBand() {
        assertThat(defaults.bandResponses().responseByBand())
                .containsOnlyKeys(AssessmentBand.values())
                .containsEntry(AssessmentBand.MUCH_WEAKER, new BandResponse(-2, PlayStyle.DEFENSIVE))
                .containsEntry(AssessmentBand.WEAKER, new BandResponse(-1, PlayStyle.COUNTER_ATTACK))
                .containsEntry(AssessmentBand.EVEN, new BandResponse(0, PlayStyle.BALANCED))
                .containsEntry(AssessmentBand.STRONGER, new BandResponse(1, PlayStyle.POSSESSION))
                .containsEntry(AssessmentBand.MUCH_STRONGER, new BandResponse(2, PlayStyle.ATTACKING));
    }

    @Test
    void rejectsBandResponsesMissingABand() {
        Map<AssessmentBand, BandResponse> responses = new EnumMap<>(defaults.bandResponses().responseByBand());
        responses.remove(AssessmentBand.EVEN);

        assertThatThrownBy(() -> new BandResponses(responses))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("band responses need a value for EVEN");
        assertThatThrownBy(() -> new BandResponses(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("band responses must not be null");
    }

    @Test
    void rejectsABandResponseWithoutStyle() {
        assertThatThrownBy(() -> new BandResponse(0, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("band response style must not be null");
    }

    @ParameterizedTest
    @CsvSource({
            "-100.0, MUCH_WEAKER",
            "-6.0, MUCH_WEAKER",
            "-5.99, WEAKER",
            "-2.0, WEAKER",
            "-1.99, EVEN",
            "0.0, EVEN",
            "1.99, EVEN",
            "2.0, STRONGER",
            "5.99, STRONGER",
            "6.0, MUCH_STRONGER",
            "100.0, MUCH_STRONGER"})
    void theBoundariesBelongToTheOuterBand(double evaluation, AssessmentBand expected) {
        assertThat(defaults.assessment().bandOf(evaluation)).isEqualTo(expected);
    }

    @Test
    void aFormationCountsThePlayersOfEveryRole() {
        Formation formation = new Formation(4, 3, 3, 2);

        assertThat(formation.playersIn(Role.GOALKEEPER)).isEqualTo(1);
        assertThat(formation.playersIn(Role.DEFENDER)).isEqualTo(4);
        assertThat(formation.playersIn(Role.MIDFIELDER)).isEqualTo(3);
        assertThat(formation.playersIn(Role.FORWARD)).isEqualTo(3);
    }
}
