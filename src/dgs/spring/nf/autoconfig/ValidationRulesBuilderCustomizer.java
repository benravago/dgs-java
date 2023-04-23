package dgs.spring.nf.autoconfig;

import graphql.validation.rules.ValidationRules;

@FunctionalInterface
public interface ValidationRulesBuilderCustomizer {
    public void customize(ValidationRules.Builder builder);
}

