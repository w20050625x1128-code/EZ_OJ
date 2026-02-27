//package test_Evaluation_machine.Scheduler;
//
//import test_Evaluation_machine.Pojo.JudgeTask;
//
//import java.util.concurrent.BlockingDeque;
//
//public class RRSchedulingStrategy implements SchedulingStrategy {
//    private static final long TIME_SLICE = 1000; //时间片
//
//    @Override
//    public void addTask(BlockingDeque<JudgeTask> taskQueue, JudgeTask task) throws InterruptedException {
//        taskQueue.putLast(task);
//    }
//
//    @Override
//    public JudgeTask takeTask(BlockingDeque<JudgeTask> taskQueue) throws InterruptedException {
//
//    }
//}
