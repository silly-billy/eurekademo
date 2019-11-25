package nettysocketserver;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

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
            pipelineAdd.websocketAdd(ctx);
            //对于 webSocket ，不设置超时断开
            ctx.pipeline().remove(StringDecoder.class);
            ctx.pipeline().remove(StringEncoder.class);
            ctx.pipeline().remove(DeviceServerHandler.class);
        }else {
            ctx.pipeline().remove(WebServerHandler.class);
        }
        in.resetReaderIndex();
        ctx.pipeline().remove(this.getClass());

    }


    private String getBufStart(ByteBuf in) {
        int length = in.readableBytes();


        byte[] test = new byte[length];
        in.getBytes(in.readerIndex(),test);
        System.out.println(new String(test));

        if (length > MAX_LENGTH) {
            length = MAX_LENGTH;
        }
        // 标记读位置
        in.markReaderIndex();
        byte[] content = new byte[length];
        in.readBytes(content);
        return new String(content);
    }

}
