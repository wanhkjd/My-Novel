package io.github.xxyopen.novel.crawl;

import io.github.xxyopen.novel.crawl.config.CrawlProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 小说爬取与 TXT 导入工具启动类
 *
 * <p>功能一：按配置文件中的站点规则爬取指定小说，写入 novel 数据库；</p>
 * <p>功能二：解析本地小说 TXT 文件（自动识别编码、按章节切分），写入 novel 数据库。</p>
 * <p>启动后访问 http://localhost:8899/ 打开网页操作台。</p>
 */
@SpringBootApplication
@MapperScan("io.github.xxyopen.novel.crawl.mapper")
@EnableConfigurationProperties(CrawlProperties.class)
public class CrawlApplication {

    /**
     * 应用入口
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(CrawlApplication.class, args);
    }

}
