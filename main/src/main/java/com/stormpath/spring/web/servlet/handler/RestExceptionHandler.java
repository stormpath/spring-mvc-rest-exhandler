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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.util.CollectionUtils;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;
import org.springframework.web.servlet.handler.AbstractHandlerExceptionResolver;
import org.springframework.web.util.WebUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Renders a response with a RESTful Error representation based on the error format discussed in
 * <a href="http://www.stormpath.com/blog/spring-mvc-rest-exception-handling-best-practices-part-1">
 *     Spring MVC Rest Exception Handling Best Practices.</a>
 * <p/>
 * At a high-level, this implementation functions as follows:
 *
 * <ol>
 *     <li>Upon encountering an Exception, the configured {@link RestErrorResolver} is consulted to resolve the
 *     exception into a {@link RestError} instance.</li>
 *     <li>The HTTP Response's Status Code will be set to the {@code RestError}'s
 *     {@link com.stormpath.spring.web.servlet.handler.RestError#getStatus() status} value.</li>
 *     <li>The {@code RestError} instance is presented to a configured {@link RestErrorConverter} to allow transforming
 *     the {@code RestError} instance into an object potentially more suitable for rendering as the HTTP response body.</li>
 *     <li>The
 *     {@link #setMessageConverters(org.springframework.http.converter.HttpMessageConverter[]) HttpMessageConverters}
 *     are consulted (in iteration order) with this object result for rendering.  The first
 *     {@code HttpMessageConverter} instance that {@link HttpMessageConverter#canWrite(Class, org.springframework.http.MediaType) canWrite}
 *     the object based on the request's supported {@code MediaType}s will be used to render this result object as
 *     the HTTP response body.</li>
 *     <li>If no {@code HttpMessageConverter}s {@code canWrite} the result object, nothing is done, and this handler
 *     returns {@code null} to indicate other ExceptionResolvers potentially further in the resolution chain should
 *     handle the exception instead.</li>
 * </ol>
 *
 * <h3>Defaults</h3>
 * This implementation has the following property defaults:
 * <table>
 *     <tr>
 *         <th>Property</th>
 *         <th>Instance</th>
 *         <th>Notes</th>
 *     </tr>
 *     <tr>
 *         <td>errorResolver</td>
 *         <td>{@link DefaultRestErrorResolver DefaultRestErrorResolver}</td>
 *         <td>Converts Exceptions to {@link RestError} instances.  Should be suitable for most needs.</td>
 *     </tr>
 *     <tr>
 *         <td>errorConverter</td>
 *         <td>{@link MapRestErrorConverter}</td>
 *         <td>Converts {@link RestError} instances to {@code java.util.Map} instances to be used as the response body.
 *         Maps can then be trivially rendered as JSON by a (configured)
 *         {@link HttpMessageConverter HttpMessageConverter}.  If you want the raw {@code RestError} instance to
 *         be presented to the {@code HttpMessageConverter} instead, set this property to {@code null}.</td>
 *     </tr>
 *     <tr>
 *         <td>messageConverters</td>
 *         <td>multiple instances</td>
 *         <td>Default collection are those automatically enabled by Spring as
 *         <a href="http://static.springsource.org/spring/docs/current/spring-framework-reference/html/mvc.html#mvc-config-enable">defined here</a> (specifically item #5)</td>
 *     </tr>
 * </table>
 *
 * <h2>JSON Rendering</h2>
 * This implementation comes pre-configured with Spring's typical default
 * {@link HttpMessageConverter} instances; JSON will be enabled automatically if Jackson is in the classpath.  If you
 * want to match the JSON representation shown in the article above (recommended) but do not want to use Jackson
 * (or the Spring's default Jackson config), you will need to
 * {@link #setMessageConverters(org.springframework.http.converter.HttpMessageConverter[]) configure} a different
 * JSON-capable {@link HttpMessageConverter}.
 *
 * @see DefaultRestErrorResolver
 * @see MapRestErrorConverter
 * @see HttpMessageConverter
 * @see org.springframework.http.converter.json.MappingJacksonHttpMessageConverter MappingJacksonHttpMessageConverter
 *
 * @author Les Hazlewood
 */
public class RestExceptionHandler extends AbstractHandlerExceptionResolver implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);

    private HttpMessageConverter<?>[] messageConverters = null;

    private List<HttpMessageConverter<?>> allMessageConverters = null;

    private RestErrorResolver errorResolver;

    private RestErrorConverter<?> errorConverter;

    public RestExceptionHandler() {
        this.errorResolver = new DefaultRestErrorResolver();
        this.errorConverter = new MapRestErrorConverter();
    }

    public void setMessageConverters(HttpMessageConverter<?>[] messageConverters) {
        this.messageConverters = messageConverters;
    }

    public void setErrorResolver(RestErrorResolver errorResolver) {
        this.errorResolver = errorResolver;
    }

    public RestErrorResolver getErrorResolver() {
        return this.errorResolver;
    }

    public RestErrorConverter<?> getErrorConverter() {
        return errorConverter;
    }

    public void setErrorConverter(RestErrorConverter<?> errorConverter) {
        this.errorConverter = errorConverter;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        ensureMessageConverters();
    }

    @SuppressWarnings("unchecked")
    private void ensureMessageConverters() {

        List<HttpMessageConverter<?>> converters = new ArrayList<HttpMessageConverter<?>>();

        //user configured values take precedence:
        if (this.messageConverters != null && this.messageConverters.length > 0) {
            converters.addAll(CollectionUtils.arrayToList(this.messageConverters));
        }

        //defaults next:
        new HttpMessageConverterHelper().addDefaults(converters);

        this.allMessageConverters = converters;
    }

    //leverage Spring's existing default setup behavior:
    private static final class HttpMessageConverterHelper extends WebMvcConfigurationSupport {
        public void addDefaults(List<HttpMessageConverter<?>> converters) {
            addDefaultHttpMessageConverters(converters);
        }
    }

    /**
     * Actually resolve the given exception that got thrown during on handler execution, returning a ModelAndView that
     * represents a specific error page if appropriate.
     * <p/>
     * May be overridden in subclasses, in order to apply specific
     * exception checks. Note that this template method will be invoked <i>after</i> checking whether this resolved applies
     * ("mappedHandlers" etc), so an implementation may simply proceed with its actual exception handling.
     *
     * @param request  current HTTP request
     * @param response current HTTP response
     * @param handler  the executed handler, or <code>null</code> if none chosen at the time of the exception (for example,
     *                 if multipart resolution failed)
     * @param ex       the exception that got thrown during handler execution
     * @return a corresponding ModelAndView to forward to, or <code>null</code> for default processing
     */
    @Override
    protected ModelAndView doResolveException(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {

        ServletWebRequest webRequest = new ServletWebRequest(request, response);

        RestErrorResolver resolver = getErrorResolver();

        RestError error = resolver.resolveError(webRequest, handler, ex);
        if (error == null) {
            return null;
        }

        ModelAndView mav = null;

        try {
            mav = getModelAndView(webRequest, handler, error);
        } catch (Exception invocationEx) {
            log.error("Acquiring ModelAndView for Exception [" + ex + "] resulted in an exception.", invocationEx);
        }

        return mav;
    }

    protected ModelAndView getModelAndView(ServletWebRequest webRequest, Object handler, RestError error) throws Exception {

        applyStatusIfPossible(webRequest, error);

        Object body = error; //default the error instance in case they don't configure an error converter

        RestErrorConverter converter = getErrorConverter();
        if (converter != null) {
            body = converter.convert(error);
        }

        return handleResponseBody(body, webRequest);
    }

    private void applyStatusIfPossible(ServletWebRequest webRequest, RestError error) {
        if (!WebUtils.isIncludeRequest(webRequest.getRequest())) {
            webRequest.getResponse().setStatus(error.getStatus().value());
        }
        //TODO support response.sendError ?
    }

    @SuppressWarnings("unchecked")
    private ModelAndView handleResponseBody(Object body, ServletWebRequest webRequest) throws ServletException, IOException {

        HttpInputMessage inputMessage = new ServletServerHttpRequest(webRequest.getRequest());

        List<MediaType> acceptedMediaTypes = inputMessage.getHeaders().getAccept();
        if (acceptedMediaTypes.isEmpty()) {
            acceptedMediaTypes = Collections.singletonList(MediaType.ALL);
        }

        MediaType.sortByQualityValue(acceptedMediaTypes);

        HttpOutputMessage outputMessage = new ServletServerHttpResponse(webRequest.getResponse());

        Class<?> bodyType = body.getClass();

        List<HttpMessageConverter<?>> converters = this.allMessageConverters;

        if (converters != null) {
            for (MediaType acceptedMediaType : acceptedMediaTypes) {
                for (HttpMessageConverter messageConverter : converters) {
                    if (messageConverter.canWrite(bodyType, acceptedMediaType)) {
                        messageConverter.write(body, acceptedMediaType, outputMessage);
                        //return empty model and view to short circuit the iteration and to let
                        //Spring know that we've rendered the view ourselves:
                        return new ModelAndView();
                    }
                }
            }
        }

        if (logger.isWarnEnabled()) {
            logger.warn("Could not find HttpMessageConverter that supports return type [" + bodyType +
                    "] and " + acceptedMediaTypes);
        }
        return null;
    }
}
