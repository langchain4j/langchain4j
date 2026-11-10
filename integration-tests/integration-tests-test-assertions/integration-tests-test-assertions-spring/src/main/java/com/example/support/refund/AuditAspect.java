package com.example.support.refund;

import java.util.Arrays;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuditAspect {

    private final AuditLog auditLog;

    public AuditAspect(AuditLog auditLog) {
        this.auditLog = auditLog;
    }

    @Around("@annotation(com.example.support.refund.Audited)")
    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
        auditLog.add(joinPoint.getSignature().getName() + Arrays.toString(joinPoint.getArgs()));
        return joinPoint.proceed();
    }
}
