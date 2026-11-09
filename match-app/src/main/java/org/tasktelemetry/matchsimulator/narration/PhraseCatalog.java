package org.tasktelemetry.matchsimulator.narration;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

public final class PhraseCatalog {

    private static final String ITALIAN_RESOURCE = "/narration/phrases_it.properties";
    private static final Pattern VARIANT_SEPARATOR = Pattern.compile("\\|");

    private final Map<String, List<String>> variantsByKey;

    public PhraseCatalog(Map<String, List<String>> variantsByKey) {
        Objects.requireNonNull(variantsByKey, "variants must not be null");
        Map<String, List<String>> copy = new TreeMap<>();
        for (Map.Entry<String, List<String>> entry : variantsByKey.entrySet()) {
            List<String> usable = requireUsableVariants(entry.getKey(), entry.getValue());
            copy.put(entry.getKey(), usable);
        }
        this.variantsByKey = copy;
    }

    public static PhraseCatalog italian() {
        InputStream stream = PhraseCatalog.class.getResourceAsStream(ITALIAN_RESOURCE);
        if (stream == null) {
            throw new IllegalStateException("phrase file not found in the classpath: " + ITALIAN_RESOURCE);
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return read(reader);
        } catch (IOException exception) {
            throw new IllegalStateException("phrase file cannot be read: " + ITALIAN_RESOURCE, exception);
        }
    }

    public static PhraseCatalog read(Reader reader) throws IOException {
        Properties properties = new Properties();
        properties.load(reader);
        Map<String, List<String>> variantsByKey = new TreeMap<>();
        for (String key : properties.stringPropertyNames()) {
            String value = properties.getProperty(key);
            List<String> variants = splitVariants(value);
            variantsByKey.put(key, variants);
        }
        return new PhraseCatalog(variantsByKey);
    }

    public List<String> variantsOf(String key) {
        List<String> variants = variantsByKey.get(key);
        if (variants == null) {
            throw new IllegalStateException("no phrase for key " + key);
        }
        return variants;
    }

    public Set<String> keys() {
        return Collections.unmodifiableSet(variantsByKey.keySet());
    }

    private static List<String> splitVariants(String value) {
        String[] variants = VARIANT_SEPARATOR.split(value);
        return Arrays.stream(variants)
                .map(String::trim)
                .toList();
    }

    private static List<String> requireUsableVariants(String key, List<String> variants) {
        if (variants == null || variants.isEmpty()) {
            throw new IllegalArgumentException("phrase key " + key + " needs at least one variant");
        }
        boolean hasBlankVariant = variants.stream().anyMatch(variant -> variant == null || variant.isBlank());
        if (hasBlankVariant) {
            throw new IllegalArgumentException("phrase key " + key + " has a blank variant");
        }
        return List.copyOf(variants);
    }
}
