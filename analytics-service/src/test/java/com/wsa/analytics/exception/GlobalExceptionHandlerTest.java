package com.wsa.analytics.exception;

import com.wsa.contracts.ApiException;
import com.wsa.contracts.ErrorCode;
import com.wsa.contracts.ErrorMessage;
import com.wsa.contracts.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleApiUsesExceptionStatusAndBody() {
        ApiException ex = ApiException.notFound(ErrorMessage.UNKNOWN_ALERT);
        ResponseEntity<ErrorResponse> response = handler.handleApi(ex);
        assertEquals(404, response.getStatusCode().value());
        assertEquals(ErrorCode.NOT_FOUND, response.getBody().getCode());
    }

    @Test
    void handleMissingResourceReturns404() {
        ResponseEntity<ErrorResponse> response = handler.handleMissingResource(
                new NoResourceFoundException(HttpMethod.GET, "/missing"));
        assertEquals(404, response.getStatusCode().value());
        assertEquals(ErrorMessage.RESOURCE_NOT_FOUND.getValue(), response.getBody().getMessage());
    }

    @Test
    void handleUnreadableReturns400() {
        ResponseEntity<ErrorResponse> response = handler.handleUnreadable(
                new HttpMessageNotReadableException("bad"));
        assertEquals(400, response.getStatusCode().value());
        assertEquals(ErrorMessage.BODY_UNREADABLE.getValue(), response.getBody().getMessage());
    }

    @Test
    void handleIllegalArgumentReturns400WithMessage() {
        ResponseEntity<ErrorResponse> response = handler.handleIllegalArgument(
                new IllegalArgumentException("boom"));
        assertEquals(400, response.getStatusCode().value());
        assertEquals("boom", response.getBody().getMessage());
    }

    @Test
    void handleInvalidMapsFieldErrors() throws Exception {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "req");
        binding.addError(new FieldError("req", "metric", "must not be null"));
        Method method = Sample.class.getDeclaredMethod("ping", String.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, binding);

        ResponseEntity<ErrorResponse> response = handler.handleInvalid(ex);
        assertEquals(400, response.getStatusCode().value());
        assertEquals(ErrorMessage.REQUEST_INVALID.getValue(), response.getBody().getMessage());
        assertFalse(response.getBody().getDetails().isEmpty());
        assertEquals("metric", response.getBody().getDetails().get(0).getField());
    }

    @SuppressWarnings("unused")
    private static final class Sample {
        void ping(String value) {
        }
    }

    @Test
    void handleIllegalStateReturns404() {
        ResponseEntity<ErrorResponse> response = handler.handleIllegalState(
                new IllegalStateException("gone"));
        assertEquals(404, response.getStatusCode().value());
        assertEquals("gone", response.getBody().getMessage());
    }

    @Test
    void handleUnexpectedReturns500() {
        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(new RuntimeException("x"));
        assertEquals(500, response.getStatusCode().value());
        assertEquals(ErrorCode.INTERNAL_ERROR, response.getBody().getCode());
    }
}
