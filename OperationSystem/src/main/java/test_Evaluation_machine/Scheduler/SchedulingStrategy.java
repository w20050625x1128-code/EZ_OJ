package test_Evaluation_machine.Scheduler;

import test_Evaluation_machine.Pojo.JudgeTask;

import java.util.concurrent.BlockingDeque;

public interface SchedulingStrategy {
    public void addTask(BlockingDeque<JudgeTask> taskQueue, JudgeTask task) throws InterruptedException;

    public JudgeTask takeTask(BlockingDeque<JudgeTask> taskQueue) throws InterruptedException;
}
