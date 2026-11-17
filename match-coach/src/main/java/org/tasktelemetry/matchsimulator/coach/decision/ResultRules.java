package org.tasktelemetry.matchsimulator.coach.decision;

import java.util.Optional;

import org.tasktelemetry.matchsimulator.coach.config.ResultTimingConfig;
import org.tasktelemetry.matchsimulator.coach.config.TimingConfig;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;

public final class ResultRules {

    private final TimingConfig timing;
    private final ResultTimingConfig resultTiming;

    public ResultRules(TimingConfig timing, ResultTimingConfig resultTiming) {
        this.timing = timing;
        this.resultTiming = resultTiming;
    }

    public Optional<Response> responseTo(ResultSituation situation) {
        int lead = situation.lead();
        if (lead < 0) {
            return answerWhenBehind(situation);
        }
        if (lead == 0) {
            return answerWhenLevel(situation);
        }
        if (lead == 1) {
            return answerWithOneGoalLead(situation);
        }
        return answerWithComfortableLead(situation);
    }

    private Optional<Response> answerWhenBehind(ResultSituation situation) {
        if (isTimeToTakeRisk(situation)) {
            return respondWithModule(Direction.MORE_OFFENSIVE, ChangeReason.CHASE_GAME, ModuleChange.EXTRA_ATTACKER);
        }
        return pushAgainstShortHandedOpponent(situation);
    }

    private Optional<Response> answerWhenLevel(ResultSituation situation) {
        if (isTimeToSeekTheWin(situation)) {
            ModuleChange module = situation.isDominant() ? ModuleChange.EXTRA_ATTACKER : ModuleChange.NONE;
            return respondWithModule(Direction.MORE_OFFENSIVE, ChangeReason.SEEK_WINNER, module);
        }
        return pushAgainstShortHandedOpponent(situation);
    }

    private Optional<Response> answerWithOneGoalLead(ResultSituation situation) {
        Optional<Response> protection = protectTheLead(situation);
        if (protection.isPresent()) {
            return protection;
        }
        return pushAgainstShortHandedOpponent(situation);
    }

    private Optional<Response> answerWithComfortableLead(ResultSituation situation) {
        if (situation.opponentUnbalanced()) {
            return respond(Direction.MORE_PRUDENT, ChangeReason.RESPOND_TO_UNBALANCED_OPPONENT);
        }
        if (isInFinalPhase(situation)) {
            return respond(Direction.MORE_PRUDENT, ChangeReason.PROTECT_LEAD);
        }
        return Optional.empty();
    }

    private Optional<Response> protectTheLead(ResultSituation situation) {
        if (situation.isDominated()) {
            ModuleChange defenderInTheFinal = extraDefenderInTheFinal(situation);
            return respondWithModule(Direction.MORE_PRUDENT, ChangeReason.WEATHER_PRESSURE, defenderInTheFinal);
        }
        if (!isInFinalPhase(situation)) {
            return Optional.empty();
        }
        if (situation.opponentUnbalanced()) {
            return respondWithModule(Direction.MORE_PRUDENT, ChangeReason.RESPOND_TO_UNBALANCED_OPPONENT,
                    ModuleChange.EXTRA_DEFENDER);
        }
        if (situation.minute() >= resultTiming.protectLeadMinute()) {
            return respondWithModule(Direction.MORE_PRUDENT, ChangeReason.PROTECT_LEAD, ModuleChange.EXTRA_DEFENDER);
        }
        return Optional.empty();
    }

    private ModuleChange extraDefenderInTheFinal(ResultSituation situation) {
        return isInFinalPhase(situation) ? ModuleChange.EXTRA_DEFENDER : ModuleChange.NONE;
    }

    private Optional<Response> pushAgainstShortHandedOpponent(ResultSituation situation) {
        boolean canPush = situation.opponentShortHanded() && !situation.ownSetupAboveInitial();
        if (canPush) {
            return respond(Direction.MORE_OFFENSIVE, ChangeReason.EXPLOIT_NUMERICAL_ADVANTAGE);
        }
        return Optional.empty();
    }

    public boolean isRiskPhase(ResultSituation situation) {
        return situation.lead() < 0 && isTimeToTakeRisk(situation);
    }

    private boolean isTimeToTakeRisk(ResultSituation situation) {
        int goalsBehind = -situation.lead();
        int riskMinute = riskMinuteFor(goalsBehind);
        return situation.minute() >= riskMinute;
    }

    private int riskMinuteFor(int goalsBehind) {
        int earlierBy = resultTiming.riskMinutesPerGoalBehind() * goalsBehind;
        int widenedMinute = resultTiming.riskBaseMinute() - earlierBy;
        return Math.max(widenedMinute, timing.finalPhaseStartMinute());
    }

    private boolean isTimeToSeekTheWin(ResultSituation situation) {
        boolean isLateEnough = isInFinalPhase(situation) && situation.minute() >= resultTiming.riskInDrawMinute();
        return (situation.isFavoured() || situation.isDominant()) && isLateEnough;
    }

    private boolean isInFinalPhase(ResultSituation situation) {
        return situation.minute() >= timing.finalPhaseStartMinute();
    }

    private Optional<Response> respond(Direction direction, ChangeReason reason) {
        return respondWithModule(direction, reason, ModuleChange.NONE);
    }

    private Optional<Response> respondWithModule(Direction direction, ChangeReason reason, ModuleChange module) {
        Response response = new Response(direction, reason, module);
        return Optional.of(response);
    }
}
