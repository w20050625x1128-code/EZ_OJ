package test_Evaluation_machine.Pojo;

import lombok.Data;

import java.util.List;

@Data
public class Problem {
    private String problemId;
    private String problemName;
    private List<String> inputCases;
    private List<String> outputCases;
    private ProblemStatus status;
    private String description;
    //private String command;
    private Long timeLimit;
    private Long memoryLimit;

    public enum ProblemStatus {
        UNACCEPTED,
        ACCEPTED
    }
}
