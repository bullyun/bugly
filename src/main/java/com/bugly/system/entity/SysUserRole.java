package com.bugly.system.entity;

import lombok.Builder;
import lombok.Data;

/**
 * @author no_f
 * @date 2020/6/12 0:43
 */
@Data
@Builder
public class SysUserRole {

    private String userId;

    private String roleId;

    public SysUserRole(){

    }

    public SysUserRole(String userId, String roleId){
        this.userId = userId;
        this.roleId = roleId;
    }
}
