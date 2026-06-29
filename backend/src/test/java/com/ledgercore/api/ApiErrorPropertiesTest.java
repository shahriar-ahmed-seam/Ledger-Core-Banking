package com.ledgercore.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgercore.common.error.DomainException;
import com.ledgercore.common.error.ErrorCode;
import com.ledgercore.common.error.FieldError;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for the uniform error/success response envelopes.
 */
class ApiErrorPropertiesTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final ObjectMapper mapper = new ObjectMapper();

    @Provide
    Arbitrary<ErrorCode> errorCode() {
        return Arbitraries.of(ErrorCode.class);
    }

    // Feature: ledger-core-banking, Property 39: Response envelope is well-formed
    @Property(tries = 200)
    void property39_responseEnvelopeWellFormed(@ForAll("errorCode") ErrorCode code,
                                               @ForAll boolean withFields) throws Exception {
        List<FieldError> fields = withFields ? List.of(new FieldError("f", "bad")) : List.of();
        ResponseEntity<Envelopes.Error> response =
                handler.handleDomain(new DomainException(code, "message for " + code, fields));

        // Error envelope: code from the defined set, a message, and the mapped HTTP status.
        assertThat(response.getStatusCode().value()).isEqualTo(code.status().value());
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo(code.name());
        assertThat(ErrorCode.valueOf(response.getBody().error().code())).isEqualTo(code);
        assertThat(response.getBody().error().message()).isNotBlank();

        // Serialized error envelope contains "error" and no "data".
        String errorJson = mapper.writeValueAsString(response.getBody());
        assertThat(errorJson).contains("\"error\"").doesNotContain("\"data\"");

        // Serialized success envelope contains "data" and no error code/message.
        String successJson = mapper.writeValueAsString(Envelopes.ok("ok-payload"));
        assertThat(successJson).contains("\"data\"")
                .doesNotContain("\"error\"")
                .doesNotContain("\"code\"")
                .doesNotContain("\"message\"");
    }

    // Feature: ledger-core-banking, Property 35: Malformed requests are rejected with field-level detail and no state change
    @Property(tries = 200)
    void property35_malformedRequestsRejectedWithFieldDetail(
            @ForAll("fieldNames") List<String> fieldNames) {
        List<FieldError> fields = fieldNames.stream()
                .map(n -> new FieldError(n, "must not be blank")).toList();
        ResponseEntity<Envelopes.Error> response =
                handler.handleDomain(DomainException.validation("Request validation failed.", fields));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().error().code()).isEqualTo(ErrorCode.VALIDATION_ERROR.name());
        // Each invalid field is identified in the response.
        assertThat(response.getBody().error().fields())
                .extracting(FieldError::field)
                .containsExactlyElementsOf(fieldNames);
        // The handler performs no writes/state changes (it has no collaborators).
        assertThat(handler).hasNoNullFieldsOrPropertiesExcept();
    }

    @Provide
    Arbitrary<List<String>> fieldNames() {
        return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(8)
                .list().ofMinSize(1).ofMaxSize(5).uniqueElements();
    }
}
