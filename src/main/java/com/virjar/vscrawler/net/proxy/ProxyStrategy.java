package com.virjar.vscrawler.net.proxy;

/**
 * Created by virjar on 17/4/30.
 * @author virjar
 * @since 0.0.1
 */
public enum ProxyStrategy {

    REQUEST, // 每次都换IP
    SESSION, // session使用的时候换IP
    USER, // 每个用户登录的事情确定代理IP
    NONE// 不代理
}
