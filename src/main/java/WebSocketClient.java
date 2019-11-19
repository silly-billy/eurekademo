import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;

import java.net.URI;
import java.net.URISyntaxException;

public class WebSocketClient {

    public void connect() {
        EventLoopGroup eventLoopGroup = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap(); //注意和 server 的区别
        bootstrap.group(eventLoopGroup);
        bootstrap.channel(NioSocketChannel.class);//注意和 server 端的区别，server 端是 NioServerSocketChannel
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel socketChannel) throws Exception {
                ChannelPipeline pipeline = socketChannel.pipeline();
                pipeline.addLast(new ChannelHandler[]{new HttpClientCodec(),
                        new HttpObjectAggregator(1024*1024*10)});
                pipeline.addLast("hookedHandler", new WebSocketClientHandler());

            }
        });
        bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
        try {
            URI websocketURI = new URI("ws://localhost:8080/ws");
            HttpHeaders httpHeaders = new DefaultHttpHeaders();
            //进行握手
            WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(websocketURI, WebSocketVersion.V13, (String) null, true, httpHeaders);
            System.out.println("host:"+websocketURI.getHost());
            System.out.println("port:"+websocketURI.getPort());
            // Start the client.
            ChannelFuture future = bootstrap.connect(websocketURI.getHost(),websocketURI.getPort()).sync();
            WebSocketClientHandler handler = (WebSocketClientHandler)future.channel().pipeline().get("hookedHandler");
            handler.setHandshaker(handshaker);
            handshaker.handshake(future.channel());
            //阻塞等待是否握手成功
            handler.handshakeFuture().sync();

            TextWebSocketFrame frame = new TextWebSocketFrame("i am websocket");
            future.channel().writeAndFlush(frame);

            // 等待服务器  socket 关闭 。
            future.channel().closeFuture().sync();

        } catch (URISyntaxException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            eventLoopGroup.shutdownGracefully();
        }
    }

    public static void main(String[] args) {
        WebSocketClient client = new WebSocketClient();
        client.connect();
    }
}
