package dgs.graphql.nf;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.ElementType.FIELD;

/**
 * This represents code considered internal to the DGS framework and therefore its API is not stable between releases.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(value = {CONSTRUCTOR, METHOD, TYPE, FIELD})
@Inherited
public @interface Internal {
}
