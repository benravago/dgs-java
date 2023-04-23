package dgs.graphql.nf.exceptions;

public class NoDataLoaderFoundException extends RuntimeException {

    public NoDataLoaderFoundException( Class<?> clazz) {
        super("No data loader found. Missing @DgsDataLoader for " + clazz.getName() + ".");
    }

}

