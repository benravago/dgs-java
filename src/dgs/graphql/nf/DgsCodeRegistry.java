package dgs.graphql.nf;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Annotation to mark a method a provider of a CodeRegistry, which is a programmatic way of creating a schema.
 * https://netflix.github.io/dgs/advanced/schema-from-code/
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface DgsCodeRegistry {
}
