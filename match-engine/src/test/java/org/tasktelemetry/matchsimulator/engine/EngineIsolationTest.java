package org.tasktelemetry.matchsimulator.engine;

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

class EngineIsolationTest {

    private static final String ENGINE_PACKAGE_PREFIX = "org.tasktelemetry.matchsimulator.engine.";
    private static final String RANDOM_FACTORY_FILE = "/MatchRandom.java";

    private static final Pattern IMPORT = Pattern.compile(
            "^\\s*import\\s+(?:static\\s+)?([\\w.]+(?:\\.\\*)?)\\s*;", Pattern.MULTILINE);
    private static final Pattern GROUPING_BY = Pattern.compile("\\bCollectors\\s*\\.\\s*groupingBy\\s*\\(");

    private static final List<Prohibition> IO_AND_CONSOLE = List.of(
            Prohibition.of("\\bjava\\s*\\.\\s*io\\b", "java.io is forbidden in the engine"),
            Prohibition.of("\\bjava\\s*\\.\\s*nio\\s*\\.\\s*file\\b", "java.nio.file is forbidden in the engine"),
            Prohibition.of("\\bjava\\s*\\.\\s*net\\b", "java.net is forbidden in the engine"),
            Prohibition.of("\\bSystem\\s*\\.\\s*(?:out|err)\\b", "System.out and System.err are forbidden in the engine"));

    private static final List<Prohibition> RANDOMNESS_SOURCES = List.of(
            Prohibition.of("\\bMath\\s*\\.\\s*random\\b", "Math.random is not seeded"),
            Prohibition.of("\\bRandom\\b", "java.util.Random has no specified algorithm"),
            Prohibition.of("\\bSecureRandom\\b", "SecureRandom cannot be seeded"),
            Prohibition.of("\\bThreadLocalRandom\\b", "ThreadLocalRandom cannot be seeded"),
            Prohibition.of("\\bSplittableRandom\\b", "SplittableRandom has no specified algorithm"),
            Prohibition.of("\\bRandomGenerator\\s*\\.\\s*getDefault\\b", "the default generator is not reproducible"));

    private static final List<Prohibition> GENERATOR_FACTORY = List.of(
            Prohibition.of("\\bRandomGeneratorFactory\\b", "the generator factory belongs only to MatchRandom"),
            Prohibition.of("\\bRandomGenerator\\s*\\.\\s*of\\b", "the generator factory belongs only to MatchRandom"));

    private static final List<Prohibition> UNORDERED_COLLECTIONS = List.of(
            Prohibition.of("\\b(?:HashMap|HashSet|Hashtable|ConcurrentHashMap|IdentityHashMap|WeakHashMap)\\b",
                    "iteration order is not guaranteed"),
            Prohibition.of("\\bMap\\s*\\.\\s*(?:of|ofEntries|copyOf)\\b", "Map factories have no guaranteed order"),
            Prohibition.of("\\bSet\\s*\\.\\s*(?:of|copyOf)\\b", "Set factories have no guaranteed order"),
            Prohibition.of("\\bCollectors\\s*\\.\\s*(?:toMap|toSet|toUnmodifiableMap|toUnmodifiableSet)\\b",
                    "this collector has no guaranteed order"),
            Prohibition.of("import\\s+static\\s+java\\.util\\.stream\\.Collectors\\s*\\.",
                    "use Collectors qualified, so that the ordering guard can see the call"));

    private final EngineSources sources = new EngineSources();

    @Test
    void engineSourcesAreFound() {
        assertThat(sources.mainSources()).isNotEmpty();
    }

    @Test
    void engineImportsOnlyJavaAndItsOwnPackages() {
        assertThat(violationsInEngine(this::importViolations)).isEmpty();
    }

    @Test
    void engineUsesNoFilesNetworkOrConsole() {
        assertThat(violationsInEngine(file -> findAll(IO_AND_CONSOLE, file))).isEmpty();
    }

    @Test
    void engineModuleHasOnlyTestScopedDependencies() throws Exception {
        assertThat(dependencyViolations(sources.pomText())).isEmpty();
    }

