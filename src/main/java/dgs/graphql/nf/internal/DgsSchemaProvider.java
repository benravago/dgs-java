package dgs.graphql.nf.internal;

import graphql.execution.DataFetcherExceptionHandler;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import graphql.parser.MultiSourceReader;
import graphql.schema.Coercing;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetcherFactories;
import graphql.schema.DataFetcherFactory;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaDirectiveWiring;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeRuntimeWiring;
import graphql.schema.visibility.DefaultGraphqlFieldVisibility;
import graphql.schema.visibility.GraphqlFieldVisibility;

import java.io.InputStreamReader;

import java.lang.reflect.Method;

import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;
import java.util.stream.Stream;

import dgs.graphql.nf.DgsCodeRegistry;
import dgs.graphql.nf.DgsComponent;
import dgs.graphql.nf.DgsData;
import dgs.graphql.nf.DgsDataFetchingEnvironment;
import dgs.graphql.nf.DgsDefaultTypeResolver;
import dgs.graphql.nf.DgsDirective;
import dgs.graphql.nf.DgsEnableDataFetcherInstrumentation;
import dgs.graphql.nf.DgsEntityFetcher;
import dgs.graphql.nf.DgsFederationResolver;
import dgs.graphql.nf.DgsRuntimeWiring;
import dgs.graphql.nf.DgsScalar;
import dgs.graphql.nf.DgsTypeDefinitionRegistry;
import dgs.graphql.nf.DgsTypeResolver;
import dgs.graphql.nf.exceptions.InvalidDgsConfigurationException;
import dgs.graphql.nf.exceptions.InvalidTypeResolverException;
import dgs.graphql.nf.exceptions.NoSchemaFoundException;
import dgs.graphql.nf.federation.DefaultDgsFederationResolver;
import dgs.graphql.nf.internal.method.MethodDataFetcherFactory;
import dgs.graphql.nf.federation.Federation;
import dgs.graphql.nf.support.Kt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.ReflectionUtils;

/**
 * Main framework class that scans for components and configures a runtime executable schema.
 */
public final class DgsSchemaProvider {

    private final ApplicationContext applicationContext;
    private final Optional<DgsFederationResolver> federationResolver;
    private final Optional<TypeDefinitionRegistry> existingTypeDefinitionRegistry;
    private final List<String> schemaLocations;
    private final List<DataFetcherResultProcessor> dataFetcherResultProcessors;
    private final Optional<DataFetcherExceptionHandler> dataFetcherExceptionHandler;
    private final EntityFetcherRegistry entityFetcherRegistry;
    private final Optional<DataFetcherFactory<?>> defaultDataFetcherFactory;
    private final MethodDataFetcherFactory methodDataFetcherFactory;
    private final Predicate<Object> componentFilter;
    // private final Set<MockProvider> mockProviders;

    private final ReentrantReadWriteLock schemaReadWriteLock;
    private final Map<String, Boolean> dataFetcherInstrumentationEnabled;
    private final List<DataFetcherReference> dataFetchers;


    public DgsSchemaProvider(
        ApplicationContext applicationContext,
        Optional<DgsFederationResolver> federationResolver,
        Optional<TypeDefinitionRegistry> existingTypeDefinitionRegistry,
        List<String> schemaLocations,
        List<DataFetcherResultProcessor> dataFetcherResultProcessors,
        Optional<DataFetcherExceptionHandler> dataFetcherExceptionHandler,
        EntityFetcherRegistry entityFetcherRegistry,
        Optional<DataFetcherFactory<?>> defaultDataFetcherFactory,
        MethodDataFetcherFactory methodDataFetcherFactory,
        Predicate<Object> componentFilter
        // Set<MockProvider> mockProviders,
    ) {
        this.applicationContext = applicationContext;
        this.federationResolver = federationResolver;
        this.existingTypeDefinitionRegistry = existingTypeDefinitionRegistry;
        // TODO: set defaults
        this.schemaLocations = schemaLocations; // = listOf(DEFAULT_SCHEMA_LOCATION),
        this.dataFetcherResultProcessors = dataFetcherResultProcessors; // = emptyList(),
        this.dataFetcherExceptionHandler = dataFetcherExceptionHandler; // = Optional.empty(),
        this.entityFetcherRegistry = entityFetcherRegistry; // = EntityFetcherRegistry(),
        this.defaultDataFetcherFactory = defaultDataFetcherFactory; // Optional.empty(),
        this.methodDataFetcherFactory = methodDataFetcherFactory;
        this.componentFilter = componentFilter; // = { true }
        // this.mockProviders = mockProviders; = emptySet(),

        this.schemaReadWriteLock = new ReentrantReadWriteLock();
        this.dataFetcherInstrumentationEnabled = new LinkedHashMap<>();
        this.dataFetchers = new ArrayList<>();
    }

