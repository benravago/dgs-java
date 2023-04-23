package dgs.graphql.nf;

import dgs.graphql.nf.internal.utils.DataLoaderNameUtil;
import org.dataloader.registries.DispatchPredicate;
import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class or field as a DataLoader, which will be registered to the framework as a DataLoader.
 * The class or field must implement one of the BatchLoader interfaces.
 * See https://netflix.github.io/dgs/data-loaders/
 */
@Target({ElementType.TYPE, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Component
@Inherited
public @interface DgsDataLoader {

    /**
     * Used internally by {@link DataLoaderNameUtil#getDataLoaderName(Class, DgsDataLoader)}.
     * <p>
     * The <strong>value</strong> of this constant may change in future versions,
     * and should therefore not be relied upon.
     */
    String GENERATE_DATA_LOADER_NAME = "NETFLIX_DGS_GENERATE_DATALOADER_NAME";

    String name() default GENERATE_DATA_LOADER_NAME;

    boolean caching() default true;

    boolean batching() default true;

    int maxBatchSize() default 0;
}

