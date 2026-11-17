package org.tasktelemetry.matchsimulator.calibration.tactics;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.tasktelemetry.matchsimulator.message.UserMessages;

public record TacticsReport(TacticsConfig config, List<VariantComparison> comparisons) {

    private static final String TITLE_FORMAT = "Confronto di tattiche: %d campioni per variante, forza %d, seme %d";
    private static final String SETUP_NOTE =
            "Squadra in prova contro un avversario equilibrato 4-4-2 della stessa forza, senza interventi";
    private static final String UNCERTAINTY_NOTE = "Differenza appaiata campione per campione dal riferimento; "
            + "errore standard = dev. std. delle differenze / radice di n";

    public TacticsReport {
        Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(comparisons, "comparisons must not be null");
        comparisons = List.copyOf(comparisons);
    }

    public static TacticsReport compare(TacticsConfig config, List<VariantMeasurements> measurements) {
        Objects.requireNonNull(measurements, "measurements must not be null");
        VariantMeasurements reference = measurements.get(0);
        List<VariantComparison> comparisons = measurements.stream()
                .map(measured -> VariantComparison.against(reference, measured))
                .toList();
        return new TacticsReport(config, comparisons);
    }

    public String render(UserMessages messages) {
        Objects.requireNonNull(messages, "messages must not be null");
        StringBuilder text = new StringBuilder();
        appendHeader(text);
        for (TacticMetric metric : TacticMetric.values()) {
            appendBlock(text, metric, messages);
        }
        return text.toString();
    }

    private void appendHeader(StringBuilder text) {
        String title = String.format(Locale.ITALY, TITLE_FORMAT, config.samples(), config.strength(),
                config.baseSeed());
        appendLine(text, title);
        appendLine(text, SETUP_NOTE);
        appendLine(text, UNCERTAINTY_NOTE);
    }

    private void appendBlock(StringBuilder text, TacticMetric metric, UserMessages messages) {
        appendLine(text, "");
        appendLine(text, metric.label());
        String columns = String.format(Locale.ITALY, "  %-36s %9s %11s %12s", "variante", "media", "differenza",
                "errore std.");
        appendLine(text, columns);
        for (int index = 0; index < comparisons.size(); index++) {
            VariantComparison comparison = comparisons.get(index);
            boolean isReference = index == 0;
            String row = renderRow(comparison, metric, isReference, messages);
            appendLine(text, row);
        }
    }

    private String renderRow(VariantComparison comparison, TacticMetric metric, boolean isReference,
            UserMessages messages) {
        String name = nameOf(comparison.variant(), isReference, messages);
        MetricComparison values = comparison.of(metric);
        return String.format(Locale.ITALY, "  %-36s %9.2f %+11.2f %12.2f", name, values.mean(),
                values.meanDifference(), values.differenceStandardError());
    }

    private String nameOf(TacticVariant variant, boolean isReference, UserMessages messages) {
        String style = messages.text(variant.style());
        String formation = variant.formation().label();
        String name = style + " " + formation;
        return isReference ? name + " (riferimento)" : name;
    }

    private void appendLine(StringBuilder text, String line) {
        text.append(line).append(System.lineSeparator());
    }
}
