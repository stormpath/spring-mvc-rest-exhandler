/*
 * Copyright 2012 Stormpath, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.stormpath.spring.web.servlet.handler;

import org.springframework.core.convert.converter.Converter;

/**
 * A {@code RestErrorConverter} is an intermediate 'bridge' component in the response rendering pipeline: it converts
 * a {@link RestError} object into another object that is potentially better suited for HTTP response rendering by an
 * {@link org.springframework.http.converter.HttpMessageConverter HttpMessageConverter}.
 * <p/>
 * For example, a {@code RestErrorConverter} implementation might produce an intermediate Map of name/value pairs.
 * This resulting map might then be given to an {@code HttpMessageConverter} to write the response body:
 * <pre>
 *     Object result = mapRestErrorConverter.convert(aRestError);
 *     assert result instanceof Map;
 *     ...
 *     httpMessageConverter.write(result, ...);
 * </pre>
 * <p/>
 * This allows spring configurers to use or write simpler RestError conversion logic and let the more complex registered
 * {@code HttpMessageConverter}s operate on the converted result instead of needing to implement the more
 * complex {@code HttpMessageConverter} interface directly.
 *
 * @param <T> The type of object produced by the converter.
 *
 * @see org.springframework.http.converter.HttpMessageConverter
 * @see Converter
 *
 * @author Les Hazlewood
 */
public interface RestErrorConverter<T> extends Converter<RestError, T> {

    /**
     * Converts the RestError instance into an object that will then be used by an
     * {@link org.springframework.http.converter.HttpMessageConverter HttpMessageConverter} to render the response body.
     *
     * @param re the {@code RestError} instance to convert to another object instance 'understood' by other registered
     *           {@code HttpMessageConverter} instances.
     * @return an object suited for HTTP response rendering by an {@code HttpMessageConverter}
     */
    T convert(RestError re);
}
