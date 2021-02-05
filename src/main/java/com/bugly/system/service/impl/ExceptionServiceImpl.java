package com.bugly.system.service.impl;

import cn.hutool.cache.impl.TimedCache;
import com.bugly.common.base.ApiResponse;
import com.bugly.common.job.CacheTask;
import com.bugly.common.logrobot.DingTalkSender;
import com.bugly.common.utils.CacheUtil;
import com.bugly.common.utils.TimeUtils;
import com.bugly.common.utils.UUIDUtils;
import com.bugly.system.bo.ExceptionTypeBo;
import com.bugly.system.bo.ServiceExceptionBo;
import com.bugly.system.bo.ServiceTypeBo;
import com.bugly.system.dao.*;
import com.bugly.system.dto.DealWithServerLogDto;
import com.bugly.system.dto.GetServerLogDto;
import com.bugly.system.dto.UpdateAlarmConfigDto;
import com.bugly.system.entity.AlarmConfig;
import com.bugly.system.entity.SysUser;
import com.bugly.system.model.*;
import com.bugly.system.service.ExceptionService;
import com.bugly.system.vo.BuglyDetailSearchVo;
import com.bugly.system.vo.BuglySearchVo;
import com.bugly.system.vo.CommonResult;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;

import static com.bugly.system.vo.CommonResult.success;


/**
 * @author no_f
 * @create 2020-06-18 11:26
 */
@Service
public class ExceptionServiceImpl implements ExceptionService {

    public static String DATE_Y_M_DDHHMMSS = "yyyy-MM-dd HH:mm:ss";

    @Autowired
    private ServiceLogDao serviceLogDao;

    @Autowired
    private ServiceTypeDao serviceTypeDao;

    @Autowired
    private SysUserDao sysUserDao;

    @Autowired
    private ServiceTypeUserDao serviceTypeUserDao;

    @Autowired
    private AlarmConfigDao alarmConfigDao;

    @Autowired
    private ExceptionTypeDao exceptionTypeDao;

    @Autowired
    private ExceptionTypeUserDao exceptionTypeUserDao;

    @Autowired
    private CacheUtil cacheUtil;

    @Autowired
    private CacheTask cacheTask;

    @Value("${bugly.httpUrl}")
    private String buglyHttpUrl;

    public static TimedCache<String, JSONObject> timedCache = cn.hutool.cache.CacheUtil.newTimedCache(60000);

    private final static String DUBBO_TIME_OUT = "TimeoutException";

    private final static String DUBBO_TIME_OUT_I = "The timeout response";


    /**
     * 1.save 不同的异常类型
     * 2.save 每条异常记录
     * 是不是每次都得去数据库查询配置地址？？？优化??
     * read 4
     * write 2
     * @param content
     * @return
     */
    @Override
    public CommonResult<Boolean> saveServiceLog(JSONObject content) {
        ServiceLog serviceLog = getData(JSONObject.fromObject(content));
        ExceptionType exceptionType = exceptionTypeAction(content);
        if (null == exceptionType || exceptionType.getState() == 3) {
            return success(true);
        }
        serviceLog.setExceptionTypeId(exceptionType.getId());
        serviceLogDao.insert(serviceLog);


        Date endTime = new Date();
        content.put("buglyHttpUrl", buglyHttpUrl);
        content.put("id", serviceLog.getId());

        //1分钟内相同的异常不需要重复通知
        if (sendOrNot(exceptionType.getId(), content)) {
            return success(true);
        }

        //最近24h发生异常
        int num = serviceLogDao.findNumByToday(exceptionType.getId(), TimeUtils.getOneDayBefore(endTime), endTime);
        content.put("num",num);

        //超时的异常单独群通知
        AlarmConfig alarmConfig = cacheUtil.getAlarmConfig("common");
        AlarmConfig dubboAlarmConfig = cacheUtil.getAlarmConfig("timeOut");
        if (dubboTimeOut(content)) {
            alarmConfig = (dubboAlarmConfig == null) ? alarmConfig : dubboAlarmConfig;
            content.put("reason","调用超时");
        } else {
            String remark = exceptionTypeUserDao.findByExceptionTypeIdByOne(exceptionType.getId());
            if (remark == null) {
                remark = StringUtils.isBlank(exceptionType.getTag()) ? "异常出现还未解释说明" : exceptionType.getTag();

            }
            content.put("dealReason",remark);
        }

        //1.发生钉群 2.通知所有用户或单独通知责任人
        DingTalkSender.sendDingTalk(content, alarmConfig.getWebhookUrl());
        DingTalkSender.sendCommonDingTalk(content, alarmConfig.getWebhookUrl(), saveServiceNameType(content));
        return success(true);
    }


