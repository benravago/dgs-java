package dgs.graphql.nf;

import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@DgsData(parentType = "Mutation")
@Inherited
public @interface DgsMutation {
    @AliasFor(annotation = DgsData.class)
    String field() default "";
}
