package test_Evaluation_machine.Utils;

import test_Evaluation_machine.Pojo.JudgeResponse;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 任务结果缓存：存储异步任务的执行结果，支持阻塞等待结果返回
 */
public class TaskResponseHolder {
    private static final TaskResponseHolder INISTANCE = new TaskResponseHolder();
    //TaskId -> JudgeResponse
    private final Map<String, JudgeResponse> responseMap = new ConcurrentHashMap<>();
    //TaskId -> 倒计时锁（用于判断线程是否执行完成）
    private final Map<String, CountDownLatch> latchMap = new ConcurrentHashMap<>();

    private TaskResponseHolder() {}

    public static TaskResponseHolder getInstance(){
        return INISTANCE;
    }

    /**
     * 注册任务：初始化等待锁
     * @param taskId 任务ID
     */
    public void registerJudgeTask(String taskId){
        latchMap.put(taskId, new CountDownLatch(1));
    }

    /**
     * 保存任务结果：释放等待锁
     * @param taskId 任务ID
     * @param judgeResponse 判题结果
     */
    public void saveJudgeResponse(String taskId, JudgeResponse judgeResponse){
        responseMap.put(taskId, judgeResponse);
        CountDownLatch latch = latchMap.remove(taskId);
        if(latch != null){
            latch.countDown(); //释放锁，唤醒等待线程
        }
    }

    /**
     * 获取任务结果：阻塞等待直到结果返回/超时
     * @param taskId 任务ID
     * @param timeout 超时时间
     * @param timeUnit 时间单位
     * @return 判题结果（超时返回null）
     * @throws InterruptedException 中断异常
     */
    public JudgeResponse getResponse(String taskId,long timeout, TimeUnit timeUnit) throws InterruptedException {
        CountDownLatch latch = latchMap.get(taskId);
        if(latch == null){ //说明任务已执行完成
            // 已完成：返回并移除结果，避免重复读取与内存占用
            return responseMap.remove(taskId);
        }
        //阻塞等待结果
        boolean success = latch.await(timeout, timeUnit);
        if(success){
            return responseMap.remove(taskId); //拿到结果移除缓存
        }
        return null; //超时返回null
    }

}
