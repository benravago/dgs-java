package dgs.graphql.nf.exceptions;

public final class UnsupportedSecuredDataLoaderException extends RuntimeException {

    public UnsupportedSecuredDataLoaderException( Class<?> clazz) {
        super("Field level @DgsDataLoader is not supported on classes that use @Secured. Move your @DgsDataLoader to its own class. The offending field is in: " + clazz.getName());
    }

}

