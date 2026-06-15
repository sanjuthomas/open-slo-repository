package com.openslo.repository.exception;

import com.openslo.repository.dto.ApiError;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsDomainExceptionsToApiError() {
        ResponseEntity<ApiError> validation = handler.handleValidation(new OpenSloValidationException("invalid"));
        ResponseEntity<ApiError> duplicate = handler.handleDuplicate(new DuplicateOpenSloException("key"));
        ResponseEntity<ApiError> notFound = handler.handleNotFound(new OpenSloNotFoundException("missing"));

        assertThat(validation.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(validation.getBody().message()).isEqualTo("invalid");
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
