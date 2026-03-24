package test_Evaluation_machine.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class XfyunConfig {

    // 从application.yml读取，变量名和你之前保持一致
    @Value("${xfyun.appid}")
    private String appId;

    @Value("${xfyun.apiKey}")
    private String apiKey;

    @Value("${xfyun.apiSecret}")
    private String apiSecret;

    @Value("${xfyun.url}")
    private String url = "wss://spark-api.xf-yun.com/v4.0/chat";

    @Value("${xfyun.domain}")
    private String domain= "4.0Ultra";

    // Getter方法（供Service层调用，无Setter保证配置不可变）
    public String getAppId() {
        return appId;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getApiSecret() {
        return apiSecret;
    }

    public String getUrl() {
        return url;
    }

    public String getDomain() {
        return domain;
    }
}