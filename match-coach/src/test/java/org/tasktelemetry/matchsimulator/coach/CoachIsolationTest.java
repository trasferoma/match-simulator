package org.tasktelemetry.matchsimulator.coach;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

class CoachIsolationTest {

    private static final String ENGINE_PACKAGE_PREFIX = "org.tasktelemetry.matchsimulator.engine.";
    private static final String COACH_PACKAGE_PREFIX = "org.tasktelemetry.matchsimulator.coach.";
    private static final String ENGINE_ARTIFACT = "match-engine";

    private static final Pattern IMPORT = Pattern.compile(
            "^\\s*import\\s+(?:static\\s+)?([\\w.]+(?:\\.\\*)?)\\s*;", Pattern.MULTILINE);
    private static final Pattern GROUPING_BY = Pattern.compile("\\bCollectors\\s*\\.\\s*groupingBy\\s*\\(");

    private static final List<Prohibition> IO_AND_CONSOLE = List.of(
            Prohibition.of("\\bjava\\s*\\.\\s*io\\b", "java.io is forbidden in the coach"),
            Prohibition.of("\\bjava\\s*\\.\\s*nio\\s*\\.\\s*file\\b", "java.nio.file is forbidden in the coach"),
            Prohibition.of("\\bjava\\s*\\.\\s*net\\b", "java.net is forbidden in the coach"),
            Prohibition.of("\\bSystem\\s*\\.\\s*(?:out|err)\\b", "System.out and System.err are forbidden in the coach"));

    private static final List<Prohibition> RANDOMNESS_SOURCES = List.of(
            Prohibition.of("\\bMath\\s*\\.\\s*random\\b", "Math.random is a source of randomness"),
            Prohibition.of("\\bRandom\\b", "java.util.Random is a source of randomness"),
            Prohibition.of("\\bSecureRandom\\b", "SecureRandom is a source of randomness"),
            Prohibition.of("\\bThreadLocalRandom\\b", "ThreadLocalRandom is a source of randomness"),
            Prohibition.of("\\bSplittableRandom\\b", "SplittableRandom is a source of randomness"),
            Prohibition.of("\\bRandomGenerator\\w*\\b", "no generator of any kind belongs to the coach"),
            Prohibition.of("\\bMatchRandom\\b", "the coach must not use the generator of the match"),
            Prohibition.of("\\bSystem\\s*\\.\\s*(?:currentTimeMillis|nanoTime)\\b", "the clock is not reproducible"),
            Prohibition.of("\\.\\s*now\\s*\\(", "the current date and time are not reproducible"));

    private static final List<Prohibition> UNORDERED_COLLECTIONS = List.of(
            Prohibition.of("\\b(?:HashMap|HashSet|Hashtable|ConcurrentHashMap|IdentityHashMap|WeakHashMap)\\b",
                    "iteration order is not guaranteed"),
            Prohibition.of("\\bMap\\s*\\.\\s*(?:of|ofEntries|copyOf)\\b", "Map factories have no guaranteed order"),
            Prohibition.of("\\bSet\\s*\\.\\s*(?:of|copyOf)\\b", "Set factories have no guaranteed order"),
            Prohibition.of("\\bCollectors\\s*\\.\\s*(?:toMap|toSet|toUnmodifiableMap|toUnmodifiableSet)\\b",
                    "this collector has no guaranteed order"),
            Prohibition.of("import\\s+static\\s+java\\.util\\.stream\\.Collectors\\s*\\.",
                    "use Collectors qualified, so that the ordering guard can see the call"));

    private final CoachSources sources = new CoachSources();

    @Test
    void coachSourcesAreFound() {
        assertThat(sources.mainSources()).isNotEmpty();
    }

    @Test
    void coachImportsOnlyJavaTheEngineAndItsOwnPackages() {
        assertThat(violationsInCoach(this::importViolations)).isEmpty();
    }

    @Test
    void coachUsesNoFilesNetworkOrConsole() {
        assertThat(violationsInCoach(file -> findAll(IO_AND_CONSOLE, file))).isEmpty();
    }

    @Test
    void coachHasNoRandomnessSource() {
        assertThat(violationsInCoach(file -> findAll(RANDOMNESS_SOURCES, file))).isEmpty();
    }

