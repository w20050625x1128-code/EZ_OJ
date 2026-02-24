package test_Evaluation_machine.Controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PostMapping("/cpp")
    public JudgeResponse judgeCpp(@RequestBody JudgeRequest request)throws Exception{

        String taskId = judgeScheduler.submitJudgeTaskToHolder(request);
        log.info("提交判题任务成功，任务ID：{}", taskId);
        JudgeResponse response = judgeScheduler.getJudgeResponse(taskId);

        if(response == null){
            JudgeResponse timeError = new JudgeResponse();
            timeError.setError("执行超时");
            timeError.setStatus(JudgeResponse.JudgeStatus.TIME_LIMIT_EXCEEDED);
            return timeError;
        }
        return response;
    }
}
