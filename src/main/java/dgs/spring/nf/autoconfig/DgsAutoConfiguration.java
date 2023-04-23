package dgs.spring.nf.autoconfig;

import graphql.execution.AsyncExecutionStrategy;
import graphql.execution.AsyncSerialExecutionStrategy;
import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.ExecutionIdProvider;
import graphql.execution.ExecutionStrategy;
import graphql.execution.instrumentation.ChainedInstrumentation;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.preparsed.PreparsedDocumentProvider;
import graphql.schema.DataFetcherFactory;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.visibility.DefaultGraphqlFieldVisibility;
import graphql.schema.visibility.GraphqlFieldVisibility;
import graphql.schema.visibility.NoIntrospectionGraphqlFieldVisibility;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import dgs.graphql.nf.DgsFederationResolver;
import dgs.graphql.nf.DgsQueryExecutor;
import dgs.graphql.nf.context.DgsCustomContextBuilder;
import dgs.graphql.nf.context.DgsCustomContextBuilderWithRequest;
import dgs.graphql.nf.context.GraphQLContextContributor;
import dgs.graphql.nf.context.GraphQLContextContributorInstrumentation;
import dgs.graphql.nf.exceptions.DefaultDataFetcherExceptionHandler;
import dgs.graphql.nf.internal.DataFetcherResultProcessor;
import dgs.graphql.nf.internal.DefaultDgsGraphQLContextBuilder;
import dgs.graphql.nf.internal.DefaultDgsQueryExecutor;
import dgs.graphql.nf.internal.DgsDataLoaderProvider;
import dgs.graphql.nf.internal.DgsQueryExecutorRequestCustomizer;
import dgs.graphql.nf.internal.DgsSchemaProvider;
import dgs.graphql.nf.internal.EntityFetcherRegistry;
import dgs.graphql.nf.internal.QueryValueCustomizer;
import dgs.graphql.nf.internal.ReactiveDataFetcherResultProcessor;
import dgs.graphql.nf.internal.method.ArgumentResolver;
import dgs.graphql.nf.internal.method.MethodDataFetcherFactory;
import dgs.graphql.nf.scalars.UploadScalar;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;

@AutoConfiguration
@EnableConfigurationProperties(value={DgsConfigurationProperties.class})
@ImportAutoConfiguration(classes={JacksonAutoConfiguration.class, DgsInputArgumentConfiguration.class})
public class DgsAutoConfiguration {

    private final DgsConfigurationProperties configProps;

    public static final String AUTO_CONF_PREFIX = "dgs.graphql";

    public DgsAutoConfiguration(DgsConfigurationProperties configProps) {
        this.configProps = configProps;
    }

    @Bean
    @Order(PriorityOrdered.HIGHEST_PRECEDENCE)
    public Instrumentation graphQLContextContributionInstrumentation(
        ObjectProvider<GraphQLContextContributor> graphQLContextContributors
    ) {
        return new GraphQLContextContributorInstrumentation(graphQLContextContributors.orderedStream().toList());
    }