    @Test
    void coachUsesOnlyCollectionsWithGuaranteedOrder() {
        assertThat(violationsInCoach(this::unorderedCollectionViolations)).isEmpty();
    }

    @Test
    void coachModuleDependsOnlyOnTheEngineOutsideTheTestScope() throws Exception {
        assertThat(dependencyViolations(sources.pomText())).isEmpty();
    }

    @Test
    void importGuardAcceptsJavaTheEngineAndTheCoach() {
        SourceFile file = source("""
                import static java.util.Objects.requireNonNull;
                import java.util.*;
                import org.tasktelemetry.matchsimulator.engine.team.Team;
                import org.tasktelemetry.matchsimulator.coach.squad.Squad;
                """);

        assertThat(importViolations(file)).isEmpty();
    }

    @Test
    void importGuardRejectsAnyOtherPackage() {
        SourceFile file = source("""
                import javax.xml.parsers.DocumentBuilder;
                import tools.jackson.databind.ObjectMapper;
                import org.tasktelemetry.matchsimulator.summary.MatchSummary;
                """);

        assertThat(importViolations(file)).hasSize(3);
    }

    @Test
    void ioGuardRejectsFilesNetworkAndConsole() {
        SourceFile file = source("""
                import java.io.File;
                Path p = java.nio.file.Path.of("x");
                java.net.URI u;
                System.out.println("x");
                System.err.println("x");
                """);

        assertThat(findAll(IO_AND_CONSOLE, file)).hasSize(5);
    }

    @Test
    void randomnessGuardRejectsEveryForbiddenSource() {
        SourceFile file = source("""
                double a = Math.random();
                java.util.Random b = new Random(1);
                SecureRandom c;
                ThreadLocalRandom d;
                SplittableRandom e;
                RandomGenerator f = RandomGenerator.getDefault();
                MatchRandom g;
                long h = System.currentTimeMillis();
                long i = System.nanoTime();
                Instant j = Instant.now();
                """);

        assertThat(findAll(RANDOMNESS_SOURCES, file)).hasSize(12);
    }

    @Test
    void guardsIgnoreCommentsAndStrings() {
        SourceFile file = source("""
                // java.io.File, Math.random and HashMap are not allowed here
                String text = "System.out.println(new Random())";
                """);

        assertThat(findAll(IO_AND_CONSOLE, file)).isEmpty();
        assertThat(findAll(RANDOMNESS_SOURCES, file)).isEmpty();
        assertThat(unorderedCollectionViolations(file)).isEmpty();
    }

    @Test
    void orderGuardRejectsUnorderedStructures() {
        SourceFile file = source("""
                Map<String, Integer> a = new HashMap<>();
                Set<String> b = new HashSet<>();
                Map<String, Integer> c = Map.of("a", 1);
                Set<String> d = Set.of("a");
                Map<String, Integer> e = Map.copyOf(a);
                Set<String> f = Set.copyOf(b);
                Collectors.toMap(x, y);
                Collectors.toSet();
                Collectors.groupingBy(Foo::bar);
                """);

        assertThat(unorderedCollectionViolations(file)).hasSize(9);
    }

    @Test
    void orderGuardAcceptsStructuresWithGuaranteedOrder() {
        SourceFile file = source("""
                Map<Role, Integer> a = new EnumMap<>(Role.class);
                Set<Role> b = EnumSet.noneOf(Role.class);
                Map<String, Integer> c = new LinkedHashMap<>();
                Set<String> d = new LinkedHashSet<>();
                List<String> f = List.of("a");
                var g = items.stream().collect(Collectors.groupingBy(Foo::bar, LinkedHashMap::new, Collectors.toList()));
                """);

        assertThat(unorderedCollectionViolations(file)).isEmpty();
    }

    @Test
    void dependencyGuardAcceptsTheEngineAndTestDependenciesOnly() throws Exception {
        String pom = """
                <project>
                  <dependencies>
                    <dependency><groupId>g</groupId><artifactId>match-engine</artifactId></dependency>
                    <dependency><groupId>g</groupId><artifactId>c</artifactId><scope>test</scope></dependency>
                  </dependencies>
                </project>
                """;

        assertThat(dependencyViolations(pom)).isEmpty();
    }

