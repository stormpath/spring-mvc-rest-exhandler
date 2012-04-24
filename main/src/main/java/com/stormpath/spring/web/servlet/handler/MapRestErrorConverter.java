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

import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Simple {@code RestErrorConverter} implementation that creates a new Map instance based on the specified RestError
 * instance.  Some {@link org.springframework.http.converter.HttpMessageConverter HttpMessageConverter}s (like a JSON
 * converter) can easily automatically convert Maps to response bodies.  The map is populated with the following
 * default name/value pairs:
 *
 * <table>
 *     <tr>
 *         <th>Key (a String)</th>
 *         <th>Value (an Object)</th>
 *         <th>Notes</th>
 *     </tr>
 *     <tr>
 *         <td>status</td>
 *         <td>restError.{@link RestError#getStatus() getStatus()}.{@link org.springframework.http.HttpStatus#value() value()}</td>
 *         <td></td>
 *     </tr>
 *     <tr>
 *         <td>code</td>
 *         <td>restError.{@link RestError#getCode() getCode()}</td>
 *         <td>Only set if {@code code > 0}</td>
 *     </tr>
 *     <tr>
 *         <td>message</td>
 *         <td>restError.{@link RestError#getMessage() getMessage()}</td>
 *         <td>Only set if {@code message != null}</td>
 *     </tr>
 *     <tr>
 *         <td>developerMessage</td>
 *         <td>restError.{@link RestError#getDeveloperMessage() getDeveloperMessage()}</td>
 *         <td>Only set if {@code developerMessage != null}</td>
 *     </tr>
 *     <tr>
 *         <td>moreInfo</td>
 *         <td>restError.{@link RestError#getMoreInfoUrl() getMoreInfoUrl()}</td>
 *         <td>Only set if {@code moreInfoUrl != null}</td>
 *     </tr>
 * </table>
 * <p/>
 * The map key names are customizable via setter methods (setStatusKey, setMessageKey, etc).
 *
 * @author Les Hazlewood
 */
public class MapRestErrorConverter implements RestErrorConverter<Map> {

    private static final String DEFAULT_STATUS_KEY = "status";
    private static final String DEFAULT_CODE_KEY = "code";
    private static final String DEFAULT_MESSAGE_KEY = "message";
    private static final String DEFAULT_DEVELOPER_MESSAGE_KEY = "developerMessage";
    private static final String DEFAULT_MORE_INFO_URL_KEY = "moreInfoUrl";

    private String statusKey = DEFAULT_STATUS_KEY;
    private String codeKey = DEFAULT_CODE_KEY;
    private String messageKey = DEFAULT_MESSAGE_KEY;
    private String developerMessageKey = DEFAULT_DEVELOPER_MESSAGE_KEY;
    private String moreInfoUrlKey = DEFAULT_MORE_INFO_URL_KEY;

    @Override
    public Map convert(RestError re) {
        Map<String, Object> m = createMap();
        HttpStatus status = re.getStatus();
        m.put(getStatusKey(), status.value());

        int code = re.getCode();
        if (code > 0) {
            m.put(getCodeKey(), code);
        }

        String message = re.getMessage();
        if (message != null) {
            m.put(getMessageKey(), message);
        }

        String devMsg = re.getDeveloperMessage();
        if (devMsg != null) {
            m.put(getDeveloperMessageKey(), devMsg);
        }

        String moreInfoUrl = re.getMoreInfoUrl();
        if (moreInfoUrl != null) {
            m.put(getMoreInfoUrlKey(), moreInfoUrl);
        }

        return m;
    }

    protected Map<String,Object> createMap() {
        return new LinkedHashMap<String, Object>();
    }

    public String getStatusKey() {
        return statusKey;
    }

    public void setStatusKey(String statusKey) {
        this.statusKey = statusKey;
    }

    public String getCodeKey() {
        return codeKey;
    }

    public void setCodeKey(String codeKey) {
        this.codeKey = codeKey;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public void setMessageKey(String messageKey) {
        this.messageKey = messageKey;
    }

    public String getDeveloperMessageKey() {
        return developerMessageKey;
    }

    public void setDeveloperMessageKey(String developerMessageKey) {
        this.developerMessageKey = developerMessageKey;
    }

    public String getMoreInfoUrlKey() {
        return moreInfoUrlKey;
    }

    public void setMoreInfoUrlKey(String moreInfoUrlKey) {
        this.moreInfoUrlKey = moreInfoUrlKey;
    }
}
