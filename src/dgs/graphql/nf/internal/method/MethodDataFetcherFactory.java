package dgs.graphql.nf.internal.method;

import graphql.schema.DataFetcher;

import java.lang.reflect.Method;
import java.util.List;

import dgs.graphql.nf.internal.DataFetcherInvoker;

import org.springframework.core.ParameterNameDiscoverer;

/**
 * Factory for constructing a [DataFetcher] given a [DgsData] annotated method.
 *
 * Resolving of method arguments is handled by the supplied [argument resolvers][ArgumentResolver].
 */
public final class MethodDataFetcherFactory {

    private final ArgumentResolverComposite resolvers;
    private final ParameterNameDiscoverer parameterNameDiscoverer;

    public MethodDataFetcherFactory(List<ArgumentResolver> argumentResolvers, ParameterNameDiscoverer parameterNameDiscoverer) {
        this.parameterNameDiscoverer = parameterNameDiscoverer;
        this.resolvers = new ArgumentResolverComposite(argumentResolvers);
    }
 
    public final DataFetcher<Object> createDataFetcher(Object bean, Method method) {
        return new DataFetcherInvoker(bean, method, resolvers, parameterNameDiscoverer);
    }

}

