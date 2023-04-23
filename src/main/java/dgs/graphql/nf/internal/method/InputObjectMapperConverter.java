package dgs.graphql.nf.internal.method;

import java.util.Map;
import java.util.Set;

import dgs.graphql.nf.internal.InputObjectMapper;

import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalGenericConverter;
import org.springframework.core.convert.converter.GenericConverter;

public class InputObjectMapperConverter implements ConditionalGenericConverter {

    private final InputObjectMapper inputObjectMapper;

    public InputObjectMapperConverter(InputObjectMapper inputObjectMapper) {
        this.inputObjectMapper = inputObjectMapper;
    }

    @Override
    public Set<GenericConverter.ConvertiblePair> getConvertibleTypes() {
        return Set.of(new GenericConverter.ConvertiblePair(Map.class, Object.class));
    }

    @Override
    public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
        return sourceType.isMap() && !targetType.isMap();
    }

    @Override
    public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
        var mapInput = (Map)source;
        return inputObjectMapper.mapToJavaObject(mapInput, targetType.getType());
    }

}
