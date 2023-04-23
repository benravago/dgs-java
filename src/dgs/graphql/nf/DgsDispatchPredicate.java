package dgs.graphql.nf;

import dgs.graphql.nf.internal.utils.DataLoaderNameUtil;
import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * Marks a class or field as a Dispatch Predicate for a ScheduledDataLoaderRegistry, which will be registered to the framework.
 * The method must return an instance of DispatchPredicate.
 * See https://netflix.github.io/dgs/data-loaders/
 */
//@Target(ElementType.METHOD)
@Target(ElementType.FIELD)
@Inherited
@Retention(RetentionPolicy.RUNTIME)
public @interface DgsDispatchPredicate {
}

