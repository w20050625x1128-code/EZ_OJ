package test_Evaluation_machine.Service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import test_Evaluation_machine.Mapper.ProblemMapper;
import test_Evaluation_machine.Pojo.*;
import test_Evaluation_machine.Scheduler.JudgeScheduler;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j // 记得加这个注解，才能用log
public class JudgeService {
    private static final String WORKSPACE = "E:/Judger_test/";
    private static final String CPP_COMPILE_CMD = "g++ %s -o %s 2>&1";
    private static final long DOCKER_STARTUP_GRACE_MS = 8000; // Windows 下 docker run 启动开销缓冲

    public JudgeResponse judgeInDocker(JudgeTask task) throws Exception{
        Problem problem = task.getProblem();
        SubmitRecord record = judge(task, problem);
        JudgeResponse response = new JudgeResponse();
        //sql insert record
        response.setError(record.getErrorMsg());
        response.setUsedTime(record.getUsedTime());
        response.setUsedMemory(record.getUsedMemory());
        response.setStatus(RecordToResponseStatusMapper(record.getJudgeStatus()));
        response.setOutput(record.getJudgeMessage());
        return response;
    }

    /**
     * @param task    判题任务
     * @param problem 题目信息
     * @return SubmitRecord 判题结果（
     */
    private SubmitRecord judge(JudgeTask task, Problem problem) throws Exception {
        //初始化提交信息
        SubmitRecord submitRecord = new SubmitRecord();
        submitRecord.setUserId(1);
        submitRecord.setProblemNum(problem.getProblemNum());
        submitRecord.setTaskId(task.getTaskId());
        submitRecord.setSourceCode(task.getSourceCode());
        submitRecord.setLanguage(task.getLanguage());
        submitRecord.setJudgeStatus("WAITING");
        submitRecord.setScore(0);
        submitRecord.setUsedTime(0L);
        submitRecord.setUsedMemory(0L);

        try {
            //创建临时工作目录
            Path taskDir = Paths.get(WORKSPACE, task.getTaskId());
            if (!Files.exists(taskDir)) {
                Files.createDirectories(taskDir);
                log.info("创建任务目录：{}", taskDir);
            }

            //启动判题
            judgeCpp(task, problem, submitRecord, taskDir);

//            //按语言分支处理，暂时仅支持cpp
//            if("cpp".equalsIgnoreCase(task.getLanguage())){
//
//            }else{
//                submitRecord.setJudgeStatus("UNKNOWN");
//                submitRecord.setErrorMsg("暂不支持该语言");
//            }

        } catch (Exception e) {
            submitRecord.setJudgeStatus("UNKNOWN");
            submitRecord.setErrorMsg("判题异常：" + e.getMessage());
            log.error("判题逻辑异常", e);
        }

        return submitRecord;

    }

    private void judgeCpp(JudgeTask task, Problem problem, SubmitRecord submitRecord, Path taskDir) throws Exception {

        //将待评测代码写入临时工作目录的一个cpp文件
        Path cppFile = taskDir.resolve("test.cpp");
        Files.writeString(cppFile, task.getSourceCode(), StandardCharsets.UTF_8);
        log.info("写入代码文件：{}，内容：{}", cppFile, task.getSourceCode());

        //编译待评测代码
        String compileError = compileCpp(cppFile, taskDir);
        if (compileError != null) {
            //编译失败
            submitRecord.setErrorMsg("编译错误" + compileError);
            submitRecord.setJudgeStatus("CE");
            //log.error("编译失败：{}", compileError);
            return;
        }

        //轮询解析和执行测试点
        List<Problem.TestCase> testCaseList = problem.getTestCaseList();
        if (testCaseList == null) {
            submitRecord.setJudgeStatus("UNKNOWN");
            submitRecord.setErrorMsg("无测试点信息");
            log.error("题目{}无测试点配置", problem.getProblemNum());
            return;
        }

        int totalScore = 0;
        long totalUsedTime = 0;
        long maxUsedMemory = 0;
        String finalStatus = "AC";
        StringBuilder judgeMsg = new StringBuilder(); //多测试点详情

        for (Problem.TestCase testCase : testCaseList) {
            judgeMsg.append(String.format("测试点%d", testCase.getCaseId()));
            JudgeResponse caseResponse = executeSingleCase(taskDir, testCase, problem);
            totalUsedTime += caseResponse.getUsedTime();
            maxUsedMemory = Math.max(maxUsedMemory, caseResponse.getUsedMemory());

            String caseStatus = ResponseToRecordStatusMapper(caseResponse.getStatus());
            if(!"AC".equals(caseStatus)) {
                finalStatus = caseStatus;
                judgeMsg.append(caseStatus).append("-").append(caseResponse.getError()).append("\n");
                //超时或内存超限直接终止执行
                if("TLE".equals(caseStatus) || "MLE".equals(caseStatus)) {
                    judgeMsg.append(String.format("超时或内存超限，后续停止执行"));
                    break;
                }
            }else{
                totalScore+=testCase.getScore();
                judgeMsg.append("AC scored:").append(testCase.getScore()).append("\n");
            }
        }

        //封装判题结果
        submitRecord.setJudgeStatus(finalStatus);
        submitRecord.setJudgeMessage(judgeMsg.toString());
        submitRecord.setScore(totalScore);
        submitRecord.setUsedTime(totalUsedTime);
        submitRecord.setUsedMemory(maxUsedMemory);
        submitRecord.setErrorMsg(finalStatus.equals("AC") ? "" : "部分测试点未通过");

        log.info("题目{}判题完成，最终状态：{}，总得分：{}", problem.getProblemNum(), finalStatus, totalScore);
    }

