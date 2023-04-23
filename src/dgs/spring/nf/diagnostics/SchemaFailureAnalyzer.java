package dgs.spring.nf.diagnostics;

import graphql.schema.idl.errors.SchemaProblem;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Spring failure analyzer that reports schema problems at startup in a more readable way.
 */
public final class SchemaFailureAnalyzer extends AbstractFailureAnalyzer<SchemaProblem> {
    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, SchemaProblem cause) {
        var errors = cause.getErrors().stream().map(it -> it.toString()).toList();

        return new FailureAnalysis(
            "There are problems with the GraphQL Schema:\n" +
                "\t * " + String.join("\n\t * ", errors) + "\n",
            null,
            cause
        );
    }

}
