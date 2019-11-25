package facade;

import lombok.Data;


@Data
public class AppletContext {

    //cmd
    int cmd;
    //pos机设备序列号
    String posDevNo;
    String hash;
    //时间戳
    String timestamp;
    //响应的成功失败代码
    String code;
    //数据
    String data;

}