    /**
     * Returns an immutable list of [DataFetcherReference]s that were identified after the schema was loaded.
     * The returned list will be unstable until the [schema] is fully loaded.
     */
    public List<DataFetcherReference> resolvedDataFetchers() {
        var readLock = schemaReadWriteLock.readLock();
        readLock.lock();
        try { return Collections.unmodifiableList(dataFetchers); }
        finally { readLock.unlock(); }
    }

    /**
     * Given a field, expressed as a GraphQL `<Type>.<field name>` tuple, return...
     * 1. `true` if the given field has _instrumentation_ enabled, or is missing an explicit setting.
     * 2. `false` if the given field has _instrumentation_ explicitly disabled.
     *
     * The method should be considered unstable until the [schema] is fully loaded.
     */
    public boolean isFieldInstrumentationEnabled(String field) {
        var readLock = schemaReadWriteLock.readLock();
        readLock.lock();
        try { return dataFetcherInstrumentationEnabled.getOrDefault(field, true); }
        finally { readLock.unlock(); }
    }

    public GraphQLSchema schema(String schema, GraphqlFieldVisibility fieldVisibility) {
        var readLock = schemaReadWriteLock.readLock();
        int n = schemaReadWriteLock.getWriteHoldCount() == 0 ? schemaReadWriteLock.getReadHoldCount() : 0;
        for (int i = 0; i < n; ++i) readLock.unlock();
        var writeLock = schemaReadWriteLock.writeLock();
        writeLock.lock();
        try {
            dataFetchers.clear();
            dataFetcherInstrumentationEnabled.clear();
            return computeSchema(schema, fieldVisibility != null ? fieldVisibility : DefaultGraphqlFieldVisibility.DEFAULT_FIELD_VISIBILITY );
        }
        finally {
            for (int i = 0; i < n; ++i) readLock.lock();
            writeLock.unlock();
        }
    }

