package io.enthusia.express.infrastructure.gui

internal fun dialogStatic(
    type: String,
    method: String,
    vararg args: Any?,
): Any = dialogInvoke(type, null, method, args)

internal fun dialogCall(
    type: String,
    owner: Any,
    method: String,
    vararg args: Any?,
): Any = dialogInvoke(type, owner, method, args)

@Suppress("SpreadOperator") // Method.invoke requires the unpacked JVM argument array.
internal fun dialogInvoke(
    type: String,
    owner: Any?,
    name: String,
    args: Array<out Any?>,
): Any {
    val method =
        Class.forName(type).methods.filter { method ->
            method.name == name && method.parameterCount == args.size &&
                method.parameterTypes.withIndex().all {
                        (index, p) ->
                    argumentMatches(p, args[index])
                }
        }.maxByOrNull { method ->
            // ViaAPI also exposes an erased T-player overload accepting Object.
            // A UUID must select the UUID overload before that generic bridge.
            method.parameterTypes.withIndex().count { (index, parameter) -> parameter == args[index]?.javaClass }
        }
            ?: throw NoSuchMethodException("$type.$name/${args.size}")
    return method.invoke(owner, *args) ?: Unit
}

private fun argumentMatches(type: Class<*>, value: Any?): Boolean {
    if (value == null || type.isInstance(value)) return true
    return (type == Integer.TYPE && value is Int) || (type == java.lang.Boolean.TYPE && value is Boolean)
}
