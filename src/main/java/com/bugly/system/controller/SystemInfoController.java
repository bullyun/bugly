package com.bugly.system.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author no_f
 * @ClassName SystemInfoController
 * @Description TODO
 * @Date 2020/62/27 16:57
 */
@Controller
@RequestMapping("/system")
public class SystemInfoController {

    @GetMapping("/serverInfo")
    public String serverInfo(){
        return "module/system/server";
    }

    @RequestMapping("/introduce")
    public String introduce(){
        return "module/system/introduce";
    }
}
