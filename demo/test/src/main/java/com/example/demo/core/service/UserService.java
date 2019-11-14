package com.example.demo.core.service;

import com.baomidou.mybatisplus.plugins.Page;
import com.example.demo.core.common.entity.APIResult;
import com.example.demo.core.dal.domain.User;

import java.util.List;

public interface UserService {

    //查询所有用户信息
    APIResult<List<User>> findAllUserInfo();

    /**
     * MP分页demo
     * current -- 当前页
     * num -- 每页的数据量
     */
    APIResult<Page<User>> findPageInfo(int current,int num);
}
