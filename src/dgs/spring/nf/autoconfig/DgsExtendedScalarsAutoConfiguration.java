package dgs.spring.nf.autoconfig;

import graphql.scalars.ExtendedScalars;
import graphql.schema.GraphQLScalarType;
import graphql.schema.idl.RuntimeWiring;

import java.util.List;
import java.util.ListIterator;

import dgs.graphql.nf.DgsComponent;
import dgs.graphql.nf.DgsRuntimeWiring;
import java.util.Collections;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ConfigurationCondition;

@ConditionalOnClass(ExtendedScalars.class)
@ConditionalOnProperty(
    prefix = "dgs.graphql.extensions.scalars",
    name = {"enabled"},
    havingValue = "true",
    matchIfMissing = true
)
@AutoConfiguration
public class DgsExtendedScalarsAutoConfiguration {

    @ConditionalOnProperty(
        prefix = "dgs.graphql.extensions.scalars.time-dates",
        name = {"enabled"},
        havingValue = "true",
        matchIfMissing = true
    )
    @Configuration(proxyBeanMethods = false)
    public static class TimeExtendedScalarsAutoConfiguration {
        @Bean
        public ExtendedScalarRegistrar timesExtendedScalarsRegistrar() {
            return new AbstractExtendedScalarRegistrar() {
                @Override
                public List<GraphQLScalarType> getScalars() {
                    return List.of(
                        ExtendedScalars.DateTime,
                        ExtendedScalars.Date,
                        ExtendedScalars.Time,
                        ExtendedScalars.LocalTime)
                    ;
                }
            };
        }
    }

    @ConditionalOnProperty(
        prefix = "dgs.graphql.extensions.scalars.objects",
        name = {"enabled"},
        havingValue = "true",
        matchIfMissing = true
    )
    @Configuration(proxyBeanMethods = false)
    public static class ObjectsExtendedScalarsAutoConfiguration {
        @Bean
        public ExtendedScalarRegistrar objectsExtendedScalarsRegistrar() {
            return new AbstractExtendedScalarRegistrar(){
                @Override
                public List<GraphQLScalarType> getScalars() {
                    return List.of(
                        ExtendedScalars.Object,
                        ExtendedScalars.Json,
                        ExtendedScalars.Url,
                        ExtendedScalars.Locale
                    );
                }
            };
        }
    }

    @ConditionalOnProperty(
        prefix = "dgs.graphql.extensions.scalars.numbers",
        name = {"enabled"},
        havingValue = "true",
        matchIfMissing = true
    )
    @Configuration(proxyBeanMethods=false)
    public static class NumbersExtendedScalarsAutoConfiguration {
        @Bean
        public ExtendedScalarRegistrar numbersExtendedScalarsRegistrar() {
            return new AbstractExtendedScalarRegistrar(){
                @Override
                public List<GraphQLScalarType> getScalars() {
                    return List.of(
                        // Integers
                        ExtendedScalars.PositiveInt,
                        ExtendedScalars.NegativeInt,
                        ExtendedScalars.NonNegativeInt,
                        ExtendedScalars.NonPositiveInt,
                        // Floats
                        ExtendedScalars.PositiveFloat,
                        ExtendedScalars.NegativeFloat,
                        ExtendedScalars.NonNegativeFloat,
                        ExtendedScalars.NonPositiveFloat,
                        // Others
                        ExtendedScalars.GraphQLLong,
                        ExtendedScalars.GraphQLShort,
                        ExtendedScalars.GraphQLByte
                    );
                }
            };
        }

        @Conditional(OnBigDecimalAndNumbers.class)
        @Configuration(proxyBeanMethods = false)
        public static class BigDecimalAutoConfiguration {
            @Bean
            public ExtendedScalarRegistrar bigDecimalExtendedScalarsRegistrar() {
                return new AbstractExtendedScalarRegistrar(){
                    @Override
                    public List<GraphQLScalarType> getScalars() {
                        return List.of(
                            // Others
                            ExtendedScalars.GraphQLBigDecimal
                        );
                    }
                };
            }
        }

