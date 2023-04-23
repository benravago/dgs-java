package dgs.graphql.nf.internal.method;

import graphql.schema.DataFetchingEnvironment;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import dgs.graphql.nf.exceptions.DgsInvalidInputArgumentException;
import dgs.graphql.nf.internal.InputObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.MethodParameter;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.core.convert.support.DefaultConversionService;

public abstract class AbstractInputArgumentResolver implements ArgumentResolver {

    private static final Logger logger = LoggerFactory.getLogger(AbstractInputArgumentResolver.class);

    private final DefaultConversionService conversionService;
    private final ConcurrentMap<MethodParameter, String> argumentNameCache;

    public AbstractInputArgumentResolver( InputObjectMapper inputObjectMapper) {
        this.conversionService = new DefaultConversionService();
        this.argumentNameCache = new ConcurrentHashMap();
        this.conversionService.addConverter((GenericConverter)new InputObjectMapperConverter(inputObjectMapper));
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, DataFetchingEnvironment dfe) {
        var argumentName = getArgumentName(parameter);
        var value = dfe.getArgument(argumentName);

        var typeDescriptor = new TypeDescriptor(parameter);
        var convertedValue = this.convertValue(value, typeDescriptor);

        if (convertedValue == null && dfe.getFieldDefinition().getArguments().stream().noneMatch(it -> it.getName().equals(argumentName))) {
            logger.warn(
                "Unknown argument '{}'",
                argumentName
            );
        }
        return convertedValue;
    }

    protected abstract String resolveArgumentName(MethodParameter parameter);

    String getArgumentName(MethodParameter parameter) {
        var cachedName = argumentNameCache.get(parameter);
        if (cachedName != null) {
            return cachedName;
        }
        var name = this.resolveArgumentName(parameter);
        argumentNameCache.put(parameter, name);
        return name;
    }

    Object convertValue(Object source, TypeDescriptor target) {
        if (source == null) {
            return target.getType().equals(Optional.class) ? Optional.empty() : null;
        }

        if (target.getResolvableType().isInstance(source)) {
            return source;
        }

        if (Optional.class.equals(target.getType())) {
            var generic = target.getResolvableType().getGeneric(0);
            var elementType = new TypeDescriptor(generic, null, null);
            return Optional.ofNullable(this.convertValue(source, elementType));
        }
        var sourceType = TypeDescriptor.forObject((Object)source);
        if (conversionService.canConvert(sourceType, target)) {
            return conversionService.convert(source, sourceType, target);
        }
        throw new DgsInvalidInputArgumentException("Unable to convert from " + source.getClass() + " to " + target.getType(),null);
    }

}
