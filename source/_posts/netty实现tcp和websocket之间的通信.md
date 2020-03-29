---
title: netty实现tcp和websocket之间的通信
date: 2020-03-28 20:56:40
tags:
 - netty
 - tcp
 - websocket
categories:
 - netty
---

## 前提

公司要求做一个中间层，用来实现pos机和web端之间的通信。由于年前做通信网关的时候，我接触了些netty的皮毛，深知netty在通信编程中的地位以及强大的api实现，所以这次我也准备用netty来实现此功能。

## netty代码实现

首先根据官网demo写一个主类用于监听pos机和web端的接入

<!-- more -->
``` java
/**
 * @Author:      sillybilly
 * @Date:        2020/3/25 16:16
 * @Description: 服务启动类
 * @Version:     1.0
 */
public class SocketServer {

    private int port;

    public SocketServer(int port) {
        this.port = port;
    }

    public void run() throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup(); // (1)EpollEventLoop
        EventLoopGroup workerGroup = new NioEventLoopGroup();//EpollEventLoopGroup
        try {
            ServerBootstrap b = new ServerBootstrap(); // (2)
            //开启内存泄漏调试
            ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.ADVANCED);
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class) // (3)
                    .childHandler(new ChannelInitializer<SocketChannel>() { // (4)
                        @Override
                        public void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            //pipeline.addLast(new ReadTimeoutHandler(60)); // 长时间不写会断
                            // ====================== 增加心跳支持 start    ======================
                            // 针对客户端，如果在3分钟时没有向服务端发送读写心跳(ALL)，则主动断开
                            // 如果是读空闲或者写空闲，不处理
                            pipeline.addLast(new IdleStateHandler(NETTY_READ_TIMEOUT, NETTY_WRITE_TIMEOUT, NETTY_ALL_TIMEOUT));
                            // 自定义的空闲状态检测
                            //判断是websocket 还是普通socket
                            //如果是websocket 则添加HttpServerCodec()等   否则添加new ProtobufDecoder（）等
                            pipeline.addLast(new SocketChooseHandler());

                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)          // (5)
                    .childOption(ChannelOption.SO_KEEPALIVE, true); // (6)

            //如果需要绑定多个端口 保留
            //List<ChannelFuture> futures = new ArrayList<>();
            //futures.add(b.bind(8080));
            //futures.add(b.bind(8081));
            //for (ChannelFuture f : futures) {
            //    f.channel().closeFuture().sync();
            //}

            // 绑定端口，开始接收进来的连接
            ChannelFuture f = b.bind(port).sync(); // (7)

            System.out.println("Server start listen at " + port );
            // 等待服务器  socket 关闭 。
            // 在这个例子中，这不会发生，但你可以优雅地关闭你的服务器。
            f.channel().closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }

    public static void main(String[] args) throws Exception {
        int port;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        } else {
            port = 8080;
        }
        new SocketServer(port).run();
    }

}
```

NETTY_READ_TIMEOUT, NETTY_WRITE_TIMEOUT, NETTY_ALL_TIMEOUT是读写超时时间,可以根据需要自行设置。SocketChooseHandler是自己定义的handler处理类，主要用来区分不同的通信协议。
下面是SocketChooseHandler代码：
``` java 
/**
 * @Author:      sillybilly
 * @Date:        2020/3/25 16:15
 * @Description: 区分tcpsocket和websocket
 * @Version:     1.0
 */
@Slf4j
public class SocketChooseHandler extends ByteToMessageDecoder {

    /**
     * 默认暗号长度为23(随便取吧)
     */
    private static final int MAX_LENGTH = 23;
    /**
     * WebSocket握手的协议前缀
     */
    private static final String WEBSOCKET_PREFIX = "GET /";

    private PipelineAdd pipelineAdd = new PipelineAdd();


    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        String protocol = getBufStart(in);
        if (protocol.startsWith(WEBSOCKET_PREFIX)) {
            //对于 webSocket ，不设置超时断开
            ctx.pipeline().remove(IdleStateHandler.class);
            pipelineAdd.websocketAdd(ctx);
        }else {
            ByteBuf buf = Unpooled.copiedBuffer("$$".getBytes());
            ctx.pipeline().addLast(new DelimiterBasedFrameDecoder(1024, buf));
            ctx.pipeline().addLast(new StringDecoder());
            ctx.pipeline().addLast(new StringEncoder());
            ctx.pipeline().addLast(new ConnectorIdleState());
            ctx.pipeline().addLast("devicehandler",new DeviceServerHandler());
        }
        in.resetReaderIndex();
        ctx.pipeline().remove(this.getClass());

    }


    private String getBufStart(ByteBuf in) {
        int length = in.readableBytes();


        if (length > MAX_LENGTH) {
            length = MAX_LENGTH;
        }
        // 标记读位置
        in.markReaderIndex();
        byte[] content = new byte[length];
        in.readBytes(content);
        return new String(content);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("{},已上线",ctx.channel());
    }
}
```
因为websocket协议请求头会带上'GET /'标志，所以通过getBufStart方法把tcp和web端的消息做个区分，不同的协议走不同的handler处理类。这里的DelimiterBasedFrameDecoder处理粘包，因为我们的消息格式设计的很简单，消息类型会以”$$“结尾，所以这里用netty自带的解析器就可以了。

