package ru.airport.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage(), "resource", ex.getResource(), "id", String.valueOf(ex.getId())));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, String>> conflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::formatFieldError)
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", msg.isEmpty() ? "Validation failed" : msg));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> notReadable(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getMostSpecificCause();
        String detail = cause != null && cause.getMessage() != null ? cause.getMessage() : ex.getMessage();
        if (detail == null || detail.isBlank()) {
            detail = "Некорректное тело запроса (JSON или типы полей)";
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", detail));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> typeMismatch(MethodArgumentTypeMismatchException ex) {
        String name = ex.getName();
        Object value = ex.getValue();
        String required = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "?";
        String msg = "Параметр «" + name + "»"
                + (value != null ? " = «" + value + "»" : "")
                + ": ожидается тип " + required;
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", msg));
    }

    private static String formatFieldError(FieldError fe) {
        return fe.getField() + ": " + fe.getDefaultMessage();
    }
}
