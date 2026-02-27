package test_Evaluation_machine.Pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

@Data
@TableName("submit_record")
public class SubmitRecord implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer userId;
    private String problemNum;
    private String taskId;
    private String sourceCode;
    private String language;

    /**
     * 整体判题状态
     * 可选值：WAITING(待判题)、AC(通过)、WA(答案错误)、TLE(超时)、MLE(内存超限)、CE(编译错误)、RE(运行错误)、UNKNOWN(未知错误)
     */
    private String judgeStatus;
    private String judgeMessage;
    private Long usedTime;
    private Long usedMemory;
    private Integer score;
    private String errorMsg;
}
