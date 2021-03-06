package com.bugly.system.config.security;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.session.SessionInformationExpiredEvent;
import org.springframework.security.web.session.SessionInformationExpiredStrategy;
import org.springframework.stereotype.Component;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author no_f
 * @ClassName CustomExpiredSessionStrategy
 * @Description TODO
 * @Date 2020/62/18 20:38
 */
@Component
public class CustomExpiredSessionStrategy implements SessionInformationExpiredStrategy {

    @Value("${server.servlet.context-path}")
    private String contextPath;

    @Override
    public void onExpiredSessionDetected(SessionInformationExpiredEvent sessionInformationExpiredEvent) throws IOException, ServletException {
        HttpServletResponse response = sessionInformationExpiredEvent.getResponse();
        HttpServletRequest request = sessionInformationExpiredEvent.getRequest();
        request.getRequestDispatcher("/expired").forward(request,response);
    }
}
