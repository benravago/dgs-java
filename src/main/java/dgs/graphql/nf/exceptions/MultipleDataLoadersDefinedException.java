package dgs.graphql.nf.exceptions;

public class MultipleDataLoadersDefinedException extends RuntimeException {

    public MultipleDataLoadersDefinedException( Class<?> clazz) {
        super("Multiple data loaders found, unable to disambiguate for " + clazz.getName() + ".");
    }

}

