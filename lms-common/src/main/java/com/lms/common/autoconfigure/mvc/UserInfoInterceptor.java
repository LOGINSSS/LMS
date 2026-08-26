package com.lms.common.autoconfigure.mvc;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import com.lms.common.constants.Constant;
import com.lms.common.utils.JsonUtils;
import com.lms.common.utils.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 用户信息透传拦截器（业务服务侧）
 *
 * 用途：解析网关透传的 user-info 头（JSON：{"userId":..,"userType":..}），写入 UserContext，
 * 让业务代码直接 UserContext.getUser() 取当前登录用户，无需自己解析 JWT。
 *
 * 适用场景：接入登录体系的业务服务（如 lms-user）；通过配置
 * lms.mvc.user-header-enabled=true 开启（见 UserInfoInterceptorConfiguration）。
 * 注意：网关已校验 JWT 并写入该头，业务服务信任此头即可；头格式非法时忽略，不阻断请求。
 */
public class UserInfoInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userInfo = request.getHeader(Constant.HEADER_USER_INFO);
        if (StrUtil.isNotBlank(userInfo)) {
            try {
                JSONObject obj = JsonUtils.parseObj(userInfo);
                Long userId = obj.getLong("userId");
                Integer userType = obj.getInt("userType");
                if (userId != null) {
                    UserContext.setUser(userId);
                }
                if (userType != null) {
                    UserContext.setUserType(userType);
                }
            } catch (Exception e) {
                // 头格式非法时忽略，不阻断请求（无用户上下文）
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.removeUser();
    }
}