    GraphQLSchema computeSchema(String schema, GraphqlFieldVisibility fieldVisibility) {
        var startTime = System.currentTimeMillis();
        var dgsComponents =
            applicationContext.getBeansWithAnnotation(DgsComponent.class).values().stream().filter(componentFilter).toList();
        var hasDynamicTypeRegistry =
            dgsComponents.stream().anyMatch(it -> Arrays.stream(it.getClass().getMethods()).anyMatch(m -> m.isAnnotationPresent(DgsTypeDefinitionRegistry.class)));

        var mergedRegistry = (schema != null)
            ? new SchemaParser().parse(schema)
            : findSchemaFiles(hasDynamicTypeRegistry).stream().map(it ->
                // Convert reader kind for GraphQL Java to specify source name in a type definition's source location
                new SchemaParser().parse(
                    MultiSourceReader.newMultiSourceReader()
                        .reader(
                            new InputStreamReader(Kt.call(it::getInputStream), StandardCharsets.UTF_8),
                            it.getFilename())
                        .build()
                    )
                ).reduce((a,b) -> a.merge(b)).orElse(new TypeDefinitionRegistry());

        if (existingTypeDefinitionRegistry.isPresent()) {
            mergedRegistry = mergedRegistry.merge(existingTypeDefinitionRegistry.get());
        }

        var federationResolverInstance =
            federationResolver.orElseGet(() ->
                new DefaultDgsFederationResolver(
                    entityFetcherRegistry,
                    dataFetcherExceptionHandler
                )
            );

        var entityFetcher = federationResolverInstance.entitiesFetcher();
        var typeResolver = federationResolverInstance.typeResolver();
        var codeRegistryBuilder = GraphQLCodeRegistry.newCodeRegistry().fieldVisibility(fieldVisibility);
        if (defaultDataFetcherFactory.isPresent()) {
            codeRegistryBuilder.defaultDataFetcher(defaultDataFetcherFactory.get());
        }

        var runtimeWiringBuilder =
            RuntimeWiring.newRuntimeWiring().codeRegistry(codeRegistryBuilder).fieldVisibility(fieldVisibility);

        var _1 = mergedRegistry;
        mergedRegistry = dgsComponents.stream()
            .filter(c -> c != null)
            .map(dgsComponent -> invokeDgsTypeDefinitionRegistry(dgsComponent, _1) )
            .reduce((a,b) -> a.merge(b)).orElse(_1);

        findScalars(applicationContext, runtimeWiringBuilder);
        findDirectives(applicationContext, runtimeWiringBuilder);
        findDataFetchers(dgsComponents, codeRegistryBuilder, mergedRegistry);
        findTypeResolvers(dgsComponents, runtimeWiringBuilder, mergedRegistry);
        findEntityFetchers(dgsComponents);

        var mergedRegistry_ = mergedRegistry;
        dgsComponents.forEach(dgsComponent ->
            invokeDgsCodeRegistry(dgsComponent,
                codeRegistryBuilder,
                mergedRegistry_
            )
        );

        runtimeWiringBuilder.codeRegistry(codeRegistryBuilder.build());

        dgsComponents.forEach( dgsComponent -> invokeDgsRuntimeWiring(dgsComponent, runtimeWiringBuilder) );
        var graphQLSchema =
            Federation.transform(mergedRegistry, runtimeWiringBuilder.build()).fetchEntities(entityFetcher)
                .resolveEntityType(typeResolver).build();

        var endTime = System.currentTimeMillis();
        var totalTime = endTime - startTime;
        logger.debug("DGS initialized schema in {}ms", totalTime);

        // in com.netflix.graphql.mocking.DgsSchemaTransformer
        // return if (mockProviders.isNotEmpty()) {
        //   DgsSchemaTransformer().transformSchemaWithMockProviders(graphQLSchema, mockProviders)
        // } else {
        //   graphQLSchema
        // }

        return graphQLSchema;
    }

    TypeDefinitionRegistry invokeDgsTypeDefinitionRegistry(
        Object dgsComponent,
        TypeDefinitionRegistry registry
    ) {
        return Arrays.stream(dgsComponent.getClass().getMethods())
            .filter(it -> it.isAnnotationPresent(DgsTypeDefinitionRegistry.class) )
            .map(method -> {
                if (!method.getReturnType().equals(TypeDefinitionRegistry.class)) {
                    throw new InvalidDgsConfigurationException("Method annotated with @DgsTypeDefinitionRegistry must have return type TypeDefinitionRegistry");
                }
                if (method.getParameterCount() == 1 && method.getParameterTypes()[0].equals(TypeDefinitionRegistry.class)) {
                    return (TypeDefinitionRegistry) ReflectionUtils.invokeMethod(method, dgsComponent, registry);
                } else {
                    return (TypeDefinitionRegistry) ReflectionUtils.invokeMethod(method, dgsComponent);
                }
            }).reduce((a,b) -> a.merge(b)).orElse(null);
    }

