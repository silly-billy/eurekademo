package com.example.demo.core.common.util;

import java.util.UUID;

/**
 * @Description: 随机生成8位
 */
public class UUIDGenerator {

    private static final int UUID_LENGTH = 8;
    public static String getUUID(){
        return uuidplus().replace("-","").toUpperCase().substring(0,UUID_LENGTH);
    }

    public static String getUUID(int length) {
        return uuidplus().replace("-","").toUpperCase().substring(0,length);
    }
    private static String uuidplus(){
        return UUID.randomUUID().toString()+UUID.randomUUID().toString();
    }

    public static void main(String[] args) {
        System.out.println(getUUID(4));
    }
}
