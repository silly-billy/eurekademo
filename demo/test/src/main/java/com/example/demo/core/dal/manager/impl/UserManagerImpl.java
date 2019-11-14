package com.example.demo.core.dal.manager.impl;

import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.baomidou.mybatisplus.mapper.Wrapper;
import com.baomidou.mybatisplus.plugins.Page;
import com.example.demo.core.dal.domain.User;
import com.example.demo.core.dal.dao.UserDao;
import com.example.demo.core.dal.manager.UserManager;
import com.example.demo.core.common.base.BaseManagerImpl;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

@Component
public class UserManagerImpl extends BaseManagerImpl<UserDao, User> implements UserManager{

    @Resource
    UserDao userDao;

    @Override
    public List<User> findAllUserInfo() {
        Wrapper<User> queryWrapper = new EntityWrapper<>();
        queryWrapper.eq("is_delete",0);
        return userDao.selectList(queryWrapper);
    }

    @Override
    public Page<User> findPageInfo(int current, int num) {
        Wrapper<User> pageWrapper = new EntityWrapper<>();
        pageWrapper.eq("is_delete",0);
        Page page = new Page(current,num);
        Page<User> pages = selectPage(page,pageWrapper);
        return pages;
    }
}
