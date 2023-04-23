package dgs.spring.nf.autoconfig;

import dgs.graphql.nf.internal.DefaultInputObjectMapper;
import dgs.graphql.nf.internal.InputObjectMapper;
import dgs.graphql.nf.internal.method.ArgumentResolver;
import dgs.graphql.nf.internal.method.DataFetchingEnvironmentArgumentResolver;
import dgs.graphql.nf.internal.method.FallbackEnvironmentArgumentResolver;
import dgs.graphql.nf.internal.method.InputArgumentResolver;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DgsInputArgumentConfiguration {

    @Bean
    public ArgumentResolver inputArgumentResolver( InputObjectMapper inputObjectMapper) {
        return new InputArgumentResolver(inputObjectMapper);
    }

    @Bean
    public ArgumentResolver dataFetchingEnvironmentArgumentResolver() {
        return new DataFetchingEnvironmentArgumentResolver();
    }

    @Bean
    public ArgumentResolver fallbackEnvironmentArgumentResolver( InputObjectMapper inputObjectMapper) {
        return new FallbackEnvironmentArgumentResolver(inputObjectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public InputObjectMapper defaultInputObjectMapper() {
        return new DefaultInputObjectMapper(null);
    }

}

