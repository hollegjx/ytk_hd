package learn.gjx.tuku1.aop;

import learn.gjx.tuku1.annotation.AuthCheck;
import learn.gjx.tuku1.exception.BusinessException;
import learn.gjx.tuku1.exception.ErrorCode;
import learn.gjx.tuku1.model.entity.User;
import learn.gjx.tuku1.model.enums.UserRoleEnum;
import learn.gjx.tuku1.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 权限校验 AOP 切面
 */
@Aspect
@Component
@Slf4j
public class AuthInterceptor {

    @Resource
    private UserService userService;

    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        UserRoleEnum mustRole = authCheck.mustRole();

        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();

        User loginUser = userService.getLoginUser(request);
        UserRoleEnum userRole = UserRoleEnum.getEnumByValue(loginUser.getUserRole());

        if (userRole == null) {
            log.warn("用户角色异常，userId: {}, userRole: {}", loginUser.getId(), loginUser.getUserRole());
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "用户角色异常");
        }

        // 要求管理员权限，但用户不是管理员
        if (UserRoleEnum.ADMIN.equals(mustRole) && !UserRoleEnum.ADMIN.equals(userRole)) {
            log.warn("权限不足，需要: {}, 当前: {}, userId: {}", mustRole, userRole, loginUser.getId());
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "需要管理员权限");
        }

        return joinPoint.proceed();
    }
}
