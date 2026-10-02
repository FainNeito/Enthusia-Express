package io.enthusia.express.domain

import java.util.UUID

/** Fixed non-player recipient for unclaimed museum submissions. */
object MapartQueue {
    @JvmField val ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    const val NAME = "Mapart Museum Intake"
}
