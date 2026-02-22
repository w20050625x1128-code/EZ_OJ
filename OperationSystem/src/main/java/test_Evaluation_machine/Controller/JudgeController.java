package test_Evaluation_machine.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import test_Evaluation_machine.Pojo.JudgeRequest;
import test_Evaluation_machine.Pojo.JudgeResponse;
import test_Evaluation_machine.Service.JudgeService;

@RestController
@RequestMapping("/judge")
public class JudgeController {
    @Autowired
    private JudgeService judgeService;

    @PostMapping("/cpp")
    public JudgeResponse judge(@RequestBody JudgeRequest request)throws Exception{
        return judgeService.judgeCpp(request);
    }
}
