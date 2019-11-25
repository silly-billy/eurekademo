package tcpsocketclient;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SocketClient {


    public void connect(String host, int port) {
        EventLoopGroup eventLoopGroup = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap(); //注意和 server 的区别
        bootstrap.group(eventLoopGroup);
        bootstrap.channel(NioSocketChannel.class);//注意和 server 端的区别，server 端是 NioServerSocketChannel
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel socketChannel) throws Exception {
                ChannelPipeline pipeline = socketChannel.pipeline();
                pipeline.addLast(new StringDecoder());
                pipeline.addLast(new StringEncoder());
                pipeline.addLast(new SocketClientHandler());
            }
        });
        bootstrap.option(ChannelOption.SO_KEEPALIVE, true);

        try {
            // Start the client.
            ChannelFuture future = bootstrap.connect(host, port).sync();


            /*byte[] msg = "i am socket".getBytes();
            ByteBuf byteBuf = Unpooled.copiedBuffer(msg);*/
            //log.info("client send: {}",byteBuf.toString(CharsetUtil.UTF_8));
            future.channel().writeAndFlush("start12323432454end");

            // 等待服务器  socket 关闭 。
            future.channel().closeFuture().sync();

        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            eventLoopGroup.shutdownGracefully();
        }
    }

    public static void main(String[] args) {
        SocketClient client = new SocketClient();
        client.connect("127.0.0.1", 8080);
    }
}





