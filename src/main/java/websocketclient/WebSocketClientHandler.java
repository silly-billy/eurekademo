package websocketclient;

import io.netty.channel.*;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WebSocketClientHandler extends ChannelInboundHandlerAdapter  {
    WebSocketClientHandshaker handshaker;
    ChannelPromise handshakeFuture;

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        this.handshakeFuture = ctx.newPromise();
    }

    public WebSocketClientHandshaker getHandshaker() {
        return handshaker;
    }

    public void setHandshaker(WebSocketClientHandshaker handshaker) {
        this.handshaker = handshaker;
    }

    public ChannelPromise getHandshakeFuture() {
        return handshakeFuture;
    }

    public void setHandshakeFuture(ChannelPromise handshakeFuture) {
        this.handshakeFuture = handshakeFuture;
    }

    public ChannelFuture handshakeFuture() {
        return this.handshakeFuture;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

        if (!this.handshaker.isHandshakeComplete()) {
            shakeHandsSuccess(ctx,msg);
        }
        else {
            String message = ((TextWebSocketFrame) msg).text().trim();
            System.out.println("socket client: " + message);
        }
        ReferenceCountUtil.release(msg);

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (ctx.channel().isActive()){
            ctx.channel().close();
        }
    }

    private void shakeHandsSuccess(ChannelHandlerContext ctx,Object msg){
        //todo 模拟浏览器设置握手
        Channel ch = ctx.channel();
        FullHttpResponse response;
        try {
            response = (FullHttpResponse) msg;
            //握手协议返回，设置结束握手
            this.handshaker.finishHandshake(ch, response);
            //设置成功
            this.handshakeFuture.setSuccess();
            log.info("WebSocket Client connected!");
            log.info("response headers[sec-websocket-extensions]:{}",response.headers());
        } catch (WebSocketHandshakeException var7) {
            FullHttpResponse res = (FullHttpResponse) msg;
            String errorMsg = String.format("WebSocket Client failed to connect,status:%s,reason:%s", res.status(), res.content().toString(CharsetUtil.UTF_8));
            this.handshakeFuture.setFailure(new Exception(errorMsg));
        }
    }



}
