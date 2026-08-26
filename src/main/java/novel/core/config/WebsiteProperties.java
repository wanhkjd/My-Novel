package novel.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 站点展示与邮件链接配置。
 */
@ConfigurationProperties(prefix = "novel.website")
public record WebsiteProperties(String name, String link, String supportEmail) {
}
