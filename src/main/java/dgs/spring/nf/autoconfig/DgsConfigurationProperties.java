package dgs.spring.nf.autoconfig;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for DGS framework.
 */
@ConfigurationProperties(prefix="dgs.graphql")
public final class DgsConfigurationProperties {

    /** Location of the GraphQL schema files. */
    private final List<String> schemaLocations;

    public static final String PREFIX = "dgs.graphql";

    public DgsConfigurationProperties(@DefaultValue(value={"classpath*:schema/**/*.graphql*"})  List<String> schemaLocations) {
        this.schemaLocations = schemaLocations;
    }

    public final List<String> getSchemaLocations() {
        return this.schemaLocations;
    }

}

