package com.revio.app.data.model

import kotlinx.serialization.Serializable

/**
 * Why a post is being reported. Serialized by name; the names are the wire contract and
 * must stay identical to the backend's `ReportReason` enum.
 */
@Serializable
enum class ReportReason {
    /** The uploaded car does not match the selected brand/model. */
    INCORRECT_CAR_MODEL,

    /** The same car/photo has already been uploaded. */
    DUPLICATE_POST,

    /** Spam, offensive, or otherwise non-car content. */
    INAPPROPRIATE_CONTENT,
}
