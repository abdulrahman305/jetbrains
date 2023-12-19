package com.sourcegraph.cody.agent.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.sourcegraph.cody.agent.CodyAgentManager

class CodyAgentRestartAction : DumbAwareAction("Restart Cody Agent") {
  override fun actionPerformed(event: AnActionEvent) {
    event.project?.let { CodyAgentManager.restartAgent(it) }
  }
}
