package com.bugly.system.config.interceptor;

import com.bugly.system.service.SysMenuService;
import com.bugly.common.utils.SecurityUtils;
import com.bugly.system.entity.SysMenu;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author no_f
 * @ClassName LogHandlerInterceptor
 * @Description TODO
 * @Date 2020/6/9 16:45
 */
@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class AccessAuthHandlerInterceptor implements HandlerInterceptor {

    @Value("${server.servlet.context-path}")
    private String contextPath;

    private final SysMenuService sysMenuService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //这块功能得优化一下
        List<SysMenu> secondMenu = sysMenuService.getSecondMenu();
        List<String> urlList = secondMenu.stream().map(o -> o.getMenuHref()).collect(Collectors.toList());
        String servletPath = StringUtils.replace(request.getServletPath(), "/", "", 1);
        if (urlList.contains(servletPath)) {
            // 判断权限
            String username = (String) SecurityUtils.getCurrentUserAuthentication().getPrincipal();
            List<SysMenu> sysMenuList = sysMenuService.findMenuListByUser(username);
            if (sysMenuList.stream().anyMatch(o -> servletPath.equals(o.getMenuHref()))) {
                return true;
            }
            response.sendRedirect(contextPath + "/403");
            return false;
        }
        return true;
    }

}
