package test_Evaluation_machine.Pojo;

import lombok.Data;

@Data
public class JudgeResponse {

    public String output;
    public JudgeStatus status;
    public String error;
    private long usedTime;
    private long usedMemory;

    public enum JudgeStatus {
        PENDING,
        TIME_LIMIT_EXCEEDED,
        MEMORY_LIMIT_EXCEEDED,
        RUNTIME_ERROR,
        COMPILE_ERROR,
        UNKNOWN_ERROR,
        WRONG_ANSWER,
        ACCEPTED

//        private final String desc;
//        JudgeStatus(String desc){
//            this.desc = desc;
//        }
    }

}
