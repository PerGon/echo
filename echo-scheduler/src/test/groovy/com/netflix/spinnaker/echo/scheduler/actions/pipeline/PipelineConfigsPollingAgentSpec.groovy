/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.scheduler.actions.pipeline

import com.netflix.scheduledactions.ActionInstance
import com.netflix.scheduledactions.ActionsOperator
import com.netflix.scheduledactions.triggers.CronTrigger
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.echo.model.Pipeline
import com.netflix.spinnaker.echo.model.Trigger
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache
import com.netflix.spinnaker.echo.scheduler.actions.pipeline.impl.PipelineConfigsPollingAgent
import spock.lang.Specification
import spock.lang.Subject

class PipelineConfigsPollingAgentSpec extends Specification {

    Registry registry = new NoopRegistry()
    def actionsOperator = Mock(ActionsOperator)
    def pipelineCache = Mock(PipelineCache)
    @Subject pollingAgent = new PipelineConfigsPollingAgent(registry, pipelineCache, actionsOperator, 1000000, 'America/Los_Angeles')

    void 'when a new pipeline trigger is added, a scheduled action instance is registered with an id same as the trigger id'() {
        given:
        Trigger trigger = Trigger.builder()
            .enabled(true)
            .type('cron')
            .cronExpression('* 0/30 * * * ? *')
            .build()
        Pipeline pipeline = buildPipeline([trigger])
        pipelineCache.getPipelines() >> [pipeline]
        actionsOperator.getActionInstances() >> []

        when:
        pollingAgent.execute()

        then:
        0 * actionsOperator.enableActionInstance(_)
        0 * actionsOperator.disableActionInstance(_)
        0 * actionsOperator.updateActionInstance(_)
        0 * actionsOperator.deleteActionInstance(_)
        1 * actionsOperator.registerActionInstance(_ as ActionInstance)
    }

    void 'when an existing pipeline trigger is disabled, corresponding scheduled action is also disabled'() {
        given:
        Trigger trigger = Trigger.builder()
            .enabled(false)
            .id('t1')
            .type(Trigger.Type.CRON.toString())
            .cronExpression('* 0/30 * * * ? *')
            .build()
        Pipeline pipeline = buildPipeline([trigger])
        ActionInstance actionInstance = buildScheduledAction(trigger.id, '* 0/30 * * * ? *', true)
        pipelineCache.getPipelines() >> [pipeline]
        actionsOperator.getActionInstances() >> [actionInstance]

        when:
        pollingAgent.execute()

        then:
        !actionInstance.disabled
        0 * actionsOperator.enableActionInstance(_)
        0 * actionsOperator.updateActionInstance(_)
        0 * actionsOperator.registerActionInstance(_)
        0 * actionsOperator.deleteActionInstance(_)
        1 * actionsOperator.disableActionInstance(actionInstance)
    }

    void 'when an existing pipeline is disabled, corresponding scheduled action is also disabled'() {
      given:
      Trigger trigger = Trigger.builder()
          .enabled(true)
          .id('t1')
          .type(Trigger.Type.CRON.toString())
          .cronExpression('* 0/30 * * * ? *')
          .build()
      Pipeline pipeline = buildPipeline([trigger], true)
      ActionInstance actionInstance = buildScheduledAction(trigger.id, '* 0/30 * * * ? *', true)
      pipelineCache.getPipelines() >> [pipeline]
      actionsOperator.getActionInstances() >> [actionInstance]

      when:
      pollingAgent.execute()

      then:
      !actionInstance.disabled
      0 * actionsOperator.enableActionInstance(_)
      0 * actionsOperator.updateActionInstance(_)
      0 * actionsOperator.registerActionInstance(_)
      0 * actionsOperator.deleteActionInstance(_)
      1 * actionsOperator.disableActionInstance(actionInstance)
    }

    void 'when an existing disabled pipeline trigger is enabled, corresponding scheduled action is also enabled'() {
        given:
        Trigger trigger = Trigger.builder()
            .enabled(true)
            .id('t1')
            .type(Trigger.Type.CRON.toString())
            .cronExpression('* 0/30 * * * ? *')
            .build()
        Pipeline pipeline = buildPipeline([trigger])
        ActionInstance actionInstance = buildScheduledAction(trigger.id, '* 0/30 * * * ? *', false)
        pipelineCache.getPipelines() >> [pipeline]
        actionsOperator.getActionInstances() >> [actionInstance]

        when:
        pollingAgent.execute()

        then:
        0 * actionsOperator.updateActionInstance(_)
        0 * actionsOperator.registerActionInstance(_)
        0 * actionsOperator.disableActionInstance(_)
        0 * actionsOperator.deleteActionInstance(_)
        1 * actionsOperator.enableActionInstance(actionInstance)
    }

