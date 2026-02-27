package test_Evaluation_machine.Pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
@TableName("problem_info")
public class Problem implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String problemNum;
    private Date createTime;
    private Date updateTime;

    private String problemName;
    private String problemDescription;
    private String compileCmd;
    private Long timeLimit;
    private Long memoryLimit;

    // ===================== 多测试点配置 =====================
    /**
     * 测试点配置（JSON格式字符串，存储所有测试点的输入/输出路径、分值）
     * 数据库中存储为TEXT类型，代码中解析为List<TestCase>
     */
    private String testCaseJson;
    //private ProblemStatus status;

    // ===================== 解析测试点JSON =====================
    /**
     * 将testCaseJson解析为测试点列表
     * @return 测试点列表，解析失败返回空列表
     */
    public List<TestCase> getTestCaseList(){
        if(testCaseJson == null || testCaseJson.isEmpty()){
            return new ArrayList<TestCase>();
        }
        try{
            return JSON.parseObject(testCaseJson, new TypeReference<List<TestCase>>(){});
        }catch (Exception e){
            System.err.println("解析测试点JSON失败：" + e.getMessage());
            return new ArrayList<TestCase>();
        }
    }

    /**
     * 将测试点列表转为JSON字符串，存入testCaseJson字段
     * 用途：新增/修改题目时，快速封装测试点配置
     * @param testCaseList 测试点列表
     */
    public void setTestCaseList(List<TestCase> testCaseList){
        if(testCaseJson == null || testCaseJson.isEmpty()){
            this.testCaseJson = "";
            return;
        }
        this.testCaseJson = JSON.toJSONString(testCaseList);
    }




    @Data
    public static class TestCase  implements Serializable{
        private static final long serialVersionUID = 1L;
        private Integer caseId;
        private String inputPath;
        private String outputPath;
        private Integer score;
    }


}
