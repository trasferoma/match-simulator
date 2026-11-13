package org.tasktelemetry.matchsimulator.narration.highlights;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.IntStream;

import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.event.ShotProfile;
import org.tasktelemetry.matchsimulator.narration.NarratedEventFilter;

public final class HighlightsSelector {

    private final HighlightsConfig config;
    private final NarratedEventFilter filter;

    public HighlightsSelector(HighlightsConfig config, NarratedEventFilter filter) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.filter = Objects.requireNonNull(filter, "filter must not be null");
    }

    public List<HighlightSegment> select(List<MatchEvent> events) {
        Objects.requireNonNull(events, "events must not be null");
        Set<Integer> toldIndices = findToldIndices(events);
        return groupIntoSegments(events, toldIndices);
    }

    private Set<Integer> findToldIndices(List<MatchEvent> events) {
        Set<Integer> toldIndices = new TreeSet<>();
        for (int index = 0; index < events.size(); index++) {
            Set<Integer> indicesToTell = findIndicesToTell(events, index, toldIndices);
            toldIndices.addAll(indicesToTell);
        }
        return toldIndices;
    }

    private Set<Integer> findIndicesToTell(List<MatchEvent> events, int index, Set<Integer> toldIndices) {
        MatchEvent event = events.get(index);
        HighlightTreatment treatment = config.treatmentOf(event.type());
        return switch (treatment) {
            case CHANCE -> findIndicesOfChance(events, index, toldIndices);
            case ONE_LINE -> findIndicesOfOneLineEvent(events, index);
            case ONE_LINE_IF_LINKED_TO_CHANCE -> findIndicesOfLinkedEvent(events, index);
            case ANIMATION_ONLY -> Set.of();
        };
    }

    private Set<Integer> findIndicesOfChance(List<MatchEvent> events, int shotIndex, Set<Integer> toldIndices) {
        List<Integer> preparingIndices = findPreparingIndices(events, shotIndex, toldIndices);
        Set<Integer> indices = new TreeSet<>(preparingIndices);
        indices.add(shotIndex);
        return indices;
    }

    private List<Integer> findPreparingIndices(List<MatchEvent> events, int shotIndex, Set<Integer> toldIndices) {
        MatchEvent shot = events.get(shotIndex);
        if (isPenaltyKick(shot)) {
            OptionalInt foulIndex = findPenaltyFoulBefore(events, shotIndex);
            if (foulIndex.isPresent()) {
                return findPenaltyPreparingIndices(foulIndex.getAsInt(), shotIndex);
            }
        }
        return findOpenPlayPreparingIndices(events, shotIndex, toldIndices);
    }

    private List<Integer> findOpenPlayPreparingIndices(List<MatchEvent> events, int shotIndex,
            Set<Integer> toldIndices) {
        int maxLines = config.maxPreparationLines();
        List<Integer> candidates = filter.findEventsPreparing(events, shotIndex, maxLines);
        return candidates.stream()
                .takeWhile(index -> !toldIndices.contains(index))
                .toList();
    }

    private List<Integer> findPenaltyPreparingIndices(int foulIndex, int shotIndex) {
        int earliestAllowedIndex = shotIndex - config.maxPreparationLines();
        int firstIndex = Math.max(foulIndex, earliestAllowedIndex);
        return IntStream.range(firstIndex, shotIndex).boxed().toList();
    }

    private OptionalInt findPenaltyFoulBefore(List<MatchEvent> events, int shotIndex) {
        return IntStream.iterate(shotIndex - 1, index -> index >= 0, index -> index - 1)
                .filter(index -> filter.awardsPenalty(events.get(index)))
                .findFirst();
    }

    private boolean isPenaltyKick(MatchEvent shot) {
        return switch (shot.detail()) {
            case EventDetail.Shot detail -> isPenalty(detail.profile());
            case EventDetail.Save detail -> isPenalty(detail.profile());
            case EventDetail.Goal detail -> isPenalty(detail.profile());
            default -> false;
        };
    }

    private boolean isPenalty(ShotProfile profile) {
        return profile.penalty();
    }

    private Set<Integer> findIndicesOfOneLineEvent(List<MatchEvent> events, int index) {
        boolean completesPair = completesFoulAndCard(events, index) || completesInjuryAndSubstitution(events, index);
        if (completesPair) {
            int previousIndex = index - 1;
            return Set.of(previousIndex, index);
        }
        return Set.of(index);
    }

    private boolean completesFoulAndCard(List<MatchEvent> events, int cardIndex) {
        MatchEvent card = events.get(cardIndex);
        if (!card.type().isCard() || cardIndex == 0) {
            return false;
        }
        int foulIndex = cardIndex - 1;
        MatchEvent foul = events.get(foulIndex);
        boolean isFoul = foul.type() == EventType.FOUL;
        boolean isForTheFouler = filter.isFollowedByCardForTheSamePlayer(events, foulIndex);
        return isFoul && isForTheFouler;
    }

    private boolean completesInjuryAndSubstitution(List<MatchEvent> events, int substitutionIndex) {
        MatchEvent substitution = events.get(substitutionIndex);
        if (substitution.type() != EventType.SUBSTITUTION || substitutionIndex == 0) {
            return false;
        }
        int injuryIndex = substitutionIndex - 1;
        MatchEvent injury = events.get(injuryIndex);
        boolean isInjury = injury.type() == EventType.INJURY;
        boolean isTheInjuredLeaving = injury.participants().primary().equals(substitution.participants().primary());
        return isInjury && isTheInjuredLeaving && haveTheSamePrimarySide(injury, substitution);
    }

    private boolean haveTheSamePrimarySide(MatchEvent first, MatchEvent second) {
        return first.participants().primarySide().equals(second.participants().primarySide());
    }

    private Set<Integer> findIndicesOfLinkedEvent(List<MatchEvent> events, int index) {
        OptionalInt chanceIndex = findChanceLinkedTo(events, index);
        if (chanceIndex.isPresent()) {
            int firstIndexAfterTheShot = chanceIndex.getAsInt() + 1;
            return indicesBetween(firstIndexAfterTheShot, index);
        }
        if (isInAStoppingZone(events.get(index))) {
            return Set.of(index);
        }
        return Set.of();
    }

    private OptionalInt findChanceLinkedTo(List<MatchEvent> events, int consequenceIndex) {
        MatchEvent consequence = events.get(consequenceIndex);
        return IntStream.rangeClosed(1, config.consequenceWindow())
                .map(distance -> consequenceIndex - distance)
                .takeWhile(index -> index >= 0 && isPartOfTheSameAttack(events.get(index), consequence))
                .filter(index -> isChance(events.get(index)))
                .findFirst();
    }

    private boolean isPartOfTheSameAttack(MatchEvent candidate, MatchEvent consequence) {
        return candidate.side() == consequence.side() && !filter.isKickOff(candidate);
    }

    private boolean isChance(MatchEvent event) {
        return config.treatmentOf(event.type()) == HighlightTreatment.CHANCE;
    }

    private boolean isInAStoppingZone(MatchEvent event) {
        return config.alsoStopsInZones(event.type()).contains(event.zoneRole());
    }

    private Set<Integer> indicesBetween(int firstIndex, int lastIndex) {
        Set<Integer> indices = new TreeSet<>();
        IntStream.rangeClosed(firstIndex, lastIndex).forEach(indices::add);
        return indices;
    }

    private List<HighlightSegment> groupIntoSegments(List<MatchEvent> events, Set<Integer> toldIndices) {
        List<HighlightSegment> segments = new ArrayList<>();
        int index = 0;
        while (index < events.size()) {
            HighlightSegment segment = findSegmentStartingAt(events, index, toldIndices);
            segments.add(segment);
            index += segment.events().size();
        }
        return List.copyOf(segments);
    }

    private HighlightSegment findSegmentStartingAt(List<MatchEvent> events, int index, Set<Integer> toldIndices) {
        if (!toldIndices.contains(index)) {
            MatchEvent frameEvent = events.get(index);
            return new HighlightSegment.Frame(frameEvent);
        }
        long toldRunLength = IntStream.range(index, events.size()).takeWhile(toldIndices::contains).count();
        int endIndex = index + (int) toldRunLength;
        List<MatchEvent> stopEvents = events.subList(index, endIndex);
        boolean containsChance = stopEvents.stream().anyMatch(this::isChance);
        return new HighlightSegment.Stop(stopEvents, containsChance);
    }
}
