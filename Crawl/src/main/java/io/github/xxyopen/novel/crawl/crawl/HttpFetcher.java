package io.github.xxyopen.novel.crawl.crawl;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;

/**
 * 网页抓取器：带重试、超时、编码处理的 HTTP 请求封装
 */
@Slf4j
@Component
public class HttpFetcher {

    /**
     * 抓取网页并解析为 Jsoup 文档（失败按规则配置自动重试）
     *
     * @param url  页面地址
     * @param rule 站点规则（使用其中的超时/编码/UA/重试配置）
     * @return 解析后的 Jsoup 文档
     * @throws IllegalStateException 重试用尽仍失败时抛出
     */
    public Document fetchPage(String url, CrawlRule rule) {
        IOException lastError = null;
        // 配置项空值兜底
        int retry = rule.getRetry() == null ? 3 : rule.getRetry();
        // 第 1 次请求 + retry 次重试
        for (int attempt = 0; attempt <= retry; attempt++) {
            try {
                Connection.Response response = buildConnection(url, rule).execute();
                // 配置了固定编码时按固定编码解码，否则交给 Jsoup 自动识别（HTTP头/meta标签）
                if (StringUtils.hasText(rule.getCharset())) {
                    String html = new String(response.bodyAsBytes(), rule.getCharset().trim());
                    return Jsoup.parse(html, url);
                }
                return response.parse();
            } catch (IOException e) {
                lastError = e;
                log.warn("抓取失败（第{}次）：{}，原因：{}", attempt + 1, url, e.getMessage());
                // 重试前递增等待，缓解源站压力
                sleepQuietly(1000L * (attempt + 1));
            }
        }
        throw new IllegalStateException("抓取页面失败：" + url + "，原因：" + (lastError == null ? "未知" : lastError.getMessage()), lastError);
    }

    /**
     * 抓取二进制内容（用于下载封面图片）
     *
     * @param url  资源地址
     * @param rule 站点规则
     * @return 资源字节数组
     * @throws IllegalStateException 重试用尽仍失败时抛出
     */
    public byte[] fetchBytes(String url, CrawlRule rule) {
        IOException lastError = null;
        // 配置项空值兜底
        int retry = rule.getRetry() == null ? 3 : rule.getRetry();
        // 第 1 次请求 + retry 次重试
        for (int attempt = 0; attempt <= retry; attempt++) {
            try {
                Connection.Response response = buildConnection(url, rule)
                        .ignoreContentType(true)
                        .execute();
                return response.bodyAsBytes();
            } catch (IOException e) {
                lastError = e;
                sleepQuietly(1000L * (attempt + 1));
            }
        }
        throw new IllegalStateException("下载资源失败：" + url + "，原因：" + (lastError == null ? "未知" : lastError.getMessage()), lastError);
    }

    /**
     * 构建统一的 Jsoup 连接（UA、超时、请求头等）
     *
     * @param url  请求地址
     * @param rule 站点规则
     * @return Jsoup 连接对象
     */
    private Connection buildConnection(String url, CrawlRule rule) {
        return Jsoup.connect(url)
                .userAgent(rule.getUserAgent())
                .timeout(rule.getTimeoutMs() == null ? 15000 : rule.getTimeoutMs())
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .referrer(url)
                .followRedirects(true)
                .maxBodySize(0);
    }

    /**
     * 静默休眠（吞掉中断异常并恢复中断标记）
     *
     * @param millis 休眠毫秒数
     */
    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            // 恢复中断标记，让上层循环尽快感知
            Thread.currentThread().interrupt();
        }
    }

}
