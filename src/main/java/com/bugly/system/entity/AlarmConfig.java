package com.bugly.system.entity;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Date;

/**
 * @author no_f
 * @since 2020-06-30
 */
@Data
@Accessors(chain = true)
public class AlarmConfig implements Serializable {

    static final long serialVersionUID = 1L;

    private String id;

    /**
     * 0:钉钉 1：微信
     */
    private Integer type;

    /**
     * 服务名称ID
     */
    private String serviceTypeId;

    private String webhookUrl;

    private Integer deleted;

    private Date ctime;

    private Date mtime;

}
