package com.steve.framework.servlet;

import com.steve.framework.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Description:
 * @Author: stevejobson
 * @CreateDate: 2017/12/19 下午9:25
 */
public class SteveDispatcherServlet extends HttpServlet {


    private Properties properties = new Properties();

    private List<String> classes = new ArrayList<String>();

    private Map<String, Object> ioc = new HashMap<String, Object>();

    private List<Handler> handlerMapping = new ArrayList<Handler>();

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
        //在doPost进行处理
        //返回结果

        System.out.println("init ....." + config);
        super.init(config);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }


    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        System.out.println("dealing with request.....");
        try {
            doDispatch(req, resp);
        } catch (Exception e) {
            resp.getWriter().write("internal Exception 500:" + Arrays.toString(e.getStackTrace()));
        }
        resp.getWriter().flush();
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
                if (fis != null) {
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

        try {
            for (String className : classes) {
                Class<?> clazz = Class.forName(className);
                //判断初始化哪些类
                if (clazz.isAnnotationPresent(SteveController.class)) {

                    //beanName 默认首字母小写
                    String beanName = lowerFirstLetter(clazz.getSimpleName());

                    //初始化
                    ioc.put(beanName, clazz.newInstance());


                } else if (clazz.isAnnotationPresent(SteveService.class)) {

                    SteveService steveService = clazz.getAnnotation(SteveService.class);

                    //判断serivce是否自己起名字
                    String beanName = steveService.value();
                    if ("".equals(beanName.trim())) {
                        ioc.put(beanName, clazz.newInstance());
                    } else {
                        beanName = lowerFirstLetter(clazz.getSimpleName());
                        ioc.put(beanName, clazz.newInstance());
                    }

                    Class<?>[] interfaces = clazz.getInterfaces();
                    for (Class<?> i : interfaces) {
                        ioc.put(i.getName(), clazz.newInstance());
                    }

                } else {
                    continue;
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    private void doAutowired() {
        if (ioc.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            //把类下面所有的属性拿来
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                if (!field.isAnnotationPresent(SteveAutowired.class)) {
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
                    field.set(entry.getValue(), ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                    continue;
                }
            }
        }
    }

    private void initHandleMapping() {
        if (ioc.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();

            //加了SteveCOntroller注解的类才有可能由RequestMapping
            if (!clazz.isAnnotationPresent(SteveController.class)) {
                continue;
            }

            String url = "";

            if (clazz.isAnnotationPresent(SteveRequestMapping.class)) {
                SteveRequestMapping steveRequestMapping = clazz.getAnnotation(SteveRequestMapping.class);
                url = steveRequestMapping.value();
            }


            Method[] methods = clazz.getDeclaredMethods();
            for (Method method : methods) {
                if (!method.isAnnotationPresent(SteveRequestMapping.class)) {
                    continue;
                }
                SteveRequestMapping steveRequestMapping = method.getAnnotation(SteveRequestMapping.class);
                String mUrl = ("/" + url + steveRequestMapping.value()).replaceAll("/+", "/");

                String regex = ("/" + url + steveRequestMapping.value()).replaceAll("/+", "/");
                Pattern pattern = Pattern.compile(regex);
                handlerMapping.add(new Handler(pattern, entry.getValue(), method));


            }

        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        try {

            Handler handler = getHandler(req);
            if (handler == null) {
                resp.getWriter().write("404 not found");
                return;
            }

            Class<?>[] paramTypes = handler.method.getParameterTypes();

            Object[] paramValues = new Object[paramTypes.length];
            Map<String, String[]> params = req.getParameterMap();
            for (Map.Entry<String, String[]> param : params.entrySet()) {
                String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "").replaceAll(",\\s", ",");

                if (!handler.paramIndexMapping.containsKey(param.getKey())) {
                    continue;
                }
                int index = handler.paramIndexMapping.get(param.getKey());
                paramValues[index] = convert(paramTypes[index], value);
            }
            int reqIndex = handler.paramIndexMapping.get(HttpServletRequest.class.getName());
            paramValues[reqIndex] = req;

            int respIndex = handler.paramIndexMapping.get(HttpServletResponse.class.getName());
            paramValues[respIndex] = resp;


            handler.method.invoke(handler.controller, paramValues);
        } catch (Exception e) {
            throw e;
        }


    }


    private Handler getHandler(HttpServletRequest req) throws Exception {
        if (handlerMapping.isEmpty()) {
            return null;
        }

        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replace(contextPath, "").replace("/+", "/");
        for (Handler handler : handlerMapping) {
            try {
                Matcher matcher = handler.pattern.matcher(url);
                if (!matcher.matches()) {
                    continue;
                }
                return handler;
            } catch (Exception e) {
                throw e;
            }
        }
        return null;
    }

    private Object convert(Class<?> type, String value) {
        if (Integer.class == type) {
            return Integer.valueOf(value);
        }
        return value;
    }

    /**
     * 首字母小写
     *
     * @param str
     * @return
     */
    private String lowerFirstLetter(String str) {
        char[] chars = str.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }


    private class Handler {
        protected Object controller;   //保存方法对应实例
        protected Method method;       //保存映射方法
        protected Pattern pattern;
        protected Map<String, Integer> paramIndexMapping;   //参数顺序


        protected Handler(Pattern pattern, Object controller, Method method) {
            this.controller = controller;
            this.pattern = pattern;
            this.method = method;

            paramIndexMapping = new HashMap<String, Integer>();
            putParamIndexMapping(method);
        }

        protected void putParamIndexMapping(Method method) {
            Annotation[][] pa = method.getParameterAnnotations();
            for (int i = 0; i < pa.length; i++) {
                for (Annotation a : pa[i]) {
                    if (a instanceof SteveRequestParam) {
                        String paramName = ((SteveRequestParam) a).value();
                        if (!"".equals(paramName.trim())) {
                            paramIndexMapping.put(paramName, i);
                        }
                    }
                }
            }

            //提取方法中request和response参数
            Class<?>[] paramsTypes = method.getParameterTypes();
            for (int i = 0; i < paramsTypes.length; i++) {
                Class<?> type = paramsTypes[i];
                if (type == HttpServletRequest.class || type == HttpServletResponse.class) {
                    paramIndexMapping.put(type.getName(), i);
                }
            }

        }


    }


}
