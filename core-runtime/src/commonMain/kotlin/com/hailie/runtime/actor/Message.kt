package com.hailie.runtime.actor

import com.hailie.domain.events.Event

sealed interface Message {
    data object Start : Message

    data class DomainEvent(val event: Event) : Message

    data object TimerFired : Message

    data object Stop : Message
}