    @Override
    public ApiResponse findAll(int page, int pageSize) {
        List<ExceptionType> exceptionTypes = exceptionTypeDao.findAll((page-1)*pageSize, pageSize);
        List<ExceptionTypeBo> exceptionTypeBos = new ArrayList<>();
        SimpleDateFormat sf = new SimpleDateFormat(DATE_Y_M_DDHHMMSS);
        exceptionTypes.forEach(e -> {
            ExceptionTypeBo exceptionTypeBo = new ExceptionTypeBo();
            BeanUtils.copyProperties(e, exceptionTypeBo);
            exceptionTypeBo.setMtime(sf.format(e.getMtime()));
            exceptionTypeBo.setState(stateString(e.getState()));
            ServiceLog serviceLog = serviceLogDao.findOneByExceptionTypeId(e.getId());
            Optional.ofNullable(serviceLog).ifPresent(s-> exceptionTypeBo.setMachineAddress(s.getMachineAddress()));
            exceptionTypeBos.add(exceptionTypeBo);
        });

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("total",exceptionTypeDao.findAllNum());
        jsonObject.put("page",page);
        jsonObject.put("page_size",pageSize);
        jsonObject.put("sysUserList",exceptionTypeBos);
        return ApiResponse.ofSuccess(jsonObject);
    }

    @Override
    public ApiResponse findByCondition(BuglySearchVo buglySearchVo) {
        if (buglySearchVo.getState() == -1) {
            buglySearchVo.setState(null);
        }
        if (buglySearchVo.getServiceName().equals("全部")) {
            buglySearchVo.setServiceName(null);
        }
        List<ExceptionType> exceptionTypes = exceptionTypeDao.findByCondition(buglySearchVo);
        List<ExceptionTypeBo> exceptionTypeBos = new ArrayList<>();
        SimpleDateFormat sf = new SimpleDateFormat(DATE_Y_M_DDHHMMSS);
        exceptionTypes.forEach(e -> {
            ExceptionTypeBo exceptionTypeBo = new ExceptionTypeBo();
            BeanUtils.copyProperties(e, exceptionTypeBo);
            exceptionTypeBo.setMtime(sf.format(e.getMtime()));
            exceptionTypeBo.setState(stateString(e.getState()));
            ServiceLog serviceLog = serviceLogDao.findOneByExceptionTypeId(e.getId());
            Optional.ofNullable(serviceLog).ifPresent(s-> exceptionTypeBo.setMachineAddress(s.getMachineAddress()));
            exceptionTypeBos.add(exceptionTypeBo);
        });
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("total",exceptionTypeDao.countCondition(buglySearchVo));
        jsonObject.put("page",buglySearchVo.getPage());
        jsonObject.put("page_size",buglySearchVo.getPageSize());
        jsonObject.put("sysUserList",exceptionTypeBos);
        return ApiResponse.ofSuccess(jsonObject);
    }


    @Override
    public ApiResponse getDetailsAll(String exceptionTypeId, Integer page, Integer pageSize) {
        List<ServiceLog> serviceLogs = null;
        int count = 0;
        if (null == exceptionTypeId || exceptionTypeId.isEmpty()) {
            serviceLogs = serviceLogDao.findAll((page-1) * pageSize , pageSize);
            count = serviceLogDao.findAllNum();
        } else {
            serviceLogs = serviceLogDao.findByExceptionTypeId(exceptionTypeId, (page-1) * pageSize , pageSize);
            count = serviceLogDao.findCountByExceptionTypeId(exceptionTypeId);

        }

        JSONObject jsonObject = new JSONObject();
        if (null == serviceLogs || serviceLogs.isEmpty()) {
            jsonObject.put("total",count);
            jsonObject.put("page",page);
            jsonObject.put("page_size",pageSize);
            jsonObject.put("sysUserList", Collections.emptyList());
            return ApiResponse.ofSuccess(jsonObject);
        }
        SimpleDateFormat sf = new SimpleDateFormat(DATE_Y_M_DDHHMMSS);
        List<ServiceExceptionBo> serviceExceptionBos = new ArrayList<>();
        serviceLogs.forEach(serviceLog -> {
            ServiceExceptionBo serviceExceptionBo = new ServiceExceptionBo();
            BeanUtils.copyProperties(serviceLog, serviceExceptionBo);
            serviceExceptionBo.setTriggerTime(sf.format(serviceLog.getTriggerTime()));
            if (null != serviceLog.getErrorException() && serviceLog.getErrorException().length() > 101) {
                serviceExceptionBo.setErrorException(serviceLog.getErrorException().substring(0,100) + "......");
            }
            if (null != serviceLog.getErrorMessage() && serviceLog.getErrorMessage().length() > 101) {
                serviceExceptionBo.setErrorMessage(serviceLog.getErrorMessage().substring(0,100) + "......");
            }
            serviceExceptionBos.add(serviceExceptionBo);

        });

        jsonObject.put("total",count);
        jsonObject.put("page",page);
        jsonObject.put("page_size",pageSize);
        jsonObject.put("sysUserList",serviceExceptionBos);
        return ApiResponse.ofSuccess(jsonObject);
    }


