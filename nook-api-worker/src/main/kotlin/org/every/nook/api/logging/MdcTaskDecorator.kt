package org.every.nook.api.logging

import org.slf4j.MDC
import org.springframework.core.task.TaskDecorator

class MdcTaskDecorator : TaskDecorator {
    override fun decorate(runnable: Runnable): Runnable {
        val callerContext = MDC.getCopyOfContextMap()
        return Runnable {
            val previousContext = MDC.getCopyOfContextMap()
            try {
                if (callerContext == null) {
                    MDC.clear()
                } else {
                    MDC.setContextMap(callerContext)
                }
                runnable.run()
            } finally {
                if (previousContext == null) {
                    MDC.clear()
                } else {
                    MDC.setContextMap(previousContext)
                }
            }
        }
    }
}
