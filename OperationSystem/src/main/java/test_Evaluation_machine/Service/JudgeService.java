package test_Evaluation_machine.Service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import test_Evaluation_machine.Pojo.JudgeRequest;
import test_Evaluation_machine.Pojo.JudgeResponse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

//@Slf4j
//@Service
//public class JudgeService {
//    private static final String WORKSPACE="E:/Judger_test/";
//    public JudgeResponse judgeCpp(JudgeRequest request) throws Exception{
//        String taskId= UUID.randomUUID().toString();
//        Path taskDir = Paths.get(WORKSPACE,taskId);
//
//        Files.createDirectories(taskDir);
//        Path cppFile = taskDir.resolve("test.cpp");
//        Files.writeString(cppFile, request.code);
//
//        Path inputFile = taskDir.resolve("input.txt");
//        Files.writeString(inputFile, request.input);
//
//        return executeInDocker(taskDir);
//    }
//
//    private JudgeResponse executeInDocker(Path taskDir) throws Exception{
//        JudgeResponse judgeResponse = new JudgeResponse();
//
//        String hostPath = taskDir.toAbsolutePath().toString()
//                .replace("\\", "/");
//
//        String command =
//                "g++ test.cpp -o test && " +
//                "./test < input.txt > output.txt";
//
//        ProcessBuilder pb = new ProcessBuilder(
//                "docker","run",
//                "--rm",
//                "--memory=256m",
//                "--cpus=1",
//                "-v", hostPath + ":/app",
//                "-w", "/app",
//                "gcc:latest",
//                "bash","-c",command
//        );
//
//        pb.redirectErrorStream(true);
//        Process process = pb.start();
//        boolean finished = process.waitFor(5, TimeUnit.SECONDS);
//
//        if(!finished){
//            process.destroyForcibly();
//            judgeResponse.error="Time Limit Exceeded";
//            return judgeResponse;
//        }
//
//        Path outputFile = taskDir.resolve("output.txt");
//        if(Files.exists(outputFile)){
//            judgeResponse.output=Files.readString(outputFile);
//            judgeResponse.error="";
//            return judgeResponse;
//        }else{
//            judgeResponse.error="Runtime Error or Complie Error";
//            return judgeResponse;
//        }
//    }
//}
@Service
@Slf4j // 记得加这个注解，才能用log
public class JudgeService {
    private static final String WORKSPACE="E:/Judger_test/";
    public JudgeResponse judgeCpp(JudgeRequest request) throws Exception{
        String taskId= UUID.randomUUID().toString();
        Path taskDir = Paths.get(WORKSPACE,taskId);

        // 确保目录创建成功
        if (!Files.exists(taskDir)) {
            Files.createDirectories(taskDir);
            log.info("创建任务目录：{}", taskDir);
        }

        Path cppFile = taskDir.resolve("test.cpp");
        Files.writeString(cppFile, request.code, StandardCharsets.UTF_8); // 指定编码
        log.info("写入代码文件：{}，内容：{}", cppFile, request.code);

        Path inputFile = taskDir.resolve("input.txt");
        Files.writeString(inputFile, request.input == null ? "" : request.input, StandardCharsets.UTF_8); // 处理input为空
        log.info("写入输入文件：{}，内容：{}", inputFile, request.input);

        return executeInDocker(taskDir);
    }

    private JudgeResponse executeInDocker(Path taskDir) throws Exception{
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
            judgeResponse.error="Time Limit Exceeded (5s)";
            log.warn("程序执行超时");
            return judgeResponse;
        }

        // 检查并读取output.txt
        Path outputFile = taskDir.resolve("output.txt");
        log.info("检查output.txt：{}，存在：{}", outputFile, Files.exists(outputFile));
        if(Files.exists(outputFile)){
            String outputContent = Files.readString(outputFile, StandardCharsets.UTF_8);
            judgeResponse.output=outputContent;
            judgeResponse.error="";
            log.info("读取output内容：{}", outputContent);
        }else{
            judgeResponse.error="output.txt未生成，Docker输出：" + dockerOutput;
            judgeResponse.output=""; // 避免output为null
            log.error("output.txt不存在");
        }
        return judgeResponse;
    }
}