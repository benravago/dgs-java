package dgs.graphql.nf;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark a class for the DGS framework.
 * Each DgsComponent is also a regular Spring Component.
 * The framework will scan each DgsComponent for other annotations.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
@Qualifier("dgs")
@Inherited
public @interface DgsComponent {
}
