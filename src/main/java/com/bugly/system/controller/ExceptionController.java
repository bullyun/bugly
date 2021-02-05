package com.bugly.system.controller;

import com.bugly.common.utils.SecurityUtils;
import com.bugly.system.dao.*;
import com.bugly.system.entity.AlarmConfig;
import com.bugly.system.entity.SysUser;
import com.bugly.system.model.ExceptionType;
import com.bugly.system.model.ExceptionTypeUser;
import com.bugly.system.model.ServiceLog;
import com.bugly.system.model.ServiceType;
import com.bugly.system.service.SysUserService;
import lombok.RequiredArgsConstructor;
import net.sf.json.JSONArray;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static com.bugly.system.service.impl.ExceptionServiceImpl.DATE_Y_M_DDHHMMSS;
import static org.apache.commons.lang3.CharUtils.LF;


/**
 * @author no_f
 * view
 * @create 2020-06-16 17:46
 */
@Controller
@RequestMapping("/bugly/exception")
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class ExceptionController {

    private final SysUserService sysUserService;

    private final ServiceLogDao serviceLogDao;

    private final ServiceTypeDao serviceTypeDao;

    private final AlarmConfigDao alarmConfigDao;

    private final ExceptionTypeDao exceptionTypeDao;

    private final ExceptionTypeUserDao exceptionTypeUserDao;

    private final ServiceTypeUserDao serviceTypeUserDao;

    private final SysUserDao sysUserDao;

    @GetMapping("/list")
    public String index(Model model){
        Authentication authentication = SecurityUtils.getCurrentUserAuthentication();
        String username = (String)authentication.getPrincipal();
        List<ServiceType> serviceTypes;
        if (username.equals("admin")) {
            serviceTypes = serviceTypeDao.findAllService();
        } else {
            SysUser sysUser = sysUserDao.findByName(username);
            serviceTypes =  serviceTypeDao.findByUserId(sysUser.getId());
        }
        ServiceType serviceType = new ServiceType();
        serviceType.setServiceName("全部");
        serviceType.setCtime(new Date());

        serviceTypes.add(serviceType);
        serviceTypes.sort(Comparator.comparing(ServiceType::getCtime).reversed());
        model.addAttribute("serviceTypes", JSONArray.fromObject(serviceTypes).toString());
        return "module/bugly/bugly";
    }

    @GetMapping("/update")
    public String update(String id, Model model){
        Authentication authentication = SecurityUtils.getCurrentUserAuthentication();
        String username = (String)authentication.getPrincipal();
        SysUser sysUser = sysUserService.findByName(username);
        model.addAttribute("exceptionTypeId", id);
        model.addAttribute("sysUser", sysUser);
        return "module/bugly/updaterBugly";
    }

    @GetMapping("/detail")
    public String detail(String id, Model model){
        Authentication authentication = SecurityUtils.getCurrentUserAuthentication();
        String username = (String)authentication.getPrincipal();
        List<ServiceType> serviceTypes;
        if (username.equals("admin")) {
            serviceTypes = serviceTypeDao.findAllService();
        } else {
            SysUser sysUser = sysUserDao.findByName(username);
            serviceTypes =  serviceTypeDao.findByUserId(sysUser.getId());
        }
        ServiceType serviceType = new ServiceType();
        serviceType.setServiceName("全部");
        serviceType.setCtime(new Date());

        serviceTypes.add(serviceType);
        serviceTypes.sort(Comparator.comparing(ServiceType::getCtime).reversed());

        model.addAttribute("serviceTypes", JSONArray.fromObject(serviceTypes).toString());
        model.addAttribute("exceptionTypeId", id);
        return "module/bugly/detail";
    }

    @GetMapping("/out_solve")
    public String outSolve(String localtion, Model model){

        ExceptionType exceptionType = exceptionTypeDao.findByLocal(localtion);
        if (null == exceptionType) {
            model.addAttribute("exceptionTypeId", 0);
            model.addAttribute("time", 0);
            model.addAttribute("nickName", 0);
            model.addAttribute("state", 0);
            model.addAttribute("tag", "");
            model.addAttribute("remark", "");
        } else {

            List<ExceptionTypeUser> exceptionTypeUsers = exceptionTypeUserDao.findByExceptionTypeId(exceptionType.getId());
            if (null != exceptionTypeUsers && exceptionTypeUsers.size() > 0) {
                SimpleDateFormat sf = new SimpleDateFormat(DATE_Y_M_DDHHMMSS);
                ExceptionTypeUser exceptionTypeUser = exceptionTypeUsers.get(0);
                SysUser sysUser = sysUserDao.selectById(exceptionTypeUser.getUserId());
                model.addAttribute("nickName", sysUser == null ? "" : sysUser.getNickName());
                model.addAttribute("time", sf.format(exceptionTypeUser.getMtime()));
                model.addAttribute("remark", exceptionTypeUser.getRemark());
            }
            model.addAttribute("exceptionTypeId", exceptionType.getId());
            model.addAttribute("state", exceptionType.getState());
            model.addAttribute("tag", exceptionType.getTag());
        }
        return "module/bugly/outDealBug";
    }

    @GetMapping("/out_detail")
    public String outDetail(String localtion, String serviceName, String currentCluster, Model model){
        model.addAttribute("localtion", localtion);
        model.addAttribute("serviceName", serviceName);
        model.addAttribute("currentCluster", currentCluster);

        ExceptionType exceptionType = exceptionTypeDao.findByLocalAndServiceName(localtion, serviceName);
        if (null == exceptionType) {
            model.addAttribute("errorException", " ");
            model.addAttribute("level", " ");
            model.addAttribute("localtion", localtion);
            model.addAttribute("exceptionTypeId", 0);
        } else {
            ServiceLog serviceLog =  serviceLogDao.findOneByExceptionTypeIdAndOther(exceptionType.getId(), currentCluster, serviceName);
            model.addAttribute("errorException", serviceLog == null ? "" : serviceLog.getErrorException());
            model.addAttribute("errorMessage", serviceLog == null ? "" : serviceLog.getErrorMessage());
            model.addAttribute("level",  serviceLog == null ? "" : serviceLog.getLevel());
            model.addAttribute("localtion", localtion);
            ServiceType serviceType = serviceTypeDao.findByName(serviceLog.getServiceName());
            List<String> nickNames = serviceTypeUserDao.findByServiceTypeId(serviceType.getId());
            Optional.ofNullable(nickNames).ifPresent(n-> model.addAttribute("name", StringUtils.join(n, ",")));
            model.addAttribute("exceptionTypeId", exceptionType.getId());
        }
        return "module/bugly/outBugDetail";
    }

    @GetMapping("/webShow")
    public String webShow(String id, Model model){
        ServiceLog serviceLog =  serviceLogDao.findById(id);
        if (null == serviceLog) {
            model.addAttribute("serviceName", " ");
            model.addAttribute("currentCluster", " ");
            model.addAttribute("errorException", " ");
            model.addAttribute("level", " ");
            model.addAttribute("localtion", " ");
            model.addAttribute("exceptionTypeId", 0);
        } else {
            SimpleDateFormat sf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            ExceptionType exceptionType = exceptionTypeDao.findById(serviceLog.getExceptionTypeId());
            model.addAttribute("serviceName", serviceLog.getServiceName());
            model.addAttribute("currentCluster", serviceLog.getCurrentCluster());
            model.addAttribute("errorException", serviceLog.getErrorException() == null ? "无异常信息" : serviceLog.getErrorException());
            model.addAttribute("errorMessage", serviceLog.getErrorMessage() == null ? "无" : serviceLog.getErrorMessage());
            model.addAttribute("level",  serviceLog.getLevel() == null ? "无" : serviceLog.getLevel());
            model.addAttribute("localtion", exceptionType.getErrorLocation());
            model.addAttribute("time", sf.format(serviceLog.getCtime().getTime()));
            ServiceType serviceType = serviceTypeDao.findByName(serviceLog.getServiceName());
            List<String> nickNames = serviceTypeUserDao.findByServiceTypeId(serviceType.getId());
            Optional.ofNullable(nickNames).ifPresent(n-> model.addAttribute("name", StringUtils.join(n, ",")));
            model.addAttribute("exceptionTypeId", exceptionType.getId());
        }
        return "module/bugly/pcOutBugDetail";
    }

    @GetMapping("/detail_show")
    public String detailShow(String id, Model model){
        ServiceLog serviceLog = serviceLogDao.selectById(id);
        StringBuffer stringBuffer = new StringBuffer(" ");
        Optional.ofNullable(serviceLog).ifPresent(s->{
            stringBuffer.append("信息:").append(LF).append(serviceLog.getErrorMessage() == null ? "无信息"
                    : serviceLog.getErrorMessage()).append(LF)
                    .append("异常：").append(LF).append(serviceLog.getErrorException() == null ? "无异常信息"
                    : serviceLog.getErrorException());
        });
        model.addAttribute("serviceException", stringBuffer.toString());
        return "module/bugly/detailShow";
    }

    @GetMapping("/detail_show_1")
    public String detailShowI(String id, Model model){
        ServiceLog serviceLog = serviceLogDao.findOneByExceptionTypeId(id);
        StringBuffer stringBuffer = new StringBuffer(" ");
        Optional.ofNullable(serviceLog).ifPresent(s->{
            stringBuffer.append("信息:").append(LF).append(serviceLog.getErrorMessage() == null ? "无信息"
                    : serviceLog.getErrorMessage()).append(LF)
                    .append("异常：").append(LF).append(serviceLog.getErrorException() == null ? "无异常信息"
                    : serviceLog.getErrorException());
        });
        model.addAttribute("serviceException", stringBuffer.toString());
        return "module/bugly/detailShow";
    }

    @GetMapping("/alarmConfig")
    public String listAlarmConfig(){
        return "module/bugly/buglyConfig";
    }

    @GetMapping("/updateAlarmConfig")
    public String updateAlarmConfig(String id, Model model){
        model.addAttribute("id", id);
        ServiceType serviceType = serviceTypeDao.selectById(id);
        AlarmConfig alarmConfig = alarmConfigDao.findByServiceTypeId(id);
        model.addAttribute("serviceName", serviceType.getServiceName());
        model.addAttribute("webhookUrl", alarmConfig == null ? "" : alarmConfig.getWebhookUrl());
        return "module/bugly/updateAlarmConfig";
    }

}
