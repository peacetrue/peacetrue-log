package com.github.peacetrue.log.aspect;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.peacetrue.aspect.AfterParams;
import com.github.peacetrue.aspect.supports.DurationAroundInterceptor;
import com.github.peacetrue.log.aspect.config.LogPointcutInfoProvider;
import com.github.peacetrue.log.service.Log;
import com.github.peacetrue.log.service.LogService;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.aspectj.AspectJAdviceParameterNameDiscoverer;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.scheduling.annotation.Async;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;

/**
 * @author xiayx
 */
public class DefaultLogAspectService implements LogAspectService, BeanFactoryAware {

    private Logger logger = LoggerFactory.getLogger(getClass());
    @Autowired
    private LogService logService;
    @Autowired
    private LogBuilder logBuilder;
    @Autowired
    private LogPointcutInfoProvider logPointcutInfoProvider;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AspectLogProperties properties;
    private BeanFactoryResolver beanFactoryResolver;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactoryResolver = new BeanFactoryResolver(beanFactory);
    }

    @Override
    @SuppressWarnings("unchecked")
    @Async(AspectLogAutoConfiguration.LOG_TASK_EXECUTOR_NAME)
    public void addLog(AfterParams<Long> afterParams) {
        ProceedingJoinPoint joinPoint = afterParams.getAroundParams().getProceedingJoinPoint();

        LogPointcutInfo logPointcutInfo = this.getLogPointcutInfo(afterParams);
        logger.debug("取得日志切面信息[{}]", logPointcutInfo);
        if (logPointcutInfo == null) {
            throw new IllegalStateException(String.format("未找到与切点[%s]匹配的日志切面信息", joinPoint.getSignature().toShortString()));
        }

        LogEvaluationContext evaluationContext = this.buildEvaluationContext(joinPoint, afterParams.getReturnValue());
        logger.debug("创建日志表达式取值上下文[{}]", evaluationContext);

        Log log = logBuilder.build(logPointcutInfo, evaluationContext);
        log.setDuration(DurationAroundInterceptor.getDuration(Objects.requireNonNull(afterParams.getData())));
        setLogInput(log, joinPoint.getArgs());
        setLogOutput(log, afterParams.getReturnValue());
        setLogException(log, afterParams.getThrowable());
        logger.debug("取得日志信息[{}]", log);

        logService.add(log);
    }

    protected LogPointcutInfo getLogPointcutInfo(AfterParams<Long> afterParams) {
        if (afterParams.getAroundParams() instanceof LogAroundParams) {
            return ((LogAroundParams) afterParams.getAroundParams()).getLogPointcutInfo();
        }

        ProceedingJoinPoint joinPoint = afterParams.getAroundParams().getProceedingJoinPoint();
        LogPointcut logPointcut = getMethod(joinPoint).getAnnotation(LogPointcut.class);
        if (logPointcut != null) {
            return LogPointcutInfo.fromLogPointcut(logPointcut);
        } else {
            return logPointcutInfoProvider.findLogPointcutInfo(joinPoint);
        }
    }


    protected LogEvaluationContext buildEvaluationContext(ProceedingJoinPoint joinPoint, @Nullable Object returnValue) {
        LogEvaluationContext evaluationContext = new LogEvaluationContext(
                joinPoint.getTarget(), getMethod(joinPoint), joinPoint.getArgs(),
                new AspectJAdviceParameterNameDiscoverer(joinPoint.getStaticPart().toLongString()),
                returnValue
        );
        evaluationContext.setVariable("returning", returnValue);
        evaluationContext.setBeanResolver(beanFactoryResolver);
        return evaluationContext;
    }

    protected void setLogInput(Log log, @Nullable Object[] args) {
        Optional.ofNullable(args).ifPresent(value -> log.setInput(this.writeValueAsString(value, properties.getInputMaxLength())));
    }

    protected void setLogOutput(Log log, @Nullable Object returnValue) {
        Optional.ofNullable(returnValue).ifPresent(value -> log.setOutput(this.writeValueAsString(value, properties.getOutputMaxLength())));
    }

    protected void setLogException(Log log, @Nullable Throwable throwable) {
        Optional.ofNullable(throwable).ifPresent(value -> log.setException(this.trunc(value.getMessage(), properties.getExceptionMaxLength())));
    }

    private String writeValueAsString(Object value, int length) {
        try {
            return trunc(objectMapper.writeValueAsString(value), length);
        } catch (JsonProcessingException e) {
            logger.warn("json转换异常", e);
            return null;
        }
    }

    private String trunc(String string, int length) {
        return string.length() > length ? string.substring(0, length) : string;
    }

    private static Method getMethod(JoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        if (!method.getDeclaringClass().isInterface()) return method;

        try {
            return joinPoint.getTarget().getClass().getDeclaredMethod(method.getName(), method.getParameterTypes());
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(String.format("未能找到切点[%s]对应的方法", joinPoint.getSignature().toShortString()), e);
        }
    }

}
