package dgs.graphql.nf.exceptions;

public class MissingDgsEntityFetcherException extends RuntimeException {

    public MissingDgsEntityFetcherException( String type) {
        super("Missing @DgsEntityFetcher for type " + type);
    }

}

