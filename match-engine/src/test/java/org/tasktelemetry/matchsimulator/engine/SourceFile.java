package org.tasktelemetry.matchsimulator.engine;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

record SourceFile(String path, String code) {

    private static final Pattern NON_CODE = Pattern.compile(
            "\"\"\"[\\s\\S]*?\"\"\""
                    + "|\"(?:\\\\.|[^\"\\\\\\n])*\""
                    + "|'(?:\\\\.|[^'\\\\\\n])+'"
                    + "|//[^\\n]*"
                    + "|/\\*[\\s\\S]*?\\*/");

    static SourceFile fromText(String path, String text) {
        Matcher nonCode = NON_CODE.matcher(text);
        StringBuilder blanked = new StringBuilder();
        while (nonCode.find()) {
            String replacement = " " + nonCode.group().replaceAll("[^\\n]", "");
            nonCode.appendReplacement(blanked, Matcher.quoteReplacement(replacement));
        }
        nonCode.appendTail(blanked);
        return new SourceFile(path.replace('\\', '/'), blanked.toString());
    }

    int lineOf(int offset) {
        long newlinesBefore = code.substring(0, offset).chars()
                .filter(character -> character == '\n')
                .count();
        return (int) newlinesBefore + 1;
    }
}