    void invokeDgsCodeRegistry(
        Object dgsComponent,
        GraphQLCodeRegistry.Builder codeRegistryBuilder,
        TypeDefinitionRegistry registry
    ) {
        Arrays.stream(dgsComponent.getClass().getMethods())
            .filter(it -> it.isAnnotationPresent(DgsCodeRegistry.class) )
            .forEach(method -> {
                if (!method.getReturnType().equals(GraphQLCodeRegistry.Builder.class)) {
                    throw new InvalidDgsConfigurationException("Method annotated with @DgsCodeRegistry must have return type GraphQLCodeRegistry.Builder");
                }

                if (method.getParameterCount() != 2 || !method.getParameterTypes()[0].equals(GraphQLCodeRegistry.Builder.class) || !method.getParameterTypes()[1].equals(TypeDefinitionRegistry.class)) {
                    throw new InvalidDgsConfigurationException("Method annotated with @DgsCodeRegistry must accept the following arguments: GraphQLCodeRegistry.Builder, TypeDefinitionRegistry. " + dgsComponent.getClass().getName() + "." + method.getName() + " has the following arguments: " + Arrays.toString(method.getParameterTypes()));
                }

                ReflectionUtils.invokeMethod(method, dgsComponent, codeRegistryBuilder, registry);
            });
    }

    void invokeDgsRuntimeWiring(
        Object dgsComponent,
        RuntimeWiring.Builder runtimeWiringBuilder
    ) {
        Arrays.stream(dgsComponent.getClass().getMethods())
            .filter(it -> it.isAnnotationPresent(DgsRuntimeWiring.class) )
            .forEach(method -> {
                if (!method.getReturnType().equals(RuntimeWiring.Builder.class)) {
                    throw new InvalidDgsConfigurationException("Method annotated with @DgsRuntimeWiring must have return type RuntimeWiring.Builder");
                }

                if (method.getParameterCount() != 1 || !method.getParameterTypes()[0].equals(RuntimeWiring.Builder.class)) {
                    throw new InvalidDgsConfigurationException("Method annotated with @DgsRuntimeWiring must accept an argument of type RuntimeWiring.Builder. " + dgsComponent.getClass().getName() + "." + method.getName() + " has the following arguments: " + Arrays.toString(method.getParameterTypes()));
                }

                ReflectionUtils.invokeMethod(method, dgsComponent, runtimeWiringBuilder);
            });
    }

    void findDataFetchers(
        Collection<? extends Object> dgsComponents,
        GraphQLCodeRegistry.Builder codeRegistryBuilder,
        TypeDefinitionRegistry typeDefinitionRegistry
    ) {
        record p(Method method, MergedAnnotations mergedAnnotations) {}

        dgsComponents.forEach(dgsComponent -> {
            var javaClass = AopUtils.getTargetClass(dgsComponent);
            Arrays.stream(ReflectionUtils.getUniqueDeclaredMethods(javaClass, ReflectionUtils.USER_DECLARED_METHODS))
                .map(method -> {
                    var mergedAnnotations = MergedAnnotations
                        .from(method, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY);
                    return new p(method, mergedAnnotations);
                })
                .filter(p -> p.mergedAnnotations.isPresent(DgsData.class) )
                .forEach(p -> {
                    var filteredMergedAnnotations =
                        p.mergedAnnotations
                            .stream(DgsData.class)
                            .filter(it -> AopUtils.getTargetClass(((Method)it.getSource()).getDeclaringClass()) == AopUtils.getTargetClass(p.method.getDeclaringClass()) )
                            .toList();
                    filteredMergedAnnotations.forEach(dgsDataAnnotation ->
                        registerDataFetcher(
                            typeDefinitionRegistry,
                            codeRegistryBuilder,
                            dgsComponent,
                            p.method,
                            dgsDataAnnotation,
                            p.mergedAnnotations
                        )
                    );
                });
            });
    }

