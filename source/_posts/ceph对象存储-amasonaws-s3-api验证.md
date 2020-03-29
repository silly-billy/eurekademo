---
title: ceph对象存储-amasonaws s3 api验证
date: 2020-03-23 21:01:50
tags: [ceph,java,oss]
categories: 
 - [ceph,oss]
 - [java]
 - [amazonaws s3]
---

## 前提
  Ceph的软件库为客户端应用程序对基于RADOS对象的存储系统提供了直接访问，并为Ceph的某些高级功能（包括RADOS块设备（RBD），RADOS网关（RGW）和Ceph文件系统（ CephFS）提供技术支持。其中Ceph对象网关(RGW)是在librados之上构建的对象存储接口，旨在为应用程序提供通往Ceph存储集群的RESTful网关。Ceph对象存储支持两个接口,它同时兼容amazonaws s3和OpenStack swift。本文主要通过s3调用ceph的对象存储功能，验证数据上传下载的稳定性。
  在ceph官方上查阅s3的api(java)，其实文档已经很老旧了，用来验证程序可能会出现很多问题，地址:[ceph官网](https://docs.ceph.com/docs/master/radosgw/s3/java/)
  推荐直接去amazonaws官网找资料，地址:[amazonaws s3 api](https://docs.amazonaws.cn/AWSJavaSDK/latest/javadoc/index.html)


<!-- more -->
## AmazonS3 maven相关依赖
首先创建一个maven工程，添加AmazonAws S3相关jar包
``` java
<!-- AmazonS3对象存储 -->
<dependency>
   <groupId>com.amazonaws</groupId>
   <artifactId>aws-java-sdk-s3</artifactId>
   <version>1.11.592</version>
</dependency>
<dependency>
   <groupId>com.amazonaws</groupId>
   <artifactId>aws-java-sdk-core</artifactId>
   <version>1.11.592</version>
</dependency>
<dependency>
   <groupId>com.google.guava</groupId>
   <artifactId>guava</artifactId>
   <version>27.0.1-jre</version>
</dependency>
```

## s3 api基本方法
``` java
@Configuration
public class AmazonawsS3utils {
	//s3用户身份验证accessKey、secretKey 配置信息在yml文件中
    @Value("${accessKey:null}")
    private String accessKey;

    @Value("${secretKey:null}")
    private String secretKey;
    //服务器地址
    @Value("${endPoint:null}")
    private String endPoint;
    //自定义一个bucket值
    @Value("${bucketName:null}")
    private String bucketName;

    private static AmazonS3 amazonS3 = null;

    public AmazonS3 getConnection() {
        if (amazonS3 == null) {
            synchronized (this) {
                if (amazonS3 == null) {
                    AWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey);
                    ClientConfiguration clientConfig = new ClientConfiguration();
                    clientConfig.setProtocol(Protocol.HTTP);
                    ////设置用于签署此客户端请求的签名算法的名称。如果未设置或显式设置为null，客户端将根据服务和区域的支持的签名算法的配置文件选择使用的签名算法。
                    clientConfig.setSignerOverride("S3SignerType");
                    amazonS3 = AmazonS3ClientBuilder.standard()
                            .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endPoint, Regions.US_EAST_1.name()))
                            .withPathStyleAccessEnabled(true)
                            .withClientConfiguration(clientConfig)
                            .withCredentials(new AWSStaticCredentialsProvider(credentials))
                            .build();
                    if (!amazonS3.doesBucketExistV2(bucketName)) {
                        amazonS3.createBucket(bucketName);
                    }
                }
            }
        }
        return amazonS3;
    }


    public void upload(String amazonS3Key, InputStream inputStream, ObjectMetadata objectMetadata) {
        AmazonS3 amazonS3 = this.getConnection();
        PutObjectRequest request = new PutObjectRequest(bucketName, amazonS3Key, inputStream, objectMetadata);
        //设置大小接近100MB.
        request.getRequestClientOptions().setReadLimit(1024 * 1204 * 100);
        amazonS3.putObject(request);
    }

    public InputStream download(String key) {
        AmazonS3 amazonS3 = this.getConnection();
        GetObjectRequest request = new GetObjectRequest(bucketName, key);
        S3Object object = amazonS3.getObject(request);
        return object.getObjectContent();
    }

    public void download(String key, File file) {
        AmazonS3 amazonS3 = this.getConnection();
        amazonS3.getObject(new GetObjectRequest(bucketName, key), file);
    }


    public long downloadLength(String key) {
        AmazonS3 amazonS3 = this.getConnection();
        return amazonS3.getObjectMetadata(bucketName, key).getContentLength();
    }

    public void delete(String key) {
        AmazonS3 amazonS3 = this.getConnection();
        amazonS3.deleteObject(bucketName, key);
    }

    public ObjectMetadata getObjectMetadata(String key) {
        AmazonS3 amazonS3 = this.getConnection();
        ObjectMetadata objectMetadata = amazonS3.getObjectMetadata(bucketName, key);
        return objectMetadata;
    }

    public URL getGeneratePresignedUrl(String key){
        AmazonS3 amazonS3 = this.getConnection();
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucketName,key);
        //一个预签名URL，可用于访问Amazon S3资源，而无需URL用户知道账户的AWS安全凭证
        return amazonS3.generatePresignedUrl(request);
    }


    public boolean isExist(String key) {
        AmazonS3 amazonS3 = this.getConnection();
        boolean b = amazonS3.doesObjectExist(bucketName, key);
        return b;
    }

    public boolean isExistByKey(String key) {
        if (StringUtils.isNotBlank(key)) {
            AmazonS3 amazonS3 = this.getConnection();
            boolean b = amazonS3.doesObjectExist(bucketName, key);
            return b;
        }
        return false;
    }
```
## 验证上传的可用性和下载稳定性 

首先创建类实现上传功能,并注入AmazonawsS3utils工具类
SpeedVerifyService.java
``` java
@Service
public class SpeedVerifyService {
	@Autowired
    private AmazonawsS3utils amazonawsS3utils;

	/**
     * 上传文件
     *
     * @param key s3桶键值
     * @param path 源地址路径
     * @throws FileNotFoundException
     */
    public void upload(String key, String path) throws FileNotFoundException {
        File file = new File(path);
        FileInputStream stream = new FileInputStream(file);
        ObjectMetadata data = new ObjectMetadata();
        //大文件上需要预设文件长度，否则会出现outOfMemoryError
        data.setContentLength(file.length());
        amazonawsS3utils.upload(key, stream, data);
    }
}
```
通过创建Junit测试类上传不同格式的文件，在确保程序正常运行，且数据未丢包的情况下，开始侧重验证下载功能

### 单线程验证
s3提供了不同的下载接口，我们这里直接用getObject(GetObjectRequest getObjectRequest, File destinationFile)指定下载路径下载ceph上的文件。验证程序要求计算下载过程中的最大速率、平均速率以及总耗时。所以需要开启一个子线程来实时计算已下载文件的大小。在原SpeedVerifyService.java添加以下代码:
``` java
/**
 * @Author: silly-billy
 * @Date: 2020/3/20 14:23
 * @Description: 上传 下载 删除 测速
 * @Version: 1.0
 */
@Service
@Slf4j
public class SpeedVerifyService {

    @Autowired
    private AmazonawsS3utils amazonawsS3utils;
    /**
     * 上传文件
     *
     * @param key s3桶键值
     * @param path 源地址路径
     * @throws FileNotFoundException
     */
    public void upload(String key, String path) throws FileNotFoundException {
        File file = new File(path);
        FileInputStream stream = new FileInputStream(file);
        ObjectMetadata data = new ObjectMetadata();
        //大文件上需要预设文件长度，否则会出现outOfMemoryError
        data.setContentLength(file.length());
        amazonawsS3utils.upload(key, stream, data);
    }

    /**
     * 删除指定桶-键文件
     *
     * @param key
     */
    public void delete(String key) {
        amazonawsS3utils.delete(key);
    }

    /**
     * 下载文件
     * key-bucket对应键下对象
     * path本地下载地址
     */
    public CompletableFuture<Map<String, Double>> showDownloadDetails(String key, String path) {
        File file = new File(path);
        //开启一个子线程用于测速
        CompletableFuture<Map<String, Double>> future = dowloadListening(key, file);
        //文件下载
        amazonawsS3utils.download(key, file);
        return future;
    }

    /**
     * 下载测速并返回异步结果
     *
     * @param file
     * @return
     */
    private CompletableFuture<Map<String, Double>> dowloadListening(String key, File file) {
        ThreadFactory namedThreadFactory = new ThreadFactoryBuilder()
                .setNameFormat("test-speed-%d").build();
        ExecutorService singleThreadPool = new ThreadPoolExecutor(1, 300,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(1024), namedThreadFactory, new ThreadPoolExecutor.AbortPolicy());
        CompletableFuture<Map<String, Double>> resultFuture = CompletableFuture.supplyAsync(() -> {
            Map<String, Double> resultMap = new HashMap<>();
            try {
                log.info("测速线程启动,{}",Thread.currentThread().getName());
                //文件大小
                long length = amazonawsS3utils.downloadLength(key);
                //下载速度集合
                List<Double> speeds = new ArrayList<>();
                //上一次时间节点
                long lastTime = Clock.systemUTC().millis();
                //当前时间节点
                long currentTime;
                //上一次文件大小
                long lastFileSize = 0L;
                //已下载文件大小
                long currentFileSize;
                //开始下载时间
                long startTime = 0L;
                while (true) {
                    if (!file.exists()) {
                        continue;
                    }
                    if (0L == startTime){
                        startTime = Clock.systemUTC().millis();
                    }
                    currentFileSize = file.length();
                    if (currentFileSize >= length) {
                        if (speeds.isEmpty()) {
                            log.info("已下载文件");
                            break;
                        }
                        currentTime = Clock.systemUTC().millis();
                        //单位 kb/s
                        double speed = (1000 * (currentFileSize - lastFileSize)) / (1024 * (currentTime - lastTime));
                        speeds.add(speed);
                        log.info("实时速度：{}kb/s", speed);
                        log.info("当前进度：{}%", (currentFileSize / (double) length) * 100);
                        double consumeTime = (currentTime - startTime) / 1000;
                        double maxSpeed = speeds.stream().distinct().max(Double::compareTo).get();
                        double avgSpeed = speeds.stream().mapToDouble(x -> x).average().getAsDouble();
                        log.info("下载完成,总耗时：{}s", consumeTime);
                        log.info("最大时速：{}kb/s", maxSpeed);
                        log.info("平均时速：{}kb/s", avgSpeed);
                        resultMap.put("耗时", consumeTime);
                        resultMap.put("最大下载速度", maxSpeed);
                        resultMap.put("平均下载速度", (double) Math.round(avgSpeed * 100) / 100);
                        break;
                    }
                    currentTime = Clock.systemUTC().millis();
                    long size = currentFileSize - lastFileSize;
                    if (size == 0) {
                        //未下载
                        continue;
                    }
                    double speed = (1000 * size) / (1024 * (currentTime - lastTime));
                    lastTime = currentTime;
                    lastFileSize = currentFileSize;
                    speeds.add(speed);
                    log.info("实时速度：{}kb/s", speed);
                    log.info("当前进度：{}%", (currentFileSize / (double) length) * 100);
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                return null;
            }
            return resultMap;
        }, singleThreadPool);

        if (!singleThreadPool.isShutdown()) {
            singleThreadPool.shutdown();
        }

        return resultFuture;
    }


}
```
然后可以通过测试类或者用添加web依赖，用postman进行测试，我们这里新建一个Controller，调用showDownloadDetails(key,file)方法,然后抛出异步返回结果
SpeedVerifyController.java:
``` java
@Controller
@Slf4j
public class SpeedVerifyController {

    @Autowired
    SpeedVerifyService speedVerifyService;

    /**
     * @param key  通键值
     * @param path 下载保存路径
     * @return
     */
    @ResponseBody
    @GetMapping("/showSpeedDetails")
    public Map<String, Double> showSpeedDetails(@RequestParam("key") String key, @RequestParam("path") String path) {
        try {
            Map<String, Double> resultMap = speedVerifyService.showDownloadDetails(key, path).get();
            return resultMap;
        } catch (InterruptedException e) {
            log.error(e.getMessage());
        } catch (ExecutionException e) {
            log.error(e.getMessage());
        }
        return null;
    }
}
```
控制台打印结果:
``` text
2020-03-23 23:06:02.493 INFO  com.set.cephverify.service.SpeedVerifyService - 下载线程启动,Thread[http-nio-8080-exec-1,5,main]
2020-03-23 23:06:02.498 INFO  com.set.cephverify.service.SpeedVerifyService - 测速线程启动,test-speed-0
2020-03-23 23:06:03.057 INFO  com.set.cephverify.service.SpeedVerifyService - 实时速度：350.0kb/s
2020-03-23 23:06:03.057 INFO  com.set.cephverify.service.SpeedVerifyService - 当前进度：0.11664949383248767%
2020-03-23 23:06:04.098 INFO  com.set.cephverify.service.SpeedVerifyService - 实时速度：1066.0kb/s
2020-03-23 23:06:04.099 INFO  com.set.cephverify.service.SpeedVerifyService - 当前进度：11.643242433961678%
...
2020-03-23 23:07:04.877 INFO  com.set.cephverify.service.SpeedVerifyService - 实时速度：1965.0kb/s
2020-03-23 23:07:04.877 INFO  com.set.cephverify.service.SpeedVerifyService - 当前进度：97.21021070550222%
2020-03-23 23:07:05.877 INFO  com.set.cephverify.service.SpeedVerifyService - 实时速度：268.0kb/s
2020-03-23 23:07:05.877 INFO  com.set.cephverify.service.SpeedVerifyService - 当前进度：100.0%
2020-03-23 23:07:05.884 INFO  com.set.cephverify.service.SpeedVerifyService - 下载完成,总耗时：62.0s
2020-03-23 23:07:05.884 INFO  com.set.cephverify.service.SpeedVerifyService - 最大时速：2036.0kb/s
2020-03-23 23:07:05.885 INFO  com.set.cephverify.service.SpeedVerifyService - 平均时速：659.2kb/s
```
postman异步返回值：
![result](https://cdn.jsdelivr.net/gh/silly-billy/eurekademo@blog_info/static/ceph/1.png)
由于我这里是用VPN挂的网络环境，所以速度慢了很多，网络正常的话测试是十几M/s。
尝试下载不同大小的文件(1M-2GB),不同的文件格式(mp3,mp4,txt,exe...),程序执行完成后，根据下载路径打开文件，确保文件未曾损坏或缺失。

### 多线程验证

接着我们需要开启多个线程在ceph服务器上下载同一个文件，以此模拟多用户同时下载某个文件时，确认ceph服务器是否能够提供有效的下载支持以及验证ceph的稳定性。
``` java
/**
     * 模拟多用户同时下载文件，ceph性能验证
     * @param key s3桶键值
     * @param threadNum 线程数
     * @param path 下载保存路径 n个线程对应n个path
     * @return
     */
    public List<CompletableFuture<Map<String,Double>>> showMultiDownloadDetails(String key,int threadNum,final String... path){
        //开启多线程
        var executor = new ThreadPoolExecutor(threadNum, 300, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingDeque<>(1000), new ThreadPoolExecutor.CallerRunsPolicy());
        List<CompletableFuture<Map<String,Double>>> futures = new ArrayList<>();
        for (int i = 0; i < threadNum; i++) {
            int index = i;
            //保存异步处理结果
            CompletableFuture.supplyAsync(()->showDownloadDetails(key,path[index]),executor)
                    .thenAccept(result->{
                        synchronized (this){
                            //同一时间多个线程进入arrayList.add方法会出现不安全问题--数据覆盖
                            futures.add(result);
                        }
                    });
        }
        while (true) {
            if (futures.size() < threadNum) {
                try {
                    //while(true)这种方式判断的执行级别过高，会阻塞其他子线程的执行
                    //所以每次判断后需要sleep一段时间
                    Thread.sleep(100L);
                } catch (InterruptedException e) {
                    log.info(e.getMessage());
                }
                continue;
            }
            break;
        }
        if (!executor.isShutdown()){
            executor.shutdown();
        }
        return futures;
    }
```
耗时任务调用单线程的下载逻辑即可，这里简单的通过异步结果数组个数和线程数做判断，用来确定所有异步future返回了结果(因为实现逻辑的线程数和任务处理个数是一致的)，抛弃这个特殊情况，一般多个异步任务判断是否全部执行完毕，是通过定义volatile字段的数值描述线程执行状态。
注：在多线程的任务调度中，可以通过jdk原生命令调用jps命令获取java运行的pid,然后通过jstack命令查看每个执行线程的堆栈信息，排查问题。

新建一个测试类测试一下结果：
``` java
@Test
void showDownloadDetails() throws ExecutionException, InterruptedException, IOException {
        
    String key = ***;
    int threadNum = ***;
    String[] paths = ***;
    var futures = speedVerifyService.showMultiDownloadDetails(key, threadNum, paths);
        
    System.err.println(
        futures.stream().map(m->{
            try {
                return m.get();
            } catch (InterruptedException e) {
               	e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
            return null;
        });
	)；

    }
```
控制台打印结果：
![result](https://cdn.jsdelivr.net/gh/silly-billy/eurekademo@blog_info/static/ceph/2.png)
经过多次测试，ceph下载文件在单线程状态下没有任何问题。
在多线程状态下，ceph的下载速率随着线程数增多，下载速率会变慢。(发现所有线程总的下载速率和单线程情况下几乎一致，可能用的同一个连接的缘故)
如果文件格式过大，比如1G以上的文件，如果开启超过60个线程，会出现丢包的情况,甚至少数线程并没有执行s3的download方法，查看线程堆栈信息，发现此类线程一直处于wait状态。文件越大，可同时开启的线程数就越少，具体问题未知(有空去问问运维)。