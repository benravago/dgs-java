package dgs.graphql.nf.scalars;

import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import graphql.schema.idl.RuntimeWiring;

import dgs.graphql.nf.DgsComponent;
import dgs.graphql.nf.DgsRuntimeWiring;

import org.springframework.web.multipart.MultipartFile;

@DgsComponent
public final class UploadScalar {

    private final GraphQLScalarType upload = GraphQLScalarType.newScalar().name("Upload")
        .description("A custom scalar that represents files")
        .coercing(MultipartFileCoercing.INSTANCE).build();;

    static class MultipartFileCoercing implements Coercing<MultipartFile, Void> {

        static final MultipartFileCoercing INSTANCE = new MultipartFileCoercing();

        @Override
        public Void serialize( Object dataFetcherResult) throws CoercingSerializeException {
            throw new CoercingSerializeException("Upload is an input-only type");
        }

        @Override
        public MultipartFile parseValue(Object input) throws CoercingParseValueException {
            if (input instanceof MultipartFile multipartFile) {
                return multipartFile;
            } else {
                throw new CoercingParseValueException(
                    "Expected type " +
                        MultipartFile.class.getName() +
                        " but was " +
                        input.getClass().getName()
                );
            }
        }

        @Override
        public MultipartFile parseLiteral(Object input) {
            throw new CoercingParseLiteralException(
                "Must use variables to specify Upload values"
            );
        }
    }

    // add the scalar manually since we can't use @DgsScalar in the framework
    @DgsRuntimeWiring
    public RuntimeWiring.Builder addScalar(RuntimeWiring.Builder builder) {
        return builder.scalar(upload);
    }

}

