package ru.airport.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final Map<String, String> FIELD_LABELS = Map.ofEntries(
            Map.entry("flightNumber", "Номер рейса"),
            Map.entry("originAirport", "Аэропорт вылета"),
            Map.entry("destinationAirport", "Аэропорт прилёта"),
            Map.entry("scheduledDeparture", "Плановый вылет"),
            Map.entry("scheduledArrival", "Плановый прилёт"),
            Map.entry("airlineId", "Авиакомпания")
    );

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }

    // --- Domain (business layer) ---

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, String>> badRequest(BadRequestException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ServerErrorException.class)
    public ResponseEntity<Map<String, String>> serverError(ServerErrorException ex) {
        log.error("Server error: {}", ex.getMessage(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка сервера");
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(ResourceNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, String>> conflict(ConflictException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> dataIntegrity(DataIntegrityViolationException ex) {
        log.debug("Data integrity violation: {}", ex.getMessage());
        return error(HttpStatus.CONFLICT,
                "Нельзя изменить данные: есть связанные записи (например, рейсы по слоту расписания)");
    }

    // --- Bean Validation / binding ---

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> validation(MethodArgumentNotValidException ex) {
        String msg = collectBindingErrors(ex.getBindingResult().getFieldErrors(),
                ex.getBindingResult().getGlobalErrors());
        return error(HttpStatus.BAD_REQUEST, msg.isEmpty() ? "Ошибка валидации данных" : msg);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Map<String, String>> bindException(BindException ex) {
        String msg = collectBindingErrors(ex.getBindingResult().getFieldErrors(),
                ex.getBindingResult().getGlobalErrors());
        return error(HttpStatus.BAD_REQUEST, msg.isEmpty() ? "Ошибка валидации данных" : msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> constraintViolation(ConstraintViolationException ex) {
        String msg = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, msg.isEmpty() ? "Ошибка валидации данных" : msg);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> notReadable(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getMostSpecificCause();
        String detail = cause != null && cause.getMessage() != null ? cause.getMessage() : ex.getMessage();
        if (detail == null || detail.isBlank()) {
            detail = "Некорректное тело запроса (JSON или типы полей)";
        }
        return error(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, String>> missingParam(MissingServletRequestParameterException ex) {
        String msg = "Отсутствует обязательный параметр «" + ex.getParameterName() + "»";
        return error(HttpStatus.BAD_REQUEST, msg);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> responseStatus(ResponseStatusException ex) {
        String reason = ex.getReason();
        if (reason == null || reason.isBlank()) {
            reason = ex.getStatusCode().toString();
        }
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("error", reason));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> typeMismatch(MethodArgumentTypeMismatchException ex) {
        String name = ex.getName();
        Object value = ex.getValue();
        String required = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "?";
        String msg = "Параметр «" + name + "»"
                + (value != null ? " = «" + value + "»" : "")
                + ": ожидается тип " + required;
        return error(HttpStatus.BAD_REQUEST, msg);
    }

    // --- Security ---

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> badCredentials(BadCredentialsException ex) {
        log.debug("Authentication failed: {}", ex.getMessage());
        return error(HttpStatus.UNAUTHORIZED, "Неверное имя пользователя или пароль");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, String>> authentication(AuthenticationException ex) {
        log.debug("Authentication failed: {}", ex.getMessage());
        String msg = ex.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = "Ошибка аутентификации";
        }
        return error(HttpStatus.UNAUTHORIZED, msg);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> accessDenied(AccessDeniedException ex) {
        log.debug("Access denied: {}", ex.getMessage());
        String msg = ex.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = "Доступ запрещён";
        }
        return error(HttpStatus.FORBIDDEN, msg);
    }

    // --- Illegal state / fallback ---

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> illegalArgument(IllegalArgumentException ex) {
        String msg = ex.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = "Некорректный запрос";
        }
        return error(HttpStatus.BAD_REQUEST, msg);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> illegalState(IllegalStateException ex) {
        log.debug("Illegal state: {}", ex.getMessage());
        String msg = ex.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = "Операция недоступна в текущем состоянии";
        }
        return error(HttpStatus.BAD_REQUEST, msg);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> fallback(Exception ex) {
        log.error("Unhandled exception", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка сервера");
    }

    private static String collectBindingErrors(
            java.util.List<FieldError> fieldErrors,
            java.util.List<ObjectError> globalErrors
    ) {
        return Stream.concat(
                        fieldErrors.stream().map(GlobalExceptionHandler::formatFieldError),
                        globalErrors.stream().map(GlobalExceptionHandler::formatObjectError))
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining("; "));
    }

    private static String formatFieldError(FieldError fe) {
        String label = FIELD_LABELS.getOrDefault(fe.getField(), fe.getField());
        String message = fe.getDefaultMessage();
        return label + ": " + (message != null ? message : "некорректное значение");
    }

    private static String formatObjectError(ObjectError oe) {
        String message = oe.getDefaultMessage();
        if (message == null || message.isBlank()) {
            return "";
        }
        return message;
    }
}
