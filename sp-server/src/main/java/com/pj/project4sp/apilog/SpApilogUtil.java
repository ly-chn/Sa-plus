package com.pj.project4sp.apilog;

import java.util.Date;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pj.current.satoken.StpUserUtil;
import com.pj.utils.LogUtil;
import com.pj.utils.sg.AjaxJson;
import com.pj.utils.sg.WebNbUtil;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SpringMvcUtil;
import cn.hutool.core.util.IdUtil;

/**
 * 工具类：api请求记录表
 * @author kong 
 *
 */
@Component
public class SpApilogUtil {

//	req_id, req_ip, req_api, req_parame, req_token, user_id, admin_id, start_time,
//	res_code, res_msg, res_string, end_time, cost_time)
	
	/** 底层 Mapper 对象 */
	static SpApilogMapper spApilogMapper;
	@Autowired
	public void setSpApilogMapper(SpApilogMapper spApilogMapper) {
		SpApilogUtil.spApilogMapper = spApilogMapper;
	}
	
	
	
	static final String apilog_obj_save_key = "apilog_obj_save_key";
	static final String apilog_obj_save_id_key = "apilog_obj_save_id_key";
	
	
	
	// 请求开始时调用，开始计时 
	public static void startRequest() {
		if(isWeb() == false)  {
			return;
		}
		
		// 1、开始时 
    	HttpServletRequest request = SpringMvcUtil.getRequest();
    	SpApilog a = new SpApilog();
    	a.setId(getSnowflakeId());			// 随机一个请求id
    	a.setReq_ip(WebNbUtil.getIP(request));		// 请求ip 
    	a.setReq_api(request.getRequestURI());;		// 请求接口 
    	a.setReq_parame(JSON.toJSONString(WebNbUtil.getParamsMap2(request)));	// 请求参数 
    	a.setReq_token(StpUtil.getTokenValue());			// 请求token 
    	a.setReq_header(JSON.toJSONString(WebNbUtil.getHeaderMap(request)));			// 请求header 
    	a.setReq_type(request.getMethod());			// 请求类型 
    	a.setAdmin_id(StpUtil.getLoginId(0L));	// 本次请求admin_id 
    	a.setUser_id(StpUserUtil.getLoginId(0L));		// 本次请求user_id 
    	a.setStart_time(new Date());				// 请求开始时间 
    	request.setAttribute(apilog_obj_save_key, a);
    	
    	// 控制台日志 
    	LogUtil.info("===============================================================");
		LogUtil.info("IP: " + a.getReq_ip() + "\tr-> " + a.getReq_api()+ "\tp-> " + a.getReq_parame());
	}
	

	// 请求结束时调用，结束计时 
	public static void endRequest(AjaxJson aj) {
		if(isWeb() == false)  {
			return;
		}
		
		// 读取本次请求的 ApiLog 对象 
		HttpServletRequest request = SpringMvcUtil.getRequest();
		SpApilog a = (SpApilog)request.getAttribute(apilog_obj_save_key);
		if(a == null) {
//	    	LogUtil.info("未找到相应ApiLog对象（可能原因：全局异常），aj=" + aj);
	    	SpApilogUtil.startRequest();	
	    	a = (SpApilog)request.getAttribute(apilog_obj_save_key);
		}

		// 保存数据库
		try {
			// 开始结束计时 
			a.setRes_code(aj.getCode()); 	// res 状态码
			a.setRes_msg(aj.getMsg());		// res 描述信息 
			// a.setRes_string(JSON.toJSONString(aj));		// res 字符串形式
			a.setRes_string(new ObjectMapper().writeValueAsString(aj));		// res 字符串形式
			a.setEnd_time(new Date());			// 请求结束时间 
			a.setCost_time((int)(a.getEnd_time().getTime() - a.getStart_time().getTime()));	// 请求消耗时长，单位ms 
			
			// res 字符串过长时禁止写入
			if(a.getRes_string().length() > 50000) {
				a.setRes_string("{\"msg\": \"数据过长，无法写入 (length=" + a.getRes_string().length() + ")\"}");		
			}
		
        	LogUtil.info("本次请求耗时：" + ((a.getCost_time() + 0.0) / 1000) + "s, 返回：" + a.getRes_string());
        	spApilogMapper.saveObj(a);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	
	

	/**
	 * 当前是否为web环境 
	 */
	public static boolean isWeb() {
		ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();// 大善人SpringMVC提供的封装 
		if(servletRequestAttributes != null) {
			return true;
		}
		return false;
	}


	/** 获取当前请求的id */
	public static String getCurrReqId() {
		HttpServletRequest request = SpringMvcUtil.getRequest();
		String id = (String)request.getAttribute(apilog_obj_save_id_key);
		if(id == null) {
			id = IdUtil.simpleUUID();
			request.setAttribute(apilog_obj_save_id_key, id);
		}
		return id;
	}


	/**
	 * 根据雪花算法，返回唯一id 
	 * (此地方将workerId写死为1，如果你在分布式场景中应用此方法，你需要对workerId生成策略进行改造)
	 * @return
	 */
	public static String getSnowflakeId() {
		return IdUtil.getSnowflake(1, 1).nextIdStr();
	}
	
}
