package com.steve.framework.servlet;

import com.steve.framework.annotation.SteveAutowired;
import com.steve.framework.annotation.SteveController;
import com.steve.framework.annotation.SteveRequestMapping;
import com.steve.framework.annotation.SteveService;
import com.sun.org.apache.xalan.internal.xslt.Process;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

/**
 * @Description:
 * @Author: stevejobson
 * @CreateDate: 2017/12/19 下午9:25
 */
public class SteveDispatcherServlet extends HttpServlet {


    private Properties properties = new Properties();

    private List<String> classes = new ArrayList<String>();

    private Map<String,Object> ioc = new HashMap<String,Object>();

    private Map<String,Method> handlerMapping = new HashMap<String, Method>();

    @Override
    public void init(ServletConfig config) throws ServletException {
        //初始化配置

        //1.加载配置文件，这里使用application.properties
        doLoadConfig(config.getInitParameter("contextConfigLocation"));
        //2.加载配置文件Bean
        doScanner(properties.getProperty("scanPackage"));
        //3.初始化这些类，并装载到IOC容器
        doInstance();
        //4.进行依赖注入
        doAutowired();
        //5.构造handlerMapping 映射url--method
        initHandleMapping();
        //6.匹配用户请求，用反射执行对应方法
        doDispatch();
        //返回结果

        System.out.println("init ....." + config);
        super.init(config);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doPost(req, resp);
    }


    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        System.out.println("dealing with request.....");
        super.doPost(req, resp);
    }

    @Override
    public void destroy() {
        super.destroy();
    }


    private void doLoadConfig(String location) {
        InputStream fis = this.getClass().getClassLoader().getResourceAsStream(location);
        try {
            properties.load(fis);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (fis != null){
                    fis.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    private void doScanner(String packageName) {
        //从class目录下找到所有的class文件
        URL url = this.getClass().getClassLoader().getResource("/" + packageName.replaceAll("\\.", "/"));
        File dir = new File(url.getFile());

        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                doScanner(packageName + "." + file.getName());
            } else {
                String className = packageName + "." + file.getName().replace(".class", "");
                classes.add(className);
            }
        }
    }

    private void doInstance() {
        if (classes.isEmpty()) {
            return;
        }

        try{
            for (String className : classes) {
                Class<?> clazz = Class.forName(className);
                //判断初始化哪些类
                if (clazz.isAnnotationPresent(SteveController.class)){

                    //beanName 默认首字母小写
                    String beanName = lowerFirstLetter(clazz.getSimpleName());

                    //初始化
                    ioc.put(beanName,clazz.newInstance());



                }else if (clazz.isAnnotationPresent(SteveService.class)){

                    SteveService steveService = clazz.getAnnotation(SteveService.class);

                    //判断serivce是否自己起名字
                    String beanName = steveService.value();
                    if ("".equals(beanName.trim())) {
                        ioc.put(beanName,clazz.newInstance());
                    }else {
                        beanName = lowerFirstLetter(clazz.getSimpleName());
                        ioc.put(beanName,clazz.newInstance());
                    }

                    Class<?>[] interfaces = clazz.getInterfaces();
                    for (Class<?> i : interfaces){
                        ioc.put(i.getName(),clazz.newInstance());
                    }

                }else {
                    continue;
                }

            }
        }catch (Exception e) {
            e.printStackTrace();
        }

    }


    private void doAutowired() {
        if (ioc.isEmpty()){return;}

        for (Map.Entry<String,Object> entry: ioc.entrySet()) {
            //把类下面所有的属性拿来
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                if (!field.isAnnotationPresent(SteveAutowired.class)){
                    continue;
                }
                SteveAutowired steveAutowired = field.getAnnotation(SteveAutowired.class);
                String beanName = steveAutowired.value().trim();
                if ("".equals(beanName)) {
                    beanName = field.getType().getName();
                }

                //私有的属性注入
                field.setAccessible(true);
                try {
                    field.set(entry.getValue(),ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                    continue;
                }
            }
        }
    }

    private void initHandleMapping() {
        if (ioc.isEmpty()){return;}

        for (Map.Entry<String,Object> entry: ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();

            //加了SteveCOntroller注解的类才有可能由RequestMapping
            if (!clazz.isAnnotationPresent(SteveController.class)){continue;}

            String url = "";

            if (clazz.isAnnotationPresent(SteveRequestMapping.class)) {
                SteveRequestMapping steveRequestMapping = clazz.getAnnotation(SteveRequestMapping.class);
                url = steveRequestMapping.value();
            }


            Method[] methods = clazz.getDeclaredMethods();
            for (Method method: methods) {
                if (!method.isAnnotationPresent(SteveRequestMapping.class)){continue;}
                SteveRequestMapping steveRequestMapping = method.getAnnotation(SteveRequestMapping.class);
                String mUrl = ("/"+url + steveRequestMapping.value()).replaceAll("/+","/");
                handlerMapping.put(mUrl,method);
                System.out.println(mUrl+"    pppp");
            }

        }
    }

    private void doDispatch() {

    }

    /**
     * 首字母小写
     * @param str
     * @return
     */
    private String lowerFirstLetter(String str) {
        char[] chars = str.toCharArray();
        chars[0] +=32;
        return String.valueOf(chars);
    }


}
