# webMagic迁移

vsCrawler出现的其中一个原因就是webMagic不能满足我的需求,但是国内使用webMagic的团队很多。如果曾经选择的webMagic,然后想迁移到VSCrawler,
这个时候相当于有需要重新学习一套框架? 也不一定,VSCrawler提供了一个桥接模块,可以将webMagic的部分功能直接迁移到VSCrawler。甚至类名都不需要修改,直接将
WebMagic的processor或者pipeline包装一下即可

### 可以移植的组建

但是VSCrawler不支持迁移webMagic所有功能,如果全部都能迁移,那么个人认为和WebMagic没有区别了。WebMagic最优秀的在于他的抽取方案,我们习惯上在webMagic实现实现的方案也是抽取。
所以,主要迁移支持就是抽取了。

- processor 可以直接转移到vscrawler
- pipeline 可以直接转移到vscrawler,但是不建议vsCrawler和webMagic的pipeline混用,受限于webMagic对结果的封装,数据结构不同导致两种pipeline不太兼容,如果混用,你需要手动关注数据结构差异问题
- downloader webMagic在下载方案是设计不太好的,所以对于downloader的迁移,VSCrawler不建议迁移,不过要迁移也是可以,但是不保证稳定性

除了这三个组件,其他的都不支持(注解可以手动支持,主要是支持注解又得多一个依赖,懒得弄了)


### 迁移方法

VSCrawler提供了一个桥接器 com.virjar.vscrawler.support.webmagic.WebMagicBridage,里面提供了三个静态方法
- com.virjar.vscrawler.support.webmagic.WebMagicBridage.transformProcessor 转化webMagic的processor到VSCrawler的processor
- com.virjar.vscrawler.support.webmagic.WebMagicBridage.transformProcessorWithDownloader 转化webMagic的processor到VSCrawler的processor,同时使用webMagic的downloader来下载数据
- com.virjar.vscrawler.support.webmagic.WebMagicBridage.transfromPipeline 转化webMagic的pipeline到VSCrawler的pipeline

### xsoup迁移
我们经常需要使用xpath来抽取数据,但是jsoup不支持xpath,所以webmagic实现了xsoup,但是sxoup的bug不少,而且功能比较弱。所以,索性就把xsoup换掉了,
vscrawelr使用JSoupXpath替代了XSoup,JSoupXpath对xpath的支持比XSoup完整,这样可以实现更加丰富的抽取功能了。
webmagic迁移到VScrawler,自动使用JSoupXpath实现xpath规则解析