    @Bean
    public DgsQueryExecutor dgsQueryExecutor(
        ApplicationContext applicationContext,
        GraphQLSchema schema,
        DgsSchemaProvider schemaProvider,
        DgsDataLoaderProvider dgsDataLoaderProvider,
        DefaultDgsGraphQLContextBuilder dgsContextBuilder,
        DataFetcherExceptionHandler dataFetcherExceptionHandler,
        ObjectProvider<Instrumentation> instrumentations,
        Environment environment,
        @Qualifier(value="query") Optional<ExecutionStrategy> providedQueryExecutionStrategy,
        @Qualifier(value="mutation") Optional<ExecutionStrategy> providedMutationExecutionStrategy,
        Optional<ExecutionIdProvider> idProvider,
        DefaultDgsQueryExecutor.ReloadSchemaIndicator reloadSchemaIndicator,
        ObjectProvider<PreparsedDocumentProvider> preparsedDocumentProvider,
        QueryValueCustomizer queryValueCustomizer,
        ObjectProvider<DgsQueryExecutorRequestCustomizer> requestCustomizer
    ) {
        var queryExecutionStrategy =
            providedQueryExecutionStrategy.orElse(new AsyncExecutionStrategy(dataFetcherExceptionHandler));
        var mutationExecutionStrategy =
            providedMutationExecutionStrategy.orElse(new AsyncSerialExecutionStrategy(dataFetcherExceptionHandler));

        var instrumentationImpls = instrumentations.orderedStream().toList();
        var instrumentation =
            instrumentationImpls.size() == 1 ? instrumentationImpls.get(0) : // .single()
            !instrumentationImpls.isEmpty() ? new ChainedInstrumentation(instrumentationImpls) :
            null;

        return new DefaultDgsQueryExecutor(
            schema,
            schemaProvider,
            dgsDataLoaderProvider,
            dgsContextBuilder,
            instrumentation,
            queryExecutionStrategy,
            mutationExecutionStrategy,
            idProvider,
            reloadSchemaIndicator,
            preparsedDocumentProvider.getIfAvailable(),
            queryValueCustomizer,
            requestCustomizer.getIfAvailable(DgsQueryExecutorRequestCustomizer::DEFAULT_REQUEST_CUSTOMIZER)
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public QueryValueCustomizer defaultQueryValueCustomizer() {
        return (query) -> query;
    }

    @Bean
    @ConditionalOnMissingBean
    public DgsDataLoaderProvider dgsDataLoaderProvider(ApplicationContext applicationContext) {
        return new DgsDataLoaderProvider(applicationContext);
    }

    /**
     * Used by the [DefaultDgsQueryExecutor], it controls if, and when, such executor should reload the schema.
     * This implementation will return either the boolean value of the `dgs.reload` flag
     * or `true` if the `laptop` profile is an active Spring Boot profiles.
     * <p>
     * You can provide a bean of type [ReloadSchemaIndicator] if you want to control when the
     * [DefaultDgsQueryExecutor] should reload the schema.
     *
     * @implSpec the implementation of such bean should be thread-safe.
     */
    @Bean
    @ConditionalOnMissingBean
    public DefaultDgsQueryExecutor.ReloadSchemaIndicator defaultReloadSchemaIndicator(Environment environment) {
        var isLaptopProfile = Arrays.stream(environment.getActiveProfiles()).anyMatch(it -> it.equals("laptop"));
        var hotReloadSetting = environment.getProperty("dgs.reload", Boolean.class, isLaptopProfile);

        return () -> hotReloadSetting;
    }

    @Bean
    @ConditionalOnMissingBean
    public DgsSchemaProvider dgsSchemaProvider(
        ApplicationContext applicationContext,
        Optional<DgsFederationResolver> federationResolver,
        Optional<TypeDefinitionRegistry> existingTypeDefinitionFactory,
        Optional<GraphQLCodeRegistry> existingCodeRegistry,
        List<DataFetcherResultProcessor> dataFetcherResultProcessors,
        Optional<DataFetcherExceptionHandler> dataFetcherExceptionHandler,
        EntityFetcherRegistry entityFetcherRegistry,
        Optional<DataFetcherFactory<?>> defaultDataFetcherFactory,
        MethodDataFetcherFactory methodDataFetcherFactory
        // ObjectProvider<MockProvider> mockProviders,
    ) {
        return new DgsSchemaProvider(
            applicationContext,
            federationResolver,
            existingTypeDefinitionFactory,
            configProps.getSchemaLocations(),
            dataFetcherResultProcessors,
            dataFetcherExceptionHandler, // = Optional.empty(),
            entityFetcherRegistry,
            defaultDataFetcherFactory, // = Optional.empty(),
            methodDataFetcherFactory,
            null
            // Set.of(mockProviders),
        );
    }

    @Bean
    public EntityFetcherRegistry entityFetcherRegistry() {
        return new EntityFetcherRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public DataFetcherExceptionHandler dataFetcherExceptionHandler() {
        return new DefaultDataFetcherExceptionHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    public GraphQLSchema schema(DgsSchemaProvider dgsSchemaProvider, GraphqlFieldVisibility fieldVisibility) {
        return dgsSchemaProvider.schema(null, fieldVisibility);
    }

    @Bean
    @ConditionalOnProperty(
        prefix = "dgs.graphql.introspection",
        name = {"enabled"},
        havingValue = "false",
        matchIfMissing = false
    )
    public GraphqlFieldVisibility noIntrospectionFieldVisibility() {
        return NoIntrospectionGraphqlFieldVisibility.NO_INTROSPECTION_FIELD_VISIBILITY;
    }

    @Bean
    @ConditionalOnMissingBean
    public GraphqlFieldVisibility defaultFieldVisibility() {
        return DefaultGraphqlFieldVisibility.DEFAULT_FIELD_VISIBILITY;
    }

    @Bean
    @ConditionalOnMissingBean
    public DefaultDgsGraphQLContextBuilder graphQLContextBuilder(
        Optional<DgsCustomContextBuilder<?>> dgsCustomContextBuilder,
        Optional<DgsCustomContextBuilderWithRequest<?>> dgsCustomContextBuilderWithRequest
    ) {
        return new DefaultDgsGraphQLContextBuilder(dgsCustomContextBuilder, dgsCustomContextBuilderWithRequest);
    }

    @Bean
    public UploadScalar uploadScalar() {
        return new UploadScalar();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = {"reactor.core.publisher.Mono"})
    public ReactiveDataFetcherResultProcessor.MonoDataFetcherResultProcessor monoReactiveDataFetcherResultProcessor() {
        return new ReactiveDataFetcherResultProcessor.MonoDataFetcherResultProcessor();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = {"reactor.core.publisher.Flux"})
    public ReactiveDataFetcherResultProcessor.FluxDataFetcherResultProcessor fluxReactiveDataFetcherResultProcessor() {
        return new ReactiveDataFetcherResultProcessor.FluxDataFetcherResultProcessor();
    }

    @Bean
    public MethodDataFetcherFactory methodDataFetcherFactory(ObjectProvider<ArgumentResolver> argumentResolvers) {
        return new MethodDataFetcherFactory(argumentResolvers.orderedStream().toList(), null);
    }

    @Bean
    @ConditionalOnClass(name = {"org.springframework.mock.web.MockHttpServletRequest"})
    public DgsQueryExecutorRequestCustomizer mockRequestHeaderCustomizer() {
        return null;

        /**
         * [DgsQueryExecutorRequestCustomizer] implementation which copies headers into
         * the request if the request is [MockHttpServletRequest]; intendeded to support
         * test use cases.
         **
        return object : DgsQueryExecutorRequestCustomizer {
            override fun apply(request: WebRequest?, headers: HttpHeaders?): WebRequest? {
                if (headers.isNullOrEmpty() || request !is NativeWebRequest) {
                    return request
                }
                val mockRequest = request.nativeRequest as? MockHttpServletRequest
                    ?: return request
                headers.forEach { key, value ->
                    if (mockRequest.getHeader(key) == null) {
                        mockRequest.addHeader(key, value)
                    }
                }
                return request
            }

            override fun toString(): String {
                return "{MockRequestHeaderCustomizer}"
            }
        }
        */
    }

}