    void registerDataFetcher(
        TypeDefinitionRegistry typeDefinitionRegistry,
        GraphQLCodeRegistry.Builder codeRegistryBuilder,
        Object dgsComponent,
        Method method,
        MergedAnnotation<DgsData> dgsDataAnnotation,
        MergedAnnotations mergedAnnotations
    ) {
        var f = dgsDataAnnotation.getString("field");
        var field = (f == null || f.isBlank()) ? f : method.getName();
        var parentType = dgsDataAnnotation.getString("parentType");

        if (dataFetchers.stream().anyMatch(it -> it.parentType().equals(parentType) && it.field().equals(field))) {
            logger.error("Duplicate data fetchers registered for "+parentType+'.'+field);
            throw new InvalidDgsConfigurationException("Duplicate data fetchers registered for "+parentType+'.'+field);
        }

        dataFetchers.add(new DataFetcherReference(dgsComponent, method, mergedAnnotations, parentType, field));

        var enableInstrumentation = method.isAnnotationPresent(DgsEnableDataFetcherInstrumentation.class)
            ? method.getAnnotation(DgsEnableDataFetcherInstrumentation.class).value()
            : !method.getReturnType().equals(CompletionStage.class) && !method.getReturnType().equals(CompletableFuture.class);

        dataFetcherInstrumentationEnabled.put(parentType+'.'+field, enableInstrumentation);

        try {
            if (!typeDefinitionRegistry.getType(parentType).isPresent()) {
                logger.error("Parent type " + parentType + " not found, but it was referenced in " + getClass().getName() + " in @DgsData annotation for field " + field);
                throw new InvalidDgsConfigurationException("Parent type " + parentType + " not found, but it was referenced on " + getClass().getName() + " in @DgsData annotation for field " + field);
            }
            switch (typeDefinitionRegistry.getType(parentType).get()) {
                case InterfaceTypeDefinition type -> {
                    var implementationsOf = typeDefinitionRegistry.getImplementationsOf(type);
                    implementationsOf.forEach(implType -> {
                        var dataFetcher =
                            createBasicDataFetcher(method, dgsComponent, parentType.equals("Subscription"));
                        codeRegistryBuilder.dataFetcher(
                            FieldCoordinates.coordinates(implType.getName(), field),
                            dataFetcher
                        );
                        dataFetcherInstrumentationEnabled.put(implType.getName()+'.'+field, enableInstrumentation);
                    });
                }
                case UnionTypeDefinition type -> {
                    type.getMemberTypes().stream().filter(it -> it instanceof TypeName).forEach(t -> {
                        var memberType = (TypeName)t;
                        var dataFetcher =
                            createBasicDataFetcher(method, dgsComponent, parentType.equals("Subscription"));
                        codeRegistryBuilder.dataFetcher(
                            FieldCoordinates.coordinates(memberType.getName(), field),
                            dataFetcher
                        );
                        dataFetcherInstrumentationEnabled.put(memberType.getName()+'.'+field, enableInstrumentation);
                    });
                }
                default -> {
                    var dataFetcher = createBasicDataFetcher(method, dgsComponent, parentType.equals("Subscription"));
                    codeRegistryBuilder.dataFetcher(
                        FieldCoordinates.coordinates(parentType, field),
                        dataFetcher
                    );
                }
            }
        } catch (Exception ex) {
            logger.error("Invalid parent type $parentType");
            throw ex;
        }
    }

    void findEntityFetchers(
        Collection<? extends Object> dgsComponents
    ) {
        dgsComponents.forEach(dgsComponent -> {
            var javaClass = AopUtils.getTargetClass(dgsComponent);

            Arrays.stream(ReflectionUtils.getDeclaredMethods(javaClass))
                .filter(it -> it.isAnnotationPresent(DgsEntityFetcher.class) )
                .forEach(method -> {
                    var dgsEntityFetcherAnnotation = method.getAnnotation(DgsEntityFetcher.class);

                    var enableInstrumentation = method.getAnnotation(DgsEnableDataFetcherInstrumentation.class);

                    dataFetcherInstrumentationEnabled.put("__entities." + dgsEntityFetcherAnnotation.name(),
                        enableInstrumentation != null ? enableInstrumentation.value() : false);

                    entityFetcherRegistry.put(dgsEntityFetcherAnnotation.name(), dgsComponent, method);
                });
        });
    }

