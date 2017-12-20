package com.steve.demo.controller;

import com.steve.demo.service.DemoService;
import com.steve.framework.annotation.SteveAutowired;
import com.steve.framework.annotation.SteveController;
import com.steve.framework.annotation.SteveRequestMapping;
import com.steve.framework.annotation.SteveRequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @Description:
 * @Author: stevejobson
 * @CreateDate: 2017/12/19 下午9:16
 */
@SteveController
public class DemoController {

    @SteveAutowired
    DemoService demoService;

    @SteveRequestMapping("/demo/query")
    public void query(
            HttpServletRequest request,
            HttpServletResponse response,
            @SteveRequestParam("name") String name
    ){
        out(response,demoService.queryName(name));
    }



    public void out(HttpServletResponse resp,String str){
        try {
            resp.getWriter().write(str);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
