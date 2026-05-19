package org.gms.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResultBody(Object code, String message, String responseId, Object data) {
    /** 服务端 BizExceptionEnum.SUCCESS = 20000 */
    public boolean isSuccess() {
        return code != null && "20000".equals(String.valueOf(code));
    }
}
