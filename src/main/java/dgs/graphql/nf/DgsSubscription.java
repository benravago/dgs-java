package dgs.graphql.nf;

import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@DgsData(parentType = "Subscription")
@Inherited
public @interface DgsSubscription {
    @AliasFor(annotation = DgsData.class)
    String field() default "";
}
