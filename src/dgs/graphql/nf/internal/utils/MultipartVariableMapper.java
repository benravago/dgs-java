package dgs.graphql.nf.internal.utils;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.web.multipart.MultipartFile;

/**
 * This implementation has borrowed heavily from graphql-servlet-java implementation of the variable mapper.
 * It handles populating the query variables with the files specified by object paths in the multi-part request.
 * Specifically, it takes each entry here '-F map='{ "0": ["variables.input.files.0"], "1": ["variables.input.files.1"] }',
 * and uses the object path, e.g., variables.input.files.0, to navigate to the appropriate path in the query variables, i.e.,
 * "variables": { "input": { "description": "test", "files": [null, null] } } }' and sets it to the file specified as
 * -F '0=@file1.txt' -F '1=@file2.txt'
 *
 * The resulting map of populated query variables is the output.
 * Original => "variables": { "input": { "description": "test", "files": [null, null] } }
 * Transformed => "variables": { "input": { "description": "test", "files": [file1.txt, file2.txt] } }
 */
public final class MultipartVariableMapper {
    private MultipartVariableMapper() {}

    public static final MultipartVariableMapper INSTANCE = new MultipartVariableMapper();

    static final Pattern PERIOD = Pattern.compile("\\.");

    static final Mapper<Map<String, Object>> MAP_MAPPER = new Mapper<>(){
        @Override
        public Object set(Map<String, Object> location, String target, MultipartFile value) {
            return location.put(target, value);
        }
        @Override
        public Object recurse( Map<String, Object> location,  String target) {
            var object = location.get(target);
            if (object == null) throw new IllegalStateException("");
            return object;
        }
    };

    static final Mapper<List<Object>> LIST_MAPPER = new Mapper<>(){
        @Override
        public Object set(List<Object> location, String target, MultipartFile value) {
            return location.set(Integer.parseInt(target), value);
        }
        @Override
        public Object recurse( List<Object> location,  String target) {
            return location.get(Integer.parseInt(target));
        }
    };

    interface Mapper<T> {
        public Object set(T location, String target, MultipartFile value);
        public Object recurse(T location, String target);
    }

    public void mapVariable(String objectPath, Map<String,Object> variables, MultipartFile part) {
        var segments = PERIOD.split(objectPath);
        if (segments.length < 2) {
            throw new RuntimeException("object-path in map must have at least two segments");
        }
        if (!"variables".equals(segments[0])) {
            throw new RuntimeException("can only map into variables");
        }
        var currentLocation = (Object)variables;
        for (var i = 1; i < segments.length; ++i) {
            var segmentName = segments[i];
            if (i == segments.length - 1) {
                if (currentLocation instanceof Map map) {
                    // Map map = TypeIntrinsics.asMutableMap(currentLocation);
                    if (null != MAP_MAPPER.set(map, segmentName, part)) {
                        throw new RuntimeException("expected null value when mapping " + objectPath);
                    }
                } else if (currentLocation instanceof List list) {
                    // List list = TypeIntrinsics.asMutableList(currentLocation);
                    if (null != LIST_MAPPER.set(list, segmentName, part)) {
                        throw new RuntimeException("expected null value when mapping " + objectPath);
                    }
                } else assert false : "not map or list";
            } else {
                if (currentLocation instanceof Map map) {
                    // Map map = TypeIntrinsics.asMutableMap(currentLocation);
                    currentLocation = MAP_MAPPER.recurse(map, segmentName);
                } else if (currentLocation instanceof List list) {
                    // List list = TypeIntrinsics.asMutableList(currentLocation);
                    currentLocation = LIST_MAPPER.recurse(list, segmentName);
                } else assert false : "not map or list";

                if (null == currentLocation) {
                    throw new RuntimeException("found null intermediate value when trying to map " + objectPath);
                }
            }
        }
    }

}

