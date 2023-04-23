package dgs.graphql.nf;

import java.lang.annotation.*;

/**
 * Mark a method to be a data fetcher.
 * A data fetcher can receive the DataFetchingEnvironment.
 * The "parentType" property is the type that contains this field.
 * For root queries that is "Query", and for root mutations "Mutation".
 * The field is the name of the field this data fetcher is responsible for.
 * See https://netflix.github.io/dgs/getting-started/
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(DgsData.List.class)
@Inherited
public @interface DgsData {
    String parentType();

    String field() default "";


    /**
     * Container annotation that aggregates several {@link DgsData @DgsData} annotations.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @Documented
    @interface List {

        /**
         * Return the contained {@link DgsData} associated with this method.
         */
        DgsData[] value();
    }
}