    DataFetcher<Object> createBasicDataFetcher(
        Method method,
        Object dgsComponent,
        boolean isSubscription
    ) {
        var dataFetcher = methodDataFetcherFactory.createDataFetcher(dgsComponent, method);

        if (isSubscription) {
            return dataFetcher;
        }

        return DataFetcherFactories.wrapDataFetcher(dataFetcher, (dfe, result) -> {
            if (result != null) {
                var env = new DgsDataFetchingEnvironment(dfe);
                var p = dataFetcherResultProcessors.stream().filter(it -> it.supportsType(result)).findFirst();
                return p.isPresent() ? p.get().process(result,env) : result;
            } else {
                return null;
            }
        });
    }

    void findTypeResolvers(
        Collection<? extends Object> dgsComponents,
        RuntimeWiring.Builder runtimeWiringBuilder,
        TypeDefinitionRegistry mergedRegistry
    ) {
        var registeredTypeResolvers = new HashSet<String>();

        dgsComponents.forEach(dgsComponent -> {
            var javaClass = AopUtils.getTargetClass(dgsComponent);
            Arrays.stream(javaClass.getMethods())
                .filter(it -> it.isAnnotationPresent(DgsTypeResolver.class) )
                .forEach(method -> {
                    var annotation = method.getAnnotation(DgsTypeResolver.class);

                    if (!method.getReturnType().equals(String.class)) {
                        throw new InvalidTypeResolverException("@DgsTypeResolvers must return String");
                    }

                    if (method.getParameterCount() != 1) {
                        throw new InvalidTypeResolverException("@DgsTypeResolvers must take exactly one parameter");
                    }

                    if (!mergedRegistry.hasType(new TypeName(annotation.name()))) {
                        throw new InvalidTypeResolverException("could not find type name '" + annotation.name() +"' in schema");
                    }

                    var overrideTypeResolver = false;
                    var defaultTypeResolver = method.getAnnotation(DgsDefaultTypeResolver.class);
                    if (defaultTypeResolver != null) {
                        overrideTypeResolver = dgsComponents.stream().anyMatch(component -> {
                            return Arrays.stream(component.getClass().getMethods()).anyMatch(_method -> {
                                return _method.isAnnotationPresent(DgsTypeResolver.class) &&
                                    _method.getAnnotation(DgsTypeResolver.class).name().equals(annotation.name()) &&
                                    !component.equals(dgsComponent);
                            });
                        });
                    }
                    // do not add the default resolver if another resolver with the same name is present
                    if (defaultTypeResolver == null || !overrideTypeResolver) {
                        registeredTypeResolvers.add(annotation.name());

                        runtimeWiringBuilder.type(
                            TypeRuntimeWiring.newTypeWiring(annotation.name())
                                .typeResolver(env -> {
                                    var typeName = (String)
                                        ReflectionUtils.invokeMethod(method, dgsComponent, env.getObject());
                                    return typeName != null
                                        ? env.getSchema().getObjectType(typeName)
                                        : null;
                                })
                        );
                    }
                });
        });

        // Add a fallback type resolver for types that don't have a type resolver registered.
        // This works when the Java type has the same name as the GraphQL type.
        // Check for unregistered interface types
        var unregisteredInterfaceTypes = mergedRegistry.types().entrySet()
            .stream() // name, typeDef
            .filter(e -> e.getValue() instanceof InterfaceTypeDefinition )
            .map(e -> e.getKey() )
            .filter(registeredTypeResolvers::contains)
            .toList();
        checkTypeResolverExists(unregisteredInterfaceTypes, runtimeWiringBuilder, "interface");

        // Check for unregistered union types
        var unregisteredUnionTypes = mergedRegistry.types().entrySet()
            .stream() // name, typeDef
            .filter(e -> e.getValue() instanceof UnionTypeDefinition )
            .map(e -> e.getKey())
            .filter(registeredTypeResolvers::contains)
            .toList();
        checkTypeResolverExists(unregisteredUnionTypes, runtimeWiringBuilder, "union");
    }

