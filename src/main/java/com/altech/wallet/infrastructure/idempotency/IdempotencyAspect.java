package com.altech.wallet.infrastructure.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

@Aspect
@Component
@RequiredArgsConstructor
public class IdempotencyAspect {

    private final IdempotencyRepository repository;
    private final ObjectMapper objectMapper;

    @Around("@annotation(idempotent)")
    public Object handleIdempotency(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        HttpServletRequest request = getCurrentHttpRequest();
        if (request == null) {
            return joinPoint.proceed();
        }

        String idempotencyKey = request.getHeader("Idempotency-Key");

        // If no header is provided, proceed normally without idempotency guarantees
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return joinPoint.proceed();
        }

        String requestPayload = extractPayload(joinPoint);
        String requestHash = HashUtils.sha256(requestPayload);

        // 1. Check or create IN_PROGRESS record
        Optional<IdempotencyRecord> recordOpt = repository.findByKeyForUpdate(idempotencyKey);

        if (recordOpt.isPresent()) {
            IdempotencyRecord record = recordOpt.get();

            // Payload Hash Validation
            if (idempotent.enforceHashCheck() && !record.getRequestHash().equals(requestHash)) {
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                        .body("Idempotency-Key payload mismatch: request arguments differ from original request.");
            }

            // Status Checks
            if (record.getStatus() == IdempotencyStatus.IN_PROGRESS) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("Concurrent request currently processing. Try again later.");
            }

            if (record.getStatus() == IdempotencyStatus.SUCCESS) {
                // Return Cached Original Response
                Object cachedBody = objectMapper.readValue(record.getResponseBody(), Object.class);
                return ResponseEntity.status(record.getResponseCode()).body(cachedBody);
            }
        }

        // 2. Insert new IN_PROGRESS record
        IdempotencyRecord newRecord = IdempotencyRecord.builder()
                .key(idempotencyKey)
                .requestHash(requestHash)
                .status(IdempotencyStatus.IN_PROGRESS)
                .build();

        try {
            repository.saveAndFlush(newRecord);
        } catch (DataIntegrityViolationException e) {
            // Caught concurrent insert race condition
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Concurrent request currently processing.");
        }

        // 3. Execute Core Business Logic
        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable t) {
            // Mark as failed on unhandled exception
            markFailed(idempotencyKey);
            throw t;
        }

        // 4. Update status to SUCCESS and cache serialized response
        markSuccess(idempotencyKey, result);

        return result;
    }

    private void markSuccess(String key, Object result) throws Exception {
        Optional<IdempotencyRecord> recordOpt = repository.findById(key);
        if (recordOpt.isPresent()) {
            IdempotencyRecord record = recordOpt.get();
            record.setStatus(IdempotencyStatus.SUCCESS);

            if (result instanceof ResponseEntity<?> responseEntity) {
                record.setResponseCode(responseEntity.getStatusCode().value());
                record.setResponseBody(objectMapper.writeValueAsString(responseEntity.getBody()));
            } else {
                record.setResponseCode(200);
                record.setResponseBody(objectMapper.writeValueAsString(result));
            }
            repository.saveAndFlush(record);
        }
    }

    private void markFailed(String key) {
        Optional<IdempotencyRecord> recordOpt = repository.findById(key);
        if (recordOpt.isPresent()) {
            IdempotencyRecord record = recordOpt.get();
            record.setStatus(IdempotencyStatus.FAILED);
            repository.saveAndFlush(record);
        }
    }

    private HttpServletRequest getCurrentHttpRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }

    private String extractPayload(ProceedingJoinPoint joinPoint) {
        StringBuilder sb = new StringBuilder();
        for (Object arg : joinPoint.getArgs()) {
            if (arg != null) {
                sb.append(arg.toString());
            }
        }
        return sb.toString();
    }
}