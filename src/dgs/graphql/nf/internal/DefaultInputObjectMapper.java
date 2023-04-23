package dgs.graphql.nf.internal;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;

import java.util.List;
import java.util.Map;
import java.util.Set;

import dgs.graphql.nf.exceptions.DgsInvalidInputArgumentException;
import dgs.graphql.nf.support.Kt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.util.ReflectionUtils;

public class DefaultInputObjectMapper implements InputObjectMapper {

    private final InputObjectMapper customInputObjectMapper;

    private final Logger logger = LoggerFactory.getLogger(InputObjectMapper.class);

    public DefaultInputObjectMapper(InputObjectMapper customInputObjectMapper) {
        this.customInputObjectMapper = customInputObjectMapper;
    }

    @Override
    public <T> T mapToJavaObject( Map<String, ?> inputMap,  Class<T> targetClass) {
        if (targetClass.equals(Object.class) || targetClass.equals(Map.class)) {
            return (T) inputMap;
        }

        var instance = Kt.newInstance(targetClass);
        var nrOfFieldErrors = 0;
        for (var it:inputMap.entrySet()) {
            var declaredField = ReflectionUtils.findField(targetClass, it.getKey());
            if (declaredField != null) {
                var fieldType = getFieldType(declaredField, targetClass);

                // resolve the field class we will map into, as well as an optional type argument in case such
                // class is a parameterized type, such as a List.
                Class fieldClass;
                Type fieldArgumentType = null;
                switch (fieldType) {
                    case ParameterizedType parameterizedType -> {
                        fieldClass = (Class) parameterizedType.getRawType();
                        fieldArgumentType = parameterizedType.getActualTypeArguments()[0];
                    }
                    case Class classType -> {
                        fieldClass = classType;
                    }
                    default -> {
                        fieldClass = Kt.call(() -> Class.forName(fieldType.getTypeName()));
                    }
                }

                var fieldValue = it.getValue();
                if (fieldValue instanceof Map mapValue) {
                    var mappedValue = mapToJavaObject(mapValue, fieldClass);
                    trySetField(declaredField, instance, mappedValue);
                } else if (fieldValue instanceof List listValue) {
                    var newList = convertList(listValue, targetClass, fieldClass, fieldArgumentType);
                    if (declaredField.getType().equals(Set.class)) {
                        trySetField(declaredField, instance, Set.of(newList));
                    } else {
                        trySetField(declaredField, instance, newList);
                    }
                } else if (fieldClass.isEnum()) {
                    var enumValue = Kt.enumValue(fieldClass, fieldValue.toString());
                    trySetField(declaredField, instance, enumValue);
                } else {
                    trySetField(declaredField, instance, it.getValue());
                }
            } else {
                this.logger.warn("Field '" + it.getKey() + "' was not found on Input object of type '" + targetClass + "'");
                ++nrOfFieldErrors;
            }
        }

        /**
         We can't error out if only some fields don't match.
         This would happen if new schema fields are added, but the Java type wasn't updated yet.
         If none of the fields match however, it's a pretty good indication that the wrong type was used, hence this check.
         */
        if (!inputMap.isEmpty() && nrOfFieldErrors == inputMap.size()) {
            throw new DgsInvalidInputArgumentException("Input argument type '" + targetClass + "' doesn't match input " + inputMap, null);
        }

        return instance;
    }

    void trySetField(Field declaredField, Object instance, Object value) {
        try {
            declaredField.setAccessible(true);
            declaredField.set(instance, value);
        }
        catch (Exception ex) {
            throw new DgsInvalidInputArgumentException(
                "Invalid input argument `" + value + "` for field `" + declaredField.getName() +
                "` on type `" + (instance != null ? instance.getClass().getName() : null) + "`", null);
        }
    }

    Type getFieldType(Field field, Class<?> targetClass) {
        var genericSuperclass = targetClass.getGenericSuperclass();
        var fieldType = field.getGenericType();
        if (fieldType instanceof ParameterizedType parameterizedType && parameterizedType.getActualTypeArguments().length == 1) {
            return parameterizedType.getActualTypeArguments()[0];
        } else if (genericSuperclass instanceof ParameterizedType parameterizedType && !field.getType().equals(field.getGenericType())) {
            return argumentType(parameterizedType, fieldType.getTypeName());
        } else {
            return field.getType();
        }
    }

    List<?> convertList(
        List<?> input,
        Class<?> targetClass,
        Class<?> nestedClass,
        Type nestedType
    ) {
        var mappedList = input.stream().filter(i -> i != null).<Object>map(item -> {
            if (item instanceof List listItem) {
                return switch (nestedType) {
                    case ParameterizedType parameterizedType ->
                        convertList(
                            listItem,
                            targetClass,
                            (Class<?>)parameterizedType.getRawType(),
                            parameterizedType.getActualTypeArguments()[0]
                        );
                    case TypeVariable typeVariable -> {
                        var parameterizedType = (ParameterizedType) targetClass.getGenericSuperclass();
                        var parameterType = argumentType(parameterizedType, nestedType.getTypeName());
                        yield convertList(listItem, targetClass, (Class<?>)parameterType, null);
                    }
                    case WildcardType wildcardType ->
                        // We are assuming that the upper-bound type is a Class and not a Parametrized Type.
                        convertList(listItem, targetClass, (Class<?>)wildcardType.getUpperBounds()[0], null);

                    case Class classType ->
                        convertList(listItem, targetClass, classType, null);

                    default -> listItem;
                };
            } else if (nestedClass.isEnum()) {
                return Kt.enumValue(nestedClass, item.toString());
            } else if (item instanceof Map mapItem) {
                return (nestedClass.equals(Object.class)) ? mapItem : mapToJavaObject(mapItem, nestedClass);
            } else {
                return item;
            }
        }).toList();

        return mappedList;
    }

    //public DefaultInputObjectMapper() {
    //    this(null, 1, null);
    //}

    Type argumentType(ParameterizedType parameterizedType, String typeName) {
        var typeParameters = ((Class<?>)parameterizedType.getRawType()).getTypeParameters();
        var indexOfTypeParameter = 0;
        for (var it:typeParameters) {
            if (it.getName().equals(typeName)) {
                return parameterizedType.getActualTypeArguments()[indexOfTypeParameter];
            }
            indexOfTypeParameter++;
        }
        return null; // should not occur
    }

}

