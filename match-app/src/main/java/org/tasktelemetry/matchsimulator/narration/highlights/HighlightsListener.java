package org.tasktelemetry.matchsimulator.narration.highlights;

public interface HighlightsListener {

    void segmentNarrated(NarratedSegment segment);

    void chronicleFinished();
}
