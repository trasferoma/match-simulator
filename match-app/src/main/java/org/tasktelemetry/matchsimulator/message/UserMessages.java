package org.tasktelemetry.matchsimulator.message;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UserMessages {

    private static final String ITALIAN_RESOURCE = "/message/messages_it.properties";
    private static final String KEY_SEPARATOR = ".";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)}");

    private final Map<String, String> textsByKey;

    public UserMessages(Map<String, String> textsByKey) {
        Objects.requireNonNull(textsByKey, "texts must not be null");
        this.textsByKey = new TreeMap<>(textsByKey);
    }

    public static UserMessages italian() {
        InputStream stream = UserMessages.class.getResourceAsStream(ITALIAN_RESOURCE);
        if (stream == null) {
            throw new IllegalStateException("message file not found in the classpath: " + ITALIAN_RESOURCE);
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return read(reader);
        } catch (IOException exception) {
            throw new IllegalStateException("message file cannot be read: " + ITALIAN_RESOURCE, exception);
        }
    }

    public static UserMessages read(Reader reader) throws IOException {
        Properties properties = new Properties();
        properties.load(reader);
        Map<String, String> textsByKey = new TreeMap<>();
        for (String key : properties.stringPropertyNames()) {
            textsByKey.put(key, properties.getProperty(key));
        }
        return new UserMessages(textsByKey);
    }

    public static String keyOf(Enum<?> item) {
        Objects.requireNonNull(item, "message item must not be null");
        String group = item.getDeclaringClass().getSimpleName();
        return group + KEY_SEPARATOR + item.name();
    }

    public String text(Enum<?> item, Object... arguments) {
        String key = keyOf(item);
        String template = textsByKey.get(key);
        if (template == null) {
            throw new IllegalStateException("no message for key " + key);
        }
        return fillPlaceholders(template, arguments);
    }

    public boolean has(Enum<?> item) {
        String key = keyOf(item);
        return textsByKey.containsKey(key);
    }

    public Set<String> keys() {
        return Collections.unmodifiableSet(textsByKey.keySet());
    }

    private String fillPlaceholders(String template, Object[] arguments) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        return matcher.replaceAll(placeholder -> argumentFor(placeholder, arguments));
    }

    private String argumentFor(MatchResult placeholder, Object[] arguments) {
        int index = Integer.parseInt(placeholder.group(1));
        if (index >= arguments.length) {
            throw new IllegalStateException("the message needs argument " + index + " but got " + arguments.length);
        }
        String argument = String.valueOf(arguments[index]);
        return Matcher.quoteReplacement(argument);
    }
}
