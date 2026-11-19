package org.tasktelemetry.matchsimulator.cli.dugout;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.tasktelemetry.matchsimulator.coach.human.PlannedMoves;
import org.tasktelemetry.matchsimulator.coach.human.SheetPlayer;
import org.tasktelemetry.matchsimulator.coach.human.TeamSheet;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.Deployment;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.message.UserMessages;

final class DugoutScreen {

    private static final int NUMBER_WIDTH = 2;
    private static final int ATTRIBUTE_WIDTH = 3;
    private static final String SPACE = " ";
    private static final String NO_TEXT = "";
    private static final String LIST_SEPARATOR = ", ";

    private final UserMessages messages;
    private final MoveWording wording;
    private final String coachName;

    DugoutScreen(UserMessages messages, MoveWording wording, String coachName) {
        this.messages = messages;
        this.wording = wording;
        this.coachName = coachName;
    }

    List<String> sheetLines(TeamSheet sheet, PlannedMoves moves) {
        List<SheetPlayer> players = sheet.players();
        RosterWidths widths = measureRoster(players);
        List<String> header = describeHeader(sheet, moves);
        List<String> onPitch = describeOnPitch(players, widths);
        List<String> onBench = describeOnBench(players, widths);
        List<String> unavailable = describeUnavailable(players, widths);
        List<String> plannedMoves = describePlannedMoves(moves);
        return Stream.of(header, onPitch, onBench, unavailable, plannedMoves)
                .flatMap(List::stream)
                .toList();
    }

    List<String> helpLines() {
        String title = messages.text(DugoutLabel.HELP_TITLE);
        String substitution = messages.text(DugoutLabel.HELP_SUBSTITUTION, DugoutKeyword.SUBSTITUTION.word());
        String formation = messages.text(DugoutLabel.HELP_FORMATION, DugoutKeyword.FORMATION.word());
        String allowedStyles = StyleWord.allWords();
        String style = messages.text(DugoutLabel.HELP_STYLE, DugoutKeyword.STYLE.word(), allowedStyles);
        String undo = messages.text(DugoutLabel.HELP_UNDO, DugoutKeyword.UNDO.word());
        String help = messages.text(DugoutLabel.HELP_HELP, DugoutKeyword.HELP.word());
        String redraw = messages.text(DugoutLabel.HELP_REDRAW);
        String resume = messages.text(DugoutLabel.HELP_RESUME, DugoutKeyword.RESUME.word());
        return List.of(title, substitution, formation, style, undo, help, redraw, resume);
    }

    String formationLabels(List<Deployment> formations) {
        return formations.stream()
                .map(Deployment::label)
                .collect(Collectors.joining(LIST_SEPARATOR));
    }

    String promptLine() {
        return messages.text(DugoutLabel.PROMPT);
    }

    String problemLine(InvalidDugoutCommandException exception) {
        DugoutProblem problem = exception.problem();
        return messages.text(problem, exception.arguments());
    }

    String confirmAgainLine() {
        String yes = DugoutAnswer.YES.word();
        String no = DugoutAnswer.NO.word();
        return messages.text(DugoutLabel.CONFIRM_AGAIN, yes, no);
    }

    String declinedLine() {
        return messages.text(DugoutLabel.DECLINED);
    }

    String undoneLine() {
        return messages.text(DugoutLabel.UNDONE);
    }

    private List<String> describeHeader(TeamSheet sheet, PlannedMoves moves) {
        String title = describeTitle(sheet);
        String styleAndFormation = describeStyleAndFormation(sheet);
        String availableFormations = describeAvailableFormations(moves);
        return List.of(title, styleAndFormation, availableFormations);
    }

    private String describeTitle(TeamSheet sheet) {
        String minute = sheet.time().display();
        String score = describeScore(sheet);
        return messages.text(DugoutLabel.HEADER, sheet.teamName(), coachName, minute, score);
    }

