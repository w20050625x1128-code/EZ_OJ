package test_Evaluation_machine.Service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import test_Evaluation_machine.Mapper.ProblemMapper;
import test_Evaluation_machine.Pojo.JudgeRequest;
import test_Evaluation_machine.Pojo.JudgeResponse;
import test_Evaluation_machine.Pojo.JudgeTask;
import test_Evaluation_machine.Scheduler.JudgeScheduler;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j // 记得加这个注解，才能用log
public class JudgeService {
    private static final String WORKSPACE="E:/Judger_test/";

    public JudgeResponse judgeCpp(JudgeTask task)throws Exception{
        //String taskId= UUID.randomUUID().toString();
        Path taskDir = Paths.get(WORKSPACE,task.getTaskId());

        // 确保目录创建成功
        if (!Files.exists(taskDir)) {
            Files.createDirectories(taskDir);
            log.info("创建任务目录：{}", taskDir);
        }

        Path cppFile = taskDir.resolve("test.cpp");
        Files.writeString(cppFile, task.getSourceCode(), StandardCharsets.UTF_8); // 指定编码
        log.info("写入代码文件：{}，内容：{}", cppFile, task.getSourceCode());

        Path inputFile = taskDir.resolve("input.txt");
        Files.writeString(inputFile, task.input == null ? "" : task.input, StandardCharsets.UTF_8); // 处理input为空
        log.info("写入输入文件：{}，内容：{}", inputFile, task.input);

         return executeInDocker(task,taskDir);
    }

    private JudgeResponse executeInDocker(JudgeTask task,Path taskDir) throws Exception{

        JudgeResponse judgeResponse = new JudgeResponse();
        // 修复Windows Docker路径映射
        String hostPath = taskDir.toAbsolutePath().toString()
                .replace("\\", "/")
                .replace(":", ""); // 去掉E: → E
        hostPath = "/" + hostPath.toLowerCase(); // 转成/e/judger_test/xxx
        log.info("Docker映射路径：{}", hostPath);

        // 修复命令：保证output.txt一定生成，捕获错误输出
        String command =
                "(g++ test.cpp -o test && ./test < input.txt) > output.txt 2>&1 || echo \"编译/运行失败，错误码：$?\" > output.txt";

        ProcessBuilder pb = new ProcessBuilder(
                "docker","run",
                "--rm",
                "--memory=256m",
                "--cpus=1",
                "-v", hostPath + ":/app",
                "-w", "/app",
                "gcc:latest",
                "bash","-c",command
        );

        pb.redirectErrorStream(true);
        Process process = pb.start();
        // 读取Docker执行的输出（编译/运行日志）
        String dockerOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        log.info("Docker命令输出：{}", dockerOutput);

        // 等待执行，超时5秒
        boolean finished = process.waitFor(5, TimeUnit.SECONDS);
        if(!finished){
            process.destroyForcibly();
            judgeResponse.setError("Time Limit Exceeded (5s)");
            log.warn("程序执行超时");
            return judgeResponse;
        }

        // 检查并读取output.txt
        Path outputFile = taskDir.resolve("output.txt");
        log.info("检查output.txt：{}，存在：{}", outputFile, Files.exists(outputFile));
        if(Files.exists(outputFile)){
            String outputContent = Files.readString(outputFile, StandardCharsets.UTF_8);
            judgeResponse.setOutput(outputContent);
            judgeResponse.setError("");
            log.info("读取output内容：{}", outputContent);

        }else{
            judgeResponse.setError("output.txt未生成，Docker输出：" + dockerOutput);
            judgeResponse.setOutput("");// 避免output为null
            log.error("output.txt不存在");
        }
        return judgeResponse;
    }
}