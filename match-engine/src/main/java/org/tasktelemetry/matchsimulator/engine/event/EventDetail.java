package org.tasktelemetry.matchsimulator.engine.event;

import java.util.Objects;

public sealed interface EventDetail {

    static EventDetail none() {
        return new NoDetail();
    }

    static EventDetail stoppageAnnounced(int minutes) {
        return new StoppageAnnounced(minutes);
    }

    static EventDetail shot(ShotProfile profile) {
        return new Shot(profile);
    }

    static EventDetail save(ShotProfile profile, SaveQuality saveQuality) {
        return new Save(profile, saveQuality);
    }

    static EventDetail goal(ShotProfile profile, GoalContext context) {
        return new Goal(profile, context);
    }

    record NoDetail() implements EventDetail {
    }

    record StoppageAnnounced(int minutes) implements EventDetail {

        public StoppageAnnounced {
            if (minutes < 1) {
                throw new IllegalArgumentException("announced stoppage must be at least 1 minute, was " + minutes);
            }
        }
    }

    record Shot(ShotProfile profile) implements EventDetail {

        public Shot {
            Objects.requireNonNull(profile, "shot profile must not be null");
        }
    }

    record Save(ShotProfile profile, SaveQuality saveQuality) implements EventDetail {

        public Save {
            Objects.requireNonNull(profile, "shot profile must not be null");
            Objects.requireNonNull(saveQuality, "save quality must not be null");
        }
    }

    record Goal(ShotProfile profile, GoalContext context) implements EventDetail {

        public Goal {
            Objects.requireNonNull(profile, "shot profile must not be null");
            Objects.requireNonNull(context, "goal context must not be null");
        }
    }
}
