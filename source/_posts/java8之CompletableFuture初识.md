---
title: java8之CompletableFuture初识
date: 2020-03-24 21:43:07
tags:
 - java
 - multi-thread
categories:
 - java
---

## 提要
在jdk5中增加了很多新的并发处理机制，对于多线程处理有了很大的优化。我们通常会选择通过Future接口构建异步的应用，因为在之前的多线程的实现中，不管是继承thread类还是实现runnable接口，都无法保证获取到之前的执行结果。我们现在可以通过实现Callable接口，并用Future来接收多线程的执行结果。

## Callable处理demo

通过kafka多线程消费举个例子(伪代码):
``` java
public void execute(int threads) {
	//threads核心数 10-最大线程数 0L-空闲等待时间
	var executors = new ThreadPoolExecutor(threads, 10, 0L, TimeUnit.MILLISECONDS,
	new ArrayBlockingQueue(1000), new ThreadPoolExecutor.CallerRunsPolicy());
//启动一个子线程来监听消息
	Thread t = new Thread(() -> {
		try {
		while (true) {
		/*采用循环不断从kafka里捞数据*/
			ConsumerRecords<String, String> records = //todo 返回数据;
			List<Callable<String>> consumerWork = new ArrayList<>();
			for (final ConsumerRecord record : records) {
				consumerWork.add(writeData(record));
			}
			List<Future<String>> futures = executors.invokeAll(consumerWork);
			//拿到futures结果
			}
		} catch (InterruptedException e) {
			log.error("Test.execute()->{}",e);
		}
	});
	t.start();
}
```
<!-- more -->
下面是实现Callable的耗时任务(伪代码):
``` java
private Callable<String> writeData(ConsumerRecord record) {
	Callable<String> run = new Callable<String>() {
		public void run() {
		// TODO 耗时任务逻辑
    	/*业务处理*/
		}
		@Override
		public String call() throws Exception {
		// TODO Auto-generated method stub
   			run();
   			return null;
		}
	};
   return run;
}

```
看起来挺不错的，可是当我们真正去调用get()方法时，当前线程会去等待异步任务的执行。换言之，主线程会被阻塞，大大增加了异步操作的时耗。

## CompletableFuture使用

### 实际开发场景

1.  针对Future的完成事件，我们希望可以得到它完成后的返回结果，又不想阻塞主线程的运行
2. 面对Future集合来讲，很难将List中的future结果依赖关系描述出来，我们希望在所有future完成结束后去做一些事情
3. 在异步计算中，存在某两个业务处理独立计算，而其中一个依赖前一个的运算结果。
如上的几种场景，是future的短板，它本身缺乏一种观察者模式去监听线程处理状态从而得到回调结果。面对这样的局限性，在java8中CompletableFuture提供了较为不错的api实现

### CompletableFuture常见API解析

注：所有没有指定Executor的方法会使用ForkJoinPool.commonPool() 作为它的线程池执行异步代码

#### 创建异步任务runAsync和supplyAsync
``` java
public static <U> CompletableFuture<U> supplyAsync(Supplier<U> var0) {
    return asyncSupplyStage(asyncPool, var0);
}

public static <U> CompletableFuture<U> supplyAsync(Supplier<U> var0, Executor var1) {
    return asyncSupplyStage(screenExecutor(var1), var0);
}

public static CompletableFuture<Void> runAsync(Runnable var0) {
    return asyncRunStage(asyncPool, var0);
}

public static CompletableFuture<Void> runAsync(Runnable var0, Executor var1) {
    return asyncRunStage(screenExecutor(var1), var0);
}

```
runAsync方法没有返回值，异步操作完就结束了,而supplyAsync方法类似submit方法，支持返回值。

#### 异步任务执行完时的回调方法whenComplete和exceptionally
``` java
public CompletableFuture<T> whenComplete(BiConsumer<? super T, ? super Throwable> var1) {
    return this.uniWhenCompleteStage((Executor)null, var1);
}

public CompletableFuture<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> var1) {
    return this.uniWhenCompleteStage(asyncPool, var1);
}

public CompletableFuture<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> var1, Executor var2) {
    return this.uniWhenCompleteStage(screenExecutor(var2), var1);
}
public CompletableFuture<T> exceptionally(Function<Throwable, ? extends T> var1) {
    return this.uniExceptionallyStage(var1);
}


```
这些方法都是上述创建的异步任务完成后 (也可能是抛出异常后结束) 所执行的方法。whenComplete和whenCompleteAsync方法的区别在于：
	&emsp;前者是由上面的线程继续执行，而后者是将whenCompleteAsync的任务继续交给线程池去做决定。
