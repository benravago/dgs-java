package dgs.graphql.nf.internal.method;

import dgs.graphql.nf.InputArgument;
import dgs.graphql.nf.internal.InputObjectMapper;

import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.MergedAnnotation;

/**
 * Resolves method arguments annotated with [InputArgument].
 *
 * Argument conversion responsibilities are handled by the supplied [InputObjectMapper].
 */
public class InputArgumentResolver extends AbstractInputArgumentResolver {

    public InputArgumentResolver(InputObjectMapper inputObjectMapper) {
        super(inputObjectMapper);
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(InputArgument.class);
    }

    @Override
    protected String resolveArgumentName(MethodParameter parameter) {
        var annotation = parameter.getParameterAnnotation(InputArgument.class);
        if (annotation == null) {
            throw new IllegalArgumentException("Unsupported parameter type [" + parameter.getParameterType().getName() + "]. supportsParameter should be called first.");
        }
        var mergedAnnotation = MergedAnnotation.from(annotation).synthesize();

        var name = mergedAnnotation.name();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                "Name for argument of type [" + parameter.getNestedParameterType().getName() +
                    "] not specified, and parameter name information not found in class file either."
            );
        }
        return name;
    }

}
