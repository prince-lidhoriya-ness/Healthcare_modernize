package com.ivirma.clinic;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * STORY-003: the schema the application runs on is the one Flyway built (V1 baseline and later migrations), and
 * nothing else may change it.
 *
 * <ul>
 *   <li><b>TASK-034</b> (risk R2): every value of every {@code @Enumerated(STRING)} Java enum must be allowed by the
 *   CHECK constraint on its column. {@code ddl-auto=validate} does not look at CHECKs, so without this test a new
 *   enum value (e.g. a VisitType, RULE-001, or an AppointmentStatus, RULE-033) would only fail at runtime, on the
 *   first INSERT. Rule: <i>every enum change ships a migration</i>, which replaces the CHECK by its real name:
 *   {@code ALTER TABLE t DROP CONSTRAINT <name>; ALTER TABLE t ADD CONSTRAINT <name> CHECK (...) NOT VALID;
 *   ALTER TABLE t VALIDATE CONSTRAINT <name>;} (widen one release before the app writes the value).</li>
 *   <li><b>TASK-035</b>: Hibernate only validates ({@code ddl-auto=validate}); the schema comes from Flyway.</li>
 * </ul>
 *
 * <p>The enum columns are discovered from the {@code @Entity} classes, so a new enum column is covered without
 * editing this test. Runs only when TEST_DB_URL is set (same variables as {@link PersistenceIntegrationTest}).
 */
@SpringBootTest(properties = "clinic.seed-demo-data=false")
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@DisplayName("STORY-003 schema migrations (real DB): enum/CHECK drift guard, validate-only Hibernate, Flyway V1")
class SchemaMigrationIntegrationTest {

