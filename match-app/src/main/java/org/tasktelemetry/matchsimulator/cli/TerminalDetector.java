package org.tasktelemetry.matchsimulator.cli;

import java.io.Console;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

final class TerminalDetector {

    private static final String IS_TERMINAL_METHOD = "isTerminal";

    boolean isInteractiveTerminal() {
        Console console = System.console();
        if (console == null) {
            return false;
        }
        return consoleReportsATerminal(console);
    }

    Charset consoleCharset() {
        Console console = System.console();
        if (console == null) {
            return StandardCharsets.UTF_8;
        }
        return console.charset();
    }

    private boolean consoleReportsATerminal(Console console) {
        Method isTerminal;
        try {
            isTerminal = Console.class.getMethod(IS_TERMINAL_METHOD);
        } catch (NoSuchMethodException exception) {
            return true;
        }
        try {
            return (boolean) isTerminal.invoke(console);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            return false;
        }
    }
}
