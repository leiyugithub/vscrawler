package com.virjar.vscrawler.web.model;

import lombok.Data;

/**
 * Created by virjar on 2018/1/17.<br>
 * a sync grab request,maybe from http controller(tomcat,servlet,springMVC),
 */
@Data
public class GrabRequest {
    private String crawlerName;
    private String crawlerVersion;
    private String queryPage;
    private Object param;
}