    void checkTypeResolverExists(
        Collection<String> unregisteredTypes,
        RuntimeWiring.Builder runtimeWiringBuilder,
        String typeName
    ) {
        unregisteredTypes.forEach(it ->
            runtimeWiringBuilder.type(
                TypeRuntimeWiring.newTypeWiring(it)
                    .typeResolver(env -> {
                        var instance = env.getObject();
                        var resolvedType = env.getSchema().getObjectType(instance.getClass().getSimpleName());
                        if (resolvedType == null) {
                            throw new InvalidTypeResolverException("The default type resolver could not find a suitable Java type for GraphQL " + typeName + " type `" + it + "`. Provide a @DgsTypeResolver for `" + instance.getClass().getSimpleName() + "`.");
                        }
                        return resolvedType;
                    })
            )
        );
    }

    void findScalars(
        ApplicationContext applicationContext,
        RuntimeWiring.Builder runtimeWiringBuilder
    ) {
        applicationContext.getBeansWithAnnotation(DgsScalar.class).forEach((x, scalarComponent) -> {
            var annotation = scalarComponent.getClass().getAnnotation(DgsScalar.class);
            switch(scalarComponent) {
                case Coercing coercing ->
                    runtimeWiringBuilder.scalar(
                        GraphQLScalarType.newScalar().name(annotation.name()).coercing(coercing).build()
                    );
                default -> throw new RuntimeException("Invalid @DgsScalar type: the class must implement graphql.schema.Coercing");
            }
        });
    }

    void findDirectives(
        ApplicationContext applicationContext,
        RuntimeWiring.Builder runtimeWiringBuilder
    ) {
        applicationContext.getBeansWithAnnotation(DgsDirective.class).forEach((x, directiveComponent) -> {
            var annotation = directiveComponent.getClass().getAnnotation(DgsDirective.class);
            switch (directiveComponent) {
                case SchemaDirectiveWiring schemaDirectiveWiring -> {
                    if (annotation.name().isBlank()) {
                        runtimeWiringBuilder.directiveWiring(schemaDirectiveWiring);
                    } else {
                        runtimeWiringBuilder.directive(annotation.name(), schemaDirectiveWiring);
                    }
                }
                default -> throw new RuntimeException("Invalid @DgsDirective type: the class must implement graphql.schema.idl.SchemaDirectiveWiring");
            }
        });
    }

    List<Resource> findSchemaFiles(
        boolean hasDynamicTypeRegistry
    ) {
        var cl = Thread.currentThread().getContextClassLoader();
        var resolver = new PathMatchingResourcePatternResolver(cl);

        List<Resource> schemas, metaInfSchemas;

        try {
            var resources = schemaLocations.stream()
                .flatMap(it -> Kt.call(() -> Arrays.stream(resolver.getResources(it))) )
                .distinct()
                .toList();
            if (resources.isEmpty()) {
                throw new NoSchemaFoundException();
            }
            schemas = resources;
        } catch (Exception ex) {
            if (existingTypeDefinitionRegistry.isPresent() || hasDynamicTypeRegistry) {
                logger.info("No schema files found, but a schema was provided as an TypeDefinitionRegistry");
                schemas = Collections.EMPTY_LIST;
            } else {
                logger.error("No schema files found in $schemaLocations. Define schema locations with property dgs.graphql.schema-locations");
                throw new NoSchemaFoundException();
            }
        }

        try {
            metaInfSchemas = List.of(resolver.getResources("classpath*:META-INF/schema/**/*.graphql*"));
        } catch (Exception ex) {
            metaInfSchemas = Collections.EMPTY_LIST;
        }

        return Stream.concat(schemas.stream(), metaInfSchemas.stream()).toList();
    }

    public static final String DEFAULT_SCHEMA_LOCATION = "classpath*:schema/**/*.graphql*";

    private static final Logger logger = LoggerFactory.getLogger(DgsSchemaProvider.class);

}
