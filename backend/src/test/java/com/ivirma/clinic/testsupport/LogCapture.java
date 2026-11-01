package com.ivirma.clinic.testsupport;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * STORY-024 (SEC-007, TASK-072): records every log line the test thread writes, from any logger, so a test
 * can prove that log lines carry record ids and actions only. Register it with {@code @RegisterExtension}.
 *
 * <p>The recording appender sits on the root logger. The watched class's logger is opened to every level for
 * the duration of the test, so a DEBUG or TRACE statement that carries personal data is caught as well,
 * whatever the logging configuration of the JVM. The calling statement is resolved when the line is
 * appended, so a failure names it (class, method, file and line).
 */
public final class LogCapture implements BeforeEachCallback, AfterEachCallback {

    private final Class<?> watchedClass;
    private final ListAppender<ILoggingEvent> appender = new CallerRecordingAppender();
    private Logger rootLogger;
    private Logger watchedLogger;
    private Level previousLevel;
    private String testThread;

    private LogCapture(Class<?> watchedClass) {
        this.watchedClass = watchedClass;
    }

    /** Captures every logger; the statements of {@code watchedClass} are enabled at every level. */
    public static LogCapture watching(Class<?> watchedClass) {
        return new LogCapture(watchedClass);
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        testThread = Thread.currentThread().getName();
        rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        watchedLogger = (Logger) LoggerFactory.getLogger(watchedClass);
        previousLevel = watchedLogger.getLevel();
        watchedLogger.setLevel(Level.TRACE);
        appender.list.clear();
        appender.start();
        rootLogger.addAppender(appender);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        rootLogger.detachAppender(appender);
        appender.stop();
        watchedLogger.setLevel(previousLevel);
    }

    /** Every line the test thread logged, oldest first. */
    public List<Line> lines() {
        List<Line> result = new ArrayList<>();
        for (ILoggingEvent event : List.copyOf(appender.list)) {
            if (testThread.equals(event.getThreadName())) {
                result.add(Line.of(event));
            }
        }
        return result;
    }

    /** The action stays visible: for each value, some INFO line of the watched class shows it as a whole number. */
    public void assertInfoLineShows(long... values) {
        List<Line> info = lines().stream()
                .filter(line -> line.logger().equals(watchedClass.getName()) && line.level().isGreaterOrEqual(Level.INFO))
                .toList();
        for (long value : values) {
            Pattern token = Pattern.compile("\\b" + value + "\\b");
            assertThat(info)
                    .as("STORY-024: an INFO line of %s must show %d", watchedClass.getSimpleName(), value)
                    .anyMatch(line -> token.matcher(line.text()).find());
        }
    }

    /**
     * SEC-007: no line, at any level and from any logger, carries one of the values (compared ignoring case).
     * A failure names the label of the value and the statement that wrote it.
     */
    public void assertNoLineCarries(Map<String, ?> valuesByLabel) {
        List<Line> lines = lines();
        assertThat(lines).as("STORY-024: nothing was logged, so nothing was checked").isNotEmpty();
        valuesByLabel.forEach((label, value) -> {
            String needle = String.valueOf(value).toLowerCase(Locale.ROOT);
            assertThat(lines)
                    .as("STORY-024 SEC-007: a log statement wrote the %s", label)
                    .noneMatch(line -> line.text().toLowerCase(Locale.ROOT).contains(needle));
        });
    }

    /** CWE-117: no line contains CR or LF, so request input cannot start a forged log line. */
    public void assertNoLineBreaks() {
        List<Line> lines = lines();
        assertThat(lines).as("STORY-024: nothing was logged, so nothing was checked").isNotEmpty();
        assertThat(lines)
                .as("STORY-024 CWE-117: a log statement wrote a line break")
                .noneMatch(line -> line.text().indexOf('\n') >= 0 || line.text().indexOf('\r') >= 0);
    }

    /** One captured line: the statement that wrote it, its logger and level, and the text as it is printed. */
    public record Line(String statement, String logger, Level level, String text) {

        static Line of(ILoggingEvent event) {
            StackTraceElement[] callers = event.getCallerData();
            String statement = callers == null || callers.length == 0 ? "<unknown statement>" : callers[0].toString();
            StringBuilder text = new StringBuilder(event.getFormattedMessage());
            for (IThrowableProxy cause = event.getThrowableProxy(); cause != null; cause = cause.getCause()) {
                text.append(" | ").append(cause.getClassName()).append(": ").append(cause.getMessage());
            }
            return new Line(statement, event.getLoggerName(), event.getLevel(), text.toString());
        }

        @Override
        public String toString() {
            return level + " at " + statement + ": " + text;
        }
    }

    /** Resolves the calling statement while the logging call is still on the stack. */
    private static final class CallerRecordingAppender extends ListAppender<ILoggingEvent> {
        @Override
        protected void append(ILoggingEvent event) {
            event.getCallerData();
            super.append(event);
        }
    }
}