    @Test
    void engineHasNoRandomnessSourceOtherThanTheSeededGenerator() {
        assertThat(violationsInEngine(file -> findAll(RANDOMNESS_SOURCES, file))).isEmpty();
    }

    @Test
    void generatorFactoryAppearsOnlyInMatchRandom() {
        assertThat(violationsInEngine(this::generatorFactoryViolations)).isEmpty();
    }

    @Test
    void engineUsesOnlyCollectionsWithGuaranteedOrder() {
        assertThat(violationsInEngine(this::unorderedCollectionViolations)).isEmpty();
    }

    @Test
    void importGuardAcceptsJavaAndEnginePackages() {
        SourceFile file = source("""
                import static java.util.Objects.requireNonNull;
                import java.util.random.RandomGenerator;
                import java.util.*;
                import org.tasktelemetry.matchsimulator.engine.team.Team;
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
    void ioGuardIgnoresCommentsAndStrings() {
        SourceFile file = source("""
                // java.io.File and System.out are not allowed here
                String text = "System.out.println(java.net.URI)";
                """);

        assertThat(findAll(IO_AND_CONSOLE, file)).isEmpty();
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
                """);

        assertThat(findAll(RANDOMNESS_SOURCES, file)).hasSize(7);
    }

    @Test
    void randomnessGuardAcceptsTheSeededGeneratorTypes() {
        SourceFile file = source("""
                RandomGenerator generator = matchRandom.generator();
                MatchRandom random = new MatchRandom(42L);
                """);

        assertThat(findAll(RANDOMNESS_SOURCES, file)).isEmpty();
    }

    @Test
    void generatorFactoryGuardRejectsOtherFilesAndAcceptsMatchRandom() {
        String text = "RandomGenerator g = RandomGeneratorFactory.of(\"L64X128MixRandom\").create(1L);";
        SourceFile elsewhere = SourceFile.fromText("org/x/engine/Other.java", text);
        SourceFile matchRandom = SourceFile.fromText("org/x/engine/MatchRandom.java", text);

        assertThat(generatorFactoryViolations(elsewhere)).hasSize(1);
        assertThat(generatorFactoryViolations(matchRandom)).isEmpty();
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
                Map<String, Integer> e = new TreeMap<>();
                List<String> f = List.of("a");
                var g = items.stream().collect(Collectors.groupingBy(Foo::bar, LinkedHashMap::new, Collectors.toList()));
                """);

        assertThat(unorderedCollectionViolations(file)).isEmpty();
    }

    @Test
    void dependencyGuardRejectsRuntimeDependencies() throws Exception {
        String pom = """
                <project>
                  <dependencies>
                    <dependency><groupId>g</groupId><artifactId>a</artifactId></dependency>
                    <dependency><groupId>g</groupId><artifactId>b</artifactId><scope>compile</scope></dependency>
                    <dependency><groupId>g</groupId><artifactId>c</artifactId><scope>test</scope></dependency>
                  </dependencies>
                </project>
                """;

        assertThat(dependencyViolations(pom)).hasSize(2);
    }

    @Test
    void dependencyGuardAcceptsAModuleWithoutDependencies() throws Exception {
        assertThat(dependencyViolations("<project></project>")).isEmpty();
    }

    private List<String> violationsInEngine(Function<SourceFile, List<String>> rule) {
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
                violations.add(file.path() + ":" + line + " - import outside java.* and the engine packages: " + imported);
            }
        }
        return violations;
    }

    private boolean isAllowedImport(String imported) {
        return imported.startsWith("java.") || imported.startsWith(ENGINE_PACKAGE_PREFIX);
    }

    private List<String> generatorFactoryViolations(SourceFile file) {
        if (file.path().endsWith(RANDOM_FACTORY_FILE)) {
            return List.of();
        }
        return findAll(GENERATOR_FACTORY, file);
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
            if (!scope.equals("test")) {
                violations.add("dependency " + artifact + " has scope '" + scope + "': only test is allowed");
            }
        }
        return violations;
    }

    private SourceFile source(String text) {
        return SourceFile.fromText("org/tasktelemetry/matchsimulator/engine/Sample.java", text);
    }
}
