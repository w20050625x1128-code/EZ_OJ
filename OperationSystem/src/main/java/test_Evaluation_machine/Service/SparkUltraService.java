package test_Evaluation_machine.Service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import test_Evaluation_machine.Config.XfyunConfig;
import test_Evaluation_machine.Pojo.JudgeTask;
import test_Evaluation_machine.Pojo.Problem;

import javax.annotation.Resource;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import okhttp3.*;

@Slf4j
@Service
public class SparkUltraService {

    @Resource
    private XfyunConfig xfyunConfig;

    // 全局复用OkHttpClient，避免重复创建，提升性能
    private static final OkHttpClient OK_HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build();

    /**
     * 对外暴露的核心方法：判题后调用，返回AI分析结果
     * 完全适配你的JudgeTask、Problem实体类

    public String analyzeCode(JudgeTask task, Problem problem, String judgeStatus, String errorMsg) {



        try {
            // 1. 构建星火鉴权URL（官方要求的签名逻辑，100%适配v4.0接口）
            String authUrl = buildAuthUrl();
            // 2. 构建适配你项目的提示词（完全匹配你的实体类字段）
            String prompt = buildPrompt(task, problem, judgeStatus, errorMsg);
            // 3. 发起WebSocket请求，同步等待完整结果
            return doWebSocketRequest(authUrl, prompt);
        } catch (Exception e) {
            log.error("星火Ultra调用失败，任务ID：{}", task.getTaskId(), e);
            // 简化异常提示，避免前端显示冗长堆栈
            return "AI分析暂时不可用：请检查讯飞星火配置或网络";
        }
    }
     */

    public String analyzeCode(JudgeTask task, Problem problem, String judgeStatus, String errorMsg) {
        // 1. 强制打印配置（关键：看是否真的加载到值）
        String appId = xfyunConfig.getAppId();
        String apiKey = xfyunConfig.getApiKey();
        String apiSecret = xfyunConfig.getApiSecret();
        String url = xfyunConfig.getUrl();
        String domain = xfyunConfig.getDomain();

        log.error("【讯飞配置排查】appId是否为空：{}，值：{}", appId == null, appId);
        log.error("【讯飞配置排查】apiKey是否为空：{}，值：{}", apiKey == null, apiKey);
        log.error("【讯飞配置排查】apiSecret是否为空：{}，值：{}", apiSecret == null, apiSecret);
        log.error("【讯飞配置排查】url：{}，domain：{}", url, domain);

        // ========== 新增：空代码判断 ==========
        String sourceCode = task.getSourceCode();
        if (sourceCode == null || sourceCode.trim().isEmpty()) {
            log.info("空代码，返回默认AI提示，任务ID：{}", task.getTaskId());
            // 空代码的专属分析结果
            return "=== 星火Ultra AI代码分析 ===\n" +
                    "1. 错误原因分析：\n" +
                    "   - 代码编辑框为空，未输入任何可执行代码；\n" +
                    "   - 无语法错误，但无法通过任何测试用例。\n\n" +
                    "2. 可直接运行的修复代码：\n```cpp\n" +
                    "#include <bits/stdc++.h>\n" +
                    "using namespace std;\n" +
                    "int main() {\n" +
                    "    // 请输入符合题目要求的代码\n" +
                    "    return 0;\n" +
                    "}\n```\n\n" +
                    "3. 代码优化建议：\n" +
                    "   - 填写对应题目的核心逻辑；\n" +
                    "   - 避免空代码、死循环、语法错误等基础问题。";
        }
        // =====================================


        try {
            String authUrl = buildAuthUrl();
            log.error("【讯飞配置排查】鉴权URL生成成功：{}", authUrl); // 打印鉴权URL

            String prompt = buildPrompt(task, problem, judgeStatus, errorMsg);
            return doWebSocketRequest(authUrl, prompt);
        } catch (Exception e) {
            log.error("【讯飞配置排查】AI调用异常：", e); // 打印完整异常堆栈（关键！）
            return "AI分析暂时不可用：请检查讯飞星火配置或网络";
        }
    }

