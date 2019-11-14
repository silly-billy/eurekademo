package com.example.demo.core.service.Impl;

import com.baomidou.mybatisplus.plugins.Page;
import com.example.demo.core.common.entity.APIResult;
import com.example.demo.core.dal.domain.User;
import com.example.demo.core.dal.manager.UserManager;
import com.example.demo.core.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    UserManager userManager;

    @Override
    public APIResult<List<User>> findAllUserInfo() {
        return APIResult.ok(userManager.findAllUserInfo());
    }

    @Override
    public APIResult<Page<User>> findPageInfo(int current,int num) {
        Page<User> pages =  userManager.findPageInfo(current,num);
        return APIResult.ok(pages);
    }
}
