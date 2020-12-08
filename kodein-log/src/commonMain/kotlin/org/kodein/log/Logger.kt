package org.kodein.log

import org.kodein.log.Logger.Level.*
import org.kodein.log.frontend.defaultLogFrontend
import kotlin.reflect.KClass


public fun interface LogFilter {
    public fun filter(tag: Logger.Tag, entry: Logger.Entry): Logger.Entry?
}

public fun interface LogReceiver {
    public fun receive(entry: Logger.Entry, message: String?)
}

public fun interface LogFrontend {
    public fun getReceiverFor(tag: Logger.Tag): LogReceiver
}

private val defaultFrontEnds by lazy {
    defaultLogFrontend
            .getReceiverFor(Logger.Tag(Logger::class))
            .receive(Logger.Entry(DEBUG), "Using platform default log front end since no front end was defined")
    listOf(defaultLogFrontend)
}

@Suppress("NOTHING_TO_INLINE")
public class Logger(@PublishedApi internal val tag: Tag, frontEnds: Collection<LogFrontend>, @PublishedApi internal val filters: Collection<LogFilter> = emptyList()) {

    public data class Tag(val pkg: String, val name: String) {
        public constructor(cls: KClass<*>) : this(cls.platformPackageName, cls.platformSimpleName)
        override fun toString(): String = "$pkg.$name"
    }

    @PublishedApi
    internal val frontends = (if (frontEnds.isNotEmpty()) frontEnds else defaultFrontEnds) .map { it.getReceiverFor(tag) }

    public enum class Level(public val severity: Int) { DEBUG(0), INFO(1), WARNING(2), ERROR(3) }

    public data class Entry(val level: Level, val ex: Throwable? = null, val meta: Map<String, Any> = emptyMap(), val instant: Instant = now())

    @PublishedApi
    internal fun createEntry(level: Level, error: Throwable? = null, meta: Map<String, Any>): Entry? =
            filters.fold(Entry(level, error, meta)) { entry, filter -> filter.filter(tag, entry) ?: return null  }

    @PublishedApi
    internal var msgPrefix: String? = null

    public inline fun log(level: Level, error: Throwable? = null, meta: Map<String, Any> = emptyMap(), msgCreator: () -> String? = { null }) {
        val entry = createEntry(level, error, meta) ?: return
        val msg = msgPrefix?.let { "$it - " + msgCreator() } ?: msgCreator()
        frontends.forEach { it.receive(entry, msg) }
    }

    public inline fun debug(msgCreator: () -> String) { log(level = DEBUG, msgCreator = msgCreator) }

    public inline fun info(msgCreator: () -> String) { log(level = INFO, msgCreator = msgCreator) }

    public inline fun warning(ex: Throwable? = null, msgCreator: () -> String) { log(level = WARNING, error = ex, msgCreator = msgCreator) }
    public inline fun warning(ex: Throwable) { log(level = WARNING, error = ex) }

    public inline fun error(ex: Throwable? = null, msgCreator: () -> String) { log(level = ERROR, error = ex, msgCreator = msgCreator) }
    public inline fun error(ex: Throwable) { log(level = ERROR, error = ex) }

    public companion object {
        public inline fun <reified T: Any> from(frontends: Collection<LogFrontend>, filters: Collection<LogFilter> = emptyList()): Logger = Logger(
            Logger.Tag(T::class), frontends, filters)
    }
}

public fun Logger.prefixed(prefix: String): Logger = this.apply { msgPrefix = prefix }