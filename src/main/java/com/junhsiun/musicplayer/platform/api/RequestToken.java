package com.junhsiun.musicplayer.platform.api;

import okhttp3.Call;

import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一次浏览请求的取消令牌。
 *
 * <p>快速连续切页时，旧页面已经发起的请求如果继续排队、继续跑，会占满 HTTP 线程池，
 * 新页面的歌单/曲目请求只能等在后面，表现就是「列表很久才出来」。这里给每次请求挂一个
 * 令牌：切页时把旧代号的令牌全部取消，正在执行的 OkHttp {@link Call} 会被 {@link Call#cancel()}
 * 中断，尚未开始执行的任务也会在入口直接抛出取消异常，不再占用线程。</p>
 *
 * <p><b>为什么不用 {@code CompletableFuture.cancel()}：</b>取消一个由
 * {@code supplyAsync} 创建的 future 并不能中断里已经开始的阻塞网络调用，线程照样被占着。
 * 必须拿到真正的 {@link Call} 才能让请求立刻退出。</p>
 */
final class RequestToken {

    /** 当前线程正在执行的请求令牌；{@link DirectHttp} 通过它登记/取消底层的 {@link Call}。 */
    private static final ThreadLocal<RequestToken> CURRENT = new ThreadLocal<>();

    /** 提交时的浏览代号：切页后小于新一代的令牌全部作废。 */
    private final long epoch;
    private final Set<Call> calls = ConcurrentHashMap.newKeySet();
    private volatile boolean cancelled;

    RequestToken(long epoch) {
        this.epoch = epoch;
    }

    long epoch() {
        return epoch;
    }

    boolean isCancelled() {
        return cancelled;
    }

    /** 当前线程正在处理的令牌；没有则返回 {@code null}。 */
    static RequestToken current() {
        return CURRENT.get();
    }

    /** 在令牌上下文中执行任务；结束后一定要清掉 ThreadLocal，线程池会复用线程。 */
    <T> T run(Callable<T> task) throws Exception {
        CURRENT.set(this);
        try {
            if (cancelled) {
                throw new CancellationException("browse request cancelled");
            }
            return task.call();
        } finally {
            CURRENT.remove();
        }
    }

    /** 登记一个底层调用；如果令牌已经被取消，立刻把它取消掉。 */
    void attach(Call call) {
        calls.add(call);
        if (cancelled) {
            calls.remove(call);
            call.cancel();
        }
    }

    void detach(Call call) {
        calls.remove(call);
    }

    /** 取消这一代请求：阻塞中的调用立刻中断，其它已登记调用也一并结束。 */
    void cancel() {
        if (cancelled) {
            return;
        }
        cancelled = true;
        for (Call call : calls) {
            call.cancel();
        }
        calls.clear();
    }
}