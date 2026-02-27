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
    private Problem problem; //题目类

    private TaskStatus status;
    private Integer priority; //优先级
    private Long submitTime; //提交时间
    private Long startTime;

    public enum TaskStatus {
        WAITING,
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
        this.status = TaskStatus.WAITING;
    }

    //@Override
    public int compareTo(JudgeTask other){
        return Integer.compare(this.priority, other.priority);
    }

}