    /**
     * 新增：提取简短AI提示（供JudgeService/前端接口调用）
     * 用于前端显示的简短提示语
     */
    public String extractAiTip(String aiAnalysis) {
        if (aiAnalysis.contains("空代码")) {
            return "请输入代码后提交评测";
        } else if (aiAnalysis.contains("死循环") || aiAnalysis.contains("TLE") || aiAnalysis.contains("时间超限")) {
            return "检查输入输出格式是否正确，避免死循环";
        } else if (aiAnalysis.contains("错误") || aiAnalysis.contains("WA") || aiAnalysis.contains("CE") || aiAnalysis.contains("RE")) {
            return "检查代码逻辑，修复错误后重新提交";
        } else if (aiAnalysis.contains("通过") || aiAnalysis.contains("AC")) {
            return "代码格式正确，可提交评测";
        } else {
            return "请输入符合题目要求的代码";
        }
    }
    /**
     * 新增：供前端实时获取AI提示的接口方法
     * 前端输入代码时，实时调用此方法返回简短提示
     */
    public String getRealTimeAiHint(String problemNum, String code, Problem problem) {
        // 1. 强化空代码判断：空/仅空白/仅注释（匹配前端默认的"// 在此输入你的代码"）
        if (code == null || code.trim().isEmpty() ||
                code.trim().startsWith("//") && code.trim().replace("//", "").trim().isEmpty()) {
            // 返回前端能识别的空代码提示（简洁版，供前端展示）
            return "请输入代码后提交评测";
        }

        // 2. 构建临时JudgeTask（已修复构造参数，无需改）
        JudgeTask tempTask = new JudgeTask(
                "cpp",          // language
                code,           // sourceCode
                problemNum,     // questionNumber
                0               // userId
        );
        tempTask.setProblem(problem);

        // 3. 调用分析方法
        String aiAnalysis = analyzeCode(tempTask, problem, "", "");

        // 4. 提取简短提示
        return extractAiTip(aiAnalysis);
    }




    /**
     * 星火官方要求的鉴权签名逻辑，100%适配v4.0接口
     */
    private String buildAuthUrl() throws Exception {
        String apiKey = xfyunConfig.getApiKey();
        String apiSecret = xfyunConfig.getApiSecret();

        // 空值校验：配置项为空直接抛异常
        if (apiKey == null || apiKey.isEmpty() || apiSecret == null || apiSecret.isEmpty()) {
            throw new RuntimeException("讯飞星火配置错误：apiKey或apiSecret为空");
        }

        URI uri = URI.create(xfyunConfig.getUrl());
        String host = uri.getHost();
        String path = uri.getPath();

        // 必须用US locale，否则日期格式错误会导致鉴权失败
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        String date = sdf.format(new Date());

        // 构建签名原文
        String signatureOrigin = "host: " + host + "\n"
                + "date: " + date + "\n"
                + "GET " + path + " HTTP/1.1";

        // HmacSHA256签名
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signatureBytes = mac.doFinal(signatureOrigin.getBytes(StandardCharsets.UTF_8));
        String signatureBase64 = Base64.getEncoder().encodeToString(signatureBytes);

        // 构建authorization
        String authorizationOrigin = String.format(
                "api_key=\"%s\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\"%s\"",
                apiKey, signatureBase64
        );
        String authorization = Base64.getEncoder().encodeToString(authorizationOrigin.getBytes(StandardCharsets.UTF_8));

        // 拼接最终鉴权URL
        return xfyunConfig.getUrl() + "?"
                + "authorization=" + authorization
                + "&date=" + URLEncoder.encode(date, StandardCharsets.UTF_8.name())
                + "&host=" + host;
    }

