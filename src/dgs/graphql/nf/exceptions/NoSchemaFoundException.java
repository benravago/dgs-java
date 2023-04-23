package dgs.graphql.nf.exceptions;

public class NoSchemaFoundException extends RuntimeException {

    public NoSchemaFoundException() {
        super("No schema files found. Define schemas in src/main/resources/schema/*.graphqls");
    }

}

