package dgs.graphql.nf.internal;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

import java.lang.reflect.Method;
import java.util.Arrays;

import java.util.List;

import dgs.graphql.nf.internal.method.ArgumentResolverComposite;

import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.SynthesizingMethodParameter;
import org.springframework.util.ReflectionUtils;

public class DataFetcherInvoker implements DataFetcher<Object> {

    private final Method bridgedMethod;

    private final List<? extends MethodParameter> methodParameters;

    private final Object dgsComponent;
    private final ArgumentResolverComposite resolvers;

    public DataFetcherInvoker(
        Object dgsComponent,
        Method method,
        ArgumentResolverComposite resolvers,
        ParameterNameDiscoverer parameterNameDiscoverer
    ) {
        this.dgsComponent = dgsComponent;
        this.resolvers = resolvers;

        bridgedMethod = BridgeMethodResolver.findBridgedMethod(method);

        methodParameters = Arrays.stream(bridgedMethod.getParameters())
            .map(parameter -> {
                var methodParameter = SynthesizingMethodParameter.forParameter(parameter);
                methodParameter.initParameterNameDiscovery(parameterNameDiscoverer);
                return methodParameter;
            })
            .toList();

        ReflectionUtils.makeAccessible(bridgedMethod);
    }

    public Object get( DataFetchingEnvironment environment) {
        if (methodParameters.isEmpty()) {
            return ReflectionUtils.invokeMethod(bridgedMethod, dgsComponent);
        }

        var args = new Object[methodParameters.size()];
        var idx = 0;
        for (var parameter:methodParameters) {
            if (!resolvers.supportsParameter(parameter)) {
                throw new IllegalStateException(formatArgumentError(parameter, "No suitable resolver"));
            }
            args[idx++] = resolvers.resolveArgument(parameter, environment);
        }
        return ReflectionUtils.invokeMethod(bridgedMethod, dgsComponent, args);
    }

    String formatArgumentError(MethodParameter param, String message) {
        return "Could not resolve parameter [" + param.getParameterIndex() + "] in " +
            param.getExecutable().toGenericString() + (message.isBlank() ? "" : ": " + message);
    }

}
