package ru.airport.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Аудит вызовов прикладного слоя (FirstLab / AGENTS: сквозное логирование действий диспетчера).
 * GoF: можно трактовать как элемент цепочки обработки; GRASP: отдельная ответственность «аудит».
 */
@Aspect
@Component
@Order(0)
@Slf4j
public class ServiceAuditAspect {

    @Around("execution(* ru.airport.service..*(..))")
    public Object auditService(ProceedingJoinPoint pjp) throws Throwable {
        String user = currentUser();
        String sig = pjp.getSignature().toShortString();
        long t0 = System.currentTimeMillis();
        try {
            Object result = pjp.proceed();
            long ms = System.currentTimeMillis() - t0;
            if (isMutationLike(pjp.getSignature().getName())) {
                log.info("AUDIT user={} {} OK in {} ms", user, sig, ms);
            } else if (log.isDebugEnabled()) {
                log.debug("AUDIT user={} {} OK in {} ms", user, sig, ms);
            }
            return result;
        } catch (Throwable ex) {
            log.warn("AUDIT user={} {} FAIL after {} ms: {}", user, sig,
                    System.currentTimeMillis() - t0, ex.getMessage());
            throw ex;
        }
    }

    private static String currentUser() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated()) {
            return "anonymous";
        }
        return a.getName();
    }

    /**
     * Мутации и экспорт — в INFO; массовые чтения (list/get/find) — только DEBUG.
     */
    private static boolean isMutationLike(String methodName) {
        String m = methodName.toLowerCase();
        return m.contains("create")
                || m.contains("update")
                || m.contains("delete")
                || m.contains("assign")
                || m.contains("add")
                || m.contains("export")
                || m.contains("save");
    }
}