    private String describeScore(TeamSheet sheet) {
        String ownName = sheet.teamName();
        String opponentName = sheet.opponentName();
        int ownGoals = sheet.ownGoals();
        int opponentGoals = sheet.opponentGoals();
        if (sheet.side() == MatchSide.HOME) {
            return messages.text(DugoutLabel.SCORE, ownName, ownGoals, opponentGoals, opponentName);
        }
        return messages.text(DugoutLabel.SCORE, opponentName, opponentGoals, ownGoals, ownName);
    }

    private String describeStyleAndFormation(TeamSheet sheet) {
        String styleText = messages.text(sheet.style());
        Deployment deployment = sheet.deployment();
        String formation = deployment.label();
        int substitutionsMade = sheet.substitutionsMade();
        int maximumSubstitutions = sheet.maximumSubstitutions();
        return messages.text(DugoutLabel.STYLE_AND_FORMATION, styleText, formation, substitutionsMade,
                maximumSubstitutions);
    }

    private String describeAvailableFormations(PlannedMoves moves) {
        List<Deployment> available = moves.availableFormations();
        String labels = formationLabels(available);
        return messages.text(DugoutLabel.AVAILABLE_FORMATIONS, labels);
    }

    private List<String> describeOnPitch(List<SheetPlayer> players, RosterWidths widths) {
        List<String> rows = players.stream()
                .filter(SheetPlayer::isOnPitch)
                .map(player -> describePitchRow(player, widths))
                .toList();
        return describeSection(DugoutLabel.ON_PITCH_TITLE, rows);
    }

    private List<String> describeOnBench(List<SheetPlayer> players, RosterWidths widths) {
        List<String> rows = players.stream()
                .filter(SheetPlayer::isAvailableOnBench)
                .map(player -> describeBenchRow(player, widths))
                .toList();
        return describeSection(DugoutLabel.ON_BENCH_TITLE, rows);
    }

    private List<String> describeUnavailable(List<SheetPlayer> players, RosterWidths widths) {
        List<String> rows = players.stream()
                .filter(player -> player.status().isUnavailable())
                .map(player -> describeUnavailableRow(player, widths))
                .toList();
        return describeSection(DugoutLabel.UNAVAILABLE_TITLE, rows);
    }

    private List<String> describeSection(DugoutLabel title, List<String> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        String heading = messages.text(title);
        List<String> headingRow = List.of(heading);
        return Stream.concat(headingRow.stream(), rows.stream()).toList();
    }

    private String describePitchRow(SheetPlayer player, RosterWidths widths) {
        String number = describeNumber(player);
        String name = padRight(player.player().name(), widths.name());
        String positionText = describePosition(player);
        String position = padRight(positionText, widths.position());
        String attributes = describeAttributes(player.player().attributes());
        String fatigue = messages.text(player.fatigue());
        String cautioned = describeCautioned(player);
        String row = messages.text(DugoutLabel.PITCH_ROW, number, name, position, attributes, fatigue, cautioned);
        return row.stripTrailing();
    }

    private String describeBenchRow(SheetPlayer player, RosterWidths widths) {
        String number = describeNumber(player);
        String name = padRight(player.player().name(), widths.name());
        String roleText = describeBaseRole(player.player().role());
        String role = padRight(roleText, widths.role());
        String attributes = describeAttributes(player.player().attributes());
        return messages.text(DugoutLabel.BENCH_ROW, number, name, role, attributes);
    }

    private String describeUnavailableRow(SheetPlayer player, RosterWidths widths) {
        String number = describeNumber(player);
        String name = padRight(player.player().name(), widths.name());
        String reason = messages.text(player.status());
        return messages.text(DugoutLabel.UNAVAILABLE_ROW, number, name, reason);
    }

    private String describeNumber(SheetPlayer player) {
        String number = String.valueOf(player.number());
        return padLeft(number, NUMBER_WIDTH);
    }

