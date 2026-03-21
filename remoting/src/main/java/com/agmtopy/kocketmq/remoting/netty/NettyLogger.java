package com.agmtopy.kocketmq.remoting.netty;

import io.netty.util.internal.logging.InternalLogLevel;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * NettyLogger初始化器
 */
public class NettyLogger {
    private static final AtomicBoolean nettyLoggerSeted = new AtomicBoolean(false);
    private static final InternalLogLevel nettyLogLevel = InternalLogLevel.ERROR;

    public static void initNettyLogger() {
        if (!nettyLoggerSeted.get()) {
            try {
                InternalLoggerFactory.setDefaultFactory(new NettyBridgeLoggerFactory());
            } catch (Throwable e) {
                //ignore
            }
            nettyLoggerSeted.set(true);
        }
    }

    private static class NettyBridgeLoggerFactory extends InternalLoggerFactory {
        @Override
        protected InternalLogger newInstance(String name) {
            return new NettyBridgeLogger(name);
        }
    }

    private static class NettyBridgeLogger implements InternalLogger {
        private final com.agmtopy.kocketmq.logging.InternalLogger logger;

        NettyBridgeLogger(String name) {
            this.logger = com.agmtopy.kocketmq.logging.inner.InternalLoggerFactory.getLogger(name);
        }

        @Override
        public String name() {
            return logger.getName();
        }

        @Override
        public boolean isEnabled(InternalLogLevel level) {
            return nettyLogLevel.ordinal() <= level.ordinal();
        }

        @Override
        public void log(InternalLogLevel level, String msg) {
            switch (level) {
                case DEBUG:
                    logger.debug(msg);
                    break;
                case TRACE:
                    logger.info(msg);
                    break;
                case INFO:
                    logger.info(msg);
                    break;
                case WARN:
                    logger.warn(msg);
                    break;
                case ERROR:
                    logger.error(msg);
                    break;
            }
        }

        @Override
        public void log(InternalLogLevel level, Throwable t) {
            String msg = t != null ? t.getMessage() : "";
            switch (level) {
                case DEBUG:
                    logger.debug(msg);
                    break;
                case TRACE:
                    logger.info(msg);
                    break;
                case INFO:
                    logger.info(msg);
                    break;
                case WARN:
                    logger.warn(msg, t);
                    break;
                case ERROR:
                    logger.error(msg);
                    break;
            }
        }

        @Override
        public void log(InternalLogLevel level, String msg, Object o) {
            switch (level) {
                case DEBUG:
                    logger.debug(msg, o);
                    break;
                case TRACE:
                    logger.info(msg, o);
                    break;
                case INFO:
                    logger.info(msg, o);
                    break;
                case WARN:
                    logger.warn(msg, o);
                    break;
                case ERROR:
                    logger.error(msg, o);
                    break;
            }
        }

        @Override
        public void log(InternalLogLevel level, String msg, Object o, Object o1) {
            switch (level) {
                case DEBUG:
                    logger.debug(msg, o, o1);
                    break;
                case TRACE:
                    logger.info(msg, o, o1);
                    break;
                case INFO:
                    logger.info(msg, o, o1);
                    break;
                case WARN:
                    logger.warn(msg, o, o1);
                    break;
                case ERROR:
                    logger.error(msg, o, o1);
                    break;
            }
        }

        @Override
        public void log(InternalLogLevel level, String msg, Object... objects) {
            if (objects.length == 0) {
                log(level, msg);
            } else if (objects.length == 1) {
                log(level, msg, objects[0]);
            } else {
                log(level, msg, objects[0], objects[1]);
            }
        }

        @Override
        public void log(InternalLogLevel level, String msg, Throwable t) {
            switch (level) {
                case DEBUG:
                    logger.debug(msg);
                    break;
                case TRACE:
                    logger.info(msg);
                    break;
                case INFO:
                    logger.info(msg);
                    break;
                case WARN:
                    logger.warn(msg, t);
                    break;
                case ERROR:
                    logger.error(msg);
                    break;
            }
        }

        @Override
        public boolean isTraceEnabled() {
            return isEnabled(InternalLogLevel.TRACE);
        }

        @Override
        public void trace(String msg) {
            logger.info(msg);
        }

        @Override
        public void trace(String format, Object arg) {
            logger.info(format, arg);
        }

        @Override
        public void trace(String format, Object arg, Object arg2) {
            logger.info(format, arg, arg2);
        }

        @Override
        public void trace(String format, Object... arguments) {
            logger.info(format);
        }

        @Override
        public void trace(String msg, Throwable t) {
            logger.info(msg);
        }

        @Override
        public void trace(Throwable t) {
            logger.info(t != null ? t.getMessage() : "");
        }

        @Override
        public boolean isDebugEnabled() {
            return isEnabled(InternalLogLevel.DEBUG);
        }

        @Override
        public void debug(String msg) {
            logger.debug(msg);
        }

        @Override
        public void debug(String format, Object arg) {
            logger.debug(format, arg);
        }

        @Override
        public void debug(String format, Object arg, Object arg2) {
            logger.debug(format, arg, arg2);
        }

        @Override
        public void debug(String format, Object... arguments) {
            logger.debug(format);
        }

        @Override
        public void debug(String msg, Throwable t) {
            logger.debug(msg);
        }

        @Override
        public void debug(Throwable t) {
            logger.debug(t != null ? t.getMessage() : "");
        }

        @Override
        public boolean isInfoEnabled() {
            return isEnabled(InternalLogLevel.INFO);
        }

        @Override
        public void info(String msg) {
            logger.info(msg);
        }

        @Override
        public void info(String format, Object arg) {
            logger.info(format, arg);
        }

        @Override
        public void info(String format, Object arg, Object arg2) {
            logger.info(format, arg, arg2);
        }

        @Override
        public void info(String format, Object... arguments) {
            logger.info(format);
        }

        @Override
        public void info(String msg, Throwable t) {
            logger.info(msg);
        }

        @Override
        public void info(Throwable t) {
            logger.info(t != null ? t.getMessage() : "");
        }

        @Override
        public boolean isWarnEnabled() {
            return isEnabled(InternalLogLevel.WARN);
        }

        @Override
        public void warn(String msg) {
            logger.warn(msg);
        }

        @Override
        public void warn(String format, Object arg) {
            logger.warn(format, arg);
        }

        @Override
        public void warn(String format, Object arg, Object arg2) {
            logger.warn(format, arg, arg2);
        }

        @Override
        public void warn(String format, Object... arguments) {
            logger.warn(format);
        }

        @Override
        public void warn(String msg, Throwable t) {
            logger.warn(msg, t);
        }

        @Override
        public void warn(Throwable t) {
            logger.warn(t != null ? t.getMessage() : "", t);
        }

        @Override
        public boolean isErrorEnabled() {
            return isEnabled(InternalLogLevel.ERROR);
        }

        @Override
        public void error(String msg) {
            logger.error(msg);
        }

        @Override
        public void error(String format, Object arg) {
            logger.error(format, arg);
        }

        @Override
        public void error(String format, Object arg, Object arg2) {
            logger.error(format, arg, arg2);
        }

        @Override
        public void error(String format, Object... arguments) {
            logger.error(format);
        }

        @Override
        public void error(String msg, Throwable t) {
            logger.error(msg);
        }

        @Override
        public void error(Throwable t) {
            logger.error(t != null ? t.getMessage() : "");
        }
    }
}
