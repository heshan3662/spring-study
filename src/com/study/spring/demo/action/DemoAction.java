package com.study.spring.demo.action;

import com.study.spring.demo.service.DemoService;
import com.study.spring.frame.annotation.HSAutowired;
import com.study.spring.frame.annotation.HSController;
import com.study.spring.frame.annotation.HSRequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

@HSController
@HSRequestMapping(  "/demo" )
public class DemoAction {

    @HSAutowired
    DemoService demoService;


    @HSRequestMapping("/hello")
    public void    hello(HttpServletRequest req, HttpServletResponse resp){
        try {
            Map<String,String[]> params = req.getParameterMap();
            String  name = "guest";
            if(params.containsKey("name")){
                name = params.get("name")[0].toString();
            }
            String res = demoService.sayHello(name);
            resp.getWriter().write(res );
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
