package dgs.graphql.nf.internal.utils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;

import org.springframework.web.multipart.MultipartFile;

/**
 * This class is used only for logging purposes since we cannot serialize a MultipartFile to json otherwise.
 */
public final class MultipartFileSerializer extends StdSerializer<MultipartFile> {

    public MultipartFileSerializer() {
        super(MultipartFile.class);
    }

    @Override
    public void serialize( MultipartFile value,  JsonGenerator jgen,  SerializerProvider provider) throws IOException, JsonProcessingException {
        jgen.writeStartObject();
        jgen.writeStringField("name", value.getOriginalFilename());
        jgen.writeEndObject();
    }

}

