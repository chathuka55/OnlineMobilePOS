package com.possaas.subscription.feature;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that the annotated controller method (or type) requires a plan feature.
 * Enforced by {@link RequiresFeatureAspect}.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresFeature {

    /** Feature code, e.g. {@code "REPAIRS"}. */
    String value();
}
