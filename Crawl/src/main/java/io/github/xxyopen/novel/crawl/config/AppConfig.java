package io.github.xxyopen.novel.crawl.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 应用通用配置：爬取/导入任务使用的线程池
 */
@Configuration
public class AppConfig {

    /**
     * 爬取与导入任务线程池（任务在后台异步执行，前台通过任务接口轮询进度）
     *
     * @return 线程池执行器
     */
    @Bean(name = "crawlExecutor")
    public ThreadPoolTaskExecutor crawlExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：同时执行的任务数（爬虫对源站有压力，不宜过大）
        executor.setCorePoolSize(3);
        // 最大线程数
        executor.setMaxPoolSize(6);
        // 等待队列容量：排队中的任务数
        executor.setQueueCapacity(200);
        // 线程名前缀，方便看日志
        executor.setThreadNamePrefix("crawl-");
        executor.initialize();
        return executor;
    }

}
