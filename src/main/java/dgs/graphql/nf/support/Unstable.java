package dgs.graphql.nf.support;

import java.lang.annotation.*;

import static java.lang.annotation.ElementType.*;

/**
 * Used to mark a component as having an unstable API or implementation.
 * This is used by the team to reflect components that most likely will change in the future due its maturity and proven usefulness.
 * <p>
 * <b>Usage of this component is discouraged, use at your own risk.</b>
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(value={CONSTRUCTOR, FIELD, LOCAL_VARIABLE, METHOD, PACKAGE, PARAMETER, TYPE})
public @interface Unstable {
    String message()     default "The API/implementation of this component is unstable, use at your own risk.";
}
