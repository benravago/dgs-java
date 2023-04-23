package dgs.spring.nf.autoconfig;

import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaDirectiveWiring;
import graphql.validation.rules.ValidationRules;
import graphql.validation.schemawiring.ValidationSchemaWiring;

import dgs.graphql.nf.DgsComponent;
import dgs.graphql.nf.DgsRuntimeWiring;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@ConditionalOnClass(ValidationRules.class)
@ConditionalOnProperty(
    prefix = "dgs.graphql.extensions.validation",
    name = {"enabled"},
    havingValue = "true",
    matchIfMissing = true
)
@AutoConfiguration
public class DgsExtendedValidationAutoConfiguration {

    @Bean
    public ExtendedValidationRegistrar defaultExtendedValidationRegistrar(ObjectProvider<ValidationRulesBuilderCustomizer> validationRulesCustomizerProvider) {
        return new DefaultExtendedValidationRegistrar(validationRulesCustomizerProvider);
    }

    @DgsComponent
    @FunctionalInterface
    public static interface ExtendedValidationRegistrar {
        public RuntimeWiring.Builder addValidationRules(RuntimeWiring.Builder builder);
    }

    public static class DefaultExtendedValidationRegistrar implements ExtendedValidationRegistrar {

        private final ObjectProvider<ValidationRulesBuilderCustomizer> validationRulesCustomizerProvider;

        public DefaultExtendedValidationRegistrar(ObjectProvider<ValidationRulesBuilderCustomizer> validationRulesCustomizerProvider) {
            this.validationRulesCustomizerProvider = validationRulesCustomizerProvider;
        }

        @Override
        @DgsRuntimeWiring
        public RuntimeWiring.Builder addValidationRules( RuntimeWiring.Builder builder) {
            var validationRulesBuilder = ValidationRules.newValidationRules();
            validationRulesCustomizerProvider.ifAvailable(it -> it.customize(validationRulesBuilder));

            var validationRules = validationRulesBuilder.build();
            var schemaWiring = new ValidationSchemaWiring(validationRules);
            // we add this schema wiring to the graphql runtime
            return builder.directiveWiring((SchemaDirectiveWiring)schemaWiring);
        }
    }

}
