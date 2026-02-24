package test_Evaluation_machine.Pojo;

import lombok.Data;

@Data
public class JudgeResponse {

//    private String errorMessage; //错误信息
//    private Long timeUsed; //用时
//    private Long memoryUsed; //使用内存

    public String output;
    public JudgeStatus status;
    public String error;

    public enum JudgeStatus {
        TIME_LIMIT_EXCEEDED,
        MEMORY_LIMIT_EXCEEDED,
        RUNTIME_ERROR,
        COMPILE_ERROR,
        WRONG_ANSWER,
        ACCEPTED
    }
}
