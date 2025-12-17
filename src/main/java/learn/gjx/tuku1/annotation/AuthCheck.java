package learn.gjx.tuku1.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import learn.gjx.tuku1.model.enums.UserRoleEnum;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthCheck {
    /**
     * 必须具有某个角色（如 ADMIN）
     * 默认值为 USER，表示只需要登录即可
     */
    UserRoleEnum mustRole() default UserRoleEnum.USER;
}
