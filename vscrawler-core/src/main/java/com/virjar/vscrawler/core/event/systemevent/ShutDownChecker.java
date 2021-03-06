package com.virjar.vscrawler.core.event.systemevent;

import com.virjar.vscrawler.core.VSCrawlerContext;
import com.virjar.vscrawler.core.event.support.AutoEvent;

/**
 * Created by virjar on 17/7/28.
 */
public interface ShutDownChecker {
    @AutoEvent(clearExpire = true)
    void checkShutDown(VSCrawlerContext vsCrawlerContext);
}