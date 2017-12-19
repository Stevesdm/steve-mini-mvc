package com.steve.framework.annotation;

import java.lang.annotation.*;

/**
 * @Description:
 * @Author: stevejobson
 * @CreateDate: 2017/12/19 下午9:08
 */
@Target({ElementType.TYPE,ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SteveRequestMapping {

    String value() default "";

}
