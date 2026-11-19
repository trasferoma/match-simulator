package org.tasktelemetry.matchsimulator.engine;

import static org.tasktelemetry.matchsimulator.engine.MatchDriving.lastEventOf;

import java.util.List;
import java.util.OptionalInt;

import org.tasktelemetry.matchsimulator.engine.config.LineupConfig;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.event.MatchTime;
import org.tasktelemetry.matchsimulator.engine.state.MatchPeriod;

final class ClosingSubstitutionMoments {

    private static final int SEEDS_TO_SCAN = 3000;
    private static final int SHORTEST_SUBSTITUTION_SECONDS = LineupConfig.defaults().substitutionDuration()
            .minSeconds();

    private ClosingSubstitutionMoments() {
    }

    static Match whereASubstitutionEndsTheFirstHalf() {
        return matchWhereASubstitutionEnds(MatchPeriod.FIRST_HALF);
    }

    static Match whereASubstitutionEndsTheMatch() {
        return matchWhereASubstitutionEnds(MatchPeriod.SECOND_HALF);
    }

    private static Match matchWhereASubstitutionEnds(MatchPeriod period) {
        for (long seed = 1; seed <= SEEDS_TO_SCAN; seed++) {
            Match match = ReferenceMatch.start(seed);
            while (!match.isFinished()) {
                match.step();
                if (isPlayableWithTheBallStill(match) && substitutionWouldOutlastTheStoppage(match, period)) {
                    return match;
                }
            }
        }
        throw new IllegalStateException("no moment found in the first " + SEEDS_TO_SCAN + " seeds");
    }

    private static boolean isPlayableWithTheBallStill(Match match) {
        boolean isPlayed = !match.isFinished() && !MatchDriving.isAtHalfTime(match);
        return isPlayed && !match.isBallLive();
    }

    private static boolean substitutionWouldOutlastTheStoppage(Match match, MatchPeriod period) {
        OptionalInt expiry = expiryOf(match.events(), period);
        MatchTime now = lastEventOf(match).orElseThrow().time();
        if (now.period() != period || expiry.isEmpty()) {
            return false;
        }
        int endOfTheSubstitution = now.seconds() + SHORTEST_SUBSTITUTION_SECONDS;
        return now.seconds() <= expiry.getAsInt() && endOfTheSubstitution > expiry.getAsInt();
    }

    private static OptionalInt expiryOf(List<MatchEvent> events, MatchPeriod period) {
        return events.stream()
                .filter(event -> event.type() == EventType.STOPPAGE_ANNOUNCED && event.time().period() == period)
                .mapToInt(ClosingSubstitutionMoments::expiryAnnouncedBy)
                .findFirst();
    }

    private static int expiryAnnouncedBy(MatchEvent announcement) {
        EventDetail.StoppageAnnounced detail = (EventDetail.StoppageAnnounced) announcement.detail();
        return MatchPeriod.REGULATION_SECONDS + detail.minutes() * 60;
    }
}
