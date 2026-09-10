package ni.nexo.app.data

import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive

/** Primitive String overload visible to JSON builders in this package. */
internal fun JsonObjectBuilder.put(key: String, value: String) {
    put(key, JsonPrimitive(value))
}
