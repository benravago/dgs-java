package dgs.graphql.nf.exceptions;

public class InvalidDataLoaderTypeException extends RuntimeException {

    public InvalidDataLoaderTypeException(Class<?> clazz) {
        super("@DgsDataLoader found that doesn't implement BatchLoader: " + clazz.getName() + ".");
    }

}

