package test_Evaluation_machine.Pojo;

import lombok.Data;

import java.util.List;

@Data
public class Task {
    private String pid;
    private Long submitTime;
    private Integer priority;

    private String language;
    private String sourceCode;
    private List<String>inputCases;

    private Long timeLimit;
    private Long memoryLimit;
    private Integer cpuLimit;

    //private TaskStatus status;
    private Long startTime;
    private Long finishTime;

    private Integer exitCode;
    private String output;
    private String errorMessage;
    private Long timeUsed;
    private Long memoryUsed;

}
