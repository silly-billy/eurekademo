import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.FixedLengthFrameDecoder;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.List;

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
            ctx.pipeline().remove(IdleStateHandler.class);
            ctx.pipeline().remove(FixedLengthFrameDecoder.class);
        }
        in.resetReaderIndex();
        ctx.pipeline().remove(this.getClass());

    }


    private String getBufStart(ByteBuf in) {
        int length = in.readableBytes();


        /*byte[] test = new byte[length];
        in.getBytes(in.readerIndex(),test);
        System.out.println(new String(test));*/

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
