package dgs.graphql.nf.internal;

import java.util.Map;

/**
 * SPI intended for other frameworks/libraries that need to customize how input objects are mapped.
 * Not intended to be used by most users.
 *
 * A custom mapper might call the DefaultInputObjectMapper, passing itself to the DefaultInputObjectMapper constructor.
 * The DefaultInputObjectMapper will invoke the custom mapper each time it goes a level deeper into a nested object structure.
 * This makes it possible to have a custom mapper that still mostly relies on the default one.
 * Be careful to not create an infinite recursion calling back and forward though!
 *
 * The input to the map methods is a map of values that are already converted by scalars.
 * The input IS NOT JSON. Attempting to use a JSON mapper to converting these values will result in incorrect scalar values.
 */
public interface InputObjectMapper {

    /**
     * Convert a map of input values to a Java object.
     * @param inputMap The fields for an input object represented as a Map. This can be a nested map if nested types are used. Note that the values in this map are already converted by the scalars representing these types.
     * @param targetClass The class to convert to.
     * @return The converted object
     */
    public <T> T mapToJavaObject(Map<String, ?> inputMap, Class<T> targetClass);

}

