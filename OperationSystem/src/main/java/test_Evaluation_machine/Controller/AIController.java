package test_Evaluation_machine.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import test_Evaluation_machine.Service.SparkUltraService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/ai")
public class AIController {
    @Autowired
    private SparkUltraService sparkUltraService;

    /**
     * AI生成解题代码（前端/ai/generate-code调用）
     */
    @PostMapping("/generate-code")
    public Map<String, String> generateCode(@RequestBody Map<String, String> param) {
        String problemNum = param.get("problemNum");
        String problemDesc = param.get("problemDesc");
        String aiCode = sparkUltraService.generateProblemCode(problemNum, problemDesc);

        Map<String, String> result = new HashMap<>();
        result.put("aiCode", aiCode);
        return result;
    }

    /**
     * AI代码补全（前端实时提示调用，可选实现）
     */
    @PostMapping("/code-completion")
    public Map<String, String> codeCompletion(@RequestBody Map<String, String> param) {
        String problemNum = param.get("problemNum");
        String code = param.get("code");

        Map<String, String> result = new HashMap<>();
        result.put("hint", "建议：检查输入输出格式是否正确，避免死循环");
        return result;
    }
}