    /**
     * 构建适配你OJ项目的提示词，完全匹配你的实体类字段
     */
    private String buildPrompt(JudgeTask task, Problem problem, String judgeStatus, String errorMsg) {
        return "题目名称：" + problem.getProblemName() + "\n"
                + "题目描述：" + problem.getProblemDescription() + "\n"
                + "时间限制：" + problem.getTimeLimit() + "ms，内存限制：" + problem.getMemoryLimit() + "MB\n"
                + "用户提交的" + task.getLanguage() + "代码：\n```\n" + task.getSourceCode() + "\n```\n"
                + "系统判题结果：" + judgeStatus + "\n"
                + "判题错误信息：" + (errorMsg == null || errorMsg.isEmpty() ? "无" : errorMsg) + "\n\n"
                + "请按以下格式输出：\n"
                + "1. 错误原因分析\n"
                + "2. 可直接运行的修复代码\n"
                + "3. 代码优化建议（时间/空间复杂度、代码规范）";
    }

    /**
     * AI生成解题代码（供/ai/generate-code接口调用）
     * 适配前端传入的题目编号和描述，生成对应解题代码
     */
    public String generateProblemCode(String problemNum, String problemDesc) {
        try {
            // 1. 构建星火鉴权URL（复用已有的鉴权逻辑）
            String authUrl = buildAuthUrl();
            // 2. 构建生成代码的专属提示词
            String prompt = buildGenerateCodePrompt(problemNum, problemDesc);
            // 3. 复用WebSocket请求逻辑，获取AI生成的代码
            return doWebSocketRequest(authUrl, prompt);
        } catch (Exception e) {
            log.error("星火Ultra生成解题代码失败，题目编号：{}", problemNum, e);
            // 简化异常提示，同时给出基础代码示例
            return "// AI生成解题代码失败：请检查讯飞星火配置或网络\n" +
                    "// 以下是P1001两数之和的基础参考代码：\n" +
                    "#include <bits/stdc++.h>\n" +
                    "using namespace std;\n" +
                    "int main() {\n" +
                    "    int a, b;\n" +
                    "    cin >> a >> b;\n" +
                    "    cout << a + b << endl;\n" +
                    "    return 0;\n" +
                    "}";
        }
    }

    /**
     * 构建生成解题代码的专属提示词
     */
    private String buildGenerateCodePrompt(String problemNum, String problemDesc) {
        return "题目编号：" + problemNum + "\n"
                + "题目描述：" + problemDesc + "\n\n"
                + "请按以下要求生成C++解题代码：\n"
                + "1. 代码可直接在OJ系统中运行，符合编程竞赛规范\n"
                + "2. 包含必要的注释（输入输出逻辑、核心思路）\n"
                + "3. 处理输入输出的边界情况\n"
                + "4. 代码格式整洁，变量命名规范";
    }

    /**
     * 处理WebSocket请求，完整接收流式返回结果
     * 核心修复：增加多层空值判断，避免NullPointerException
     */
    private String doWebSocketRequest(String authUrl, String prompt) throws Exception {
        CompletableFuture<String> resultFuture = new CompletableFuture<>();
        StringBuilder resultBuilder = new StringBuilder();

        Request request = new Request.Builder().url(authUrl).build();
        // 初始化WebSocket，重写所有回调方法，匹配@NotNull签名
        OK_HTTP_CLIENT.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
                super.onOpen(webSocket, response);
                // 连接成功后发送请求体
                String requestBody = buildRequestBody(prompt);
                webSocket.send(requestBody);
            }

