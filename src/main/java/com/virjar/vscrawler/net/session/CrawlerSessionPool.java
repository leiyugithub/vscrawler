package com.virjar.vscrawler.net.session;

import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.virjar.vscrawler.event.support.AutoEventRegistry;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;
import com.googlecode.aviator.AviatorEvaluator;
import com.virjar.vscrawler.event.systemevent.CrawlerConfigChangeEvent;
import com.virjar.vscrawler.net.user.User;
import com.virjar.vscrawler.util.SingtonObjectHolder;
import com.virjar.vscrawler.util.VSCrawlerConstant;

/**
 * Created by virjar on 17/4/15.<br/>
 * 创建并管理多个用户的链接
 * @author virjar
 * @since 0.0.1
 */
public class CrawlerSessionPool implements CrawlerConfigChangeEvent {

    private static final Logger logger = LoggerFactory.getLogger(CrawlerSessionPool.class);

    private ConcurrentLinkedQueue<CrawlerSession> allSessions = new ConcurrentLinkedQueue<>();

    /**
     * 最大空闲时间,默认25分钟
     */
    private long maxIdle = 25 * 60 * 1000;

    /**
     * 至少等待事件,默认10s
     */
    private long minIdl = 10 * 1000;

    /**
     * 最多连续使用时间
     */
    private long maxDuration = 60 * 60 * 1000;

    /**
     * 一个用户最大并发数
     */
    private int maxOccurs = 1;

    /**
     * 活跃session数目
     */
    private int activeUser = Integer.MAX_VALUE;

    private LoginHandler defaultLoginHandler;

    private Set<User> allUser;

    private Set<User> idlUser;

    private int monitorThreadNumber = 2;

    private ThreadPoolExecutor monitorPool;

    private AtomicInteger sessionCreateThreadNum = new AtomicInteger(0);

    public CrawlerSessionPool(Set<User> allUser, LoginHandler defaultLoginHandler) {
        this.allUser = allUser;
        idlUser = Sets.newConcurrentHashSet(allUser);
        this.defaultLoginHandler = defaultLoginHandler;
        monitorPool = new ThreadPoolExecutor(monitorThreadNumber, monitorThreadNumber, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>());
        // 注册事件监听,接收配置文件变更消息,下面两句比较巧妙
        changeWithProperties(SingtonObjectHolder.vsCrawlerConfigFileWatcher.loadedProperties());
        AutoEventRegistry.getInstance().registerObserver(this);
    }

    private static Set<User> mockUser(int userNumber) {
        Set<User> allUser = Sets.newConcurrentHashSet();
        for (int i = 0; i < userNumber; i++) {
            allUser.add(new User());
        }
        return allUser;
    }

    private synchronized void addIdlUser(User user) {
        idlUser.add(user);
    }

    private synchronized User pollIdlUser() {
        if (idlUser.size() == 0) {
            logger.info("当前系统没有用户信息");
            return null;
        }
        User next = idlUser.iterator().next();
        idlUser.remove(next);
        return next;
    }

    public CrawlerSessionPool(int userNumber) {
        this(mockUser(userNumber), new EmptyLoginHandler());
    }

    private CrawlerSession createNewSession() {
        User user = pollIdlUser();
        if (user == null) {
            return null;
        }
        return new CrawlerSession(user, defaultLoginHandler);
    }

