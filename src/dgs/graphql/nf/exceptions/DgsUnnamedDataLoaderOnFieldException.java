package dgs.graphql.nf.exceptions;

import java.lang.reflect.Field;

public class DgsUnnamedDataLoaderOnFieldException extends RuntimeException {

    public DgsUnnamedDataLoaderOnFieldException(Field field) {
        super("Field `" + field.getName() + "` in class `" + field.getDeclaringClass().getName() + "` was annotated with @DgsDataLoader, but the data loader was not given a proper name");
    }

}