            @Override
            public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
                super.onMessage(webSocket, text);
                try {
                    // 空值校验：返回文本为空直接结束
                    if (text == null || text.isEmpty()) {
                        resultFuture.completeExceptionally(new RuntimeException("星火接口返回空内容"));
                        webSocket.close(1000, "正常关闭");
                        return;
                    }

                    // 用fastjson2解析返回内容
                    JSONObject respJson = JSON.parseObject(text);
                    int code = respJson.getIntValue("code");

                    // 接口返回错误码
                    if (code != 0) {
                        String errorMsg = respJson.getString("message");
                        resultFuture.completeExceptionally(new RuntimeException("星火接口报错：" + errorMsg));
                        webSocket.close(1000, "正常关闭");
                        return;
                    }

                    // ========== 核心修复：多层空值判断 ==========
                    // 1. 校验payload字段
                    JSONObject payload = respJson.getJSONObject("payload");
                    if (payload == null) {
                        resultFuture.completeExceptionally(new RuntimeException("星火接口返回无payload字段，响应内容：" + text));
                        webSocket.close(1000, "正常关闭");
                        return;
                    }

                    // 2. 校验choices字段
                    JSONObject choices = payload.getJSONObject("choices");
                    if (choices == null) {
                        resultFuture.completeExceptionally(new RuntimeException("星火接口返回无choices字段，响应内容：" + text));
                        webSocket.close(1000, "正常关闭");
                        return;
                    }

                    // 3. 校验text数组
                    JSONArray textArray = choices.getJSONArray("text");
                    if (textArray == null || textArray.isEmpty()) {
                        resultFuture.completeExceptionally(new RuntimeException("星火接口返回无text数组，响应内容：" + text));
                        webSocket.close(1000, "正常关闭");
                        return;
                    }

                    // 4. 校验text数组第一个元素
                    JSONObject textObj = textArray.getJSONObject(0);
                    if (textObj == null) {
                        resultFuture.completeExceptionally(new RuntimeException("星火接口text数组为空，响应内容：" + text));
                        webSocket.close(1000, "正常关闭");
                        return;
                    }

                    // 5. 提取content内容（为空则赋值空字符串）
                    String content = textObj.getString("content");
                    if (content == null) content = "";
                    // ==========================================

                    resultBuilder.append(content);

                    // 判断是否是最后一条消息（校验header字段）
                    JSONObject header = respJson.getJSONObject("header");
                    if (header == null) {
                        resultFuture.completeExceptionally(new RuntimeException("星火接口返回无header字段，响应内容：" + text));
                        webSocket.close(1000, "正常关闭");
                        return;
                    }

                    int status = header.getIntValue("status");
                    if (status == 2) {
                        // 流式传输结束，返回完整结果
                        resultFuture.complete(resultBuilder.toString());
                        webSocket.close(1000, "正常关闭");
                    }
                } catch (Exception e) {
                    resultFuture.completeExceptionally(e);
                    webSocket.close(1000, "异常关闭");
                }
            }

            @Override
            public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, Response response) {
                super.onFailure(webSocket, t, response);
                resultFuture.completeExceptionally(t);
            }

            @Override
            public void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
                super.onClosed(webSocket, code, reason);
                // 关闭回调，无需额外处理
            }
        });

        // 等待结果，超时60秒，避免阻塞判题流程
        return resultFuture.get(60, TimeUnit.SECONDS);
    }

    /**
     * 构建星火v4.0接口要求的请求体，domain=Ultra
     */
    private String buildRequestBody(String prompt) {
        // 空值校验：appId为空直接抛异常
        if (xfyunConfig.getAppId() == null || xfyunConfig.getAppId().isEmpty()) {
            throw new RuntimeException("讯飞星火配置错误：appId为空");
        }

        JSONObject requestJson = new JSONObject();
        // header部分
        JSONObject header = new JSONObject();
        header.put("app_id", xfyunConfig.getAppId());
        header.put("uid", UUID.randomUUID().toString().replace("-", ""));
        requestJson.put("header", header);

        // parameter部分
        JSONObject parameter = new JSONObject();
        JSONObject chat = new JSONObject();
        chat.put("domain", xfyunConfig.getDomain());
        chat.put("temperature", 0.3f);
        chat.put("max_tokens", 2048);
        parameter.put("chat", chat);
        requestJson.put("parameter", parameter);

        // payload部分
        JSONObject payload = new JSONObject();
        JSONObject message = new JSONObject();
        List<JSONObject> textList = new ArrayList<>();
        // 系统角色
        JSONObject systemMsg = new JSONObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", "你是专业的编程助教，负责分析OJ判题代码，给出清晰的错误原因、修复代码和优化建议，语言简洁专业。");
        textList.add(systemMsg);
        // 用户问题
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);
        textList.add(userMsg);
        message.put("text", textList);
        payload.put("message", message);
        requestJson.put("payload", payload);

        return requestJson.toJSONString();
    }
}