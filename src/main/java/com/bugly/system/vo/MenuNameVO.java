package com.bugly.system.vo;

import lombok.Builder;
import lombok.Data;

/**
 * @author no_f
 * @date 2020/6/11 15:53
 */
@Data
@Builder
public class MenuNameVO {

    private String id;

    private String menuName;
}