    @Override
    public ApiResponse getDetailsByCondition(BuglyDetailSearchVo buglyDetailSearchVo) {

        if (buglyDetailSearchVo.getServiceName().equals("全部")) {
            buglyDetailSearchVo.setServiceName(null);
        }
        GetServerLogDto getServerLogDto = new GetServerLogDto();
        BeanUtils.copyProperties(buglyDetailSearchVo, getServerLogDto);
        try {

            SimpleDateFormat dff = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS",Locale.ENGLISH);
            if (null != buglyDetailSearchVo.getStartTime()) {
                Calendar rightStart = Calendar.getInstance();
                rightStart.setTime(dff.parse(buglyDetailSearchVo.getStartTime()));
                rightStart.add(Calendar.HOUR, +8);
                getServerLogDto.setStartTime(rightStart.getTime());
            }
            if (null != buglyDetailSearchVo.getEndTime()) {
                Calendar rightEnd = Calendar.getInstance();
                rightEnd.setTime(dff.parse(buglyDetailSearchVo.getEndTime()));
                rightEnd.add(Calendar.HOUR, +31);
                rightEnd.add(Calendar.MINUTE, +59);
                rightEnd.add(Calendar.SECOND, +59);
                getServerLogDto.setEndTime(rightEnd.getTime());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        List<ServiceLog> serviceLogs = serviceLogDao.findByCondition(getServerLogDto);
        JSONObject jsonObject = new JSONObject();
        if (null == serviceLogs || serviceLogs.isEmpty()) {
            jsonObject.put("total",0);
            jsonObject.put("page",buglyDetailSearchVo.getPage());
            jsonObject.put("page_size",buglyDetailSearchVo.getPageSize());
            jsonObject.put("sysUserList", Collections.emptyList());
            return ApiResponse.ofSuccess(jsonObject);
        }
        SimpleDateFormat sf = new SimpleDateFormat(DATE_Y_M_DDHHMMSS);
        List<ServiceExceptionBo> serviceExceptionBos = new ArrayList<>();
        serviceLogs.forEach(serviceLog -> {
            ServiceExceptionBo serviceExceptionBo = new ServiceExceptionBo();
            BeanUtils.copyProperties(serviceLog, serviceExceptionBo);
            serviceExceptionBo.setTriggerTime(sf.format(serviceLog.getTriggerTime()));

            if (null != serviceLog.getErrorException()) {
                int errorExceptionSize = serviceLog.getErrorException().length();
                serviceExceptionBo.setErrorException(errorExceptionSize > 100 ? serviceLog.getErrorException().substring(0,100) + "......"
                        : serviceLog.getErrorException());

                int errorMessageSize = serviceLog.getErrorMessage().length();
                serviceExceptionBo.setErrorMessage(errorMessageSize > 100 ? serviceLog.getErrorMessage().substring(0,100) + "......"
                        : serviceLog.getErrorMessage());

            }
            serviceExceptionBos.add(serviceExceptionBo);
        });
        Long cc = System.currentTimeMillis();


        jsonObject.put("total",serviceLogDao.countCondition(getServerLogDto));
        jsonObject.put("page",buglyDetailSearchVo.getPage());
        jsonObject.put("page_size",buglyDetailSearchVo.getPageSize());
        jsonObject.put("sysUserList",serviceExceptionBos);

        return ApiResponse.ofSuccess(jsonObject);
    }

    @Override
    public ApiResponse dealWith(DealWithServerLogDto dto) {
        ExceptionType exceptionType = new ExceptionType();
        BeanUtils.copyProperties(dto, exceptionType);
        int num = exceptionTypeDao.updateById(exceptionType);

        JSONObject jsonObject = new JSONObject();
        if (num == 0) {
            jsonObject.put("code",500);
            return ApiResponse.ofSuccess(jsonObject);
        }
        Date day = new Date();
        ExceptionTypeUser exceptionTypeUser = new ExceptionTypeUser();
        exceptionTypeUser.setId(UUIDUtils.getUUID()).setExceptionTypeId(dto.getId());
        exceptionTypeUser.setDeleted(0).setMtime(day).setCtime(day);
        exceptionTypeUser.setRemark(dto.getRemark());
        SysUser sysUser = sysUserDao.findByNickame(dto.getNickName());
        Optional.ofNullable(sysUser).ifPresent(u -> exceptionTypeUser.setUserId(u.getId()));
        exceptionTypeUserDao.insert(exceptionTypeUser);

        jsonObject.put("code",200);
        return ApiResponse.ofSuccess(jsonObject);
    }


    @Override
    public ApiResponse findAllServiceType(int page, int pageSize) {
        List<ServiceType> serviceTypes = serviceTypeDao.findAll((page-1)*pageSize, pageSize);
        List<ServiceTypeBo> serviceTypeBos = new ArrayList<>();

        serviceTypes.forEach(e -> {
            ServiceTypeBo serviceTypeBo = new ServiceTypeBo();
            BeanUtils.copyProperties(e, serviceTypeBo);
            List<String> nicknames = serviceTypeUserDao.findByServiceTypeId(e.getId());
            Optional.ofNullable(nicknames).ifPresent(n-> serviceTypeBo.setNickNames(StringUtils.join(n, ",")));
            AlarmConfig alarmConfig = alarmConfigDao.findByServiceTypeId(e.getId());
            Optional.ofNullable(alarmConfig).ifPresent(a-> serviceTypeBo.setWebhookUrl(a.getWebhookUrl()));
            serviceTypeBos.add(serviceTypeBo);
        });

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("total",serviceTypeDao.findAllNum());
        jsonObject.put("page",page);
        jsonObject.put("page_size",pageSize);
        jsonObject.put("sysUserList",serviceTypeBos);
        return ApiResponse.ofSuccess(jsonObject);
    }

    @Override
    public ApiResponse updateAlarmConfig(UpdateAlarmConfigDto dto) {
        Date day = new Date();

        //webhook_url
        if (null != dto.getWebhookUrl() && !dto.getWebhookUrl().isEmpty()) {
            AlarmConfig alarmConfig = alarmConfigDao.findByServiceTypeId(dto.getId());
            if (null == alarmConfigDao.findByServiceTypeId(dto.getId())) {
                alarmConfig = new AlarmConfig();
                alarmConfig.setId(UUIDUtils.getUUID()).setMtime(day).setCtime(day).setDeleted(0).setType(0)
                        .setWebhookUrl(dto.getWebhookUrl()).setServiceTypeId(dto.getId());
                alarmConfigDao.insert(alarmConfig);
            } else {
                alarmConfig.setMtime(day).setWebhookUrl(dto.getWebhookUrl());
                alarmConfigDao.updateById(alarmConfig);
            }
        }
        //user

        JSONObject jsonObject = new JSONObject();
        if (dto.getNickname().equals("clear")) {
            serviceTypeUserDao.deleteByServiceTypeId(dto.getId());
        } else {
            SysUser sysUser = sysUserDao.findByNickame(dto.getNickname());
            if (null == sysUser) {
                jsonObject.put("code",500);
                return ApiResponse.ofSuccess(jsonObject);
            }
            ServiceTypeUser serviceTypeUser = serviceTypeUserDao.findByServiceTypeIdAndUserId(dto.getId(), sysUser.getId());
            if (null != serviceTypeUser) {
                jsonObject.put("code",200);
                return ApiResponse.ofSuccess(jsonObject);
            }
            serviceTypeUser = new ServiceTypeUser();
            serviceTypeUser.setId(UUIDUtils.getUUID()).setCtime(day).setMtime(day).setDeleted(0).setServiceTypeId(dto.getId()).setUserId(sysUser.getId());
            serviceTypeUserDao.insert(serviceTypeUser);
        }

        jsonObject.put("code",200);
        return ApiResponse.ofSuccess(jsonObject);
    }

    private ServiceLog getData(JSONObject jsonObject) {
        ServiceLog serviceLog = new ServiceLog();
        try {
            if (jsonObject.containsKey("currentCluster")) {
                serviceLog.setCurrentCluster((String) jsonObject.get("currentCluster"));
            }
            if (jsonObject.containsKey("serviceName")) {
                serviceLog.setServiceName((String) jsonObject.get("serviceName"));
            }
            if (jsonObject.containsKey("machineAddress")) {
                String address = (String) jsonObject.get("machineAddress");
                serviceLog.setMachineAddress(address);
            }
            if (jsonObject.containsKey("triggerTime")) {
                String triggerTime = (String) jsonObject.get("triggerTime");
                serviceLog.setTriggerTime(new Date(Long.valueOf(triggerTime)));
            }

            if (jsonObject.containsKey("threadId")) {
                serviceLog.setThreadId(Integer.parseInt(jsonObject.get("threadId").toString()));
            }
            if (jsonObject.containsKey("level")) {
                serviceLog.setLevel((String) jsonObject.get("level"));
            }

            if (jsonObject.containsKey("errorMessage")) {
                serviceLog.setErrorMessage((String) jsonObject.get("errorMessage"));
            }
            if (jsonObject.containsKey("errorException")) {
                serviceLog.setErrorException((String) jsonObject.get("errorException"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        Date day = new Date();
        //todo 目前这个type类型不知道填什么东西暂时先放着
        serviceLog.setId(UUIDUtils.getUUID()).setType(0).setDeleted(0).setCtime(day).setMtime(day);
        return serviceLog;
    }

    /**
     * 不同位置异常保存
     * @param jsonObject
     * @return
     */
    private ExceptionType exceptionTypeAction(JSONObject jsonObject) {
        if (!jsonObject.containsKey("errorLocation")) {
            return null;
        }
        String location = (String) jsonObject.get("errorLocation");
        String serviceName = (String) jsonObject.get("serviceName");
        Date day = new Date();
        ExceptionType e = exceptionTypeDao.findByLocalAndServiceName(location, serviceName);
        if (null == e) {
            e = new ExceptionType();
            e.setId(UUIDUtils.getUUID()).setErrorLocation(location).setState(0).setDeleted(0).setCtime(day).setMtime(day);
            e.setNum(1).setServiceName(serviceName);
            exceptionTypeDao.insert(e);
        } else {
            e.setNum(e.getNum() + 1).setMtime(day);
            e.setServiceName(serviceName);
            exceptionTypeDao.updateById(e);
        }
        return e;
    }

    /**
     * 保存服务类型
     * @param jsonObject
     */
    private List<String> saveServiceNameType(JSONObject jsonObject) {
        if (!jsonObject.containsKey("serviceName")) {
            return null;
        }
        String serviceName = (String) jsonObject.get("serviceName");
        ServiceType serviceType = serviceTypeDao.findByName(serviceName);
        if (null != serviceType) {
            return cacheTask.getServerNamePhoneMap(serviceName);
        }
        Date day = new Date();
        serviceType = new ServiceType();
        serviceType.setId(UUIDUtils.getUUID()).setServiceName(serviceName).setDeleted(0).setCtime(day).setMtime(day);
        serviceTypeDao.insert(serviceType);
        return null;
    }

    private String stateString(int state) {
        switch (state) {
            case 0:
                return "待处理";
            case 1:
                return "已处理";
            case 2:
                return "处理中";
            default: return "待处理";
        }
    }

    /**
     * 60秒内相同的报警，只发送钉钉一次
     * @param excId
     * @param content
     * @return
     */
    private Boolean sendOrNot(String excId, JSONObject content) {
        if (timedCache.isEmpty()) {
            timedCache.schedulePrune(86400);
        }
        if (null != timedCache.get(excId)) {
            return true;
        }
        timedCache.put(excId, content, 60000); //30秒过期
        return false;
    }

    private Boolean dubboTimeOut(JSONObject content) {
        String errorMessage = (String) content.get("errorMessage");
        String errorException = (String) content.get("errorException");

        if (StringUtils.isBlank(errorMessage) && StringUtils.isBlank(errorException)) {
            return false;
        }

        if (StringUtils.isBlank(errorMessage) && StringUtils.isNotBlank(errorException)) {
            return errorException.contains(DUBBO_TIME_OUT);
        }

        if (StringUtils.isNotBlank(errorMessage) && StringUtils.isBlank(errorException)) {
            return errorMessage.contains(DUBBO_TIME_OUT_I) || errorMessage.contains("invoke time out");
        }

        if (StringUtils.isNotBlank(errorMessage) && StringUtils.isNotBlank(errorException)) {
            return errorMessage.contains(DUBBO_TIME_OUT_I) || errorMessage.contains("invoke time out")
                    || errorException.contains(DUBBO_TIME_OUT);
        }
        return false;
    }

    /**
     * 获取异常常见的原因
     * @return
     */
    private String getReason(String exception) {
        return "";
    }

}
