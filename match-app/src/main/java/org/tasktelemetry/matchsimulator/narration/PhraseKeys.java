package org.tasktelemetry.matchsimulator.narration;

import java.util.Optional;

import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.event.Participants;
import org.tasktelemetry.matchsimulator.engine.event.ShotProfile;

public final class PhraseKeys {

    private static final String SEPARATOR = ".";
    private static final String PENALTY = "PENALTY";
    private static final String WITH_SECONDARY = "WITH_SECONDARY";
    private static final String GOAL_CONTEXT = "GOAL_CONTEXT";
    private static final String NO_SEGMENT = "";
    private static final String DEPLOYMENT_CHANGE = "LINEUP_CHANGE.DEPLOYMENT";

    public String keyOf(MatchEvent event) {
        String type = event.type().name();
        String detail = detailSegmentsOf(event.detail());
        String secondary = secondarySegmentOf(event.participants());
        return type + detail + secondary;
    }

    public String keyOf(GoalHighlight highlight) {
        return GOAL_CONTEXT + SEPARATOR + highlight.name();
    }

    public Optional<String> deploymentChangeKeyOf(MatchEvent event) {
        if (event.detail() instanceof EventDetail.LineupChange change && change.changesDeployment()) {
            return Optional.of(DEPLOYMENT_CHANGE);
        }
        return Optional.empty();
    }

    private String detailSegmentsOf(EventDetail detail) {
        return switch (detail) {
            case EventDetail.NoDetail noDetail -> NO_SEGMENT;
            case EventDetail.StoppageAnnounced announced -> NO_SEGMENT;
            case EventDetail.Suspension suspension -> NO_SEGMENT;
            case EventDetail.StyleChange styleChange -> segment(styleChange.reason().name());
            case EventDetail.LineupChange lineupChange -> segment(lineupChange.reason().name());
            case EventDetail.Shot shot -> shotSegmentOf(shot.profile());
            case EventDetail.Save save -> saveSegmentOf(save);
            case EventDetail.Goal goal -> goalSegmentOf(goal.profile());
            case EventDetail.Foul foul -> foulSegmentsOf(foul);
        };
    }

    private String shotSegmentOf(ShotProfile profile) {
        if (profile.penalty()) {
            return segment(PENALTY);
        }
        return segment(profile.quality().name());
    }

    private String saveSegmentOf(EventDetail.Save save) {
        if (save.profile().penalty()) {
            return segment(PENALTY);
        }
        return segment(save.saveQuality().name());
    }

    private String goalSegmentOf(ShotProfile profile) {
        if (profile.penalty()) {
            return segment(PENALTY);
        }
        return segment(profile.kind().name());
    }

    private String foulSegmentsOf(EventDetail.Foul foul) {
        String severity = segment(foul.severity().name());
        if (foul.penalty()) {
            return severity + segment(PENALTY);
        }
        return severity;
    }

    private String secondarySegmentOf(Participants participants) {
        if (participants.secondary().isPresent()) {
            return segment(WITH_SECONDARY);
        }
        return NO_SEGMENT;
    }

    private String segment(String name) {
        return SEPARATOR + name;
    }
}
