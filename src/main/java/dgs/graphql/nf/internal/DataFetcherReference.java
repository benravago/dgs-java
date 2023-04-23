package dgs.graphql.nf.internal;

import java.lang.reflect.Method;
import org.springframework.core.annotation.MergedAnnotations;

public record DataFetcherReference(
    Object instance,
    Method method,
    MergedAnnotations annotations,
    String parentType,
    String field
) {}
