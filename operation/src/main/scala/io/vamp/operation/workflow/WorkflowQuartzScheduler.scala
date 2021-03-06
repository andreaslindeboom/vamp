package io.vamp.operation.workflow

import java.util.{ Date, Properties }

import akka.actor.{ Actor, ActorLogging, ActorRef }
import io.vamp.model.workflow.{ ScheduledWorkflow, TimeTrigger }
import io.vamp.operation.workflow.WorkflowSchedulerActor.RunWorkflow
import org.quartz._
import org.quartz.impl.StdSchedulerFactory

trait WorkflowQuartzScheduler {
  this: Actor with ActorLogging ⇒

  private lazy val scheduler = {
    val props = new Properties()
    props.setProperty("org.quartz.scheduler.instanceName", "workflow-scheduler")
    props.setProperty("org.quartz.threadPool.threadCount", "1")
    props.setProperty("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore")
    props.setProperty("org.quartz.scheduler.skipUpdateCheck", "true")
    new StdSchedulerFactory(props).getScheduler
  }

  def quartzSchedule(scheduledWorkflow: ScheduledWorkflow) = scheduledWorkflow.trigger match {
    case TimeTrigger(pattern) ⇒
      log.info(s"Creating a new Quartz job for workflow '${scheduledWorkflow.name}'.")
      val job = {
        val data = new JobDataMap()
        data.put("message", RunWorkflow(scheduledWorkflow))
        data.put("actor", self)
        JobBuilder.newJob(classOf[QuartzJob])
          .usingJobData(data)
          .withIdentity(new JobKey(scheduledWorkflow.name))
          .build()
      }

      val trigger = {
        TriggerBuilder.newTrigger()
          .startNow()
          .withIdentity(new TriggerKey(s"${scheduledWorkflow.name}-trigger")).forJob(job)
          .withSchedule(org.quartz.CronScheduleBuilder.cronSchedule(pattern))
          .build()
      }

      scheduler.scheduleJob(job, trigger)

    case _ ⇒
  }

  def quartzUnschedule(scheduledWorkflow: ScheduledWorkflow) = {
    if (scheduler.deleteJob(new JobKey(scheduledWorkflow.name)))
      log.info(s"Quartz job successfully removed for workflow '${scheduledWorkflow.name}'.")
  }

  def quartzStart() = scheduler.start()

  def quartzShutdown() = scheduler.shutdown()
}

private class QuartzJob() extends Job {
  def execute(ctx: JobExecutionContext) {
    val data = ctx.getJobDetail.getJobDataMap
    data.get("actor").asInstanceOf[ActorRef] ! (data.get("message") -> new Date().getTime)
  }
}