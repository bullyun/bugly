package com.bugly.system.vo;

import lombok.Data;

/**
 * @author no_f
 * @since 2020-06-30
 */
@Data
public class BuglyDetailSearchVo {

    private String threadId;
    private String nickName;
    private String errorMessage;
    private String errorException;
}