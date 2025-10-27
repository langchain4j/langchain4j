package dev.langchain4j.agentic.workflow.impl;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

import dev.langchain4j.agentic.workflow.SchedulerService;
import java.util.UUID;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.impl.StdSchedulerFactory;

public class SchedulerServiceImpl implements SchedulerService {

    public static final String TASK_KEY = "task";
    public static final String MAX_ITERATIONS = "maxIterations";
    public static final String JOB_KEY = "jobKey";

    private final Scheduler scheduler;

    public SchedulerServiceImpl() {
        try {
            this.scheduler = StdSchedulerFactory.getDefaultScheduler();
        } catch (SchedulerException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void schedule(final String cronExpression, final int maxIterations, final Runnable task) {
        try {
            String jobKey = UUID.randomUUID().toString();
            final JobDataMap dataMap = new JobDataMap();
            dataMap.put(TASK_KEY, task);
            dataMap.put(MAX_ITERATIONS, maxIterations);
            dataMap.put(JOB_KEY, jobKey);

            JobDetail job = newJob(ScheduledJob.class)
                    .setJobData(dataMap)
                    .withIdentity(jobKey)
                    .build();

            Trigger trigger = newTrigger()
                    .withIdentity(jobKey)
                    .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
                    .startNow()
                    .build();

            scheduler.scheduleJob(job, trigger);
        } catch (SchedulerException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void start() {
        try {
            scheduler.start();
        } catch (SchedulerException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void stop() {
        try {
            scheduler.shutdown();
        } catch (SchedulerException e) {
            throw new RuntimeException(e);
        }
    }
}
