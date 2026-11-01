package com.ivirma.clinic.config;

import com.ivirma.clinic.ClinicApplication;
import com.ivirma.clinic.testsupport.TestSecrets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * STORY-009 (ADR-003): the database password is supplied at runtime through the {@code DB_PASSWORD}
 * environment variable and is never committed.
 *
 * <p>The start-up cases boot the real {@link ClinicApplication} with an environment that holds only the
 * variables each case supplies (the machine's own environment is left out, so a {@code DB_PASSWORD} set on a
 * developer machine cannot change the result) and a JDBC URL nobody listens on, so no database is needed:
 * start-up either stops at the password check or gets as far as opening a connection. The embedded web server
 * is not started: the datasource is created the same way without it, and the test stays fast.
 *
 * <p>Assertions are written as booleans with a description, so a failing run never prints a password value
 * or the captured log. The supplied value comes from {@link TestSecrets} (random per run).
 */
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("STORY-009 database password supplied at runtime, never committed")
class RuntimeDatabasePasswordStory009Test {

    /** Port 1 on the loopback interface: refused at once, so a connection attempt fails fast. */
    private static final String UNREACHABLE_DB = "jdbc:postgresql://127.0.0.1:1/ivirma_clinic";

    private static final String ENV_REFERENCE = "${DB_PASSWORD}";

    /** Key fragments of database connection settings; demo-login passwords are STORY-018 and not matched. */
    private static final List<String> DB_SETTING_PREFIXES = List.of("datasource", "flyway", "liquibase", "r2dbc");

    private static final Pattern ENV_VARIABLE_NAME = Pattern.compile("[A-Z][A-Z0-9_]*");

