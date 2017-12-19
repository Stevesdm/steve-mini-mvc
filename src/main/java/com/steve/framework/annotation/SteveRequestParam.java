package com.steve.framework.annotation;

import java.lang.annotation.*;

/**
 * @Description:
 * @Author: stevejobson
 * @CreateDate: 2017/12/19 下午9:12
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SteveRequestParam {

    String value() default "";

    boolean required() default true;
}
