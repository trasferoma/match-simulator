package org.tasktelemetry.matchsimulator.coach.decision;

import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.team.Role;

record Reinforcement(Role target, Role donor, ChangeReason reason) {
}