### websocket处理

netty有相关处理websocket握手协议的api,当然这也是我选择用netty作为技术栈的原因。通过上文的pipelineAdd.websocketAdd(ctx)处理websocket协议，websocketAdd的方法是这样的：
``` java 
/**
 * @Author:      sillybilly
 * @Date:        2020/3/25 16:12
 * @Description: websoceket协议添加请求头处理handler
 * @Version:     1.0
 */
@Slf4j
public class PipelineAdd {
    public void websocketAdd(ChannelHandlerContext ctx) {

        // HttpServerCodec：将请求和应答消息解码为HTTP消息
        ctx.pipeline().addLast("http-codec",new HttpServerCodec());

        // HttpObjectAggregator：将HTTP消息的多个部分合成一条完整的HTTP消息
        ctx.pipeline().addLast("aggregator",new HttpObjectAggregator(65535));

        // ChunkedWriteHandler：向客户端发送HTML5文件,文件过大会将内存撑爆
        ctx.pipeline().addLast("http-chunked",new ChunkedWriteHandler());

        ctx.pipeline().addLast("WebSocketAggregator",new WebSocketFrameAggregator(65535));

        //解析uri 带?参数
        ctx.pipeline().addLast("url-explained",new CustomUrlHandler());
        //用于处理websocket, /ws为访问websocket时的uri
        ctx.pipeline().addLast("ProtocolHandler", new WebSocketServerProtocolHandler("/ws"));
        //消息处理
        ctx.pipeline().addLast("webhandler",new WebServerHandler());
    }
}
```
其中CustomUrlHandler和WebServerHandler是自己定义的handler，其他是api实现。
CustomUrlHandler是用于web端的登录校验用的，伪代码如下：
``` java
/**
 * @Author: sillybilly
 * @Date: 2020/3/25 16:14
 * @Description: 请求url参数解析
 * @Version: 1.0
 */
@Slf4j
public class CustomUrlHandler extends ChannelInboundHandlerAdapter {

    //设置web端登录超时时间为10分钟 防止恶意重试
    private static final long OVER_TIME = 10 * 60 * 1000L;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        // 只针对FullHttpRequest类型的做处理，其它类型的自动放过
        if (msg instanceof FullHttpRequest) {
            FullHttpRequest request = (FullHttpRequest) msg;
            String uri = request.uri();
            int idx = uri.indexOf("?");
            if (idx > 0) {
                String query = uri.substring(idx + 1);
                // uri中参数的解析使用的是jetty-util包，其性能比自定义及正则性能高。
                MultiMap values = new MultiMap();
                UrlEncoded.decodeTo(query, values, "UTF-8");
                
                //根据约定算法 -- 登录校验
                boolean isValid = loginValidate(values);
                if (isValid) {
                    //登录成功-保存当前登录信息
                    saveWebInfo(***);

                    request.setUri(uri.substring(0, idx));
                } else {
                	//验证失败 关闭连接
                    ctx.disconnect();
                }
            }
        }
        ctx.fireChannelRead(msg);
    }

    /**
     * @return boolean
     * @Author sillybilly
     * @Description values Verification validity
     * @Date 2020/3/4
     * @Param [values, ts]
     */
    private boolean loginValidate(MultiMap values) {
        //ts超时？
        long parmTime = StringToLong(ts).orElseGet(() -> 0L);
        //long diffTime = Clock.systemUTC().millis() - parmTime;
        long current = Clock.systemUTC().millis();
        long diffTime = current - parmTime;
        if (diffTime > OVER_TIME) {
            log.info("CustomUrlHandler.loginValidate 验证超时,{}", ts);
            return false;
        }
        //根据约定算法验证登录者身份信息 成功返回true 失败false
        return ?;
    }

}
```
WebServerHandler是handler的尾链，用来处理成功登录的web端与pos机通信，web端信息和pos机信息分别存放在两个不同本地缓存中，根据登录的身份码完成一对一通信。
WebServerHandler伪代码如下：
``` java 
/**
 * @Author: sillybilly
 * @Date: 2020/3/25 16:16
 * @Description: web端tailhandler
 * @Version: 1.0
 */
@Slf4j
public class WebServerHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // WebSocket消息处理
        try {
            if (msg instanceof WebSocketFrame) {
                String webSocketInfo = ((TextWebSocketFrame) msg).text().trim();
                log.debug("receive webSocket Info:{}", webSocketInfo);
                //找到与其对应的tcp连接并发送消息
                tcpInfO = findTcpInfo()
                ChannelWriteUtils.channelWrite(tcpInfO,webSocketInfo);
                
            }
        } catch (Exception e) {
            e.printStackTrace();
            log.info("WebServerHandler.channelRead 非心跳，非规范业务消息类型");
        }
    }


    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.debug("client: " + ctx.channel() + " disconnect");
        //同时删除对应本地缓存信息
        deleteCache(***);

        super.channelInactive(ctx);
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        if (ctx.channel().isActive()) {
            ctx.channel().close();
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, WebSocketFrame webSocketFrame) throws Exception {

    }
}
```
### tcp处理

