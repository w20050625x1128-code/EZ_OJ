package test_Evaluation_machine.Pojo;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class JudgeTask {
    private String taskId; //任务id
    private String sourceCode; //待测评源码
    private String language; //语言
    private String questionNumber; //题目编号


    private TaskStatus status;
    private Integer priority; //优先级
    private Long submitTime; //提交时间

    private Long startTime;
    private Long timeLimit; //时间限制
    private Long memoryLimit; //内存限制

//    private String output; //运行输出
//    private String errorMessage; //错误信息
//    private Long timeUsed; //用时
//    private Long memoryUsed; //使用内存



    public enum TaskStatus {
        WATING,
        RUNNING,
        TIME_LIMIT_EXCEEDED,
        MEMORY_LIMIT_EXCEEDED,
        RUNTIME_ERROR,
        COMPILE_ERROR,
        WRONG_ANSWER,
        ACCEPTED
    }

    public JudgeTask(String language,String sourceCode,String questionNumber) {
        this.taskId = UUID.randomUUID().toString();
        this.submitTime = System.currentTimeMillis();
        this.language = language;
        this.sourceCode = sourceCode;
        this.questionNumber = questionNumber;

        this.priority = 3;
        this.timeLimit = 5000L;
        this.memoryLimit = 256L;
        this.status = TaskStatus.WATING;
    }

    //@Override
    public int compareTo(JudgeTask other){
        return Integer.compare(this.priority, other.priority);
    }
    //getter方法获取参数
}
