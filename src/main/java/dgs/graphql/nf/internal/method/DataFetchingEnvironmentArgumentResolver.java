package dgs.graphql.nf.internal.method;

import graphql.schema.DataFetchingEnvironment;

import dgs.graphql.nf.DgsDataFetchingEnvironment;

import org.springframework.core.MethodParameter;

/**
 * Resolves method arguments for parameters of type [DataFetchingEnvironment]
 * or [DgsDataFetchingEnvironment].
 */
public class DataFetchingEnvironmentArgumentResolver implements ArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        var t = parameter.getParameterType();
        return DgsDataFetchingEnvironment.class.equals(t) || DataFetchingEnvironment.class.equals(t);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, DataFetchingEnvironment dfe) {
        if (parameter.getParameterType().equals(DgsDataFetchingEnvironment.class) && !(dfe instanceof DgsDataFetchingEnvironment)) {
            return new DgsDataFetchingEnvironment(dfe);
        }
        return dfe;
    }

}

