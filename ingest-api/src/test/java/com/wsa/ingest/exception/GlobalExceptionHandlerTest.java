package com.wsa.ingest.exception;

import com.wsa.contracts.ApiException;
import com.wsa.contracts.ErrorCode;
import com.wsa.contracts.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleApiReturnsStatusFromException() {
        ApiException ex = ApiException.brokerUnavailable();
        ResponseEntity<ErrorResponse> response = handler.handleApi(ex);
        assertEquals(503, response.getStatusCode().value());
        assertEquals(ErrorCode.BROKER_UNAVAILABLE, response.getBody().getCode());
    }

    @Test
    void handlePublishReturns503() {
        ResponseEntity<ErrorResponse> response =
                handler.handlePublish(new EventPublishException("down", new RuntimeException("x")));
        assertEquals(503, response.getStatusCode().value());
        assertEquals(ErrorCode.BROKER_UNAVAILABLE, response.getBody().getCode());
    }

    @Test
    void handleDataAccessReturns503() {
        ResponseEntity<ErrorResponse> response =
                handler.handleDataAccess(new DataAccessResourceFailureException("db down"));
        assertEquals(503, response.getStatusCode().value());
        assertEquals(ErrorCode.BROKER_UNAVAILABLE, response.getBody().getCode());
    }
}
