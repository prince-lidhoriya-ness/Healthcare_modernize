package com.ivirma.clinic.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivirma.clinic.appointment.AppointmentController;
import com.ivirma.clinic.appointment.AppointmentService;
import com.ivirma.clinic.auth.AuthenticatedUser;
import com.ivirma.clinic.auth.Role;
import com.ivirma.clinic.auth.SecurityConfig;
import com.ivirma.clinic.auth.TokenService;
import com.ivirma.clinic.customer.CustomerPortalController;
import com.ivirma.clinic.patient.PatientController;
import com.ivirma.clinic.patient.PatientRequest;
import com.ivirma.clinic.patient.PatientService;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * STORY-010 (SEC-006): error responses keep the documented body {status, error, message, detail, timestamp}
 * but never carry a stack trace, an exception class name or exception text.
 *
 * <ul>
 *   <li>TASK-022: an unexpected error is a generic 500 ({@code detail} null); Spring's own {@code /error}
 *       body (server.error.include-*) has no trace, exception or message.</li>
 *   <li>TASK-023: the unexpected error is logged for operators (type and stack frames) without the
 *       exception text, which can echo patient data (a PostgreSQL "Failing row contains (...)" detail).</li>
 *   <li>TASK-044: malformed client input is 400, not 500: a non-numeric id (gate-4 cases {@code bad-id} and
 *       {@code no-stacktrace-leak}), a missing request parameter, unreadable JSON, an unknown visit type or
 *       status, a malformed date.</li>
 * </ul>
 *
 * Q11 (strict parity): the RULE-025 integrity edge (null fields on PUT) keeps its legacy 500, now generic.
 * The documented business messages (RULE-014 to RULE-034, RULE-044 to RULE-046) are pinned byte for byte
 * by {@code GoldenApiResponsesTest}; {@link #STORY010_goldenErrorBodiesHaveOnlyTheDocumentedFields} checks
 * that those golden error bodies have exactly the documented fields.
 */
@WebMvcTest(controllers = {PatientController.class, AppointmentController.class, CustomerPortalController.class})
@Import({SecurityConfig.class, TokenService.class, ErrorResponseStory010Test.ProbeController.class})
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("STORY-010 error responses: documented shape, no stack traces or exception details")
class ErrorResponseStory010Test {

    private static final String TIMESTAMP = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}[+-]\\d{2}:\\d{2}";
    private static final Pattern TIMESTAMP_FIELD = Pattern.compile("\"timestamp\":\"([^\"]*)\"");
    private static final Path GOLDEN_DIR = Paths.get("src", "test", "resources", "golden");

    /** JSON-escaped "TAB at " of a Java stack frame: what gate-4 {@code no-stacktrace-leak} searches the raw body for. */
    private static final String ESCAPED_STACK_FRAME = "\\tat ";
    private static final String RAW_STACK_FRAME = "\tat ";

    private static final String GENERIC_500 = "{\"status\":500,\"error\":\"Internal Server Error\","
            + "\"message\":\"An unexpected error occurred\",\"detail\":null,\"timestamp\":\"<TIMESTAMP>\"}";
    private static final String UNREADABLE_400 = "{\"status\":400,\"error\":\"Bad Request\","
            + "\"message\":\"Request body is invalid\",\"detail\":null,\"timestamp\":\"<TIMESTAMP>\"}";

    /** Synthetic patient data (allowlisted domain) that a database error message can echo back. */
    private static final String PATIENT_MAIL = "laura.gomez@example.com";
    private static final String PATIENT_BIRTH_DATE = "1990-04-12";
    private static final String PATIENT_SURNAME = "Gomez";

    private static final String ADMIN = "ADMIN";
    private static final String CUSTOMER = "CUSTOMER";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @MockBean
    private PatientService patientService;

    @MockBean
    private AppointmentService appointmentService;

    /** Test-only endpoint with a required query parameter: no production endpoint has one today. */
    @RestController
    static class ProbeController {
        @GetMapping("/api/story-010/probe")
        String probe(@RequestParam("term") String term) {
            return term;
        }
    }

    private String bearer(String who) {
        if (ADMIN.equals(who)) {
            return "Bearer " + tokenService.issue(
                    new AuthenticatedUser(1L, "admin", Role.ADMIN, "Clinic coordinator", null));
        }
        if (CUSTOMER.equals(who)) {
            return "Bearer " + tokenService.issue(
                    new AuthenticatedUser(2L, PATIENT_MAIL, Role.CUSTOMER, "Laura Gomez", 42L));
        }
        return null;
    }

    private MvcResult call(String method, String path, String who, String body) throws Exception {
        MockHttpServletRequestBuilder builder = request(HttpMethod.valueOf(method), path);
        String auth = bearer(who);
        if (auth != null) {
            builder.header("Authorization", auth);
        }
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(builder).andReturn();
    }

    private static String body(MvcResult result) throws IOException {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** The body with its legacy-format timestamp replaced by a placeholder (fails when there is none). */
    private static String normalised(String body) {
        Matcher m = TIMESTAMP_FIELD.matcher(body);
        assertThat(m.find()).as("body has a timestamp field: %s", body).isTrue();
        assertThat(m.group(1)).matches(TIMESTAMP);
        return body.replace(m.group(1), "<TIMESTAMP>");
    }

    /** Nothing a client could use to probe the internals: no stack frame, exception class or Java package. */
    private static void assertNoInternals(String body) {
        assertThat(body)
                .doesNotContain(ESCAPED_STACK_FRAME)
                .doesNotContain(RAW_STACK_FRAME)
                .doesNotContain("Exception")
                .doesNotContain("java.")
                .doesNotContain("com.ivirma")
                .doesNotContain("org.springframework")
                .doesNotContain("\"trace\"")
                .doesNotContain("\"exception\"");
    }

    // ---------------------------------------------------------------- TASK-022 unexpected errors

    @Test
    @DisplayName("TASK-022 STORY-010 unexpected error -> generic 500 in the documented shape, no stack trace or exception text")
    void TASK_022_STORY010_unexpectedErrorIsGeneric500() throws Exception {
        when(patientService.get(1L)).thenThrow(new IllegalStateException("Connection to db:5432 refused"));

        MvcResult result = call("GET", "/api/patients/1", ADMIN, null);

        assertThat(result.getResponse().getStatus()).isEqualTo(500);
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        String body = body(result);
        assertThat(normalised(body)).isEqualTo(GENERIC_500);
        assertNoInternals(body);
        assertThat(body).doesNotContain("IllegalState").doesNotContain("db:5432");
    }

    @Test
    @DisplayName("TASK-022 STORY-010 Q11 parity: RULE-025 PUT with null fields stays 500, now generic and without the DB error")
    void TASK_022_STORY010_rule025IntegrityEdgeStays500WithoutDetail() throws Exception {
        when(patientService.update(eq(3L), any(PatientRequest.class))).thenThrow(failingRowViolation());

        MvcResult result = call("PUT", "/api/patients/3", ADMIN,
                "{\"firstName\":null,\"lastName\":null,\"dateOfBirth\":null,\"email\":null,\"treatmentInterest\":null}");

        assertThat(result.getResponse().getStatus()).isEqualTo(500);
        String body = body(result);
        assertThat(normalised(body)).isEqualTo(GENERIC_500);
        assertNoInternals(body);
        assertThat(body).doesNotContain("Failing row").doesNotContain(PATIENT_MAIL).doesNotContain(PATIENT_BIRTH_DATE);
    }

    @Test
    @DisplayName("TASK-022 STORY-010 Spring's /error body (server.error.include-*) has no trace, exception or message")
    void TASK_022_STORY010_springErrorEndpointExposesNoInternals() throws Exception {
        MvcResult result = mockMvc.perform(get("/error")
                        .accept(MediaType.APPLICATION_JSON)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/api/patients")
                        .requestAttr(RequestDispatcher.ERROR_EXCEPTION,
                                new IllegalStateException("Connection to db:5432 refused")))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(500);
        String body = body(result);
        JsonNode json = new ObjectMapper().readTree(body);
        assertThat(json.has("trace")).as("trace field in %s", body).isFalse();
        assertThat(json.has("exception")).as("exception field in %s", body).isFalse();
        assertThat(json.has("message")).as("message field in %s", body).isFalse();
        assertNoInternals(body);
        assertThat(body).doesNotContain("db:5432");
    }

    // ---------------------------------------------------------------- TASK-023 server-side log

    @Test
    @DisplayName("TASK-023 STORY-010 unexpected error is logged with its type and stack frames but without patient data")
    void TASK_023_STORY010_unexpectedErrorIsLoggedWithoutPatientData(CapturedOutput output) throws Exception {
        when(patientService.update(eq(3L), any(PatientRequest.class))).thenThrow(failingRowViolation());

        call("PUT", "/api/patients/3", ADMIN, "{\"firstName\":null,\"lastName\":null}");

        String captured = output.getAll();
        assertThat(captured)
                .contains("Unexpected error handling PUT /api/patients/{id}")
                .contains("org.springframework.dao.DataIntegrityViolationException")
                .contains("java.sql.SQLException SQLState=23502")
                .contains(RAW_STACK_FRAME + "com.ivirma.clinic.patient.PatientController.update")
                .doesNotContain(PATIENT_MAIL)
                .doesNotContain(PATIENT_BIRTH_DATE)
                .doesNotContain(PATIENT_SURNAME)
                .doesNotContain("Failing row");
    }

    // ---------------------------------------------------------------- TASK-044 malformed client input -> 400

    @ParameterizedTest(name = "TASK-044 STORY-010 {0} {1} as {2} -> 400 \"{3}\"")
    @CsvSource(delimiter = '|', value = {
            // gate-4 cases bad-id and no-stacktrace-leak
            "GET   | /api/patients/abc                     | ADMIN    | id: must be a number",
            "GET   | /api/patients/abc/appointments        | ADMIN    | patientId: must be a number",
            "PUT   | /api/patients/abc                     | ADMIN    | id: must be a number",
            "PATCH | /api/appointments/abc/status          | ADMIN    | id: must be a number",
            "PATCH | /api/customer/appointments/abc/cancel | CUSTOMER | id: must be a number",
            "GET   | /api/patients/99999999999999999999    | ADMIN    | id: must be a number",
            "GET   | /api/appointments?upcoming=maybe      | ADMIN    | upcoming: must be true or false",
            "GET   | /api/story-010/probe                  | ADMIN    | term: must be provided",
    })
    @DisplayName("TASK-044 STORY-010 malformed id or parameter -> 400 in the documented shape, service not called")
    void TASK_044_STORY010_malformedParameterIs400(String method, String path, String who, String message)
            throws Exception {
        String requestBody = "PUT".equals(method) || "PATCH".equals(method) ? "{\"status\":\"COMPLETED\"}" : null;

        MvcResult result = call(method, path, who, requestBody);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        String body = body(result);
        assertThat(normalised(body)).isEqualTo("{\"status\":400,\"error\":\"Bad Request\",\"message\":\"" + message
                + "\",\"detail\":null,\"timestamp\":\"<TIMESTAMP>\"}");
        assertNoInternals(body);
        assertThat(body).doesNotContain("abc").doesNotContain("maybe");
        verifyNoInteractions(patientService, appointmentService);
    }

    @ParameterizedTest(name = "TASK-044 STORY-010 {0} {1} as {2} ({4}) -> 400 Request body is invalid, no parser detail")
    @CsvSource(delimiter = '|', value = {
            "POST  | /api/patients              | ADMIN    | '{\"firstName\":\"Ana\",'                                                                        | malformed JSON",
            "POST  | /api/appointments          | ADMIN    | '{\"patientId\":2,\"visitType\":\"LASER\",\"clinician\":\"Dr. Priya Nair\",\"startTime\":\"2026-11-02T09:30\"}' | unknown visit type",
            "POST  | /api/customer/appointments | CUSTOMER | '{\"visitType\":\"BLOOD_TEST\",\"clinician\":\"Dr. Priya Nair\",\"startTime\":\"next tuesday\"}' | malformed date",
            "PATCH | /api/appointments/12/status | ADMIN   | '{\"status\":\"DONE\"}'                                                                          | unknown status",
            "POST  | /api/patients              | ADMIN    | ''                                                                                              | empty body",
    })
    @DisplayName("TASK-044 STORY-010 unreadable request body -> 400 Request body is invalid with detail null")
    void TASK_044_STORY010_unreadableBodyIs400WithoutParserDetail(String method, String path, String who,
                                                                  String requestBody, String why) throws Exception {
        MvcResult result = call(method, path, who, requestBody);

        assertThat(result.getResponse().getStatus()).as(why).isEqualTo(400);
        String body = body(result);
        assertThat(normalised(body)).as(why).isEqualTo(UNREADABLE_400);
        assertNoInternals(body);
        assertThat(body).doesNotContain("JSON").doesNotContain("deserialize").doesNotContain("VisitType")
                .doesNotContain("LASER").doesNotContain("next tuesday");
        verifyNoInteractions(patientService, appointmentService);
    }

    // ---------------------------------------------------------------- documented error cases (criteria 1 and 4)

    /**
     * Every golden error response (400/401/403/404/409; compared byte for byte with the live output by
     * {@code GoldenApiResponsesTest}) has exactly the documented fields, {@code detail} null, and no internals.
     */
    @Test
    @DisplayName("STORY-010 every golden error body has only status, error, message, detail (null) and timestamp")
    void STORY010_goldenErrorBodiesHaveOnlyTheDocumentedFields() throws IOException {
        List<Path> errorCases = new ArrayList<>();
        try (Stream<Path> files = Files.list(GOLDEN_DIR)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".txt")).sorted().toList()) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                int status = Integer.parseInt(goldenField(lines, "status: ", file));
                if (status < 400) {
                    continue;
                }
                errorCases.add(file);
                String body = goldenField(lines, "body: ", file);
                JsonNode json = new ObjectMapper().readTree(body);
                List<String> fields = new ArrayList<>();
                json.fieldNames().forEachRemaining(fields::add);
                assertThat(fields).as(file.toString()).containsExactly("status", "error", "message", "detail", "timestamp");
                assertThat(json.get("detail").isNull()).as("detail is null in %s", file).isTrue();
                assertThat(json.get("status").asInt()).as(file.toString()).isEqualTo(status);
                assertNoInternals(body);
            }
        }
        // 8 x 400, 3 x 401, 2 x 403, 2 x 404, 5 x 409 at the time of writing; guards against a vacuous pass
        assertThat(errorCases).hasSizeGreaterThanOrEqualTo(20);
    }

    /** The value of the golden-file line that starts with {@code prefix} (fails the test when there is none). */
    private static String goldenField(List<String> lines, String prefix, Path file) {
        return lines.stream()
                .filter(line -> line.startsWith(prefix))
                .map(line -> line.substring(prefix.length()).trim())
                .findFirst()
                .orElseThrow(() -> new AssertionError("no '" + prefix.trim() + "' line in " + file));
    }

    private static DataIntegrityViolationException failingRowViolation() {
        String dbMessage = "ERROR: null value in column \"first_name\" of relation \"patient\" violates not-null constraint"
                + "\n  Detail: Failing row contains (3, 2026-10-29 10:00:00, " + PATIENT_BIRTH_DATE + ", " + PATIENT_MAIL
                + ", null, " + PATIENT_SURNAME + ", null, IVF).";
        return new DataIntegrityViolationException("could not execute statement [" + dbMessage + "]",
                new SQLException(dbMessage, "23502"));
    }
}
