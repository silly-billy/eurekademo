package com.example.demo.web.controller;

import com.baomidou.mybatisplus.plugins.Page;
import com.example.demo.core.common.entity.APIResult;
import com.example.demo.core.dal.domain.User;
import com.example.demo.core.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;


@Controller
@RequestMapping("api")
public class UserController {
    @Autowired
    UserService userService;

    @ResponseBody
    @RequestMapping(method = RequestMethod.GET,value = "/user")
    public APIResult<List<User>> getUserInfo(){
        return userService.findAllUserInfo();
    }

    @ResponseBody
    @RequestMapping(method = RequestMethod.GET,value = "/userPage")
    public APIResult<Page<User>> getUserPages(){
        //输出第二页
        return userService.findPageInfo(2,2);
    }
}
