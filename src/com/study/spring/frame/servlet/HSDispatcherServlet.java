package com.study.spring.frame.servlet;

import com.study.spring.frame.annotation.*;


import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

public class HSDispatcherServlet extends HttpServlet {

    private Map<String,Object> ioc = new HashMap<String,Object>();

    private List<String> classNames = new ArrayList<String>();

    private Properties  contextConfig = new Properties();

    private Map<String , Method> handlerMapping = new HashMap<String, Method>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req,resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        //完成调度
        doDispatch(req,resp);
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String url=req.getRequestURI();
        String contextPath= req.getContextPath();
        url = url.replaceAll(contextPath,"").replaceAll("/+","/");
        if(!this.handlerMapping.containsKey(url )){
            resp.getWriter().write("404 ! ");
            return;
        }
        Method method = handlerMapping.get(url);
//        Map<String,String[]> params = req.getParameterMap();
        //硬编码
        String beanName  = toLowerFirstCase(method.getDeclaringClass().getSimpleName());
        try {
            method.invoke(ioc.get(beanName),new Object[]{req,resp } );
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }

    }



    @Override
    public void init(ServletConfig config) throws ServletException {
        System.out.println("start init dispatcher  ! ");

        //1.加载配置
        doLoadConfig(config.getInitParameter("contextConfigLocation"));

        //2.扫描相关类
        doScenner(contextConfig.getProperty("scanPackage"));

        //3.实例化类并缓存到ioc容器中
        doInstance();

        //4.完成依赖注入
        doAutowired();

        //5.初始化HandlerMapping
        doInitHandlerMapping();

        //完成加载spring
        System.out.println("init dispatcher success! ");
        
    }

    private void doInitHandlerMapping() {
        if(ioc.isEmpty()){
            return;
        }
        for(Map.Entry entry :ioc.entrySet()){
            Class<?> clazz = entry.getValue().getClass();
            if( !clazz.isAnnotationPresent(HSController.class)){
                continue;
            }
            String   baseUrl="";
            if(clazz.isAnnotationPresent(HSController.class)){
                HSRequestMapping hsRequestMapping  =  clazz.getAnnotation(HSRequestMapping.class);
                   baseUrl =  hsRequestMapping.value();
            }
            for(Method method : clazz.getMethods()){
                if(method.isAnnotationPresent(HSRequestMapping.class)){
                    HSRequestMapping hsRequestMapping  =  method.getAnnotation(HSRequestMapping.class);
                    String  url = ("/"+ baseUrl +"/"+ hsRequestMapping.value()).replaceAll("//","/");
                    handlerMapping.put(url, method);
                }
            }
        }
    }

    /**
     * 1.循环出ioc容器中对象
     * 2.获取遍历所有声明的字段
     * 3.字段是否被HSAutowired注释
     * 4.被注释的对象进行依赖注入
     */
    private void doAutowired() {
        if(ioc.isEmpty()){
            return;
        }
        for(Map.Entry entry :ioc.entrySet()){
            //获取对象所有声明的字段
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for(Field field :fields){
                if( !field.isAnnotationPresent(HSAutowired.class)){
                    continue;
                }
                HSAutowired hsAutowired = field.getAnnotation(HSAutowired.class);
                String  beanName  = hsAutowired.value().trim();
                if("".equals(beanName)){
                    beanName = toLowerFirstCase(field.getType().getSimpleName());
                }
                //private 暴力访问
                field.setAccessible(true);
                try {
                    //依赖注入 ,将beanName实例放入 到entry的类中
                    field.set(entry.getValue(),ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 1.遍历扫描类名集合classNames
     * 2.特定标记的类进行实例化
     * 3.实例化的对象放入ioc容器中
     */
    private void doInstance() {
        if(classNames.isEmpty()){
            return;
        }
        for(String className : classNames){
            try {
                Class clazz = Class.forName(className);
                if( clazz.isAnnotationPresent(HSController.class) ){
                    String beanName =  toLowerFirstCase(clazz.getSimpleName());
                    Object instance = clazz.newInstance();
                    ioc.put(beanName,instance);
                }else  if( clazz.isAnnotationPresent(HSService.class) ){

                    //1.首字母小写
                    String beanName =  toLowerFirstCase(clazz.getSimpleName());
                    //2.自定义命名
                    HSService hsService  = (HSService) clazz.getAnnotation(HSService.class);
                    if(!"".equals(  hsService.value() )   ){
                        beanName = hsService.value();
                    }
                    Object instance = clazz.newInstance();
                    ioc.put(beanName,instance);
                    //3.如果是接口
                     for(Class i : clazz.getInterfaces()){
                         if(ioc.containsKey(i.getName())){
                             throw new  Exception(i.getName() + " ，The BeanName is exist ！ ");
                         }
                         ioc.put(i.getName(),instance);
                     }
                }


            } catch ( Exception e) {
                e.printStackTrace();
            }

        }
    }

    /**
     * //工具类：将类的名字首字母小写
     * @param simpleName
     * @return
     */
    private String toLowerFirstCase(String simpleName) {
        char [] chars = simpleName.toCharArray();
        if(chars[0] >= 65 && chars[0] <= 90 ){
            chars[0]+= 32 ;
            return String.valueOf(chars);
        }else {
            return simpleName;
        }
    }

    /**
     * 1.扫描指定的包进行遍历
     * 2.将包下所有class对象放入classNames容器
     * @param scanPackage 包名
     */
    private void doScenner(String scanPackage) {
        // System.getProperty("user.dir"); //项目根目录
        //扫描scanPachage 的class
        URL url = this.getClass().getClassLoader().getResource("/" + scanPackage
                .replaceAll("\\.","/"));
        File classPath = new File(url.getFile());
        for(File file: classPath.listFiles()){
            if(file.isDirectory()){
                doScenner(scanPackage+"." + file.getName());
            }else {
                if(!file.getName().endsWith(".class")){
                    continue;
                }
                String className =  scanPackage + "." + file.getName().replace( ".class","");
                classNames.add(className);
            }
        }
    }

    /**
     * 加载 contextConfigLocation 文件
     * @param contextConfigLocation
     */
    private void doLoadConfig(String contextConfigLocation ) {
        InputStream is =  this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try {
            contextConfig.load(is);
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if(null != is){
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }
}
