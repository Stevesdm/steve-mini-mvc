package com.steve.demo.service.impl;

import com.steve.demo.service.DemoService;
import com.steve.framework.annotation.SteveService;

/**
 * @Description:
 * @Author: stevejobson
 * @CreateDate: 2017/12/19 下午9:21
 */
@SteveService
public class DemoServiceImpl implements DemoService {

    public String queryName(String name) {
        return "hello : " + name;
    }
}
