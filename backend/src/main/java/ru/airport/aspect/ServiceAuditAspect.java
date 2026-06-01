package ru.airport.aspect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import ru.airport.audit.OperationalEventMapper;
import ru.airport.dto.DelayWarningRq;
import ru.airport.dto.FlightAircraftAssignmentRq;
import ru.airport.dto.FlightStatusUpdateRq;
import ru.airport.dto.GateAssignmentRq;
import ru.airport.scheduler.FlightStatusScheduler;
import ru.airport.service.AuthService;
import ru.airport.websocket.RealtimeNotificationService;

import java.util.StringJoiner;

/**
 * Аудит вызовов прикладного слоя (FirstLab / AGENTS: сквозное логирование действий диспетчера).
 * Успешные мутации диспетчера дополнительно публикуются в {@code /topic/operational-events}.
 */
@Aspect
@Component
@Order(0)
@Slf4j
@RequiredArgsConstructor
public class ServiceAuditAspect {

    private final OperationalEventMapper operationalEventMapper;
    private final RealtimeNotificationService realtimeNotificationService;

    @Around("execution(* ru.airport.service..*(..)) || execution(* ru.airport.scheduler..*(..))")
    public Object auditService(ProceedingJoinPoint pjp) throws Throwable {
        String user = currentUser();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String sig = pjp.getSignature().toShortString();
        String details = extractDomainDetails(pjp);
        long t0 = System.currentTimeMillis();
        MethodSignature methodSig = (MethodSignature) pjp.getSignature();
        try {
            Object result = pjp.proceed();
            long ms = System.currentTimeMillis() - t0;
            if (isFlightStatusSchedulerTick(methodSig)) {
                if (log.isDebugEnabled()) {
                    log.debug("AUDIT user={} {} OK in {} ms{}", user, sig, ms, details);
                }
            } else if (isAuthAuditMethod(methodSig)) {
                log.info("AUDIT user={} {} OK in {} ms{}", user, sig, ms, details);
            } else if (isMutationLike(methodSig.getMethod().getName())) {
                log.info("AUDIT user={} {} OK in {} ms{}", user, sig, ms, details);
                publishOperationalEventIfApplicable(methodSig, pjp.getArgs(), result, user, authentication, details);
            } else if (log.isDebugEnabled()) {
                log.debug("AUDIT user={} {} OK in {} ms{}", user, sig, ms, details);
            }
            return result;
        } catch (Throwable ex) {
            if (isAuthAuditMethod(methodSig)) {
                log.info("AUDIT user={} {} FAIL after {} ms: {}{}", user, sig,
                        System.currentTimeMillis() - t0, ex.getMessage(), details);
            } else {
                log.warn("AUDIT user={} {} FAIL after {} ms: {}{}", user, sig,
                        System.currentTimeMillis() - t0, ex.getMessage(), details);
            }
            throw ex;
        }
    }

    private void publishOperationalEventIfApplicable(
            MethodSignature methodSig,
            Object[] args,
            Object result,
            String user,
            Authentication authentication,
            String details) {
        operationalEventMapper.tryMap(methodSig, args, result, user, authentication, details)
                .ifPresent(realtimeNotificationService::publishOperationalEvent);
    }

    private static String currentUser() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated()) {
            return "anonymous";
        }
        return a.getName();
    }

    private static boolean isAuthAuditMethod(MethodSignature ms) {
        Class<?> declaring = ms.getDeclaringType();
        if (!AuthService.class.isAssignableFrom(declaring)) {
            return false;
        }
        String name = ms.getMethod().getName();
        return "login".equals(name) || "logout".equals(name);
    }

    private static boolean isFlightStatusSchedulerTick(MethodSignature ms) {
        Class<?> declaring = ms.getDeclaringType();
        if (!FlightStatusScheduler.class.isAssignableFrom(declaring)) {
            return false;
        }
        return "updateFlightStatuses".equals(ms.getMethod().getName());
    }

    private static boolean isMutationLike(String methodName) {
        String m = methodName.toLowerCase();
        return m.contains("create")
                || m.contains("update")
                || m.contains("delete")
                || m.contains("assign")
                || m.contains("add")
                || m.contains("generate")
                || m.contains("correct");
    }

    private static String extractDomainDetails(ProceedingJoinPoint pjp) {
        Object[] args = pjp.getArgs();
        String[] paramNames = ((MethodSignature) pjp.getSignature()).getParameterNames();
        if (args == null || args.length == 0) {
            return "";
        }

        StringJoiner sj = new StringJoiner(", ");

        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg == null) {
                continue;
            }

            String name = (paramNames != null && i < paramNames.length) ? paramNames[i] : null;

            if (arg instanceof Integer id && isIdParam(name)) {
                sj.add(name + "=" + id);
            } else if (arg instanceof FlightStatusUpdateRq rq) {
                sj.add("newStatus=" + rq.getStatus());
            } else if (arg instanceof GateAssignmentRq rq) {
                sj.add("gateId=" + rq.getGateId()
                        + " interval=[" + rq.getAssignedFrom() + " .. " + rq.getAssignedTo() + "]");
            } else if (arg instanceof FlightAircraftAssignmentRq rq) {
                sj.add("aircraftTypeId=" + rq.getAircraftTypeId());
            } else if (arg instanceof DelayWarningRq rq) {
                sj.add("delayMinutes=" + rq.getDelayMinutes()
                        + (rq.getReason() != null ? " reason=\"" + rq.getReason() + "\"" : ""));
            }
        }

        String result = sj.toString();
        return result.isEmpty() ? "" : " | " + result;
    }

    private static boolean isIdParam(String name) {
        return name != null && (name.endsWith("Id") || name.equals("id"));
    }
}
