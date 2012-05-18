package com.stormpath.blog.spring.http.converter.json;

import org.codehaus.jackson.JsonEncoding;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.type.TypeFactory;
import org.codehaus.jackson.type.JavaType;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.util.Assert;

import java.io.IOException;
import java.nio.charset.Charset;

/**
 * Replaces Spring's {@link org.springframework.http.converter.json.MappingJacksonHttpMessageConverter}, which is
 * difficult to configure for pretty-printing.  This implementation enables pretty-printing easily via a setter/getter.
 * <p/>
 * See <a href="http://stackoverflow.com/questions/6541757/when-using-spring-mvc-for-rest-how-do-you-enable-jackson-to-pretty-print-render">
 *     When using Spring MVC for REST, how do you enable Jackson to pretty-print rendered JSON?</a> and the latest
 *     <a href="https://gist.github.com/2423129">Spring Framework incarnation supporting pretty printing</a>
 *     (not yet released at the time of writing).
 *
 * @author Les Hazlewood
 */
public class DefaultJacksonHttpMessageConverter extends AbstractHttpMessageConverter<Object> {

    public static final Charset DEFAULT_CHARSET = Charset.forName("UTF-8");

    private ObjectMapper objectMapper = new ObjectMapper();
    private boolean prefixJson = false;
    private boolean prettyPrint = false;

    /**
     * Construct a new {@code DefaultJacksonHttpMessageConverter}.
     */
    public DefaultJacksonHttpMessageConverter() {
        super(new MediaType("application", "json", DEFAULT_CHARSET));
    }

    @Override
    public boolean canRead(Class<?> clazz, MediaType mediaType) {
        JavaType javaType = getJavaType(clazz);
        return objectMapper.canDeserialize(javaType) && canRead(mediaType);
    }

    @Override
    public boolean canWrite(Class<?> clazz, MediaType mediaType) {
        return objectMapper.canSerialize(clazz) && canWrite(mediaType);
    }

    /**
     * Returns the Jackson {@link JavaType} for the specific class.
     * <p/>
     * <p>Default implementation returns {@link org.codehaus.jackson.map.type.TypeFactory#type(java.lang.reflect.Type)}, but this can be overridden
     * in subclasses, to allow for custom generic collection handling. For instance:
     * <pre class="code">
     * protected JavaType getJavaType(Class&lt;?&gt; clazz) {
     * if (List.class.isAssignableFrom(clazz)) {
     * return TypeFactory.collectionType(ArrayList.class, MyBean.class);
     * } else {
     * return super.getJavaType(clazz);
     * }
     * }
     * </pre>
     *
     * @param clazz the class to return the java type for
     * @return the java type
     */
    protected JavaType getJavaType(Class<?> clazz) {
        return TypeFactory.type(clazz);
    }

    @Override
    protected Object readInternal(Class<?> clazz, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {
        JavaType javaType = getJavaType(clazz);
        try {
            return objectMapper.readValue(inputMessage.getBody(), javaType);
        } catch (JsonParseException ex) {
            throw new HttpMessageNotReadableException("Could not read JSON: " + ex.getMessage(), ex);
        }
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        // should not be called, since we override canRead/Write instead
        throw new UnsupportedOperationException();
    }

    @Override
    protected void writeInternal(Object o, HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotWritableException {
        JsonEncoding encoding = getEncoding(outputMessage.getHeaders().getContentType());
        JsonGenerator jsonGenerator =
                getObjectMapper().getJsonFactory().createJsonGenerator(outputMessage.getBody(), encoding);
        try {
            if (prefixJson) {
                jsonGenerator.writeRaw("{} && ");
            }
            if (isPrettyPrint()) {
                jsonGenerator.useDefaultPrettyPrinter();
            }
            getObjectMapper().writeValue(jsonGenerator, o);
        } catch (JsonGenerationException ex) {
            throw new HttpMessageNotWritableException("Could not write JSON: " + ex.getMessage(), ex);
        }
    }

    private JsonEncoding getEncoding(MediaType contentType) {
        if (contentType != null && contentType.getCharSet() != null) {
            Charset charset = contentType.getCharSet();
            for (JsonEncoding encoding : JsonEncoding.values()) {
                if (charset.name().equals(encoding.getJavaName())) {
                    return encoding;
                }
            }
        }
        return JsonEncoding.UTF8;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /**
     * Sets the {@code ObjectMapper} for this view. If not set, a default
     * {@link ObjectMapper#ObjectMapper() ObjectMapper} is used.
     * <p>Setting a custom-configured {@code ObjectMapper} is one way to take further control of the JSON serialization
     * process. For example, an extended {@link org.codehaus.jackson.map.SerializerFactory} can be configured that provides
     * custom serializers for specific types. The other option for refining the serialization process is to use Jackson's
     * provided annotations on the types to be serialized, in which case a custom-configured ObjectMapper is unnecessary.
     *
     * @param objectMapper -
     */
    public void setObjectMapper(ObjectMapper objectMapper) {
        Assert.notNull(objectMapper, "'objectMapper' must not be null");
        this.objectMapper = objectMapper;
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    /**
     * Indicates whether the JSON output by this view should be prefixed with "{} &&". Default is false.
     * <p> Prefixing the JSON string in this manner is used to help prevent JSON Hijacking. The prefix renders the string
     * syntactically invalid as a script so that it cannot be hijacked. This prefix does not affect the evaluation of JSON,
     * but if JSON validation is performed on the string, the prefix would need to be ignored.
     *
     * @param prefixJson -
     */
    public void setPrefixJson(boolean prefixJson) {
        this.prefixJson = prefixJson;
    }
}