    @Test
    @DisplayName("STORY-009 application.properties takes the datasource password from DB_PASSWORD, with no default")
    void STORY009_datasourcePasswordComesFromDbPasswordWithoutDefault() throws IOException {
        Properties properties = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));

        assertThat(ENV_REFERENCE.equals(properties.getProperty("spring.datasource.password")))
                .as("spring.datasource.password must be exactly " + ENV_REFERENCE + " (value not shown)")
                .isTrue();
    }

    @Test
    @DisplayName("STORY-009 no backend config file assigns a literal to a database password setting")
    void STORY009_noBackendConfigFileHoldsADatabasePasswordLiteral() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src"))) {
            for (Path file : files.filter(Files::isRegularFile).filter(RuntimeDatabasePasswordStory009Test::isConfigFile).toList()) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i).trim();
                    int separator = indexOfSeparator(line);
                    if (line.startsWith("#") || separator < 0) {
                        continue;
                    }
                    String key = line.substring(0, separator).trim();
                    if (isDatabasePasswordKey(key) && !isRuntimeSupplied(line.substring(separator + 1))) {
                        offenders.add(file + ":" + (i + 1) + " " + key); // key only, never the value
                    }
                }
            }
        }

        assertThat(offenders)
                .as("database password settings must be empty or an environment reference such as " + ENV_REFERENCE)
                .isEmpty();
    }

    @ParameterizedTest(name = "STORY-009 DB_PASSWORD {0}: start-up stops with a clear configuration error")
    @ValueSource(strings = {"missing", "empty"})
    void STORY009_backendFailsFastWithoutDbPassword(String variant, CapturedOutput output) {
        Map<String, Object> environment = new HashMap<>();
        if ("empty".equals(variant)) {
            environment.put("DB_PASSWORD", "");
        }

        Throwable failure = catchThrowable(() -> start(environment, new AtomicReference<>()));

        assertThat(failure).as("start-up must fail").isNotNull();
        assertThat(hasCause(failure, SQLException.class))
                .as("start-up must stop before any database connection is attempted")
                .isFalse();
        String report = output.getAll();
        assertThat(report.contains("APPLICATION FAILED TO START")).as("Spring Boot prints its failure report").isTrue();
        assertThat(report.contains("DB_PASSWORD")).as("the report names the DB_PASSWORD environment variable").isTrue();
        assertThat(report.contains(".env.example")).as("the report says how to supply it for docker compose").isTrue();
        assertThat(report.contains("ivirma-db-password")).as("the report names the Jenkins credential").isTrue();
    }

    @Test
    @DisplayName("STORY-009 the datasource uses the DB_PASSWORD value from the environment and never prints it")
    void STORY009_backendUsesDbPasswordFromTheEnvironmentAndNeverPrintsIt(CapturedOutput output) {
        String supplied = TestSecrets.password("DB_PASSWORD");
        AtomicReference<String> bound = new AtomicReference<>();

        Throwable failure = catchThrowable(() -> start(Map.of("DB_PASSWORD", supplied), bound));

        assertThat(supplied.equals(bound.get()))
                .as("spring.datasource.password is bound to the DB_PASSWORD value (values not shown)")
                .isTrue();
        assertThat(failure).as("start-up fails: nothing listens on " + UNREACHABLE_DB).isNotNull();
        assertThat(hasCause(failure, SQLException.class))
                .as("start-up passed the password check and got as far as connecting to the database")
                .isTrue();
        assertThat(output.getAll().contains(supplied) || stackTrace(failure).contains(supplied))
                .as("the password appears neither in the log nor in the start-up exception")
                .isFalse();
    }

    /** Runs the application with exactly the given environment variables; records the bound datasource password. */
    private static void start(Map<String, Object> environmentVariables, AtomicReference<String> boundPassword) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        environmentVariables));

        SpringApplication application = new SpringApplication(ClinicApplication.class);
        application.setMainApplicationClass(ClinicApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setEnvironment(environment);
        application.setRegisterShutdownHook(false);
        application.setBannerMode(Banner.Mode.OFF);
        application.addInitializers(context -> context.getBeanFactory().addBeanPostProcessor(new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof DataSourceProperties properties) {
                    boundPassword.set(properties.getPassword());
                }
                return bean;
            }
        }));
        application.run("--spring.datasource.url=" + UNREACHABLE_DB, "--clinic.seed-demo-data=false");
    }

    private static boolean isConfigFile(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".properties") || name.endsWith(".yml") || name.endsWith(".yaml")
                || name.startsWith(".env") || name.endsWith(".env") || name.endsWith(".conf") || name.endsWith(".toml");
    }

    /** Position of the first '=' or ':' (properties, YAML and .env syntax), or -1. */
    private static int indexOfSeparator(String line) {
        int equals = line.indexOf('=');
        int colon = line.indexOf(':');
        if (equals < 0 || colon < 0) {
            return Math.max(equals, colon);
        }
        return Math.min(equals, colon);
    }

    /** spring.datasource.password, spring.flyway.password, ..., POSTGRES_PASSWORD, DB_PASSWORD, TEST_DB_PASSWORD. */
    private static boolean isDatabasePasswordKey(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        boolean connectionSetting = lower.contains("password") && DB_SETTING_PREFIXES.stream().anyMatch(lower::contains);
        return connectionSetting || lower.endsWith("postgres_password") || lower.endsWith("db_password");
    }

    /** Empty, or exactly one environment reference without a default, e.g. ${DB_PASSWORD} (quotes allowed). */
    private static boolean isRuntimeSupplied(String rawValue) {
        String value = rawValue.trim();
        boolean quoted = (value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"));
        if (value.length() >= 2 && quoted) {
            value = value.substring(1, value.length() - 1);
        }
        boolean reference = value.startsWith("${") && value.endsWith("}")
                && ENV_VARIABLE_NAME.matcher(value.substring(2, value.length() - 1)).matches();
        return value.isEmpty() || reference;
    }

    private static boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
        for (Throwable t = failure; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }

    private static String stackTrace(Throwable failure) {
        if (failure == null) {
            return "";
        }
        StringWriter text = new StringWriter();
        failure.printStackTrace(new PrintWriter(text));
        return text.toString();
    }
}
