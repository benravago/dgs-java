package dgs.graphql.nf.internal.method;

import graphql.schema.DataFetchingEnvironment;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.core.MethodParameter;

/**
 * Resolves method parameters by delegating to the supplied list of
 * [argument resolvers][ArgumentResolver].
 * Previously resolved method parameters are cached.
 */
public class ArgumentResolverComposite implements ArgumentResolver {

    private final List<ArgumentResolver> argumentResolvers;
    private final ConcurrentMap<MethodParameter, ArgumentResolver> argumentResolverCache;

    public ArgumentResolverComposite(List<ArgumentResolver> argumentResolvers) {
        this.argumentResolvers = argumentResolvers;
        this.argumentResolverCache = new ConcurrentHashMap();
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return getArgumentResolver(parameter) != null;
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, DataFetchingEnvironment dfe) {
        var resolver = this.getArgumentResolver(parameter);
        if (resolver == null) {
            throw new IllegalArgumentException("Unsupported parameter type [" + parameter.getParameterType().getName() + "]. supportsParameter should be called first.");
        }
        return resolver.resolveArgument(parameter, dfe);
    }

    ArgumentResolver getArgumentResolver(MethodParameter parameter) {
        var cachedResolver = argumentResolverCache.get(parameter);
        if (cachedResolver != null) {
            return cachedResolver;
        }
        for (var resolver : argumentResolvers) {
            if (resolver.supportsParameter(parameter)) {
                argumentResolverCache.put(parameter, resolver);
                return resolver;
            }
        }
        return null;
    }

}

