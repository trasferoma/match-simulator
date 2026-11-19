package org.tasktelemetry.matchsimulator.cli.dugout;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BackgroundLineReaderTest {

    private static final int WAIT_SECONDS = 10;

    private final ControlledReader source = new ControlledReader();
    private final BackgroundLineReader liveReader = new BackgroundLineReader(new BufferedReader(source));

    @AfterEach
    void stopTheReadingThread() {
        liveReader.close();
        source.end();
    }

    @Test
    void withoutAnyLineNoPauseIsRequested() {
        liveReader.start();

        assertThat(liveReader.isPauseRequested()).isFalse();
    }

    @Test
    void aLineEvenAnEmptyOneRequestsAPauseWhileTheInputIsOpen() {
        liveReader.start();

        source.supply("\n");
        source.awaitReadRequests(2);

        assertThat(liveReader.isPauseRequested()).isTrue();
    }

    @Test
    void severalLinesBeforeTheOpeningAreOnePauseAndOpeningDiscardsThemAll() {
        liveReader.start();
        source.supply("uno\ndue\ntre\n");
        source.awaitReadRequests(2);
        assertThat(liveReader.isPauseRequested()).isTrue();

        liveReader.discardQueuedLines();

        assertThat(liveReader.isPauseRequested()).isFalse();
    }

    @Test
    void theLinesWrittenAfterTheDiscardAreTheOnesTheScreenReadsInOrder() {
        liveReader.start();
        source.supply("vecchia\n");
        source.awaitReadRequests(2);
        liveReader.discardQueuedLines();

        source.supply("cambio 4 15\nriprendi\n");
        Optional<String> first = liveReader.awaitLine();
        Optional<String> second = liveReader.awaitLine();

        assertThat(first).contains("cambio 4 15");
        assertThat(second).contains("riprendi");
        assertThat(liveReader.isPauseRequested()).isFalse();
    }

    @Test
    void theLineReadByTheScreenIsNoLongerAPauseRequest() {
        liveReader.start();
        source.supply("riprendi\n");

        Optional<String> line = liveReader.awaitLine();
        source.awaitReadRequests(2);

        assertThat(line).contains("riprendi");
        assertThat(liveReader.isPauseRequested()).isFalse();
    }

    @Test
    void theEndOfTheInputIsSignalledToWhoWaitsForALine() {
        liveReader.start();
        source.supply("ultima\n");

        Optional<String> last = liveReader.awaitLine();
        source.end();
        Optional<String> afterTheEnd = liveReader.awaitLine();

        assertThat(last).contains("ultima");
        assertThat(afterTheEnd).isEmpty();
        assertThat(liveReader.awaitLine()).isEmpty();
    }

    @Test
    void closingTheReaderReleasesWhoWaitsAndIgnoresTheLinesThatArriveLater() {
        liveReader.start();

        liveReader.close();
        source.supply("tardiva\n");

        assertThat(liveReader.awaitLine()).isEmpty();
        assertThat(liveReader.isPauseRequested()).isFalse();
    }

    @Test
    void theReadingThreadIsADaemonSoItNeverKeepsTheJvmAlive() {
        liveReader.start();
        source.awaitReadRequests(1);

        List<Thread> readingThreads = Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.getName().equals("dugout-line-reader"))
                .toList();

        assertThat(readingThreads).isNotEmpty().allMatch(Thread::isDaemon);
    }

    @Test
    void afterTheEndOfTheInputNoPauseIsRequestedEvenWithLinesStillQueued() {
        BackgroundLineReader finished = new BackgroundLineReader(new BufferedReader(new StringReader("a\nb\n")));

        finished.pumpLines();

        assertThat(finished.isPauseRequested()).isFalse();
        assertThat(finished.awaitLine()).contains("a");
        assertThat(finished.awaitLine()).contains("b");
        assertThat(finished.awaitLine()).isEmpty();
    }

    @Test
    void anInputThatEndsWithoutLinesIsAnEndOfInputForTheScreen() {
        BackgroundLineReader empty = new BackgroundLineReader(new BufferedReader(new StringReader("")));

        empty.pumpLines();

        assertThat(empty.awaitLine()).isEmpty();
        assertThat(empty.isPauseRequested()).isFalse();
    }

    @Test
    void aReadFailureIsTreatedAsTheEndOfTheInput() {
        Reader failing = new Reader() {
            @Override
            public int read(char[] buffer, int offset, int length) throws IOException {
                throw new IOException("stream closed");
            }

            @Override
            public void close() {
            }
        };
        BackgroundLineReader broken = new BackgroundLineReader(new BufferedReader(failing));

        broken.pumpLines();

        assertThat(broken.awaitLine()).isEmpty();
    }

    @Test
    void theStandardReaderDecodesTheStreamAsUtf8() {
        byte[] bytes = "è già così\n".getBytes(StandardCharsets.UTF_8);

        try (BackgroundLineReader standard = BackgroundLineReader.startedOn(new ByteArrayInputStream(bytes))) {
            assertThat(standard.awaitLine()).contains("è già così");
        }
    }

    private static final class ControlledReader extends Reader {

        private static final String END_OF_INPUT = new String("end of input");

        private final BlockingQueue<String> chunks = new LinkedBlockingQueue<>();
        private final Semaphore readRequests = new Semaphore(0);

        void supply(String text) {
            chunks.add(text);
        }

        void end() {
            chunks.add(END_OF_INPUT);
        }

        void awaitReadRequests(int count) {
            try {
                boolean arrived = readRequests.tryAcquire(count, WAIT_SECONDS, TimeUnit.SECONDS);
                assertThat(arrived).as("the reading thread asked for more input").isTrue();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public int read(char[] buffer, int offset, int length) throws IOException {
            readRequests.release();
            String chunk = nextChunk();
            if (chunk == END_OF_INPUT) {
                return -1;
            }
            chunk.getChars(0, chunk.length(), buffer, offset);
            return chunk.length();
        }

        @Override
        public void close() {
        }

        private String nextChunk() throws IOException {
            try {
                return chunks.take();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted", exception);
            }
        }
    }
}
