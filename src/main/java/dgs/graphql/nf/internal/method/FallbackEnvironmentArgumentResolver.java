package dgs.graphql.nf.internal.method;

import dgs.graphql.nf.internal.InputObjectMapper;

import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * Resolves arguments based on the name by looking for any matching
 * arguments in the current [DataFetchingEnvironment]. Intended as
 * a fallback if no other resolvers can handle the argument.
 */
@Order(Ordered.LOWEST_PRECEDENCE)
public final class FallbackEnvironmentArgumentResolver extends AbstractInputArgumentResolver {

    public FallbackEnvironmentArgumentResolver(InputObjectMapper inputObjectMapper) {
        super(inputObjectMapper);
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterName() != null;
    }

    @Override
    protected String resolveArgumentName(MethodParameter parameter) {
        return parameter.getParameterName();
    }

}

