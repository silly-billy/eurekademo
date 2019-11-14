package com.example.demo.core.dal.manager;

import com.baomidou.mybatisplus.plugins.Page;
import com.example.demo.core.dal.domain.User;
import com.example.demo.core.common.base.BaseManager;

import java.util.List;

public interface UserManager extends BaseManager<User> {

    /*查询所有用户信息*/
    List<User> findAllUserInfo();

    /*MP分页demo*/
    Page<User> findPageInfo(int current, int num);
}
