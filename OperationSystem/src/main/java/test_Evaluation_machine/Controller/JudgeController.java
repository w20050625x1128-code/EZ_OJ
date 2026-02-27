package test_Evaluation_machine.Controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import test_Evaluation_machine.Pojo.JudgeRequest;
import test_Evaluation_machine.Pojo.JudgeResponse;
import test_Evaluation_machine.Pojo.JudgeTask;
import test_Evaluation_machine.Scheduler.JudgeScheduler;
import test_Evaluation_machine.Service.JudgeService;
@Slf4j
@RestController
@RequestMapping("/judge")
public class JudgeController {
    @Autowired
    private JudgeScheduler judgeScheduler;

    public static class SubmitTaskResponse {
        public String taskId;
        public SubmitTaskResponse(String taskId) {
            this.taskId = taskId;
        }
    }

    /**
     * 异步提交：只返回 taskId，前端轮询 /judge/result/{taskId} 获取结果
     */
    @PostMapping("/submit")
    public SubmitTaskResponse submit(@RequestBody JudgeRequest request) throws Exception {
        String taskId = judgeScheduler.submitJudgeTaskToHolder(request);
        log.info("提交判题任务成功，任务ID：{}", taskId);
        return new SubmitTaskResponse(taskId);
    }

    /**
     * 轮询获取判题结果：未完成返回 PENDING
     */
    @GetMapping("/result/{taskId}")
    public JudgeResponse getResult(@PathVariable("taskId") String taskId) throws Exception {
        JudgeResponse response = judgeScheduler.tryGetJudgeResponse(taskId);
        if (response == null) {
            JudgeResponse pending = new JudgeResponse();
            pending.setStatus(JudgeResponse.JudgeStatus.PENDING);
            pending.setError("");
            pending.setOutput("");
            pending.setUsedTime(0);
            pending.setUsedMemory(0);
            return pending;
        }
        return response;
    }

    @PostMapping("/cpp")
    public JudgeResponse judgeCpp(@RequestBody JudgeRequest request)throws Exception{

        String taskId = judgeScheduler.submitJudgeTaskToHolder(request);
        log.info("提交判题任务成功，任务ID：{}", taskId);
        JudgeResponse response = judgeScheduler.getJudgeResponse(taskId);

        if(response == null){
            JudgeResponse timeError = new JudgeResponse();
            timeError.setError("等待判题结果超时");
            timeError.setStatus(JudgeResponse.JudgeStatus.TIME_LIMIT_EXCEEDED);
            return timeError;
        }
        return response;
    }
}
