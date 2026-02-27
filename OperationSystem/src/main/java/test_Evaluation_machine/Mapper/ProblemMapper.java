package test_Evaluation_machine.Mapper;


import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import test_Evaluation_machine.Pojo.Problem;

@Mapper
public interface ProblemMapper {
    @Select("SELECT * FROM problem_info WHERE problem_num = #{problemNum}")
    public Problem getProblem(String problemNum);

}