    void 'when an existing pipeline trigger is removed, corresponding scheduled action is also removed'() {
        given:
        Pipeline pipeline = buildPipeline([])
        ActionInstance actionInstance = buildScheduledAction('t1', '* 0/30 * * * ? *', true)
        pipelineCache.getPipelines() >> [pipeline]
        actionsOperator.getActionInstances() >> [actionInstance]

        when:
        pollingAgent.execute()

        then:
        0 * actionsOperator.updateActionInstance(_)
        0 * actionsOperator.registerActionInstance(_)
        0 * actionsOperator.disableActionInstance(_)
        0 * actionsOperator.enableActionInstance(_)
        1 * actionsOperator.deleteActionInstance(actionInstance)
    }

    void 'when an existing pipeline trigger is updated, corresponding scheduled action is also updated'() {
        given:
        Trigger trigger = Trigger.builder()
            .enabled(true)
            .id('t1')
            .type(Trigger.Type.CRON.toString())
            .cronExpression('* 0/45 * * * ? *')
            .build()
        Pipeline pipeline = buildPipeline([trigger])
        ActionInstance actionInstance = buildScheduledAction('t1', '* 0/30 * * * ? *', true)
        pipelineCache.getPipelines() >> [pipeline]
        actionsOperator.getActionInstances() >> [actionInstance]

        when:
        pollingAgent.execute()

        then:
        0 * actionsOperator.registerActionInstance(_)
        0 * actionsOperator.disableActionInstance(_)
        0 * actionsOperator.enableActionInstance(_)
        0 * actionsOperator.deleteActionInstance(_)
        1 * actionsOperator.updateActionInstance(_ as ActionInstance)
    }

    void 'when an existing pipeline trigger is updated but is still disabled, corresponding scheduled action is NOT updated'() {
        given:
        Trigger trigger = Trigger.builder()
            .enabled(true)
            .id('t1')
            .type(Trigger.Type.CRON.toString())
            .cronExpression('* 0/45 * * * ? *')
            .build()
        Pipeline pipeline = buildPipeline([trigger])
        ActionInstance actionInstance = buildScheduledAction('t1', '* 0/30 * * * ? *', false)
        pipelineCache.getPipelines() >> [pipeline]
        actionsOperator.getActionInstances() >> [actionInstance]

        when:
        pollingAgent.execute()

        then:
        0 * actionsOperator.registerActionInstance(_)
        0 * actionsOperator.disableActionInstance(_)
        0 * actionsOperator.enableActionInstance(_)
        0 * actionsOperator.deleteActionInstance(_)
        0 * actionsOperator.updateActionInstance(actionInstance)
    }

    void 'with no changes to pipeline trigger, no scheduled actions are updated for that pipeline'() {
        given:
        Trigger trigger = Trigger.builder()
            .enabled(true)
            .id('t1')
            .type(Trigger.Type.CRON.toString())
            .cronExpression('* 0/30 * * * ? *')
            .build()
        Pipeline pipeline = buildPipeline([trigger])
        ActionInstance actionInstance = buildScheduledAction('t1', '* 0/30 * * * ? *', true)
        pipelineCache.getPipelines() >> [pipeline]
        actionsOperator.getActionInstances() >> [actionInstance]

        when:
        pollingAgent.execute()

        then:
        0 * actionsOperator.registerActionInstance(_)
        0 * actionsOperator.disableActionInstance(_)
        0 * actionsOperator.enableActionInstance(_)
        0 * actionsOperator.deleteActionInstance(_)
        0 * actionsOperator.updateActionInstance(_)
    }

    private static Pipeline buildPipeline(List<Trigger> triggers) {
        buildPipeline(triggers, false)
    }

    private static Pipeline buildPipeline(List<Trigger> triggers, boolean disabled) {
      Pipeline
        .builder()
        .application('api')
        .name('Pipeline 1')
        .id('p1')
        .parallel(true)
        .triggers(triggers)
        .disabled(disabled)
        .build()
    }

    private static ActionInstance buildScheduledAction(String id, String cronExpression, boolean enabled) {
        ActionInstance actionInstance = ActionInstance.newActionInstance()
            .withId(id)
            .withTrigger(new CronTrigger(cronExpression))
            .withParameters([triggerTimeZoneId: 'America/Los_Angeles'])
            .build()
        actionInstance.disabled = !enabled
        return actionInstance
    }
}
