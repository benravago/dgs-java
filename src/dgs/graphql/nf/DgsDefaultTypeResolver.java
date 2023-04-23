package dgs.graphql.nf;

import java.lang.annotation.*;

/**
 * Mark a type resolver method to be the default.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface DgsDefaultTypeResolver {
}