    @Test
    void dependencyGuardRejectsAnyOtherRuntimeDependency() throws Exception {
        String pom = """
                <project>
                  <dependencies>
                    <dependency><groupId>g</groupId><artifactId>a</artifactId></dependency>
                    <dependency><groupId>g</groupId><artifactId>b</artifactId><scope>compile</scope></dependency>
                    <dependency><groupId>g</groupId><artifactId>match-engine</artifactId><scope>runtime</scope></dependency>
                  </dependencies>
                </project>
                """;

        assertThat(dependencyViolations(pom)).hasSize(3);
    }

    private List<String> violationsInCoach(Function<SourceFile, List<String>> rule) {
        return sources.mainSources().stream()
                .flatMap(file -> rule.apply(file).stream())
                .toList();
    }

    private List<String> findAll(List<Prohibition> prohibitions, SourceFile file) {
        return prohibitions.stream()
                .flatMap(prohibition -> prohibition.findIn(file).stream())
                .toList();
    }

    private List<String> importViolations(SourceFile file) {
        List<String> violations = new ArrayList<>();
        Matcher imports = IMPORT.matcher(file.code());
        while (imports.find()) {
            String imported = imports.group(1);
            if (!isAllowedImport(imported)) {
                int line = file.lineOf(imports.start(1));
                violations.add(file.path() + ":" + line
                        + " - import outside java.*, the engine and the coach packages: " + imported);
            }
        }
        return violations;
    }

    private boolean isAllowedImport(String imported) {
        return imported.startsWith("java.")
                || imported.startsWith(ENGINE_PACKAGE_PREFIX)
                || imported.startsWith(COACH_PACKAGE_PREFIX);
    }

    private List<String> unorderedCollectionViolations(SourceFile file) {
        List<String> violations = new ArrayList<>(findAll(UNORDERED_COLLECTIONS, file));
        violations.addAll(groupingByWithoutSupplier(file));
        return violations;
    }

    private List<String> groupingByWithoutSupplier(SourceFile file) {
        List<String> violations = new ArrayList<>();
        Matcher calls = GROUPING_BY.matcher(file.code());
        while (calls.find()) {
            int arguments = argumentCount(file.code(), calls.end());
            if (arguments < 3) {
                int line = file.lineOf(calls.start());
                violations.add(file.path() + ":" + line + " - Collectors.groupingBy needs a map supplier");
            }
        }
        return violations;
    }

    private int argumentCount(String code, int afterOpeningParenthesis) {
        int depth = 1;
        int separators = 0;
        for (int position = afterOpeningParenthesis; position < code.length() && depth > 0; position++) {
            char character = code.charAt(position);
            if (character == '(') {
                depth++;
            } else if (character == ')') {
                depth--;
            } else if (character == ',' && depth == 1) {
                separators++;
            }
        }
        return separators + 1;
    }

    private List<String> dependencyViolations(String pomXml) throws Exception {
        Document pom = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new InputSource(new StringReader(pomXml)));
        XPath xpath = XPathFactory.newInstance().newXPath();
        NodeList dependencies = (NodeList) xpath.evaluate(
                "/project/dependencies/dependency", pom, XPathConstants.NODESET);
        List<String> violations = new ArrayList<>();
        for (int index = 0; index < dependencies.getLength(); index++) {
            String artifact = xpath.evaluate("artifactId", dependencies.item(index)).trim();
            String scope = xpath.evaluate("scope", dependencies.item(index)).trim();
            if (!isAllowedDependency(artifact, scope)) {
                violations.add("dependency " + artifact + " has scope '" + scope
                        + "': only " + ENGINE_ARTIFACT + " with its default scope, or test scope, is allowed");
            }
        }
        return violations;
    }

    private boolean isAllowedDependency(String artifact, String scope) {
        boolean isTestScoped = scope.equals("test");
        boolean isTheEngineInTheDefaultScope = artifact.equals(ENGINE_ARTIFACT) && scope.isEmpty();
        return isTestScoped || isTheEngineInTheDefaultScope;
    }

    private SourceFile source(String text) {
        return SourceFile.fromText("org/tasktelemetry/matchsimulator/coach/Sample.java", text);
    }
}
