package nettysocketserver;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.ResourceLeakDetector;

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
            //内存泄漏调试
            ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.ADVANCED);
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class) // (3)
                    .childHandler(new ChannelInitializer<SocketChannel>() { // (4)
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();
                            //pipeline.addLast(new ReadTimeoutHandler(60)); // 长时间不写会断
                            // ====================== 增加心跳支持 start    ======================
                            // 针对客户端，如果在1分钟时没有向服务端发送读写心跳(ALL)，则主动断开
                            // 如果是读空闲或者写空闲，不处理
                            pipeline.addLast(new IdleStateHandler(0, 0, 15));
                            // 自定义的空闲状态检测
                            //判断是websocket 还是普通socket
                            //如果是websocket 则添加HttpServerCodec()等   否则添加new ProtobufDecoder（）等
                            pipeline.addLast(new SocketChooseHandler());
                            //注意，这个专门针对 Socket 信息的解码器只能放在 SocketChooseHandler 之后，否则会导致 webSocket 连接出错
                            pipeline.addLast(new StringDecoder());
                            pipeline.addLast(new StringEncoder());
                            pipeline.addLast("devicehandler",new DeviceServerHandler());
                            pipeline.addLast("webhandler",new WebServerHandler());
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)          // (5)
                    .childOption(ChannelOption.SO_KEEPALIVE, true); // (6)

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
