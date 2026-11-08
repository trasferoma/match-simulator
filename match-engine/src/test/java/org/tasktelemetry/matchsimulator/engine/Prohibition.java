package org.tasktelemetry.matchsimulator.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

record Prohibition(Pattern pattern, String reason) {

    static Prohibition of(String regex, String reason) {
        return new Prohibition(Pattern.compile(regex), reason);
    }

    List<String> findIn(SourceFile file) {
        List<String> violations = new ArrayList<>();
        Matcher matcher = pattern.matcher(file.code());
        while (matcher.find()) {
            int line = file.lineOf(matcher.start());
            violations.add(file.path() + ":" + line + " - " + reason + " (" + matcher.group().trim() + ")");
        }
        return violations;
    }
}
