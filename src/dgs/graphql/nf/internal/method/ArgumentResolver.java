package dgs.graphql.nf.internal.method;

import graphql.schema.DataFetchingEnvironment;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * Resolves method parameters into argument values for @DgsData annotated methods.
 *
 * See [org.springframework.web.method.support.HandlerMethodArgumentResolver]
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public interface ArgumentResolver {

    /**
     * Determine whether the given [MethodParameter] is supported by this resolver.
     *
     * @param parameter the method parameter to check
     * @return Boolean indicating if this resolver supports the supplied parameter
     */
    public boolean supportsParameter(MethodParameter parameter);

    /**
     * Resolves a method parameter into an argument value for a @DgsData annotated method.
     *
     * @param parameter the method parameter to resolve. This parameter must
     * have previously been passed to [supportsParameter] which must
     * have returned `true`.
     * @param dfe the associated [DataFetchingEnvironment] for the current request
     * @return the resolved argument value
     */
    public Object resolveArgument(MethodParameter parameter, DataFetchingEnvironment dfe);

}