    private String describePosition(SheetPlayer player) {
        String sectorText = messages.text(player.sector());
        String baseRole = describeBaseRole(player.player().role());
        if (player.isOutOfPosition()) {
            return messages.text(DugoutLabel.POSITION_OUT_OF_ROLE, sectorText, baseRole);
        }
        return messages.text(DugoutLabel.POSITION, sectorText, baseRole);
    }

    private String describeCautioned(SheetPlayer player) {
        if (player.cautioned()) {
            return messages.text(DugoutLabel.CAUTIONED);
        }
        return NO_TEXT;
    }

    private String describeBaseRole(Role role) {
        DugoutLabel label = switch (role) {
            case GOALKEEPER -> DugoutLabel.ROLE_GOALKEEPER;
            case DEFENDER -> DugoutLabel.ROLE_DEFENDER;
            case MIDFIELDER -> DugoutLabel.ROLE_MIDFIELDER;
            case FORWARD -> DugoutLabel.ROLE_FORWARD;
        };
        return messages.text(label);
    }

    private String describeAttributes(PlayerAttributes attributes) {
        Object[] paddedValues = IntStream.of(attributes.passing(), attributes.dribbling(), attributes.shooting(),
                        attributes.tackling(), attributes.positioning(), attributes.pace(), attributes.aerial(),
                        attributes.goalkeeping(), attributes.stamina())
                .mapToObj(this::describeAttributeValue)
                .toArray();
        return messages.text(DugoutLabel.ATTRIBUTES, paddedValues);
    }

    private String describeAttributeValue(int value) {
        String text = String.valueOf(value);
        return padLeft(text, ATTRIBUTE_WIDTH);
    }

    private List<String> describePlannedMoves(PlannedMoves moves) {
        List<Intervention> accepted = moves.accepted();
        if (accepted.isEmpty()) {
            String none = messages.text(DugoutLabel.NO_PLANNED_MOVES);
            return List.of(none);
        }
        String title = messages.text(DugoutLabel.PLANNED_MOVES_TITLE);
        List<String> rows = IntStream.range(0, accepted.size())
                .mapToObj(index -> describePlannedMove(index + 1, accepted.get(index)))
                .toList();
        String resultingLineup = describeResultingLineup(moves);
        List<String> titleRow = List.of(title);
        List<String> resultingLineupRow = List.of(resultingLineup);
        return Stream.of(titleRow, rows, resultingLineupRow)
                .flatMap(List::stream)
                .toList();
    }

    private String describePlannedMove(int position, Intervention move) {
        String description = wording.describe(move);
        return messages.text(DugoutLabel.PLANNED_MOVE_ROW, position, description);
    }

    private String describeResultingLineup(PlannedMoves moves) {
        String styleText = messages.text(moves.resultingStyle());
        Deployment deployment = moves.resultingDeployment();
        String formation = deployment.label();
        return messages.text(DugoutLabel.RESULTING_LINEUP, styleText, formation);
    }

    private RosterWidths measureRoster(List<SheetPlayer> players) {
        List<SheetPlayer> playersOnPitch = players.stream()
                .filter(SheetPlayer::isOnPitch)
                .toList();
        int name = findWidest(players, player -> player.player().name());
        int position = findWidest(playersOnPitch, this::describePosition);
        int role = findWidest(players, player -> describeBaseRole(player.player().role()));
        return new RosterWidths(name, position, role);
    }

    private int findWidest(List<SheetPlayer> players, Function<SheetPlayer, String> text) {
        return players.stream()
                .map(text)
                .mapToInt(String::length)
                .max()
                .orElse(0);
    }

    private record RosterWidths(int name, int position, int role) {
    }

    private String padRight(String text, int width) {
        int missing = Math.max(0, width - text.length());
        return text + SPACE.repeat(missing);
    }

    private String padLeft(String text, int width) {
        int missing = Math.max(0, width - text.length());
        return SPACE.repeat(missing) + text;
    }
}