exceptionally则是上面的任务执行抛出异常后所要执行的方法。
值得注意的是：哪怕supplyAsync抛出了异常，whenComplete也会执行，意思就是，只要supplyAsync执行结束，它就会执行，不管是不是正常执行完。exceptionally只有在异常的时候才会执行。其实，在whenComplete的参数内e就代表异常了，判断它是否为null，就可以判断是否有异常，只不过这样的做法，我们不提倡。whenComplete和exceptionally这两个谁在前，谁先执行。 此类的回调方法，哪怕主线程已经执行结束，回调方法依然可以继续等待异步任务执行完成再触发得到执行结果。

#### thenApply和handle方法

如果两个任务之间有依赖关系，比如B任务依赖于A任务的执行结果，那么就可以使用这两个方法。
``` java
public <U> CompletableFuture<U> thenApply(Function<? super T, ? extends U> var1) {
    return this.uniApplyStage((Executor)null, var1);
}

public <U> CompletableFuture<U> thenApplyAsync(Function<? super T, ? extends U> var1) {
    return this.uniApplyStage(asyncPool, var1);
}
public <U> CompletableFuture<U> thenApplyAsync(Function<? super T, ? extends U> var1, Executor var2) {
    return this.uniApplyStage(screenExecutor(var2), var1);
}
public <U> CompletableFuture<U> handle(BiFunction<? super T, Throwable, ? extends U> var1) {
    return this.uniHandleStage((Executor)null, var1);
}

public <U> CompletableFuture<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> var1) {
    return this.uniHandleStage(asyncPool, var1);
}

public <U> CompletableFuture<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> var1, Executor var2) {
    return this.uniHandleStage(screenExecutor(var2), var1);
}

```
这两个方法，效果是一样的。区别在于，当A任务执行出现异常时，thenApply方法不会执行，而handle 方法一样会去执行，因为在handle方法里，我们可以处理异常，而前者不行。
这里延伸两个方法thenAccept和thenRun。其实和上面两个方法差不多，都是等待前面一个任务执行完 再执行。区别就在于thenAccept接收前面任务的结果，且无需return。而thenRun只要前面的任务执行完成，它就执行，不关心前面的执行结果如何如果前面的任务抛了异常，非正常结束，这两个方法是不会执行的，所以处理不了异常情况。

#### allOf方法

``` java
public static CompletableFuture<Void> allOf(CompletableFuture... var0) {
    return andTree(var0, 0, var0.length - 1);
}

```
很多时候，不止存在两个异步任务，可能有几十上百个。我们需要等这些任务都完成后，再来执行相应的操作。那怎么集中监听所有任务执行结束与否呢？ allOf方法可以帮我们完成
它接收一个可变入参，既可以接收CompletableFuture单个对象，可以接收其数组对象。

## 代码重构

大致了解jdk8中对多线程异步流处理之后，我们对刚开始的代码进一步修改（对应业务->某两个业务处理独立计算，而其中一个依赖前一个的运算结果）

``` java
public void execute(int threads){
    var executors = new ThreadPoolExecutor(threads, 10, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue(1000), new ThreadPoolExecutor.CallerRunsPolicy());

        while (true) { 
            /*采用循环不断从kafka里捞数据*/
            ConsumerRecords<String, String> records = //todo 返回数据;
            //基于异步的流式编程
            Flux.fromIterable(records)
                    .doOnNext(a -> CompletableFuture.supplyAsync(()->{   //创建异步任务
                        return parse1(a);  //todo 处理d中数据
                    },executors).thenAcceptAsync(b->parse2(b)))  //取得parse1处理后的数据进行下一步处理
                    .subscribe();  //订阅处理流
           
        }
}

```
## 归档

  | | Futrue | FutureTask | CompletionService | CompletableFuture|
|:-: | :-: | :-: | :-: | :-:|
|原理 | Futrue接口 | RunnableFuture的实现类| 阻塞队列+FutureTask接口 | Future<T>CompletionStage<T>实现类| 
|支持任务完成先后顺序 | 支持| 未知 | 支持 | 支持|
|异常捕捉 | 代码实现| 代码实现 | 代码实现 | 源生API支持|
|建议 | CPU高速轮询，耗资源| 功能不对口，并发任务这一块多套一层 | 没有JDK8CompletableFuture之前最好的方案| API极端丰富，配合流式编程，速度飞起，推荐使用|




