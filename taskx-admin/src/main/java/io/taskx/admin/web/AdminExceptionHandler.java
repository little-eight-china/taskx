package io.taskx.admin.web;

import io.taskx.admin.web.dto.ErrorBody;
import io.taskx.core.store.SlotBusyException;
import io.taskx.core.store.SlotOccupiedException;
import io.taskx.meta.MetaException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.NoSuchElementException;

@RestControllerAdvice
public class AdminExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<ErrorBody> notFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorBody(ex.getMessage()));
    }

    @ExceptionHandler({
            SlotBusyException.class,
            SlotOccupiedException.class,
            IllegalStateException.class
    })
    ResponseEntity<ErrorBody> conflict(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorBody(ex.getMessage()));
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ErrorBody> badRequest(Exception ex) {
        return ResponseEntity.badRequest().body(new ErrorBody(ex.getMessage()));
    }

    @ExceptionHandler(MetaException.class)
    ResponseEntity<ErrorBody> meta(MetaException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorBody(ex.getMessage()));
    }
}