        public static class OnBigDecimalAndNumbers extends AllNestedConditions {
            public OnBigDecimalAndNumbers() {
                super(ConfigurationCondition.ConfigurationPhase.PARSE_CONFIGURATION);
            }

            @ConditionalOnProperty(
                prefix = "dgs.graphql.extensions.scalars.numbers.",
                name = {"enabled"},
                havingValue = "true",
                matchIfMissing = true
            )
            public static class OnNumbers {}

            @ConditionalOnProperty(
                prefix = "dgs.graphql.extensions.scalars.numbers.bigdecimal",
                name = {"enabled"},
                havingValue = "true",
                matchIfMissing = true
            )
            public static class OnBigDecimal {}
        }

        @Conditional(OnBigIntegerAndNumbers.class)
        @Configuration(proxyBeanMethods = false)
        public static class BigIntegerAutoConfiguration {
            @Bean
            public ExtendedScalarRegistrar bigIntegerExtendedScalarsRegistrar() {
                return new AbstractExtendedScalarRegistrar(){
                    @Override
                    public List<GraphQLScalarType> getScalars() {
                        return List.of(ExtendedScalars.GraphQLBigInteger);
                    }
                };
            }
        }

        public static class OnBigIntegerAndNumbers extends AllNestedConditions {
            public OnBigIntegerAndNumbers() {
                super(ConfigurationCondition.ConfigurationPhase.PARSE_CONFIGURATION);
            }

            @ConditionalOnProperty(
                prefix = "dgs.graphql.extensions.scalars.numbers.",
                name = {"enabled"},
                havingValue = "true",
                matchIfMissing = true
            )
            public static class OnNumbers {}

            @ConditionalOnProperty(
                prefix = "dgs.graphql.extensions.scalars.numbers.biginteger",
                name = {"enabled"},
                havingValue = "true",
                matchIfMissing = true
            )
            public static class OnBigInteger {}
        }
    }

    @ConditionalOnProperty(
        prefix = "dgs.graphql.extensions.scalars.chars",
        name = {"enabled"},
        havingValue = "true",
        matchIfMissing = true
    )
    @Configuration(proxyBeanMethods = false)
    public static class CharsExtendedScalarsAutoConfiguration {
        @Bean
        public ExtendedScalarRegistrar charsExtendedScalarsRegistrar() {
            return new AbstractExtendedScalarRegistrar(){
                @Override
                public List<GraphQLScalarType> getScalars() {
                    return List.of(ExtendedScalars.GraphQLChar);
                }
            };
        }
    }

    @ConditionalOnProperty(
        prefix = "dgs.graphql.extensions.scalars.ids",
        name = {"enabled"},
        havingValue = "true",
        matchIfMissing = true
    )
    @Configuration(proxyBeanMethods = false)
    public static class IDsExtendedScalarsAutoConfiguration {
        @Bean
        public ExtendedScalarRegistrar idsExtendedScalarsRegistrar() {
            return new AbstractExtendedScalarRegistrar(){
                @Override
                public List<GraphQLScalarType> getScalars() {
                    return List.of(ExtendedScalars.UUID);
                }
            };
        }
    }

    @DgsComponent
    @FunctionalInterface
    public static interface ExtendedScalarRegistrar {
        public List<GraphQLScalarType> getScalars();
    }

    public static abstract class AbstractExtendedScalarRegistrar implements ExtendedScalarRegistrar {

        @DgsRuntimeWiring
        public final RuntimeWiring.Builder addScalar(RuntimeWiring.Builder builder) {
            // return getScalars().foldRight(builder) { a, acc -> acc.scalar(a) }
            
            var scalars = getScalars();
            Collections.reverse(scalars);
            return scalars.stream().reduce(builder, (acc, a) -> acc.scalar(a), (acc, a) -> acc);    
        }
    }

}