tcp端需要处理心跳连接，每次心跳都需要进行缓存。心跳即为ping-pong处理，pos机每次ping结束，netty服务端都需要发送一个pong信息，以便tcp和netty都能确定双方的存活状态。伪代码大概如下：
``` java 
/**
 * @Author:      sillybilly
 * @Date:        2020/5/27 13:36
 * @Description: 心跳发送处理
 * @Version:     1.0
 */
@Slf4j
public class ConnectorIdleState extends ChannelInboundHandlerAdapter {


    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        String heartBeats = String.valueOf(msg);
        //json字符串不合法 解析失败？
        try {
            if 发送的信息为心跳信息{
                //更新redis缓存 pos机心跳的时间戳
                updataHeartBeatsTs();
                //逻辑处理 存入pos机代码
                checkAndSave(ctx.channel());
                //将pong写回pos机
                writeBackHeartBeats(sth.);
                //释放bytebuf
                ReferenceCountUtil.release(msg);
            }else {
                if 此前已登录{
                	//将信息转给下个handler
                    ctx.fireChannelRead(msg);
                }else {
                    ReferenceCountUtil.release(msg);
                    log.info("ConnectorIdleState.channelRead 心跳未接入成功");
                }
            }
        } catch (JsonSyntaxException e){
            ReferenceCountUtil.release(msg);
            log.warn("ConnectorIdleState.channelRead 非心跳，非规范业务消息类型");
        }

    }


    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.debug("client: " + ctx.channel().remoteAddress() + " disconnect");
        //清缓存
        deleteCache(***);
        super.channelInactive(ctx);
    }

}
```

### 测试
我们可以写一个tcp客户端和web端测试一下
以websocket为例:
``` java 
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>WebSocket客户端</title>
</head>
<body>

<script type="text/javascript">

    var socket;

    if (window.WebSocket) {
    	//？后面携带自身信息，包含当前时间戳
        socket = new WebSocket("ws://localhost:8080/ws?********");

        socket.onmessage = function (event) {
            var ta = document.getElementById('responseText');
            ta.value = ta.value + "\n" + event.data;
        };

        socket.onopen = function (event) {
            var ta = document.getElementById('responseText')
            ta.value = "连接开启"
        }

        socket.onclose = function (event) {
            var ta = document.getElementById('responseText');
            ta.value = ta.value + "\n" + "连接关闭";
        }

    } else {
        alert('浏览器不支持WebSocket！')
    }

    function send(message) {
        if (!window.WebSocket) {
            return;
        }
        if (socket.readyState == WebSocket.OPEN) {
            socket.send(message);
        } else {
            alert("连接尚未开启")
        }
    }

</script>

<form onsubmit="return false;">

    <textarea name="message" style="width: 400px;height:200px"></textarea>

    <input type="button" value="发送数据" onclick="send(this.form.message.value)">

    <h3>服务端输出：</h3>

    <textarea id="responseText" style="width: 400px;height: 300px;"></textarea>

    <input type="button" onclick="javascript: document.getElementById('responseText').value=''" value="清除内容">

</form>


</body>
</html>

```