    public synchronized CrawlerSession borrowOne() {
        logger.info("当前会话池中,共有:{}个用户可用", allSessions.size());
        CrawlerSession session = allSessions.poll();
        if (session == null) {
            session = allSessions.poll();
            if (session == null) {
                // 第一个session同步创建,后面的可以异步创建,所以这里这么写
                logger.info("第一个用户开始登录");
                session = createNewSession();
                if (session == null) {
                    logger.info("当前系统没有用户信息");
                    return null;
                }
                // allSessions.offer(session);
            }
        }

        for (int i = 0; i < allSessions.size() + 1; i++, session = allSessions.poll()) {
            if (session == null) {
                break;
            }
            allSessions.offer(session);
            if (!session.getEnable()) {
                monitorPool.submit(new LoginThread(session));
                continue;
            }

            if (System.currentTimeMillis() - session.getLastActiveTimeStamp() > maxIdle) {
                logger.info("session使用距离上次时间超过:{}秒,检查session是否存在", maxIdle / 1000);
                monitorPool.submit(new SessionTestThread(session));
                continue;
            }

            if (System.currentTimeMillis() - session.getLastActiveTimeStamp() < minIdl) {
                logger.info("session使用距离上次小于超过:{}秒,检查session是否存在,暂时放弃使用这个账号", minIdl / 1000);
                continue;
            }

            if (System.currentTimeMillis() - session.getInitTimeStamp() > maxDuration) {
                logger.info("session使用距离超过最大使用时间,暂时下线这个session", maxIdle / 1000);
                User user = session.getUser();
                addIdlUser(user);
                allSessions.remove(session);
                continue;
            }

            if (session.borrowTimes() >= maxOccurs) {
                logger.info("当前session超过最大并发数:{} 不能被使用", maxOccurs);
                continue;
            }

            if (allSessions.size() < activeUser && idlUser.size() > 0) {
                // 扩大活跃账户数量
                monitorPool.submit(new CreateSessionThread());
            }

            session.recordBorrow();
            return session;
        }

        //
        if (session == null) {
            session = createNewSession();
            if (session == null) {
                return null;
            }
        }

        if (session.getEnable()) {
            allSessions.offer(session);
            session.recordBorrow();
            return session;
        }

        session = createNewSession();
        if (session == null) {
            return null;
        }
        if (session.getEnable()) {
            allSessions.offer(session);
            session.recordBorrow();
            return session;
        }

        return null;
    }

    private void changeWithProperties(Properties properties) {
        activeUser = NumberUtils.toInt(
                AviatorEvaluator.exec(properties.getProperty(VSCrawlerConstant.SESSION_POOL_ACTIVE_USER)).toString(),
                Integer.MAX_VALUE);
        maxIdle = NumberUtils.toInt(
                AviatorEvaluator.exec(properties.getProperty(VSCrawlerConstant.SESSION_POOL_MAX_IDLE)).toString(),
                150000);

        maxDuration = NumberUtils.toInt(
                AviatorEvaluator.exec(properties.getProperty(VSCrawlerConstant.SESSION_POOL_MAX_DURATION)).toString(),
                3600000);
        maxOccurs = NumberUtils.toInt(
                AviatorEvaluator.exec(properties.getProperty(VSCrawlerConstant.SESSION_POOL_MAX_OCCURS)).toString(), 1);
        minIdl = NumberUtils.toInt(
                AviatorEvaluator.exec(properties.getProperty(VSCrawlerConstant.SESSION_POOL_MIN_IDLE)).toString(),
                10000);
        int newThreadNumber = NumberUtils.toInt(AviatorEvaluator
                .exec(properties.getProperty(VSCrawlerConstant.SESSION_POOL_MONTOR_THREAD_NUMBER)).toString(), 2);
        if(newThreadNumber != monitorThreadNumber){
            monitorThreadNumber = newThreadNumber;
            monitorPool.setMaximumPoolSize(monitorThreadNumber);
            monitorPool.setCorePoolSize(monitorThreadNumber);
        }

    }

    /**
     * 当配置信息变更过来的时候,会回调这里
     * 
     * @param oldProperties 旧配置文件内容
     * @param newProperties 新配置文件内容
     */
    @Override
    public void configChange(Properties oldProperties, Properties newProperties) {
        changeWithProperties(newProperties);
    }

    private class CreateSessionThread implements Runnable {

        @Override
        public void run() {
            try {
                if (sessionCreateThreadNum.incrementAndGet() + allSessions.size() < activeUser) {
                    User user = pollIdlUser();
                    if (user == null) {
                        return;
                    }
                    CrawlerSession session = new CrawlerSession(user, defaultLoginHandler);
                    allSessions.offer(session);
                }
            } finally {
                sessionCreateThreadNum.decrementAndGet();
            }
        }
    }

    private class SessionTestThread implements Runnable {
        private CrawlerSession session;

        public SessionTestThread(CrawlerSession session) {
            this.session = session;
        }

        @Override
        public void run() {
            session.testLoginState();
        }
    }

    private class LoginThread implements Runnable {
        private CrawlerSession session;

        public LoginThread(CrawlerSession session) {
            this.session = session;
        }

        @Override
        public void run() {
            logger.info("账号:{}当前登录状态不可用,尝试重新登录", session.getUser().getUserID());
            if (session.login() && !session.isLogin()) {
                logger.info("登录失败,禁用本账户");
                allSessions.remove(session);// TODO 禁用上下线逻辑
            }
        }
    }
}
