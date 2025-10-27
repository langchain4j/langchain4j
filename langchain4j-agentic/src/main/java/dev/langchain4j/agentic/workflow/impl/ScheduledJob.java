package dev.langchain4j.agentic.workflow.impl;

import static dev.langchain4j.agentic.workflow.impl.SchedulerServiceImpl.JOB_KEY;
import static dev.langchain4j.agentic.workflow.impl.SchedulerServiceImpl.MAX_ITERATIONS;
import static dev.langchain4j.agentic.workflow.impl.SchedulerServiceImpl.TASK_KEY;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScheduledJob implements Job {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduledJob.class);
    private static final Map<String, Integer> iterations = new ConcurrentHashMap<>();

    @Override
    public void execute(final JobExecutionContext jobExecutionContext) throws JobExecutionException {
        try {
            final JobDataMap dataMap = jobExecutionContext.getMergedJobDataMap();
            final Runnable task = (Runnable) dataMap.get(TASK_KEY);
            final int maxIterations = dataMap.getInt(MAX_ITERATIONS);
            final String jobKey = dataMap.getString(JOB_KEY);

            Integer realIterations = iterations.get(jobKey);
            if (realIterations == null) {
                realIterations = 1;
            } else {
                realIterations += 1;
            }
            iterations.put(jobKey, realIterations);

            if (realIterations > maxIterations) {
                jobExecutionContext.getScheduler().deleteJob(JobKey.jobKey(jobKey));
                iterations.remove(jobKey);
            } else {
                LOGGER.info("Execute scheduled task...");
                task.run();
            }
        } catch (SchedulerException e) {
            throw new RuntimeException(e);
        }
    }
}
