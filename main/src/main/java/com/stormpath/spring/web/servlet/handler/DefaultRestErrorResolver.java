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
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceAware;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.LocaleResolver;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Default {@code RestErrorResolver} implementation that converts discovered Exceptions to
 * {@link RestError} instances.
 *
 * @author Les Hazlewood
 */
public class DefaultRestErrorResolver implements RestErrorResolver, MessageSourceAware, InitializingBean {

    public static final String DEFAULT_EXCEPTION_MESSAGE_VALUE = "_exmsg";

    private static final Logger log = LoggerFactory.getLogger(DefaultRestErrorResolver.class);

    private Map<String, RestError> exceptionMappings = Collections.emptyMap();

    private Map<String, String> exceptionMappingDefinitions = Collections.emptyMap();

    private MessageSource messageSource;
    private LocaleResolver localeResolver;

    private String defaultMoreInfoUrl;
    private boolean defaultEmptyCodeToStatus;

    public DefaultRestErrorResolver() {
        this.defaultEmptyCodeToStatus = true;
    }

    public void setMessageSource(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public void setLocaleResolver(LocaleResolver resolver) {
        this.localeResolver = resolver;
    }

    public void setExceptionMappingDefinitions(Map<String, String> exceptionMappingDefinitions) {
        this.exceptionMappingDefinitions = exceptionMappingDefinitions;
    }

    public void setDefaultMoreInfoUrl(String defaultMoreInfoUrl) {
        this.defaultMoreInfoUrl = defaultMoreInfoUrl;
    }

    public void setDefaultEmptyCodeToStatus(boolean defaultEmptyCodeToStatus) {
        this.defaultEmptyCodeToStatus = defaultEmptyCodeToStatus;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.exceptionMappings = toRestErrors(this.exceptionMappingDefinitions);
    }

    @Override
    public RestError resolveError(ServletWebRequest request, Object handler, Exception ex) {

        RestError template = getRestErrorTemplate(ex);
        if (template == null) {
            return null;
        }

        RestError.Builder builder = new RestError.Builder();
        builder.setStatus(getStatusValue(template, request, ex));
        builder.setCode(getCode(template, request, ex));
        builder.setMoreInfoUrl(getMoreInfoUrl(template, request, ex));
        builder.setThrowable(ex);

        String msg = getMessage(template, request, ex);
        if (msg != null) {
            builder.setMessage(msg);
        }
        msg = getDeveloperMessage(template, request, ex);
        if (msg != null) {
            builder.setDeveloperMessage(msg);
        }

        return builder.build();
    }

    protected int getStatusValue(RestError template, ServletWebRequest request, Exception ex) {
        return template.getStatus().value();
    }

    protected int getCode(RestError template, ServletWebRequest request, Exception ex) {
        int code = template.getCode();
        if ( code <= 0 && defaultEmptyCodeToStatus) {
            code = getStatusValue(template, request, ex);
        }
        return code;
    }

    protected String getMoreInfoUrl(RestError template, ServletWebRequest request, Exception ex) {
        String moreInfoUrl = template.getMoreInfoUrl();
        if (moreInfoUrl == null) {
            moreInfoUrl = this.defaultMoreInfoUrl;
        }
        return moreInfoUrl;
    }

    protected String getMessage(RestError template, ServletWebRequest request, Exception ex) {
        return getMessage(template.getMessage(), request, ex);
    }

    protected String getDeveloperMessage(RestError template, ServletWebRequest request, Exception ex) {
        return getMessage(template.getDeveloperMessage(), request, ex);
    }

    /**
     * Returns the response status message to return to the client, or {@code null} if no
     * status message should be returned.
     *
     * @return the response status message to return to the client, or {@code null} if no
     *         status message should be returned.
     */
    protected String getMessage(String msg, ServletWebRequest webRequest, Exception ex) {

        if (msg != null) {
            if (msg.equalsIgnoreCase("null") || msg.equalsIgnoreCase("off")) {
                return null;
            }
            if (msg.equalsIgnoreCase(DEFAULT_EXCEPTION_MESSAGE_VALUE)) {
                msg = ex.getMessage();
            }
            if (messageSource != null) {
                Locale locale = null;
                if (localeResolver != null) {
                    locale = localeResolver.resolveLocale(webRequest.getRequest());
                }
                msg = messageSource.getMessage(msg, null, msg, locale);
            }
        }

        return msg;
    }

    /**
     * Returns the config-time 'template' RestError instance configured for the specified Exception, or
     * {@code null} if a match was not found.
     * <p/>
     * The config-time template is used as the basis for the RestError constructed at runtime.
     * @param ex
     * @return
     */
    private RestError getRestErrorTemplate(Exception ex) {
        Map<String, RestError> mappings = this.exceptionMappings;
        if (CollectionUtils.isEmpty(mappings)) {
            return null;
        }
        RestError template = null;
        String dominantMapping = null;
        int deepest = Integer.MAX_VALUE;
        for (Map.Entry<String, RestError> entry : mappings.entrySet()) {
            String key = entry.getKey();
            int depth = getDepth(key, ex);
            if (depth >= 0 && depth < deepest) {
                deepest = depth;
                dominantMapping = key;
                template = entry.getValue();
            }
        }
        if (template != null && log.isDebugEnabled()) {
            log.debug("Resolving to RestError template '" + template + "' for exception of type [" + ex.getClass().getName() +
                    "], based on exception mapping [" + dominantMapping + "]");
        }
        return template;
    }

    /**
     * Return the depth to the superclass matching.
     * <p>0 means ex matches exactly. Returns -1 if there's no match.
     * Otherwise, returns depth. Lowest depth wins.
     */
    protected int getDepth(String exceptionMapping, Exception ex) {
        return getDepth(exceptionMapping, ex.getClass(), 0);
    }

    private int getDepth(String exceptionMapping, Class exceptionClass, int depth) {
        if (exceptionClass.getName().contains(exceptionMapping)) {
            // Found it!
            return depth;
        }
        // If we've gone as far as we can go and haven't found it...
        if (exceptionClass.equals(Throwable.class)) {
            return -1;
        }
        return getDepth(exceptionMapping, exceptionClass.getSuperclass(), depth + 1);
    }


    private static Map<String, RestError> toRestErrors(Map<String, String> smap) {
        if (CollectionUtils.isEmpty(smap)) {
            return Collections.emptyMap();
        }

        Map<String, RestError> map = new LinkedHashMap<String, RestError>(smap.size());

        for (Map.Entry<String, String> entry : smap.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            RestError template = toRestError(value);
            map.put(key, template);
        }

        return map;
    }

    private static RestError toRestError(String exceptionConfig) {
        String[] values = StringUtils.commaDelimitedListToStringArray(exceptionConfig);
        if (values == null || values.length == 0) {
            throw new IllegalStateException("Invalid config mapping.  Exception names must map to a string configuration.");
        }

        RestError.Builder builder = new RestError.Builder();

        boolean statusSet = false;
        boolean codeSet = false;
        boolean msgSet = false;
        boolean devMsgSet = false;
        boolean moreInfoSet = false;

        for (String value : values) {

            String trimmedVal = StringUtils.trimWhitespace(value);

            //check to see if the value is an explicitly named key/value pair:
            String[] pair = StringUtils.split(trimmedVal, "=");
            if (pair != null) {
                //explicit attribute set:
                String pairKey = StringUtils.trimWhitespace(pair[0]);
                if (!StringUtils.hasText(pairKey)) {
                    pairKey = null;
                }
                String pairValue = StringUtils.trimWhitespace(pair[1]);
                if (!StringUtils.hasText(pairValue)) {
                    pairValue = null;
                }
                if ("status".equalsIgnoreCase(pairKey)) {
                    int statusCode = getRequiredInt(pairKey, pairValue);
                    builder.setStatus(statusCode);
                    statusSet = true;
                } else if ("code".equalsIgnoreCase(pairKey)) {
                    int code = getRequiredInt(pairKey, pairValue);
                    builder.setCode(code);
                    codeSet = true;
                } else if ("msg".equalsIgnoreCase(pairKey)) {
                    builder.setMessage(pairValue);
                    msgSet = true;
                } else if ("devMsg".equalsIgnoreCase(pairKey)) {
                    builder.setDeveloperMessage(pairValue);
                    devMsgSet = true;
                } else if ("infoUrl".equalsIgnoreCase(pairKey)) {
                    builder.setMoreInfoUrl(pairValue);
                    moreInfoSet = true;
                }
            } else {
                //not a key/value pair - use heuristics to determine what value is being set:
                int val;
                if (!statusSet) {
                    val = getInt("status", trimmedVal);
                    if (val > 0) {
                        builder.setStatus(val);
                        statusSet = true;
                        continue;
                    }
                }
                if (!codeSet) {
                    val = getInt("code", trimmedVal);
                    if (val > 0) {
                        builder.setCode(val);
                        codeSet = true;
                        continue;
                    }
                }
                if (!moreInfoSet && trimmedVal.toLowerCase().startsWith("http")) {
                    builder.setMoreInfoUrl(trimmedVal);
                    moreInfoSet = true;
                    continue;
                }
                if (!msgSet) {
                    builder.setMessage(trimmedVal);
                    msgSet = true;
                    continue;
                }
                if (!devMsgSet) {
                    builder.setDeveloperMessage(trimmedVal);
                    devMsgSet = true;
                    continue;
                }
                if (!moreInfoSet) {
                    builder.setMoreInfoUrl(trimmedVal);
                    moreInfoSet = true;
                    //noinspection UnnecessaryContinue
                    continue;
                }
            }
        }

        return builder.build();
    }

    private static int getRequiredInt(String key, String value) {
        try {
            int anInt = Integer.valueOf(value);
            return Math.max(-1, anInt);
        } catch (NumberFormatException e) {
            String msg = "Configuration element '" + key + "' requires an integer value.  The value " +
                    "specified: " + value;
            throw new IllegalArgumentException(msg, e);
        }
    }

    private static int getInt(String key, String value) {
        try {
            return getRequiredInt(key, value);
        } catch (IllegalArgumentException iae) {
            return 0;
        }
    }
}
