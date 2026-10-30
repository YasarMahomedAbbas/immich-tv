package io.github.yasarmahomedabbas.immichtv.domain.home

import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import java.time.LocalDate

/** Memories a test sets, recording the days asked for. */
class FakeMemoryRepository(var memories: ServerResult<List<Memory>> = ServerResult.Ok(emptyList())) : MemoryRepository {
    val days = mutableListOf<LocalDate>()

    override suspend fun memoriesOn(day: LocalDate): ServerResult<List<Memory>> {
        days += day
        return memories
    }
}
