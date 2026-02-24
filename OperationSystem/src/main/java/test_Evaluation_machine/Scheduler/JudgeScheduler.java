package test_Evaluation_machine.Scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import test_Evaluation_machine.Mapper.ProblemMapper;
import test_Evaluation_machine.Pojo.JudgeRequest;
import test_Evaluation_machine.Pojo.JudgeResponse;
import test_Evaluation_machine.Pojo.JudgeTask;
import test_Evaluation_machine.Service.JudgeService;
import test_Evaluation_machine.Utils.TaskResponseHolder;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@Component
@Slf4j // 记得加这个注解，才能用log
public class JudgeScheduler {
    @Autowired
    private JudgeService judgeService;
    private ProblemMapper problemMapper;

        private static final int Judger_NUM=3; //如果有三台判题机

        private static final int TASK_QUEUE_CAPACITY=10; //任务队列最大长度

        private static final long Task_TIMEOUT = 5; //超时时间
        private static final TimeUnit Task_TIMEOUT_UNIT = TimeUnit.SECONDS;

        private final BlockingDeque<JudgeTask> taskQueue = new LinkedBlockingDeque<>(TASK_QUEUE_CAPACITY); //任务队列

        private final Semaphore JudgerSemaphore = new Semaphore(Judger_NUM);//判题机作为临界资源

        private final ReentrantLock queueLock = new ReentrantLock();//互斥锁，实现对于任务队列的互斥访问，实现生产者-消费者模型
        private final Condition notEmpty = queueLock.newCondition();
        private final Condition notFull = queueLock.newCondition();

        private Thread[] workerThreads;

        private SchedulingStrategy schedulingStrategy = new PrioritySchedulingStrategy(); //调度算法，待扩展

        private final TaskResponseHolder responseHolder = TaskResponseHolder.getInstance();

        // ====================== 初始化&销毁 ======================
        /**
         * 初始化：手动创建Worker线程
         */
        @PostConstruct
        public void init() {
            workerThreads = new Thread[Judger_NUM];
            for (int i = 0; i < Judger_NUM; i++) {
                workerThreads[i] = new Thread(new Worker("Worker-" + i));
                workerThreads[i].setDaemon(false);
                workerThreads[i].start();
                log.info("初始化Worker线程：{}", workerThreads[i].getWorkerId());
            }
        }

    /**
     * 销毁：关闭Worker线程
     */
        @PostConstruct
        public void destroy() {
            if(workerThreads==null) return;
            //中断所有worker线程
            for (Thread worker : workerThreads) {
                worker.interrupt();
                log.info("中断Worker线程：{}", worker.getWorkerId());
            }

            for (Thread worker : workerThreads) {
                try {
                    worker.join(1000);
                }catch (InterruptedException e) {
                    log.error("等待Worker线程退出失败", e);
                }
            }
            log.info("所有Worker线程已退出");
        }

        // ====================== 任务提交（生产者）======================
        /**
         * 提交评测任务（模拟OS的进程提交）
         * @param task 评测任务
         * @return 是否提交成功
         * @throws InterruptedException 中断异常
         */
        public boolean submitTaskToSchedulerQueue(JudgeTask task) throws InterruptedException {
            queueLock.lock();//加锁
            try {
                //任务队列已满，生产者等到
                while(taskQueue.size() >= TASK_QUEUE_CAPACITY) {
                    log.warn("任务队列已满，生产者等待：{}", task.getTaskId());
                    notFull.await();
                }

                schedulingStrategy.addTask(taskQueue,task);//使用调度算法将任务插入队列
                log.info("提交任务成功：{}，队列当前长度：{}", task.getTaskId(), taskQueue.size());

                notEmpty.signal(); //唤醒消费者
                return true;
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                queueLock.unlock();//释放锁
            }
        }

        // ====================== Worker线程（消费者/执行器）======================
        private class Worker implements Runnable {
            private final String workerId;
            public Worker(String workerId) {
                this.workerId = workerId;
            }

            @Override
            public void run() {
                log.info("Worker线程启动:{}", workerId);
                while(!Thread.currentThread().isInterrupted()) {
                    JudgeTask task = null;
                    try {
                        //取任务队列中的任务
                        queueLock.lock();
                        try {
                            while(taskQueue.isEmpty()) {
                                log.info("任务队列为空，Worker线程等待：{}", workerId);
                                notEmpty.await(1,TimeUnit.SECONDS); //防止永久堵塞
                            }
                            task = schedulingStrategy.takeTask(taskQueue);
                            notFull.signal(); //唤醒生产者
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }finally {
                            queueLock.unlock();
                        }

                        if(task==null)   continue;

                        log.info("Worker{} 尝试使用判题机，剩余许可数：{}", workerId, JudgerSemaphore.availablePermits());
                        JudgerSemaphore.acquire(); //尝试获得判题机
                        log.info("Worker{} 占用判题机，开始执行任务：{}", workerId, task.getTaskId());
                        executeTask(task); //执行判题

                    }catch (InterruptedException e) { //捕获中断，退出循环
                        log.info("Worker线程收到中断信号，准备退出：{}", workerId);
                        Thread.currentThread().interrupt(); //重置中断状态
                        break;
                    }catch (Exception e) {
                        log.error("Worker{} 执行任务异常", workerId, e);
                        if(JudgerSemaphore.availablePermits() < Judger_NUM) {
                            JudgerSemaphore.release();
                        }
                    }finally {
                        if(task != null && JudgerSemaphore.availablePermits() < Judger_NUM) {
                            JudgerSemaphore.release();
                            log.info("Worker{} 释放判题机，剩余许可数：{}", workerId, JudgerSemaphore.availablePermits());
                        }
                    }
                }
                log.info("Worker线程退出：{}", workerId);
            }
        }

        // ====================== 任务执行（模拟OS的进程执行）======================
        /**
         * 执行任务
         * @param task 评测任务
         */
        private void executeTask(JudgeTask task)throws Exception{
            try {
                task.setStatus(JudgeTask.TaskStatus.RUNNING);
                task.setStartTime(System.currentTimeMillis());
                JudgeResponse response = judgeService.judgeCpp(task);
                responseHolder.saveJudgeResponse(task.getTaskId(), response);
                log.info("任务{}执行完成，结果已保存", task.getTaskId());
            }catch (Exception e) {
                log.error("任务{}执行失败", task.getTaskId(), e);
                JudgeResponse errorResponse = new JudgeResponse();
                errorResponse.setError(e.getMessage());
                responseHolder.saveJudgeResponse(task.getTaskId(), errorResponse);
                throw e;
            }
        }

        /**
         * 对外提供：提交任务至缓存并返回任务ID（供Controller调用）
         */
        public String submitJudgeTaskToHolder(JudgeRequest request) throws InterruptedException {
            //构造task
            JudgeTask task = new JudgeTask(request.getLanguage(),request.getCode(),request.getProbNum());
            //注册任务，初始化等待锁
            responseHolder.registerJudgeTask(task.getTaskId());
            //提交任务到调度队列
            boolean submitSuccess = submitTaskToSchedulerQueue(task);
            if(!submitSuccess) {
                throw new RuntimeException("提交至调度队列失败");
            }
            return task.getTaskId();
        }

        /**
         * 对外提供：根据任务ID获取判题结果（供Controller调用）
         */
        public JudgeResponse getJudgeResponse(String taskId) throws InterruptedException{
            return responseHolder.getResponse(taskId,5,TimeUnit.SECONDS);
        }


}