    private static final String BASE_PACKAGE = "com.ivirma.clinic";
    private static final Pattern QUOTED_LITERAL = Pattern.compile("'([^']*+(?:''[^']*+)*+)'");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("TEST_DB_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("TEST_DB_PASSWORD"));
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private Environment environment;

    /** One {@code @Enumerated(STRING)} attribute of an entity. */
    record EnumColumn(String table, String column, Class<? extends Enum<?>> enumType) {
        String id() {
            return table + "." + column;
        }
    }

    // ------------------------------------------------------------------ TASK-034

    @Test
    @DisplayName("TASK-034 STORY-003 the entity scan finds the five known enum columns (guards the drift test itself)")
    void TASK_034_STORY003_entityScanFindsTheKnownEnumColumns() {
        Set<String> found = enumColumns().stream().map(EnumColumn::id).collect(Collectors.toSet());
        assertThat(found).contains("patient.treatment_interest", "appointment.visit_type", "appointment.status",
                "doctor.specialty", "user_account.role");
    }

    @TestFactory
    @DisplayName("TASK-034 STORY-003 every Java enum value has a matching CHECK value in the migrated schema")
    Stream<DynamicTest> TASK_034_STORY003_everyEnumValueIsAllowedByItsCheckConstraint() {
        return enumColumns().stream().map(ec -> DynamicTest.dynamicTest(
                "TASK-034 " + ec.enumType().getSimpleName() + " -> " + ec.id(), () -> assertEnumMatchesCheck(ec)));
    }

    private void assertEnumMatchesCheck(EnumColumn ec) {
        Set<String> enumValues = Stream.of(ec.enumType().getEnumConstants()).map(Enum::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<Map<String, Object>> checks = jdbcTemplate.queryForList("""
                select c.conname, pg_get_constraintdef(c.oid) as def
                from pg_constraint c
                join pg_class t on t.oid = c.conrelid
                join pg_namespace n on n.oid = t.relnamespace
                join pg_attribute a on a.attrelid = t.oid and a.attnum = any (c.conkey)
                where c.contype = 'c' and n.nspname = current_schema() and t.relname = ? and a.attname = ?
                order by c.conname""", ec.table(), ec.column());
        if (checks.isEmpty()) {
            fail("Java enum " + ec.enumType().getName() + " is stored in " + ec.id()
                    + " but that column has no CHECK constraint in the migrated schema");
        }

        for (Map<String, Object> check : checks) {
            String name = (String) check.get("conname");
            Set<String> allowed = new TreeSet<>();
            Matcher m = QUOTED_LITERAL.matcher((String) check.get("def"));
            while (m.find()) {
                allowed.add(m.group(1).replace("''", "'"));
            }
            List<String> missing = new ArrayList<>(enumValues);
            missing.removeAll(allowed);
            if (!missing.isEmpty()) {
                fail("Java enum " + ec.enumType().getName() + " value(s) " + missing + " have no matching value in"
                        + " CHECK constraint " + name + " on " + ec.id() + " (allowed: " + allowed + "). Every enum"
                        + " change ships a migration: ALTER TABLE " + ec.table() + " DROP CONSTRAINT " + name
                        + "; ADD CONSTRAINT " + name + " CHECK (...) NOT VALID; VALIDATE CONSTRAINT " + name + ".");
            }
        }

        Integer maxLength = jdbcTemplate.queryForObject("""
                select character_maximum_length from information_schema.columns
                where table_schema = current_schema() and table_name = ? and column_name = ?""",
                Integer.class, ec.table(), ec.column());
        int longest = enumValues.stream().mapToInt(String::length).max().orElse(0);
        if (maxLength != null && longest > maxLength) {
            fail("Java enum " + ec.enumType().getName() + " has a value of length " + longest + " but " + ec.id()
                    + " is varchar(" + maxLength + "); widen the column in a migration");
        }
    }

    @SuppressWarnings("unchecked")
    private static List<EnumColumn> enumColumns() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        List<EnumColumn> result = new ArrayList<>();
        for (BeanDefinition bd : scanner.findCandidateComponents(BASE_PACKAGE)) {
            Class<?> entity = ClassUtils.resolveClassName(bd.getBeanClassName(), null);
            Table table = entity.getAnnotation(Table.class);
            String tableName = table != null && !table.name().isEmpty() ? table.name() : snake(entity.getSimpleName());
            for (Field field : entity.getDeclaredFields()) {
                Enumerated enumerated = field.getAnnotation(Enumerated.class);
                if (enumerated == null || enumerated.value() != EnumType.STRING || !field.getType().isEnum()) {
                    continue;
                }
                Column column = field.getAnnotation(Column.class);
                String columnName = column != null && !column.name().isEmpty() ? column.name() : snake(field.getName());
                result.add(new EnumColumn(tableName, columnName, (Class<? extends Enum<?>>) field.getType()));
            }
        }
        result.sort((a, b) -> a.id().compareTo(b.id()));
        return result;
    }

    /** Spring Boot's default CamelCaseToUnderscoresNamingStrategy, for the simple names used here. */
    private static String snake(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }

    // ------------------------------------------------------------------ TASK-035

    @Test
    @DisplayName("TASK-035 STORY-003 Hibernate runs with ddl-auto=validate: it never issues DDL against the migrated schema")
    void TASK_035_STORY003_hibernateOnlyValidatesTheSchema() {
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(entityManagerFactory.getProperties().get("hibernate.hbm2ddl.auto")).hasToString("validate");
    }

    @Test
    @DisplayName("TASK-035 STORY-003 the schema is Flyway's: flyway_schema_history has a successful V1 (executed or baselined)")
    void TASK_035_STORY003_schemaComesFromFlywayV1() {
        List<Map<String, Object>> v1 = jdbcTemplate.queryForList(
                "select type, success from flyway_schema_history where version = '1'");
        assertThat(v1).hasSize(1);
        assertThat(v1.get(0).get("success")).isEqualTo(Boolean.TRUE);
        assertThat(v1.get(0).get("type")).isIn("SQL", "BASELINE");
        Integer failed = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where not success", Integer.class);
        assertThat(failed).isZero();
    }
}
