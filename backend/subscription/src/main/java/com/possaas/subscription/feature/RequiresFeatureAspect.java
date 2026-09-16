package com.possaas.subscription.feature;

import com.possaas.subscription.service.EntitlementService;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class RequiresFeatureAspect {

    private final EntitlementService entitlementService;

    public RequiresFeatureAspect(EntitlementService entitlementService) {
        this.entitlementService = entitlementService;
    }

    @Before("@within(com.possaas.subscription.feature.RequiresFeature) "
            + "|| @annotation(com.possaas.subscription.feature.RequiresFeature)")
    public void checkFeature(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        RequiresFeature annotation = AnnotationUtils.findAnnotation(
                signature.getMethod(), RequiresFeature.class);
        if (annotation == null) {
            annotation = AnnotationUtils.findAnnotation(
                    signature.getDeclaringType(), RequiresFeature.class);
        }
        if (annotation != null) {
            entitlementService.assertFeature(annotation.value());
        }
    }
}