    /**
     * 编译C++源码
     *
     * @return 编译错误信息（null表示编译成功）
     */
    private String compileCpp(Path cppfile, Path taskDir) throws IOException, InterruptedException {
        //编译后的可执行文件
        Path exeFile = taskDir.resolve("test");
        //编译命令
        String compileCMD = String.format(CPP_COMPILE_CMD, cppfile.getFileName(), exeFile.getFileName());

        //构建docker环境
        ProcessBuilder pb = new ProcessBuilder("docker", "run",
                "--rm",
                "-v", getDockerPath(taskDir) + ":/app",
                "-w", "/app",
                "gcc:latest",
                "bash", "-c", compileCMD);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        //读取编译输出
        String compileOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            //编译失败
            log.error("C++编译失败，退出码：{}，输出：{}", exitCode, compileOutput);
            return compileOutput;
        }
        log.info("C++源码编译成功，编译输出：{}", compileOutput);
        return null;

    }

    private JudgeResponse executeSingleCase(Path taskDir, Problem.TestCase testCase, Problem problem) throws Exception {
        JudgeResponse judgeResponse = new JudgeResponse();
        Path exeFile = taskDir.resolve("test");
        Path outputFile = taskDir.resolve("case_" + testCase.getCaseId() + "_output.txt");

        Path hostInputPath = Paths.get(testCase.getInputPath());
        if (!Files.exists(hostInputPath)) {
            judgeResponse.setStatus(JudgeResponse.JudgeStatus.RUNTIME_ERROR);
            judgeResponse.setError("测试点输入文件不存在：" + hostInputPath.toAbsolutePath());
            return judgeResponse;
        }

        Path hostOutputPath = Paths.get(testCase.getOutputPath());
        if (!Files.exists(hostOutputPath)) {
            judgeResponse.setStatus(JudgeResponse.JudgeStatus.RUNTIME_ERROR);
            judgeResponse.setError("测试点标准输出文件不存在：" + hostOutputPath.toAbsolutePath());
            return judgeResponse;
        }

        String inputFileName = hostInputPath.getFileName().toString();
        String outputFileName = outputFile.getFileName().toString();
        long timeLimitMs = getTimeLimitMs(problem);
        double timeLimitSeconds = Math.max(0.001, timeLimitMs / 1000.0);
        String runCmd = String.format(
                Locale.US,
                "timeout -k 1s %.3fs ./%s < /testdata/%s > %s 2>&1",
                timeLimitSeconds,
                exeFile.getFileName(),
                inputFileName,
                outputFileName
        );
        log.info("Case {} run cmd in container: {}", testCase.getCaseId(), runCmd);

        ProcessBuilder pb = new ProcessBuilder(
                "docker", "run", "--rm",
                "--memory=" + problem.getMemoryLimit() + "m", //内存限制
                "--cpus=1",
                "-v", getDockerPath(taskDir) + ":/app", //挂载任务目录
                "-v", getDockerPath(hostInputPath.getParent()) + ":/testdata",//挂载测试点目录
                "-w", "/app",
                "gcc:latest",
                "bash", "-lc", runCmd
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();

        //计算运行时间
        long startTime = System.currentTimeMillis();
        // 外层等待要包含 docker 启动开销；真实程序运行时间由容器内 timeout 控制
        boolean finished = process.waitFor(timeLimitMs + DOCKER_STARTUP_GRACE_MS, TimeUnit.MILLISECONDS);
        long usedTime = System.currentTimeMillis() - startTime;
        judgeResponse.setUsedTime(usedTime);
        //超时控制
        if (!finished) {
            process.destroyForcibly();
            judgeResponse.setStatus(JudgeResponse.JudgeStatus.TIME_LIMIT_EXCEEDED);
            judgeResponse.setError(String.format("运行超时（含容器启动开销），程序限制%dms，实际耗时%dms", timeLimitMs, usedTime));
            return judgeResponse;
        }

        int exitCode = process.exitValue();
        // coreutils timeout：超时返回 124（部分场景可能是 137）
        if (exitCode == 124 || exitCode == 137) {
            judgeResponse.setStatus(JudgeResponse.JudgeStatus.TIME_LIMIT_EXCEEDED);
            judgeResponse.setError(String.format("程序运行超时，限制%dms（exitCode=%d）", timeLimitMs, exitCode));
            return judgeResponse;
        }
        if (exitCode != 0) {
            // 运行失败：可能是挂载问题、文件不存在、运行时崩溃等
            judgeResponse.setStatus(JudgeResponse.JudgeStatus.RUNTIME_ERROR);
            judgeResponse.setError(String.format("运行失败（exitCode=%d）", exitCode));
            // 若输出文件已生成，留给前端/日志查看
        }

        //读取输出
        if (!Files.exists(outputFile)) {
            judgeResponse.setStatus(JudgeResponse.JudgeStatus.RUNTIME_ERROR);
            judgeResponse.setError("运行时错误：未生成输出文件");
            return judgeResponse;
        }
        String actualOutput = Files.readString(outputFile, StandardCharsets.UTF_8);
        String exceptedOutput = Files.readString(hostOutputPath, StandardCharsets.UTF_8);
        //输出归一化
        actualOutput = normalizeOutput(actualOutput);
        exceptedOutput = normalizeOutput(exceptedOutput);
        judgeResponse.setOutput(actualOutput);

        if (judgeResponse.getStatus() == JudgeResponse.JudgeStatus.RUNTIME_ERROR) {
            // 运行失败时不做 AC/WA 判断，直接把输出内容作为调试信息返回
            judgeResponse.setOutput(actualOutput);
            return judgeResponse;
        }

        if (actualOutput.equals(exceptedOutput)) {
            judgeResponse.setStatus(JudgeResponse.JudgeStatus.ACCEPTED);
            judgeResponse.setError("");
        } else {
            judgeResponse.setStatus(JudgeResponse.JudgeStatus.WRONG_ANSWER);
            judgeResponse.setError("答案错误");
        }

        //docker内存统计，待扩充
        judgeResponse.setUsedMemory(problem.getMemoryLimit() * 1024 * 1024 / 2);//简化模拟
        return judgeResponse;
    }


    // 修复Windows Docker路径映射
    private String getDockerPath(Path path) {
        if (path == null) return "";
        String hostPath = path.toAbsolutePath().toString().replace("\\", "/");
        // Windows 下 docker run -v 更通用的写法是直接使用 E:/path（保留盘符冒号）
        return hostPath;
    }

    /**
     * 获取时间限制（毫秒）
     * 兼容：部分题库习惯用秒存储 timeLimit（例如 1、2、3），直接当毫秒会导致“必超时”
     */
    private long getTimeLimitMs(Problem problem) {
        Long tl = problem.getTimeLimit();
        if (tl == null || tl <= 0) return 1000;
        // 启发式：<=30 认为是“秒”，否则认为是“毫秒”
        if (tl <= 30) {
            long ms = tl * 1000L;
            return ms;
        }
        return tl;
    }

    //输出归一化（标准化）
    private String normalizeOutput(String output) {
        if (output == null) return "";
        return output
                .replaceAll("[\\r\\n\\t]", " ") //替换 换行、回车、制表符 为空格
                .replaceAll("\\s+", " ") //合并多个空格成一个
                .trim(); //首尾去空格
    }

    //状态映射
    private String ResponseToRecordStatusMapper(JudgeResponse.JudgeStatus status) {
        return switch (status){
            case ACCEPTED -> "AC";
            case WRONG_ANSWER -> "WA";
            case TIME_LIMIT_EXCEEDED -> "TLE";
            case MEMORY_LIMIT_EXCEEDED -> "MLE";
            case COMPILE_ERROR -> "CE";
            case RUNTIME_ERROR -> "RE";
            default -> "UNKNOWN";
        };
    }

    private JudgeResponse.JudgeStatus RecordToResponseStatusMapper(String status){
        return switch (status) {
            case "AC" -> JudgeResponse.JudgeStatus.ACCEPTED;
            case "WA" -> JudgeResponse.JudgeStatus.WRONG_ANSWER;
            case "TLE" -> JudgeResponse.JudgeStatus.TIME_LIMIT_EXCEEDED;
            case "MLE" -> JudgeResponse.JudgeStatus.MEMORY_LIMIT_EXCEEDED;
            case "CE" -> JudgeResponse.JudgeStatus.COMPILE_ERROR;
            case "RE" -> JudgeResponse.JudgeStatus.RUNTIME_ERROR;
            default -> JudgeResponse.JudgeStatus.UNKNOWN_ERROR;
        };
    }

}



